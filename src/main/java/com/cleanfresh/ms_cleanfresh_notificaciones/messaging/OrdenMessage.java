package com.cleanfresh.ms_cleanfresh_notificaciones.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Cuerpo (JSON) del mensaje que publica ms-cleanfresh-orders. Es el contrato
 * entre ambos servicios; este record es la copia de este lado y tolera campos
 * nuevos que orders agregue más adelante. {@code tipo} es ORDEN_CREADA u
 * ORDEN_LISTA.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrdenMessage(
        String tipo,
        String numeroOrden,
        String cliente,
        String servicio,
        String sucursal,
        Double total,
        String fecha
) {
}
