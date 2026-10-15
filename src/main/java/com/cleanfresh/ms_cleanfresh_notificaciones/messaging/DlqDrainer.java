package com.cleanfresh.ms_cleanfresh_notificaciones.messaging;

import com.cleanfresh.ms_cleanfresh_notificaciones.service.MensajeMuertoService;
import com.rabbitmq.client.GetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Registra los mensajes que no se pudieron entregar (Spec 033). Cada cierto tiempo revisa las tres DLQ y,
 * por cada mensaje, lo guarda en el log y en la base (MensajeMuertoService) y recién entonces lo confirma:
 * si el guardado falla, el mensaje se devuelve a la DLQ y no se pierde.
 *
 * Revisa cada {@code app.rabbit.dlq-intervalo-ms} (30 s por defecto) en vez de consumir de inmediato, para que
 * un mensaje rechazado se pueda ver en la DLQ del panel de RabbitMQ antes de que se registre.
 */
@Component
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true")
public class DlqDrainer {

    static final List<String> DLQS = List.of("q.cmd.email.dlq", "q.cmd.kitchen.dlq", "q.cmd.invoice.dlq");
    static final int MAX_POR_PASADA = 50;

    private static final Logger log = LoggerFactory.getLogger(DlqDrainer.class);
    private static final DefaultMessagePropertiesConverter CONVERTIDOR = new DefaultMessagePropertiesConverter();

    private final RabbitTemplate rabbit;
    private final MensajeMuertoService muertos;

    public DlqDrainer(RabbitTemplate rabbit, MensajeMuertoService muertos) {
        this.rabbit = rabbit;
        this.muertos = muertos;
    }

    @Scheduled(fixedDelayString = "${app.rabbit.dlq-intervalo-ms:30000}")
    public void revisar() {
        for (String dlq : DLQS) {
            try {
                drenar(dlq);
            } catch (RuntimeException e) {
                log.warn("No se pudo revisar {}: {}", dlq, e.toString());
            }
        }
    }

    /** Registra y confirma los mensajes de una DLQ; devuelve cuántos registró. */
    int drenar(String dlq) {
        int registrados = 0;
        for (int i = 0; i < MAX_POR_PASADA; i++) {
            Boolean hubo = rabbit.execute(channel -> {
                GetResponse respuesta = channel.basicGet(dlq, false);
                if (respuesta == null) {
                    return false;
                }
                long tag = respuesta.getEnvelope().getDeliveryTag();
                try {
                    registrar(dlq, respuesta);
                    channel.basicAck(tag, false);
                    return true;
                } catch (RuntimeException e) {
                    // No se pudo guardar: se devuelve a la DLQ para reintentar en la próxima pasada.
                    channel.basicNack(tag, false, true);
                    throw e;
                }
            });
            if (!Boolean.TRUE.equals(hubo)) {
                break;
            }
            registrados++;
        }
        return registrados;
    }

    private void registrar(String dlq, GetResponse respuesta) {
        MessageProperties props = CONVERTIDOR.toMessageProperties(respuesta.getProps(), respuesta.getEnvelope(), "UTF-8");
        Map<String, ?> muerte = primeraMuerte(props);
        String colaOrigen = muerte == null ? null : String.valueOf(muerte.get("queue"));
        String motivo = muerte == null ? null : String.valueOf(muerte.get("reason"));
        long intentos = muerte != null && muerte.get("count") instanceof Number n ? n.longValue() : 1;
        muertos.registrar(dlq, colaOrigen, motivo, intentos, new String(respuesta.getBody(), StandardCharsets.UTF_8));
    }

    private Map<String, ?> primeraMuerte(MessageProperties props) {
        List<Map<String, ?>> muertes = props.getXDeathHeader();
        return muertes == null || muertes.isEmpty() ? null : muertes.get(0);
    }
}
