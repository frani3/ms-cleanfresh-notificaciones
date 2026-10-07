package com.cleanfresh.ms_cleanfresh_notificaciones;

import com.cleanfresh.ms_cleanfresh_notificaciones.dto.NotificationResponse;
import com.cleanfresh.ms_cleanfresh_notificaciones.repository.NotificationJpaRepository;
import com.cleanfresh.ms_cleanfresh_notificaciones.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class NotificationServiceTests {

    @Autowired
    private NotificationService service;

    @Autowired
    private NotificationJpaRepository repository;

    @BeforeEach
    void limpiar() {
        repository.deleteAll();
    }

    private static String mensaje(String tipo, String numeroOrden, String cliente, String sucursal) {
        return """
                {"tipo":"%s","numeroOrden":"%s","cliente":"%s","servicio":"Planchado",
                 "sucursal":"%s","total":15000.0,"fecha":"2026-10-07","campoNuevo":"se ignora"}"""
                .formatted(tipo, numeroOrden, cliente, sucursal);
    }

    @Test
    void ordenCreadaAvisaALaSucursalDeLaOrden() {
        String aviso = service.procesar(mensaje("ORDEN_CREADA", "ORD-0100", "ana-uuid", "Providencia"));

        assertTrue(aviso.contains("ORD-0100"));
        assertTrue(aviso.contains("Providencia"));
        assertTrue(aviso.contains("15.000"));
        var guardadas = service.listar(null, "Providencia");
        assertEquals(1, guardadas.size());
        assertEquals("SUCURSAL", guardadas.get(0).destinatarioTipo());
        assertEquals("ORD-0100", guardadas.get(0).numeroOrden());
        assertFalse(guardadas.get(0).leida());
        assertTrue(service.listar("ana-uuid", null).isEmpty());
    }

    @Test
    void ordenListaAvisaAlClienteDueno() {
        service.procesar(mensaje("ORDEN_LISTA", "ORD-0101", "ana-uuid", "Providencia"));

        var delCliente = service.listar("ana-uuid", null);
        assertEquals(1, delCliente.size());
        assertEquals("CLIENTE", delCliente.get(0).destinatarioTipo());
        assertTrue(delCliente.get(0).mensaje().contains("ORD-0101"));
        assertTrue(delCliente.get(0).mensaje().contains("listo"));
        assertTrue(service.listar("otro-uuid", null).isEmpty());
        assertTrue(service.listar(null, "Providencia").isEmpty());
    }

    @Test
    void unMensajeRepetidoNoDuplicaElAviso() {
        String body = mensaje("ORDEN_CREADA", "ORD-0102", "ana-uuid", "Maipú");

        service.procesar(body);
        service.procesar(body);

        assertEquals(1, service.listar(null, "Maipú").size());
    }

    @Test
    void elMismoNumeroConOtroTipoSiSeGuarda() {
        service.procesar(mensaje("ORDEN_CREADA", "ORD-0103", "ana-uuid", "Providencia"));
        service.procesar(mensaje("ORDEN_LISTA", "ORD-0103", "ana-uuid", "Providencia"));

        assertEquals(2, service.listar(null, null).size());
    }

    @Test
    void unTipoDesconocidoSeIgnora() {
        assertNull(service.procesar(mensaje("ORDEN_EXTRAÑA", "ORD-0104", "ana-uuid", "Providencia")));

        assertTrue(service.listar(null, null).isEmpty());
    }

    @Test
    void sinFiltroDevuelveTodasDeLaMasNuevaALaMasVieja() {
        service.procesar(mensaje("ORDEN_CREADA", "ORD-0105", "ana-uuid", "Providencia"));
        service.procesar(mensaje("ORDEN_LISTA", "ORD-0106", "ana-uuid", "Providencia"));

        var todas = service.listar(null, null);

        assertEquals(2, todas.size());
        assertEquals("ORD-0106", todas.get(0).numeroOrden());
    }

    @Test
    void marcarLeidasSoloAfectaAlDestinatarioIndicado() {
        service.procesar(mensaje("ORDEN_LISTA", "ORD-0107", "ana-uuid", "Providencia"));
        service.procesar(mensaje("ORDEN_LISTA", "ORD-0108", "luis-uuid", "Providencia"));

        assertEquals(1, service.marcarLeidas("ana-uuid", null));

        assertTrue(service.listar("ana-uuid", null).get(0).leida());
        assertFalse(service.listar("luis-uuid", null).get(0).leida());
        assertEquals(0, service.marcarLeidas("ana-uuid", null));
    }

    @Test
    void marcarLeidasSinFiltroMarcaTodas() {
        service.procesar(mensaje("ORDEN_CREADA", "ORD-0109", "ana-uuid", "Providencia"));
        service.procesar(mensaje("ORDEN_LISTA", "ORD-0110", "ana-uuid", "Providencia"));

        assertEquals(2, service.marcarLeidas(null, null));

        assertTrue(service.listar(null, null).stream().allMatch(NotificationResponse::leida));
    }

    @Test
    void unFiltroEnBlancoOAmbosFiltrosSonUnError() {
        assertThrows(IllegalArgumentException.class, () -> service.listar("", null));
        assertThrows(IllegalArgumentException.class, () -> service.listar(null, "  "));
        assertThrows(IllegalArgumentException.class, () -> service.listar("ana-uuid", "Providencia"));
        assertThrows(IllegalArgumentException.class, () -> service.marcarLeidas("", null));
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
