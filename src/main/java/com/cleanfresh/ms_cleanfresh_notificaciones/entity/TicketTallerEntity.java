package com.cleanfresh.ms_cleanfresh_notificaciones.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Ticket de taller: la hoja de trabajo que se imprime en la sucursal cuando entra un pedido. */
@Entity
@Table(name = "tickets_taller")
public class TicketTallerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(name = "numero_orden", nullable = false)
    private String numeroOrden;

    private String sucursal;
    private String servicio;

    @Column(name = "cliente_nombre")
    private String clienteNombre;

    /** Clave con la que llegó: kitchen.ticket, o kitchen.ticket.thermal para el formato térmico (exprés). */
    @Column(name = "clave_origen")
    private String claveOrigen;

    @Column(nullable = false, length = 500)
    private String texto;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected TicketTallerEntity() {
    }

    public TicketTallerEntity(String eventId, String numeroOrden, String sucursal, String servicio,
                              String clienteNombre, String claveOrigen, String texto, Instant creadoEn) {
        this.eventId = eventId;
        this.numeroOrden = numeroOrden;
        this.sucursal = sucursal;
        this.servicio = servicio;
        this.clienteNombre = clienteNombre;
        this.claveOrigen = claveOrigen;
        this.texto = texto;
        this.creadoEn = creadoEn;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getNumeroOrden() {
        return numeroOrden;
    }

    public String getSucursal() {
        return sucursal;
    }

    public String getServicio() {
        return servicio;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public String getClaveOrigen() {
        return claveOrigen;
    }

    public String getTexto() {
        return texto;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
