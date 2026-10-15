package com.cleanfresh.ms_cleanfresh_notificaciones;

import com.cleanfresh.ms_cleanfresh_notificaciones.dto.NotificationResponse;
import com.cleanfresh.ms_cleanfresh_notificaciones.messaging.Envelope;
import com.cleanfresh.ms_cleanfresh_notificaciones.repository.EventoProcesadoJpaRepository;
import com.cleanfresh.ms_cleanfresh_notificaciones.repository.NotificationJpaRepository;
import com.cleanfresh.ms_cleanfresh_notificaciones.service.ComandoService;
import com.cleanfresh.ms_cleanfresh_notificaciones.service.ComandoService.Resultado;
import com.cleanfresh.ms_cleanfresh_notificaciones.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.cleanfresh.ms_cleanfresh_notificaciones.Sembrador.envelope;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class NotificationServiceTests {

    @Autowired
    private NotificationService service;

    @Autowired
    private ComandoService comandos;

    @Autowired
    private NotificationJpaRepository repository;

    @Autowired
    private EventoProcesadoJpaRepository eventos;

    @BeforeEach
    void limpiar() {
        repository.deleteAll();
        eventos.deleteAll();
    }

    private void email(Envelope e) {
        comandos.email(e, "email.send");
    }

    @Test
    void ordenCreadaAvisaALaSucursalDeLaOrden() {
        email(envelope("ORDEN_CREADA", "ORD-0100", "ana-uuid", null, "Providencia"));

        var guardadas = service.listar(null, "Providencia");
        assertEquals(1, guardadas.size());
        assertEquals("SUCURSAL", guardadas.get(0).destinatarioTipo());
        assertEquals("ORD-0100", guardadas.get(0).numeroOrden());
        assertTrue(guardadas.get(0).mensaje().contains("15.000"));
        assertFalse(guardadas.get(0).leida());
        assertTrue(service.listar("ana-uuid", null).isEmpty());
    }

    @Test
    void ordenListaAvisaAlClienteDueno() {
        email(envelope("ORDEN_LISTA", "ORD-0101", "ana-uuid", null, "Providencia"));

        var delCliente = service.listar("ana-uuid", null);
        assertEquals(1, delCliente.size());
        assertEquals("CLIENTE", delCliente.get(0).destinatarioTipo());
        assertTrue(delCliente.get(0).mensaje().contains("ORD-0101"));
        assertTrue(delCliente.get(0).mensaje().contains("listo"));
        assertTrue(service.listar("otro-uuid", null).isEmpty());
        assertTrue(service.listar(null, "Providencia").isEmpty());
    }

    @Test
    void elMismoNumeroConOtroTipoSiSeGuarda() {
        email(envelope("ORDEN_CREADA", "ORD-0103", "ana-uuid", null, "Providencia"));
        email(envelope("ORDEN_LISTA", "ORD-0103", "ana-uuid", null, "Providencia"));

        assertEquals(2, service.listar(null, null).size());
    }

    @Test
    void sinFiltroDevuelveTodasDeLaMasNuevaALaMasVieja() {
        email(envelope("ORDEN_CREADA", "ORD-0105", "ana-uuid", null, "Providencia"));
        email(envelope("ORDEN_LISTA", "ORD-0106", "ana-uuid", null, "Providencia"));

        var todas = service.listar(null, null);

        assertEquals(2, todas.size());
        assertEquals("ORD-0106", todas.get(0).numeroOrden());
    }

    @Test
    void marcarLeidasSoloAfectaAlDestinatarioIndicado() {
        email(envelope("ORDEN_LISTA", "ORD-0107", "ana-uuid", null, "Providencia"));
        email(envelope("ORDEN_LISTA", "ORD-0108", "luis-uuid", null, "Providencia"));

        assertEquals(1, service.marcarLeidas("ana-uuid", null));

        assertTrue(service.listar("ana-uuid", null).get(0).leida());
        assertFalse(service.listar("luis-uuid", null).get(0).leida());
        assertEquals(0, service.marcarLeidas("ana-uuid", null));
    }

    @Test
    void marcarLeidasSinFiltroMarcaTodas() {
        email(envelope("ORDEN_CREADA", "ORD-0109", "ana-uuid", null, "Providencia"));
        email(envelope("ORDEN_LISTA", "ORD-0110", "ana-uuid", null, "Providencia"));

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
    void elAvisoALaSucursalMuestraElNombreLegibleDelCliente() {
        email(envelope("ORDEN_CREADA", "ORD-0300", "64888468-uuid", "cliente@cleanfresh.com", "Providencia"));

        String mensaje = service.listar(null, "Providencia").get(0).mensaje();
        assertTrue(mensaje.contains("cliente@cleanfresh.com"));
        assertFalse(mensaje.contains("64888468-uuid"));
        assertEquals("Providencia", service.listar(null, "Providencia").get(0).destinatario());
    }

    @Test
    void sinNombreLegibleElAvisoMuestraElIdentificador() {
        email(envelope("ORDEN_CREADA", "ORD-0301", "Maria Gonzalez", null, "Providencia"));
        email(envelope("ORDEN_CREADA", "ORD-0302", "64888468-uuid", "", "Providencia"));
        email(envelope("ORDEN_CREADA", "ORD-0303", "otro-uuid", "   ", "Providencia"));

        var avisos = service.listar(null, "Providencia");
        assertTrue(avisos.stream().anyMatch(a -> a.numeroOrden().equals("ORD-0301") && a.mensaje().contains("Maria Gonzalez")));
        assertTrue(avisos.stream().anyMatch(a -> a.numeroOrden().equals("ORD-0302") && a.mensaje().contains("64888468-uuid")));
        assertTrue(avisos.stream().anyMatch(a -> a.numeroOrden().equals("ORD-0303") && a.mensaje().contains("otro-uuid")));
    }

    @Test
    void elAvisoDePedidoListoSigueDirigidoAlIdentificadorAunqueHayaNombre() {
        email(envelope("ORDEN_LISTA", "ORD-0304", "64888468-uuid", "cliente@cleanfresh.com", "Providencia"));

        var porId = service.listar("64888468-uuid", null);
        assertEquals(1, porId.size());
        assertEquals("64888468-uuid", porId.get(0).destinatario());
        assertTrue(service.listar("cliente@cleanfresh.com", null).isEmpty());
    }

    @Test
    void elFlujoEmailAceptaSoloLosDosTiposDeEvento() {
        assertThrows(IllegalArgumentException.class,
                () -> email(envelope("ORDEN_EXTRAÑA", "ORD-0104", "ana-uuid", null, "Providencia")));

        assertTrue(service.listar(null, null).isEmpty());
        assertEquals(Resultado.PROCESADO, comandos.email(envelope("ORDEN_CREADA", "ORD-0111", "ana-uuid", null, "Providencia"), "email.send"));
    }
}
