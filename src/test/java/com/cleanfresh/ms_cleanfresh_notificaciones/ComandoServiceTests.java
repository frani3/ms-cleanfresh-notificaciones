package com.cleanfresh.ms_cleanfresh_notificaciones;

import com.cleanfresh.ms_cleanfresh_notificaciones.messaging.Envelope;
import com.cleanfresh.ms_cleanfresh_notificaciones.repository.BoletaJpaRepository;
import com.cleanfresh.ms_cleanfresh_notificaciones.repository.EventoProcesadoJpaRepository;
import com.cleanfresh.ms_cleanfresh_notificaciones.repository.NotificationJpaRepository;
import com.cleanfresh.ms_cleanfresh_notificaciones.repository.TicketTallerJpaRepository;
import com.cleanfresh.ms_cleanfresh_notificaciones.service.ComandoService;
import com.cleanfresh.ms_cleanfresh_notificaciones.service.ComandoService.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.cleanfresh.ms_cleanfresh_notificaciones.Sembrador.envelope;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec 033: los tres comandos, su idempotencia por eventId y que el efecto y el registro van juntos. */
@SpringBootTest
class ComandoServiceTests {

    @Autowired
    private ComandoService comandos;

    @Autowired
    private TicketTallerJpaRepository tickets;

    @Autowired
    private BoletaJpaRepository boletas;

    @Autowired
    private NotificationJpaRepository avisos;

    @Autowired
    private EventoProcesadoJpaRepository eventos;

    @BeforeEach
    void limpiar() {
        tickets.deleteAll();
        boletas.deleteAll();
        avisos.deleteAll();
        eventos.deleteAll();
    }

    // ---------- kitchen.ticket ----------

    @Test
    void kitchenRegistraElTicketDeTaller() {
        Envelope e = envelope("ORDEN_CREADA", "evt-k1", "ORD-0201", "uuid-1", "cliente@cleanfresh.com", "Maipú", "Lavado en seco", 45000.0);

        assertEquals(Resultado.PROCESADO, comandos.kitchen(e, "kitchen.ticket"));

        var ticket = tickets.findAllByOrderByIdDesc().get(0);
        assertEquals("ORD-0201", ticket.getNumeroOrden());
        assertEquals("Maipú", ticket.getSucursal());
        assertEquals("kitchen.ticket", ticket.getClaveOrigen());
        assertTrue(ticket.getTexto().contains("cliente@cleanfresh.com"));
        assertFalse(ticket.getTexto().contains("formato térmico"));
    }

    @Test
    void laVarianteTermicaQuedaRegistradaEnElTicket() {
        comandos.kitchen(envelope("ORDEN_CREADA", "evt-k2", "ORD-0202", "uuid-1", null, "Maipú", "Servicio exprés", 40000.0), "kitchen.ticket.thermal");

        var ticket = tickets.findAllByOrderByIdDesc().get(0);
        assertEquals("kitchen.ticket.thermal", ticket.getClaveOrigen());
        assertTrue(ticket.getTexto().contains("formato térmico"));
        assertTrue(ticket.getTexto().contains("uuid-1"));
    }

    @Test
    void kitchenSoloProcesaOrdenCreada() {
        Envelope lista = envelope("ORDEN_LISTA", "evt-k3", "ORD-0203", "uuid-1", null, "Maipú", "Planchado", 9500.0);

        assertThrows(IllegalArgumentException.class, () -> comandos.kitchen(lista, "kitchen.ticket"));
        assertTrue(tickets.findAll().isEmpty());
        assertFalse(eventos.existsById("kitchen:evt-k3"));
    }

    // ---------- invoice.gen ----------

    @Test
    void invoiceRegistraLaBoletaConNetoEIva() {
        Envelope e = envelope("ORDEN_LISTA", "evt-i1", "ORD-0211", "uuid-1", "cliente@cleanfresh.com", "Providencia", "Planchado", 11900.0);

        assertEquals(Resultado.PROCESADO, comandos.invoice(e, "invoice.gen"));

        var boleta = boletas.findAllByOrderByIdDesc().get(0);
        assertEquals("BOL-0211", boleta.getNumeroBoleta());
        assertEquals("ORD-0211", boleta.getNumeroOrden());
        assertEquals(11900, boleta.getTotal());
        assertEquals(10000, boleta.getNeto());
        assertEquals(1900, boleta.getIva());
        assertEquals(boleta.getTotal(), boleta.getNeto() + boleta.getIva());
        assertEquals("invoice.gen", boleta.getClaveOrigen());
    }

