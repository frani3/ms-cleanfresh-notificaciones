package com.cleanfresh.ms_cleanfresh_notificaciones.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Envelope común de los comandos que publica ms-cleanfresh-orders en RabbitMQ (Spec 033). Es el contrato
 * entre ambos servicios; este record es la copia de este lado y tolera campos nuevos.
 *
 * @param type          qué pasó (ORDEN_CREADA u ORDEN_LISTA)
 * @param eventId       identificador único del mensaje: la clave de idempotencia
 * @param timestamp     cuándo se publicó (ISO-8601, UTC)
 * @param traceId       agrupa todo lo que se dispara desde una misma operación
 * @param correlationId el número de orden
 * @param payload       los datos de la orden
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Envelope(
        String type,
        String eventId,
        String timestamp,
        String traceId,
        String correlationId,
        OrdenPayload payload
) {

    /** Datos de la orden. {@code cliente} es el identificador de Cognito; {@code clienteNombre}, el nombre legible (puede ser null). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrdenPayload(
            String numeroOrden,
            String cliente,
            String clienteNombre,
            String servicio,
            String sucursal,
            Double total,
            String fecha
    ) {
    }
}
