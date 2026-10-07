package com.cleanfresh.ms_cleanfresh_notificaciones.dto;

public record NotificationResponse(
        Long id,
        String tipo,
        String destinatarioTipo,
        String destinatario,
        String numeroOrden,
        String mensaje,
        String fechaHora,
        boolean leida
) {
}
