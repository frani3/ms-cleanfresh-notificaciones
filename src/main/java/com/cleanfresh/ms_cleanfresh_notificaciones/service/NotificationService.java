package com.cleanfresh.ms_cleanfresh_notificaciones.service;

import com.cleanfresh.ms_cleanfresh_notificaciones.dto.NotificationResponse;
import com.cleanfresh.ms_cleanfresh_notificaciones.entity.NotificationEntity;
import com.cleanfresh.ms_cleanfresh_notificaciones.messaging.Envelope;
import com.cleanfresh.ms_cleanfresh_notificaciones.repository.NotificationJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class NotificationService {

    public static final String ORDEN_CREADA = "ORDEN_CREADA";
    public static final String ORDEN_LISTA = "ORDEN_LISTA";

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationJpaRepository repository;

    public NotificationService(NotificationJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * Guarda el aviso dirigido a quien corresponde y lo devuelve: ORDEN_CREADA avisa a la sucursal de la
     * orden; ORDEN_LISTA, al cliente dueño. Si ya hay un aviso de ese tipo para esa orden (segunda defensa
     * contra repetidos, además de la idempotencia por eventId), no se duplica. Un tipo que no corresponde
     * lanza IllegalArgumentException, para que el mensaje termine en la DLQ en vez de reintentarse.
     *
     * Se llama dentro de la transacción de ComandoService: si algo falla, no queda guardado ni el aviso
     * ni el registro del evento.
     */
    public String crearAviso(Envelope e) {
        NotificationEntity aviso = construirAviso(e);
        if (repository.existsByTipoAndNumeroOrden(aviso.getTipo(), aviso.getNumeroOrden())) {
            log.info("Notificación repetida ignorada: {} de {}", aviso.getTipo(), aviso.getNumeroOrden());
            return aviso.getMensaje();
        }
        repository.save(aviso);
        log.info("Notificación: {}", aviso.getMensaje());
        return aviso.getMensaje();
    }

    /**
     * Sin filtro devuelve todas (uso del Admin); con cliente o sucursal, solo las
     * de ese destinatario. Un filtro en blanco, o los dos a la vez, es un error:
     * nunca debe caer en "todas" por descuido.
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> listar(String cliente, String sucursal) {
        validarFiltro(cliente, sucursal);
        List<NotificationEntity> avisos;
        if (cliente != null) {
            avisos = repository.findByDestinatarioTipoAndDestinatarioIgnoreCaseOrderByIdDesc(
                    NotificationEntity.DESTINO_CLIENTE, cliente);
        } else if (sucursal != null) {
            avisos = repository.findByDestinatarioTipoAndDestinatarioIgnoreCaseOrderByIdDesc(
                    NotificationEntity.DESTINO_SUCURSAL, sucursal);
        } else {
            avisos = repository.findAllByOrderByIdDesc();
        }
        return avisos.stream().map(this::toResponse).toList();
    }

    /** Marca como leídas las del mismo alcance que listar; devuelve cuántas. */
    @Transactional
    public int marcarLeidas(String cliente, String sucursal) {
        validarFiltro(cliente, sucursal);
        if (cliente != null) {
            return repository.marcarLeidasDe(NotificationEntity.DESTINO_CLIENTE, cliente);
        }
        if (sucursal != null) {
            return repository.marcarLeidasDe(NotificationEntity.DESTINO_SUCURSAL, sucursal);
        }
        return repository.marcarTodasLeidas();
    }

    private void validarFiltro(String cliente, String sucursal) {
        if (cliente != null && sucursal != null) {
            throw new IllegalArgumentException("Indicar cliente o sucursal, no ambos");
        }
        if ((cliente != null && cliente.isBlank()) || (sucursal != null && sucursal.isBlank())) {
            throw new IllegalArgumentException("El filtro no puede estar en blanco");
        }
    }

    private NotificationEntity construirAviso(Envelope e) {
        Envelope.OrdenPayload p = e.payload();
        Instant ahora = Instant.now();
        if (ORDEN_CREADA.equals(e.type())) {
            String texto = "Nuevo pedido %s: %s de %s en %s (total $%s)"
                    .formatted(p.numeroOrden(), p.servicio(), nombreVisible(p), p.sucursal(), monto(p.total()));
            return new NotificationEntity(ORDEN_CREADA, NotificationEntity.DESTINO_SUCURSAL,
                    p.sucursal(), p.numeroOrden(), texto, ahora);
        }
        if (ORDEN_LISTA.equals(e.type())) {
            String texto = "Tu pedido %s (%s) está listo".formatted(p.numeroOrden(), p.servicio());
            return new NotificationEntity(ORDEN_LISTA, NotificationEntity.DESTINO_CLIENTE,
                    p.cliente(), p.numeroOrden(), texto, ahora);
        }
        throw new IllegalArgumentException("Tipo de mensaje no soportado: " + e.type());
    }

    // El aviso muestra el nombre legible si vino; si no (ordenes anteriores o sin
    // poder resolverlo), el identificador. El destinatario sigue siendo siempre
    // el identificador.
    private String nombreVisible(Envelope.OrdenPayload p) {
        String nombre = p.clienteNombre();
        return nombre == null || nombre.isBlank() ? p.cliente() : nombre.trim();
    }

    private String monto(Double total) {
        if (total == null) {
            return "-";
        }
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CL")).format(total);
    }

    private NotificationResponse toResponse(NotificationEntity n) {
        return new NotificationResponse(n.getId(), n.getTipo(), n.getDestinatarioTipo(), n.getDestinatario(),
                n.getNumeroOrden(), n.getMensaje(), n.getFechaHora().toString(), n.isLeida());
    }
}
