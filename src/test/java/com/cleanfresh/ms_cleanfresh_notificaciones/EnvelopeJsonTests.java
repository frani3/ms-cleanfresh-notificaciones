package com.cleanfresh.ms_cleanfresh_notificaciones;

import com.cleanfresh.ms_cleanfresh_notificaciones.messaging.Envelope;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** El sobre que publica orders se lee bien, con el estado nuevo (Spec 035) y también sin él (mensajes anteriores). */
class EnvelopeJsonTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String CON_ESTADO = """
            {"type":"ORDEN_ESTADO","eventId":"e1","timestamp":"2026-10-21T00:00:00Z","traceId":"t","correlationId":"ORD-0013",
             "payload":{"numeroOrden":"ORD-0013","cliente":"ana-uuid","clienteNombre":null,"servicio":"Planchado",
                        "sucursal":"Providencia","total":9500.0,"fecha":"2026-10-21","estado":"EN_PREPARACION"}}""";

    private static final String SIN_ESTADO = """
            {"type":"ORDEN_CREADA","eventId":"e2","timestamp":"2026-10-21T00:00:00Z","traceId":"t","correlationId":"ORD-0014",
             "payload":{"numeroOrden":"ORD-0014","cliente":"ana-uuid","clienteNombre":"ana@cleanfresh.com","servicio":"Planchado",
                        "sucursal":"Providencia","total":9500.0,"fecha":"2026-10-21"}}""";

    @Test
    void leeElEstadoDelPayload() {
        Envelope e = MAPPER.readValue(CON_ESTADO, Envelope.class);

        assertEquals("ORDEN_ESTADO", e.type());
        assertEquals("EN_PREPARACION", e.payload().estado());
        assertEquals("ana-uuid", e.payload().cliente());
    }

    @Test
    void unMensajeSinEstadoSigueLeyendoseConEstadoNulo() {
        Envelope e = MAPPER.readValue(SIN_ESTADO, Envelope.class);

        assertEquals("ORDEN_CREADA", e.type());
        assertNull(e.payload().estado());
        assertEquals("ana@cleanfresh.com", e.payload().clienteNombre());
    }
}
