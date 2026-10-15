package com.cleanfresh.ms_cleanfresh_notificaciones.controller;

import com.cleanfresh.ms_cleanfresh_notificaciones.repository.BoletaJpaRepository;
import com.cleanfresh.ms_cleanfresh_notificaciones.repository.MensajeMuertoJpaRepository;
import com.cleanfresh.ms_cleanfresh_notificaciones.repository.TicketTallerJpaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consulta de solo lectura de lo que producen los otros dos flujos y de lo que no se pudo entregar (Spec
 * 033). Como el resto de este servicio, no valida JWT: no está expuesto por el BFF y sirve para revisar
 * el sistema (curl desde la EC2, demostraciones).
 */
@RestController
@RequestMapping("/api")
public class RegistrosController {

    public record TicketResponse(Long id, String numeroOrden, String sucursal, String servicio, String clienteNombre,
                                 String claveOrigen, String texto, String creadoEn) {
    }

    public record BoletaResponse(Long id, String numeroBoleta, String numeroOrden, String clienteNombre, String servicio,
                                 String sucursal, long neto, long iva, long total, String claveOrigen, String emitidaEn) {
    }

    public record MensajeMuertoResponse(Long id, String dlq, String colaOrigen, String motivo, long intentos,
                                        String eventId, String cuerpo, String recibidoEn) {
    }

    private final TicketTallerJpaRepository tickets;
    private final BoletaJpaRepository boletas;
    private final MensajeMuertoJpaRepository muertos;

    public RegistrosController(TicketTallerJpaRepository tickets, BoletaJpaRepository boletas,
                               MensajeMuertoJpaRepository muertos) {
        this.tickets = tickets;
        this.boletas = boletas;
        this.muertos = muertos;
    }

    @GetMapping("/tickets")
    public List<TicketResponse> tickets() {
        return tickets.findAllByOrderByIdDesc().stream()
                .map(t -> new TicketResponse(t.getId(), t.getNumeroOrden(), t.getSucursal(), t.getServicio(),
                        t.getClienteNombre(), t.getClaveOrigen(), t.getTexto(), t.getCreadoEn().toString()))
                .toList();
    }

    @GetMapping("/boletas")
    public List<BoletaResponse> boletas() {
        return boletas.findAllByOrderByIdDesc().stream()
                .map(b -> new BoletaResponse(b.getId(), b.getNumeroBoleta(), b.getNumeroOrden(), b.getClienteNombre(),
                        b.getServicio(), b.getSucursal(), b.getNeto(), b.getIva(), b.getTotal(), b.getClaveOrigen(),
                        b.getEmitidaEn().toString()))
                .toList();
    }

    @GetMapping("/muertos")
    public List<MensajeMuertoResponse> muertos() {
        return muertos.findAllByOrderByIdDesc().stream()
                .map(m -> new MensajeMuertoResponse(m.getId(), m.getDlq(), m.getColaOrigen(), m.getMotivo(), m.getIntentos(),
                        m.getEventId(), m.getCuerpo(), m.getRecibidoEn().toString()))
                .toList();
    }
}
