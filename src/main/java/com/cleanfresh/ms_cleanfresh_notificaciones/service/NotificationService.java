package com.cleanfresh.ms_cleanfresh_notificaciones.service;

import com.cleanfresh.ms_cleanfresh_notificaciones.dto.NotificationResponse;
import com.cleanfresh.ms_cleanfresh_notificaciones.entity.NotificationEntity;
import com.cleanfresh.ms_cleanfresh_notificaciones.messaging.OrdenMessage;
import com.cleanfresh.ms_cleanfresh_notificaciones.repository.NotificationJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.text.NumberFormat;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class NotificationService {

    public static final String ORDEN_CREADA = "ORDEN_CREADA";
    public static final String ORDEN_LISTA = "ORDEN_LISTA";

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final NotificationJpaRepository repository;

    public NotificationService(NotificationJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * Procesa el cuerpo de un mensaje de la cola: guarda el aviso dirigido a
     * quien corresponde y lo devuelve. ORDEN_CREADA avisa a la sucursal de la
     * orden; ORDEN_LISTA, al cliente dueño. Un mensaje repetido (SQS entrega
     * "al menos una vez") no duplica el aviso. Un tipo desconocido se ignora
     * (devuelve null) para no reintentarlo para siempre. Lanza una excepción si
     * el cuerpo no es un mensaje válido.
     *
     * No es transaccional a propósito: si el guardado choca con la restricción
     * de unicidad, esa excepción se atiende acá sin dejar una transacción
     * marcada para revertir.
     */
    public String procesar(String body) {
        OrdenMessage mensaje = MAPPER.readValue(body, OrdenMessage.class);
        if (mensaje.numeroOrden() == null) {
            throw new IllegalArgumentException("El mensaje no trae numeroOrden");
        }
        NotificationEntity aviso = construirAviso(mensaje);
        if (aviso == null) {
            log.warn("Mensaje de tipo desconocido ({}) ignorado: {}", mensaje.tipo(), mensaje.numeroOrden());
            return null;
        }
        if (repository.existsByTipoAndNumeroOrden(aviso.getTipo(), aviso.getNumeroOrden())) {
            log.info("Notificación repetida ignorada: {} de {}", aviso.getTipo(), aviso.getNumeroOrden());
            return aviso.getMensaje();
        }
        try {
            repository.save(aviso);
        } catch (DataIntegrityViolationException e) {
            log.info("Notificación repetida ignorada: {} de {}", aviso.getTipo(), aviso.getNumeroOrden());
            return aviso.getMensaje();
        }
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

    private NotificationEntity construirAviso(OrdenMessage m) {
        Instant ahora = Instant.now();
        if (ORDEN_CREADA.equals(m.tipo())) {
            String texto = "Nuevo pedido %s: %s de %s en %s (total $%s)"
                    .formatted(m.numeroOrden(), m.servicio(), m.cliente(), m.sucursal(), monto(m.total()));
            return new NotificationEntity(ORDEN_CREADA, NotificationEntity.DESTINO_SUCURSAL,
                    m.sucursal(), m.numeroOrden(), texto, ahora);
        }
        if (ORDEN_LISTA.equals(m.tipo())) {
            String texto = "Tu pedido %s (%s) está listo".formatted(m.numeroOrden(), m.servicio());
            return new NotificationEntity(ORDEN_LISTA, NotificationEntity.DESTINO_CLIENTE,
                    m.cliente(), m.numeroOrden(), texto, ahora);
        }
        return null;
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
