package com.cleanfresh.ms_cleanfresh_notificaciones.messaging;

import com.cleanfresh.ms_cleanfresh_notificaciones.entity.MensajeMuertoEntity;
import com.cleanfresh.ms_cleanfresh_notificaciones.repository.MensajeMuertoJpaRepository;
import com.cleanfresh.ms_cleanfresh_notificaciones.service.MensajeMuertoService;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.RabbitExceptionTranslator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec 033: los mensajes de las DLQ no se quedan mudos. Se registran (con cola de origen, motivo y
 * intentos) y solo entonces se confirman; si no se pueden guardar, vuelven a la DLQ.
 */
@SpringBootTest
class DlqDrainerTests {

    private static final String CUERPO = "{\"type\":\"ORDEN_CREADA\",\"eventId\":\"evt-muerto-1\",\"payload\":{\"numeroOrden\":\"ORD-0007\"}}";

    @Autowired
    private MensajeMuertoService muertos;

    @Autowired
    private MensajeMuertoJpaRepository repository;

    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final Channel channel = mock(Channel.class);

    @SuppressWarnings("unchecked")
    @BeforeEach
    void preparar() throws Exception {
        repository.deleteAll();
        // RabbitTemplate.execute ejecuta el callback con el canal simulado
        // Igual que el RabbitTemplate real: los errores del canal salen como excepciones de Spring AMQP
        when(rabbit.execute(any(ChannelCallback.class))).thenAnswer(inv -> {
            try {
                return ((ChannelCallback<?>) inv.getArgument(0)).doInRabbit(channel);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw RabbitExceptionTranslator.convertRabbitAccessException(e);
            }
        });
    }

    private static GetResponse muerto(long tag, String cuerpo, String cola, String motivo, long veces) {
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .headers(Map.of("x-death", List.of(Map.of("queue", cola, "reason", motivo, "count", veces))))
                .build();
        return new GetResponse(new com.rabbitmq.client.Envelope(tag, false, "cmd.dead.dlx", "email.send"),
                props, cuerpo.getBytes(StandardCharsets.UTF_8), 0);
    }

    @Test
    void registraElMensajeMuertoConSuOrigenYLoConfirma() throws Exception {
        when(channel.basicGet("q.cmd.email.dlq", false)).thenReturn(muerto(7L, CUERPO, "q.cmd.email", "rejected", 3L), (GetResponse) null);
        DlqDrainer drainer = new DlqDrainer(rabbit, muertos);

        int registrados = drainer.drenar("q.cmd.email.dlq");

        assertEquals(1, registrados);
        MensajeMuertoEntity guardado = repository.findAllByOrderByIdDesc().get(0);
        assertEquals("q.cmd.email.dlq", guardado.getDlq());
        assertEquals("q.cmd.email", guardado.getColaOrigen());
        assertEquals("rejected", guardado.getMotivo());
        assertEquals(3, guardado.getIntentos());
        assertEquals("evt-muerto-1", guardado.getEventId());
        assertEquals(CUERPO, guardado.getCuerpo());
        verify(channel).basicAck(7L, false);
    }

    @Test
    void unCuerpoQueNoEsJsonSeRegistraIgualSinEventId() throws Exception {
        when(channel.basicGet("q.cmd.kitchen.dlq", false)).thenReturn(muerto(8L, "basura", "q.cmd.kitchen", "rejected", 1L), (GetResponse) null);

        new DlqDrainer(rabbit, muertos).drenar("q.cmd.kitchen.dlq");

        MensajeMuertoEntity guardado = repository.findAllByOrderByIdDesc().get(0);
        assertEquals("basura", guardado.getCuerpo());
        assertNull(guardado.getEventId());
        assertEquals("q.cmd.kitchen", guardado.getColaOrigen());
    }

    @Test
    void drenaTodosLosMensajesDeLaDlqHastaVaciarla() throws Exception {
        when(channel.basicGet("q.cmd.invoice.dlq", false)).thenReturn(
                muerto(1L, CUERPO, "q.cmd.invoice", "rejected", 3L),
                muerto(2L, CUERPO, "q.cmd.invoice", "rejected", 3L),
                muerto(3L, CUERPO, "q.cmd.invoice", "rejected", 3L),
                (GetResponse) null);

        int registrados = new DlqDrainer(rabbit, muertos).drenar("q.cmd.invoice.dlq");

        assertEquals(3, registrados);
        assertEquals(3, repository.count());
        verify(channel).basicAck(1L, false);
        verify(channel).basicAck(2L, false);
        verify(channel).basicAck(3L, false);
    }

    @Test
    void siLaDlqEstaVaciaNoHaceNada() throws Exception {
        when(channel.basicGet(anyString(), anyBoolean())).thenReturn(null);

        assertEquals(0, new DlqDrainer(rabbit, muertos).drenar("q.cmd.email.dlq"));
        assertEquals(0, repository.count());
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void siNoSePuedeGuardarElMensajeVuelveALaDlqYNoSePierde() throws Exception {
        MensajeMuertoService roto = mock(MensajeMuertoService.class);
        when(roto.registrar(anyString(), any(), any(), anyLong(), any())).thenThrow(new IllegalStateException("base caída"));
        when(channel.basicGet("q.cmd.email.dlq", false)).thenReturn(muerto(9L, CUERPO, "q.cmd.email", "rejected", 3L));

        assertThrows(IllegalStateException.class, () -> new DlqDrainer(rabbit, roto).drenar("q.cmd.email.dlq"));

        verify(channel).basicNack(9L, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void revisarRecorreLasTresDlqYNoSeCaeSiUnaFalla() throws Exception {
        when(channel.basicGet("q.cmd.email.dlq", false)).thenThrow(new java.io.IOException("canal cerrado"));
        when(channel.basicGet("q.cmd.kitchen.dlq", false)).thenReturn(muerto(4L, CUERPO, "q.cmd.kitchen", "rejected", 3L), (GetResponse) null);
        when(channel.basicGet("q.cmd.invoice.dlq", false)).thenReturn((GetResponse) null);

        new DlqDrainer(rabbit, muertos).revisar();

        assertEquals(1, repository.count());
        verify(channel).basicAck(4L, false);
    }
}