    @Test
    void elNetoYElIvaSiempreSumanElTotal() {
        comandos.invoice(envelope("ORDEN_LISTA", "evt-i2", "ORD-0212", "uuid-1", null, "Providencia", "Planchado", 9500.0), "invoice.gen.pdf");

        var boleta = boletas.findAllByOrderByIdDesc().get(0);
        assertEquals(9500, boleta.getTotal());
        assertEquals(boleta.getTotal(), boleta.getNeto() + boleta.getIva());
        assertEquals("invoice.gen.pdf", boleta.getClaveOrigen());
    }

    @Test
    void invoiceSoloProcesaOrdenLista() {
        Envelope creada = envelope("ORDEN_CREADA", "evt-i3", "ORD-0213", "uuid-1", null, "Providencia", "Planchado", 9500.0);

        assertThrows(IllegalArgumentException.class, () -> comandos.invoice(creada, "invoice.gen"));
        assertTrue(boletas.findAll().isEmpty());
    }

    // ---------- idempotencia ----------

    @Test
    void elMismoEventIdDosVecesProduceUnSoloEfectoEnCadaFlujo() {
        Envelope creada = envelope("ORDEN_CREADA", "evt-dup-1", "ORD-0221", "uuid-1", null, "Maipú", "Planchado", 9500.0);
        Envelope lista = envelope("ORDEN_LISTA", "evt-dup-2", "ORD-0221", "uuid-1", null, "Maipú", "Planchado", 9500.0);

        assertEquals(Resultado.PROCESADO, comandos.kitchen(creada, "kitchen.ticket"));
        assertEquals(Resultado.DUPLICADO, comandos.kitchen(creada, "kitchen.ticket"));
        assertEquals(Resultado.PROCESADO, comandos.invoice(lista, "invoice.gen"));
        assertEquals(Resultado.DUPLICADO, comandos.invoice(lista, "invoice.gen"));
        assertEquals(Resultado.PROCESADO, comandos.email(creada, "email.send"));
        assertEquals(Resultado.DUPLICADO, comandos.email(creada, "email.send"));

        assertEquals(1, tickets.count());
        assertEquals(1, boletas.count());
        assertEquals(1, avisos.count());
    }

    @Test
    void siSeRepiteConOtroEventIdElEfectoNoSeDuplicaIgualPorElAvisoYaExistente() {
        comandos.email(envelope("ORDEN_CREADA", "evt-a", "ORD-0222", "uuid-1", null, "Maipú", "Planchado", 9500.0), "email.send");
        comandos.email(envelope("ORDEN_CREADA", "evt-b", "ORD-0222", "uuid-1", null, "Maipú", "Planchado", 9500.0), "email.send");

        assertEquals(1, avisos.count());
        assertTrue(eventos.existsById("email:evt-a"));
        assertTrue(eventos.existsById("email:evt-b"));
    }

    @Test
    void unMismoMensajeQueLlegaADosFlujosSeProcesaUnaVezEnCadaUno() {
        // cmd.topic puede enrutar un mismo mensaje (mismo eventId) a varias colas
        Envelope e = envelope("ORDEN_CREADA", "evt-fanout", "ORD-0223", "uuid-1", null, "Maipú", "Planchado", 9500.0);

        assertEquals(Resultado.PROCESADO, comandos.email(e, "email.send"));
        assertEquals(Resultado.PROCESADO, comandos.kitchen(e, "kitchen.ticket"));
        assertEquals(Resultado.DUPLICADO, comandos.email(e, "email.send"));
        assertEquals(Resultado.DUPLICADO, comandos.kitchen(e, "kitchen.ticket"));

        assertEquals(1, avisos.count());
        assertEquals(1, tickets.count());
        assertEquals(2, eventos.count());
    }

    // ---------- atomicidad y validación ----------

    @Test
    void siFallaElEfectoNoQuedaRegistradoElEvento() {
        String servicioEnorme = "x".repeat(600);
        Envelope e = envelope("ORDEN_CREADA", "evt-falla", "ORD-0231", "uuid-1", null, "Maipú", servicioEnorme, 9500.0);

        assertThrows(RuntimeException.class, () -> comandos.kitchen(e, "kitchen.ticket"));

        // el registro del evento se revirtió junto con el efecto: el reintento podrá procesarlo
        assertFalse(eventos.existsById("kitchen:evt-falla"));
        assertTrue(tickets.findAll().isEmpty());
    }

    @Test
    void unMensajeIncompletoSeRechaza() {
        Envelope sinId = envelope("ORDEN_CREADA", " ", "ORD-0241", "uuid-1", null, "Maipú", "Planchado", 9500.0);
        Envelope sinOrden = new Envelope("ORDEN_CREADA", "evt-x", "t", "tr", "c", null);

        assertThrows(IllegalArgumentException.class, () -> comandos.email(sinId, "email.send"));
        assertThrows(IllegalArgumentException.class, () -> comandos.email(sinOrden, "email.send"));
        assertThrows(IllegalArgumentException.class, () -> comandos.email(null, "email.send"));
        assertEquals(0, eventos.count());
    }
}
