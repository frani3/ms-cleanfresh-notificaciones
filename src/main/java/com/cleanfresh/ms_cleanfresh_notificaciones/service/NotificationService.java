package com.cleanfresh.ms_cleanfresh_notificaciones.service;

import com.cleanfresh.ms_cleanfresh_notificaciones.messaging.OrdenCreadaMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * Procesa el cuerpo de un mensaje ORDEN_CREADA: registra el aviso (sin
     * envío real de correo en esta entrega) y lo devuelve. Lanza una excepción
     * si el cuerpo no es un mensaje válido.
     */
    public String procesar(String body) {
        OrdenCreadaMessage mensaje = MAPPER.readValue(body, OrdenCreadaMessage.class);
        if (mensaje.numeroOrden() == null) {
            throw new IllegalArgumentException("El mensaje no trae numeroOrden");
        }
        String aviso = "Notificación: la orden %s de %s (%s, sucursal %s, total %s) fue creada"
                .formatted(mensaje.numeroOrden(), mensaje.cliente(), mensaje.servicio(),
                        mensaje.sucursal(), mensaje.total());
        log.info(aviso);
        return aviso;
    }
}
