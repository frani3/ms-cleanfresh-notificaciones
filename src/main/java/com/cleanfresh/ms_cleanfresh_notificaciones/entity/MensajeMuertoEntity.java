package com.cleanfresh.ms_cleanfresh_notificaciones.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Un mensaje que no se pudo entregar y llegó a una DLQ (Spec 033): se registra con su cuerpo original, la
 * cola de la que venía, el motivo y cuántas veces se rechazó, para poder revisarlo después.
 */
@Entity
@Table(name = "mensajes_muertos")
public class MensajeMuertoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String dlq;

    @Column(name = "cola_origen")
    private String colaOrigen;

    private String motivo;
    private long intentos;

    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(length = 4000)
    private String cuerpo;

    @Column(name = "recibido_en", nullable = false)
    private Instant recibidoEn;

    protected MensajeMuertoEntity() {
    }

    public MensajeMuertoEntity(String dlq, String colaOrigen, String motivo, long intentos,
                               String eventId, String cuerpo, Instant recibidoEn) {
        this.dlq = dlq;
        this.colaOrigen = colaOrigen;
        this.motivo = motivo;
        this.intentos = intentos;
        this.eventId = eventId;
        this.cuerpo = cuerpo;
        this.recibidoEn = recibidoEn;
    }

    public Long getId() {
        return id;
    }

    public String getDlq() {
        return dlq;
    }

    public String getColaOrigen() {
        return colaOrigen;
    }

    public String getMotivo() {
        return motivo;
    }

    public long getIntentos() {
        return intentos;
    }

    public String getEventId() {
        return eventId;
    }

    public String getCuerpo() {
        return cuerpo;
    }

    public Instant getRecibidoEn() {
        return recibidoEn;
    }
}
