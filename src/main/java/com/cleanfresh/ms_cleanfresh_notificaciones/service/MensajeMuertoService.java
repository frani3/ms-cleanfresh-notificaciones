package com.cleanfresh.ms_cleanfresh_notificaciones.service;

import com.cleanfresh.ms_cleanfresh_notificaciones.entity.MensajeMuertoEntity;
import com.cleanfresh.ms_cleanfresh_notificaciones.repository.MensajeMuertoJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

/**
 * Registra los mensajes que llegaron a una DLQ (Spec 033): los deja en el log y en la tabla
 * mensajes_muertos, con el cuerpo original, la cola de origen, el motivo y los intentos.
 */
@Service
public class MensajeMuertoService {

    private static final Logger log = LoggerFactory.getLogger(MensajeMuertoService.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final int MAX_CUERPO = 4000;

    private final MensajeMuertoJpaRepository repository;

    public MensajeMuertoService(MensajeMuertoJpaRepository repository) {
        this.repository = repository;
    }

    public MensajeMuertoEntity registrar(String dlq, String colaOrigen, String motivo, long intentos, String cuerpo) {
        String recortado = cuerpo == null ? "" : cuerpo.length() > MAX_CUERPO ? cuerpo.substring(0, MAX_CUERPO) : cuerpo;
        MensajeMuertoEntity guardado = repository.save(
                new MensajeMuertoEntity(dlq, colaOrigen, motivo, intentos, eventIdDe(cuerpo), recortado, Instant.now()));
        log.error("Mensaje NO ENTREGADO en {} (cola de origen {}, motivo {}, intentos {}, eventId {}): {}",
                dlq, colaOrigen, motivo, intentos, guardado.getEventId(), recortado);
        return guardado;
    }

    // Si el cuerpo es un envelope legible, se rescata su eventId; si es basura, queda null.
    private String eventIdDe(String cuerpo) {
        try {
            JsonNode nodo = MAPPER.readTree(cuerpo);
            JsonNode id = nodo == null ? null : nodo.get("eventId");
            return id == null || id.isNull() ? null : id.asString();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
