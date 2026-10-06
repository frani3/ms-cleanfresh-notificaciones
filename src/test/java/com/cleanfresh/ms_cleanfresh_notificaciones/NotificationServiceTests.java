package com.cleanfresh.ms_cleanfresh_notificaciones;

import com.cleanfresh.ms_cleanfresh_notificaciones.service.NotificationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationServiceTests {

    private final NotificationService service = new NotificationService();

    @Test
    void armaElAvisoConLosDatosDeLaOrden() {
        String body = """
                {"tipo":"ORDEN_CREADA","numeroOrden":"ORD-0012","cliente":"Ana",
                 "servicio":"Planchado","sucursal":"Providencia","total":15000.0,
                 "fecha":"2026-10-05","campoNuevo":"se ignora"}""";

        String aviso = service.procesar(body);

        assertTrue(aviso.contains("ORD-0012"));
        assertTrue(aviso.contains("Ana"));
        assertTrue(aviso.contains("Providencia"));
    }

    @Test
    void rechazaUnCuerpoQueNoEsJson() {
        assertThrows(RuntimeException.class, () -> service.procesar("esto no es json"));
    }

    @Test
    void rechazaUnMensajeSinNumeroDeOrden() {
        assertThrows(RuntimeException.class, () -> service.procesar("{\"tipo\":\"ORDEN_CREADA\"}"));
    }
}
