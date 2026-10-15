package com.cleanfresh.ms_cleanfresh_notificaciones.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Boleta de un pedido despachado. El total incluye el IVA (19 %); neto e IVA se desglosan. */
@Entity
@Table(name = "boletas")
public class BoletaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(name = "numero_boleta", nullable = false)
    private String numeroBoleta;

    @Column(name = "numero_orden", nullable = false)
    private String numeroOrden;

    @Column(name = "cliente_nombre")
    private String clienteNombre;

    private String servicio;
    private String sucursal;
    private long neto;
    private long iva;
    private long total;

    /** Clave con la que llegó: invoice.gen, o invoice.gen.pdf para el formato PDF (exprés). */
    @Column(name = "clave_origen")
    private String claveOrigen;

    @Column(name = "emitida_en", nullable = false)
    private Instant emitidaEn;

    protected BoletaEntity() {
    }

    public BoletaEntity(String eventId, String numeroBoleta, String numeroOrden, String clienteNombre,
                        String servicio, String sucursal, long neto, long iva, long total,
                        String claveOrigen, Instant emitidaEn) {
        this.eventId = eventId;
        this.numeroBoleta = numeroBoleta;
        this.numeroOrden = numeroOrden;
        this.clienteNombre = clienteNombre;
        this.servicio = servicio;
        this.sucursal = sucursal;
        this.neto = neto;
        this.iva = iva;
        this.total = total;
        this.claveOrigen = claveOrigen;
        this.emitidaEn = emitidaEn;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getNumeroBoleta() {
        return numeroBoleta;
    }

    public String getNumeroOrden() {
        return numeroOrden;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public String getServicio() {
        return servicio;
    }

    public String getSucursal() {
        return sucursal;
    }

    public long getNeto() {
        return neto;
    }

    public long getIva() {
        return iva;
    }

    public long getTotal() {
        return total;
    }

    public String getClaveOrigen() {
        return claveOrigen;
    }

    public Instant getEmitidaEn() {
        return emitidaEn;
    }
}
