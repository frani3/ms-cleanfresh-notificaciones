package com.cleanfresh.ms_cleanfresh_notificaciones;

import com.cleanfresh.ms_cleanfresh_notificaciones.messaging.Envelope;

import java.time.Instant;
import java.util.UUID;

/** Arma envelopes de prueba con el mismo formato que publica orders. */
final class Sembrador {

    private Sembrador() {
    }

    static Envelope envelope(String type, String numeroOrden, String cliente, String clienteNombre, String sucursal) {
        return envelope(type, UUID.randomUUID().toString(), numeroOrden, cliente, clienteNombre, sucursal, "Planchado", 15000.0);
    }

    static Envelope envelope(String type, String eventId, String numeroOrden, String cliente, String clienteNombre,
                             String sucursal, String servicio, Double total) {
        return envelope(type, eventId, numeroOrden, cliente, clienteNombre, sucursal, servicio, total, null);
    }

    /** Aviso de cambio de estado (ORDEN_ESTADO): el estado nuevo viaja en el payload. */
    static Envelope estado(String eventId, String numeroOrden, String cliente, String estado) {
        return envelope("ORDEN_ESTADO", eventId, numeroOrden, cliente, null, "Providencia", "Planchado", 9500.0, estado);
    }

    static Envelope envelope(String type, String eventId, String numeroOrden, String cliente, String clienteNombre,
                             String sucursal, String servicio, Double total, String estado) {
        return new Envelope(type, eventId, Instant.now().toString(), UUID.randomUUID().toString(), numeroOrden,
                new Envelope.OrdenPayload(numeroOrden, cliente, clienteNombre, servicio, sucursal, total, "2026-10-15", estado));
    }
}
