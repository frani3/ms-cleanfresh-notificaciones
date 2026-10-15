package com.cleanfresh.ms_cleanfresh_notificaciones.service;

import com.cleanfresh.ms_cleanfresh_notificaciones.entity.BoletaEntity;
import com.cleanfresh.ms_cleanfresh_notificaciones.entity.EventoProcesadoEntity;
import com.cleanfresh.ms_cleanfresh_notificaciones.entity.TicketTallerEntity;
import com.cleanfresh.ms_cleanfresh_notificaciones.messaging.Envelope;
import com.cleanfresh.ms_cleanfresh_notificaciones.repository.BoletaJpaRepository;
import com.cleanfresh.ms_cleanfresh_notificaciones.repository.EventoProcesadoJpaRepository;
import com.cleanfresh.ms_cleanfresh_notificaciones.repository.TicketTallerJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Los tres comandos que consume este servicio (Spec 033): aviso (q.cmd.email), ticket de taller
 * (q.cmd.kitchen) y boleta (q.cmd.invoice).
 *
 * Cada uno es idempotente por {@code eventId}: el registro del mensaje y su efecto se guardan en la misma
 * transacción, así que o quedan los dos o ninguno, y un mensaje que llega dos veces no repite el efecto
 * (la segunda vez devuelve DUPLICADO). Un mensaje inválido lanza IllegalArgumentException.
 */
@Service
public class ComandoService {

    public enum Resultado {
        PROCESADO, DUPLICADO
    }

    private static final Logger log = LoggerFactory.getLogger(ComandoService.class);

    private final EventoProcesadoJpaRepository eventos;
    private final NotificationService notificaciones;
    private final TicketTallerJpaRepository tickets;
    private final BoletaJpaRepository boletas;

    public ComandoService(EventoProcesadoJpaRepository eventos, NotificationService notificaciones,
                          TicketTallerJpaRepository tickets, BoletaJpaRepository boletas) {
        this.eventos = eventos;
        this.notificaciones = notificaciones;
        this.tickets = tickets;
        this.boletas = boletas;
    }

    /** email.send: aviso al Operador (ORDEN_CREADA) o al Cliente (ORDEN_LISTA). */
    @Transactional
    public Resultado email(Envelope e, String claveOrigen) {
        validar(e);
        if (!primeraVez(e, "email")) {
            return Resultado.DUPLICADO;
        }
        notificaciones.crearAviso(e);
        return Resultado.PROCESADO;
    }

    /** kitchen.ticket: ticket de taller de la sucursal. Solo para ORDEN_CREADA. */
    @Transactional
    public Resultado kitchen(Envelope e, String claveOrigen) {
        validar(e);
        exigirTipo(e, NotificationService.ORDEN_CREADA, "kitchen");
        if (!primeraVez(e, "kitchen")) {
            return Resultado.DUPLICADO;
        }
        Envelope.OrdenPayload p = e.payload();
        boolean termico = claveOrigen != null && claveOrigen.endsWith(".thermal");
        String texto = "TICKET DE TALLER %s: %s | sucursal %s | cliente %s | %s%s".formatted(
                p.numeroOrden(), p.servicio(), p.sucursal(), nombre(p), p.fecha(), termico ? " | formato térmico" : "");
        tickets.save(new TicketTallerEntity(e.eventId(), p.numeroOrden(), p.sucursal(), p.servicio(),
                p.clienteNombre(), claveOrigen, texto, Instant.now()));
        log.info("Ticket de taller registrado: {}", texto);
        return Resultado.PROCESADO;
    }

    /** invoice.gen: boleta del pedido. Solo para ORDEN_LISTA. */
    @Transactional
    public Resultado invoice(Envelope e, String claveOrigen) {
        validar(e);
        exigirTipo(e, NotificationService.ORDEN_LISTA, "invoice");
        if (!primeraVez(e, "invoice")) {
            return Resultado.DUPLICADO;
        }
        Envelope.OrdenPayload p = e.payload();
        long total = p.total() == null ? 0 : Math.round(p.total());
        long neto = Math.round(total / 1.19);
        long iva = total - neto;
        String numeroBoleta = "BOL-" + p.numeroOrden().replace("ORD-", "");
        boletas.save(new BoletaEntity(e.eventId(), numeroBoleta, p.numeroOrden(), p.clienteNombre(), p.servicio(),
                p.sucursal(), neto, iva, total, claveOrigen, Instant.now()));
        log.info("Boleta {} registrada: orden {}, total ${} (neto ${}, IVA ${})", numeroBoleta, p.numeroOrden(), total, neto, iva);
        return Resultado.PROCESADO;
    }

    // Registra consumidor + eventId; si ya estaba, es un mensaje repetido para ese flujo.
    private boolean primeraVez(Envelope e, String consumidor) {
        if (eventos.existsById(EventoProcesadoEntity.clave(consumidor, e.eventId()))) {
            log.info("Mensaje repetido ignorado ({}): eventId {}", consumidor, e.eventId());
            return false;
        }
        eventos.save(new EventoProcesadoEntity(e.eventId(), consumidor, Instant.now()));
        return true;
    }

    private void validar(Envelope e) {
        if (e == null || e.type() == null || e.type().isBlank()) {
            throw new IllegalArgumentException("El mensaje no trae type");
        }
        if (e.eventId() == null || e.eventId().isBlank()) {
            throw new IllegalArgumentException("El mensaje no trae eventId");
        }
        if (e.payload() == null || e.payload().numeroOrden() == null || e.payload().numeroOrden().isBlank()) {
            throw new IllegalArgumentException("El mensaje no trae payload.numeroOrden");
        }
    }

    private void exigirTipo(Envelope e, String esperado, String flujo) {
        if (!esperado.equals(e.type())) {
            throw new IllegalArgumentException("El flujo " + flujo + " solo procesa " + esperado + ", llegó " + e.type());
        }
    }

    private String nombre(Envelope.OrdenPayload p) {
        return p.clienteNombre() == null || p.clienteNombre().isBlank() ? p.cliente() : p.clienteNombre().trim();
    }
}
