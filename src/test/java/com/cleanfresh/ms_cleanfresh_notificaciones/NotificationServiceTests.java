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

    // ---------- Spec 035: un aviso al Cliente por cada cambio de estado ----------

    private void estado(String eventId, String orden, String cliente, String estado) {
        comandos.email(Sembrador.estado(eventId, orden, cliente, estado), "email.send");
    }

    @Test
    void cadaCambioDeEstadoAvisaAlClienteConSuPropiaFrase() {
        estado("e1", "ORD-0301", "ana-uuid", "ACEPTADO");
        estado("e2", "ORD-0301", "ana-uuid", "EN_PREPARACION");
        estado("e3", "ORD-0301", "ana-uuid", "ENTREGADO");
        estado("e4", "ORD-0301", "ana-uuid", "CANCELADO");

        var avisos = service.listar("ana-uuid", null);
        assertEquals(4, avisos.size());
        var mensajes = avisos.stream().map(NotificationResponse::mensaje).toList();
        assertTrue(mensajes.contains("Tu pedido ORD-0301 (Planchado) fue aceptado"));
        assertTrue(mensajes.contains("Tu pedido ORD-0301 (Planchado) está en preparación"));
        assertTrue(mensajes.contains("Tu pedido ORD-0301 (Planchado) fue entregado"));
        assertTrue(mensajes.contains("Tu pedido ORD-0301 (Planchado) fue cancelado"));
        assertTrue(avisos.stream().allMatch(a -> a.tipo().equals("ORDEN_ESTADO") && a.destinatarioTipo().equals("CLIENTE") && !a.leida()));
    }

    @Test
    void elAvisoDeEstadoLeLlegaSoloAlClienteDuenoYNoALaSucursal() {
        estado("e1", "ORD-0302", "ana-uuid", "ACEPTADO");

        assertEquals(1, service.listar("ana-uuid", null).size());
        assertTrue(service.listar("otro-uuid", null).isEmpty());
        assertTrue(service.listar(null, "Providencia").isEmpty());
    }

    @Test
    void elMismoEstadoDosVecesNoDuplicaElAvisoAunqueCambieElEventId() {
        estado("e1", "ORD-0303", "ana-uuid", "ACEPTADO");
        estado("e2", "ORD-0303", "ana-uuid", "ACEPTADO");

        assertEquals(1, service.listar("ana-uuid", null).size());
    }

    @Test
    void elMismoMensajeDosVecesSeIgnoraPorEventId() {
        Envelope e = Sembrador.estado("e-igual", "ORD-0304", "ana-uuid", "ENTREGADO");

        assertEquals(Resultado.PROCESADO, comandos.email(e, "email.send"));
        assertEquals(Resultado.DUPLICADO, comandos.email(e, "email.send"));
        assertEquals(1, service.listar("ana-uuid", null).size());
    }

    @Test
    void elEstadoSeAceptaEnMinusculasYConEspacios() {
        estado("e1", "ORD-0305", "ana-uuid", " aceptado ");

        assertEquals("Tu pedido ORD-0305 (Planchado) fue aceptado", service.listar("ana-uuid", null).get(0).mensaje());
    }

    @Test
    void unEstadoSinAvisoOFaltanteSeRechazaYNoGuardaNada() {
        for (String malo : new String[]{"CREADO", "DESPACHADO", "VOLANDO", "", null}) {
            Envelope e = Sembrador.estado("e-" + malo, "ORD-0306", "ana-uuid", malo);
            assertThrows(IllegalArgumentException.class, () -> comandos.email(e, "email.send"), String.valueOf(malo));
        }

        assertTrue(service.listar("ana-uuid", null).isEmpty());
        assertEquals(0, eventos.count());
    }

    @Test
    void elAvisoDeEstadoConviveConElDeOrdenListaDeLaMismaOrden() {
        estado("e1", "ORD-0307", "ana-uuid", "EN_PREPARACION");
        email(envelope("ORDEN_LISTA", "ORD-0307", "ana-uuid", null, "Providencia"));
        estado("e2", "ORD-0307", "ana-uuid", "ENTREGADO");

        var tipos = service.listar("ana-uuid", null).stream().map(NotificationResponse::tipo).toList();
        assertEquals(3, tipos.size());
        assertEquals(2, tipos.stream().filter("ORDEN_ESTADO"::equals).count());
        assertEquals(1, tipos.stream().filter("ORDEN_LISTA"::equals).count());
    }
}
