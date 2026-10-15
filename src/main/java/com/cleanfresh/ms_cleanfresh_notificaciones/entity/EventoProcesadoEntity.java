package com.cleanfresh.ms_cleanfresh_notificaciones.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Registro de los mensajes ya procesados (idempotencia, Spec 033): RabbitMQ entrega "al menos una vez", así
 * que si el mismo mensaje llega dos veces a un consumidor, la segunda se reconoce y no repite el efecto.
 *
 * La clave es consumidor + eventId, no solo el eventId: un mismo mensaje publicado por cmd.topic puede
 * llegar a varias colas con el mismo eventId, y cada flujo debe procesarlo una vez.
 */
@Entity
@Table(name = "eventos_procesados")
public class EventoProcesadoEntity {

    @Id
    @Column(length = 150)
    private String clave;

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(nullable = false, length = 20)
    private String consumidor;

    @Column(name = "procesado_en", nullable = false)
    private Instant procesadoEn;

    protected EventoProcesadoEntity() {
    }

    public EventoProcesadoEntity(String eventId, String consumidor, Instant procesadoEn) {
        this.clave = clave(consumidor, eventId);
        this.eventId = eventId;
        this.consumidor = consumidor;
        this.procesadoEn = procesadoEn;
    }

    public static String clave(String consumidor, String eventId) {
        return consumidor + ":" + eventId;
    }

    public String getClave() {
        return clave;
    }

    public String getEventId() {
        return eventId;
    }

    public String getConsumidor() {
        return consumidor;
    }

    public Instant getProcesadoEn() {
        return procesadoEn;
    }
}
