package com.cleanfresh.ms_cleanfresh_notificaciones.messaging;

import com.cleanfresh.ms_cleanfresh_notificaciones.service.ComandoService.Resultado;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Spec 033: ACK/NACK explícitos. Con un canal simulado se comprueba qué le dice el consumidor al broker:
 * ACK al procesar, y NACK sin reencolar (= a la DLQ) cuando los 3 intentos fallan.
 */
class GestorMensajesTests {

    private static final long TAG = 42L;
    private static final String VALIDO = """
            {"type":"ORDEN_CREADA","eventId":"evt-1","timestamp":"2026-10-15T14:00:00Z","traceId":"t","correlationId":"ORD-0007",
             "payload":{"numeroOrden":"ORD-0007","cliente":"uuid","clienteNombre":null,"servicio":"Planchado",
                        "sucursal":"Providencia","total":15000.0,"fecha":"2026-10-15"},"campoNuevo":"se ignora"}""";

    private final Channel channel = mock(Channel.class);
    private final GestorMensajes gestor = new GestorMensajes(new long[]{1, 1});

    private static Message mensaje(String cuerpo, String clave) {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(TAG);
        props.setReceivedRoutingKey(clave);
        return new Message(cuerpo.getBytes(StandardCharsets.UTF_8), props);
    }

    @Test
    void siSeProcesaBienSeConfirmaConAck() throws Exception {
        AtomicInteger llamadas = new AtomicInteger();

        gestor.consumir(mensaje(VALIDO, "email.send"), channel, "email", (e, clave) -> {
            llamadas.incrementAndGet();
            assertEquals("evt-1", e.eventId());
            assertEquals("ORD-0007", e.payload().numeroOrden());
            assertEquals("email.send", clave);
            return Resultado.PROCESADO;
        });

        assertEquals(1, llamadas.get());
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void unMensajeRepetidoTambienSeConfirma() throws Exception {
        gestor.consumir(mensaje(VALIDO, "email.send"), channel, "email", (e, clave) -> Resultado.DUPLICADO);

        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void siFallaDosVecesYALaTerceraFuncionaSeConfirma() throws Exception {
        AtomicInteger llamadas = new AtomicInteger();

        gestor.consumir(mensaje(VALIDO, "kitchen.ticket"), channel, "kitchen", (e, clave) -> {
            if (llamadas.incrementAndGet() < 3) {
                throw new IllegalStateException("base caída");
            }
            return Resultado.PROCESADO;
        });

        assertEquals(3, llamadas.get());
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void siFallanLosTresIntentosSeRechazaSinReencolarYVaALaDlq() throws Exception {
        AtomicInteger llamadas = new AtomicInteger();

        gestor.consumir(mensaje(VALIDO, "invoice.gen"), channel, "invoice", (e, clave) -> {
            llamadas.incrementAndGet();
            throw new IllegalStateException("siempre falla");
        });

        assertEquals(3, llamadas.get());
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void unMensajeVenenosoQueNoEsJsonSeRechazaSinReencolar() throws Exception {
        AtomicInteger llamadas = new AtomicInteger();

        gestor.consumir(mensaje("esto no es json", "email.send"), channel, "email", (e, clave) -> {
            llamadas.incrementAndGet();
            return Resultado.PROCESADO;
        });

        assertEquals(0, llamadas.get());
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void unEnvelopeIncompletoQueElServicioRechazaTerminaEnLaDlq() throws Exception {
        String sinOrden = "{\"type\":\"ORDEN_CREADA\",\"eventId\":\"evt-2\"}";

        gestor.consumir(mensaje(sinOrden, "email.send"), channel, "email", (e, clave) -> {
            throw new IllegalArgumentException("El mensaje no trae payload.numeroOrden");
        });

        verify(channel).basicNack(TAG, false, false);
    }
}
