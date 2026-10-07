package com.cleanfresh.ms_cleanfresh_notificaciones.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Un aviso dirigido a alguien: a una sucursal (le llega al Operador que esté
 * en turno en ella) o a un cliente (por su username de Cognito). Es único por
 * tipo y orden: si SQS entrega dos veces el mismo mensaje, no se duplica.
 */
@Entity
@Table(name = "notificaciones",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tipo", "numero_orden"}))
public class NotificationEntity {

    public static final String DESTINO_SUCURSAL = "SUCURSAL";
    public static final String DESTINO_CLIENTE = "CLIENTE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tipo;

    @Column(name = "destinatario_tipo", nullable = false)
    private String destinatarioTipo;

    @Column(nullable = false)
    private String destinatario;

    @Column(name = "numero_orden", nullable = false)
    private String numeroOrden;

    @Column(nullable = false, length = 500)
    private String mensaje;

    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora;

    @Column(nullable = false)
    private boolean leida;

    protected NotificationEntity() {
    }

    public NotificationEntity(String tipo, String destinatarioTipo, String destinatario,
                              String numeroOrden, String mensaje, Instant fechaHora) {
        this.tipo = tipo;
        this.destinatarioTipo = destinatarioTipo;
        this.destinatario = destinatario;
        this.numeroOrden = numeroOrden;
        this.mensaje = mensaje;
        this.fechaHora = fechaHora;
        this.leida = false;
    }

    public Long getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public String getDestinatarioTipo() {
        return destinatarioTipo;
    }

    public String getDestinatario() {
        return destinatario;
    }

    public String getNumeroOrden() {
        return numeroOrden;
    }

    public String getMensaje() {
        return mensaje;
    }

    public Instant getFechaHora() {
        return fechaHora;
    }

    public boolean isLeida() {
        return leida;
    }
}
