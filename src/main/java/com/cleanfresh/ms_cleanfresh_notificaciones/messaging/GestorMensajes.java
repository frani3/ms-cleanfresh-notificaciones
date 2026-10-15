package com.cleanfresh.ms_cleanfresh_notificaciones.messaging;

import com.cleanfresh.ms_cleanfresh_notificaciones.service.ComandoService.Resultado;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Consumo de un mensaje con ACK/NACK explícitos (Spec 033):
 * <ul>
 *   <li>Se procesa hasta 3 veces (con una pausa entre intentos).</li>
 *   <li>Si algún intento funciona, se confirma con {@code basicAck}.</li>
 *   <li>Si los 3 fallan, se rechaza con {@code basicNack(requeue=false)}: RabbitMQ lo manda a la DLQ de su
 *       cola por el exchange cmd.dead.dlx, y el siguiente mensaje sigue su curso (no se bloquea la cola).</li>
 * </ul>
 * Un mensaje inválido ("venenoso") falla igual los 3 intentos y termina en la DLQ.
 */
@Component
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true")
public class GestorMensajes {

    /** Lo que hace un flujo con el comando ya leído: recibe el envelope y la clave de enrutamiento. */
    @FunctionalInterface
    public interface ComandoHandler {
        Resultado manejar(Envelope envelope, String claveOrigen);
    }

    static final int MAX_INTENTOS = 3;
    private static final Logger log = LoggerFactory.getLogger(GestorMensajes.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final long[] esperasMs;

    public GestorMensajes() {
        this(new long[]{500, 1000});
    }

    // Para las pruebas: pausas más cortas entre intentos.
    GestorMensajes(long[] esperasMs) {
        this.esperasMs = esperasMs;
    }

    public void consumir(Message message, Channel channel, String flujo, ComandoHandler handler) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        String clave = message.getMessageProperties().getReceivedRoutingKey();
        String cuerpo = new String(message.getBody(), StandardCharsets.UTF_8);

        Resultado resultado = null;
        String eventId = null;
        Exception ultimoError = null;
        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            try {
                Envelope envelope = MAPPER.readValue(cuerpo, Envelope.class);
                eventId = envelope == null ? null : envelope.eventId();
                resultado = handler.manejar(envelope, clave);
                ultimoError = null;
                break;
            } catch (Exception e) {
                ultimoError = e;
                log.warn("[{}] intento {}/{} falló para {}: {}", flujo, intento, MAX_INTENTOS, clave, e.toString());
                if (intento < MAX_INTENTOS) {
                    pausar(esperasMs[Math.min(intento - 1, esperasMs.length - 1)]);
                }
            }
        }

        if (ultimoError == null) {
            channel.basicAck(tag, false);
            log.info("[{}] {} eventId {} -> {} (ACK)", flujo, clave, eventId, resultado);
        } else {
            channel.basicNack(tag, false, false);
            log.error("[{}] {} se rechazó tras {} intentos y va a la DLQ (NACK sin reencolar): {}",
                    flujo, clave, MAX_INTENTOS, ultimoError.toString());
        }
    }

    private void pausar(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
