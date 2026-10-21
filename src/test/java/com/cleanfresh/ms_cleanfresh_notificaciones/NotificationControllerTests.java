package com.cleanfresh.ms_cleanfresh_notificaciones;

import com.cleanfresh.ms_cleanfresh_notificaciones.repository.EventoProcesadoJpaRepository;
import com.cleanfresh.ms_cleanfresh_notificaciones.repository.NotificationJpaRepository;
import com.cleanfresh.ms_cleanfresh_notificaciones.service.ComandoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ComandoService comandos;

    @Autowired
    private EventoProcesadoJpaRepository eventos;

    @Autowired
    private NotificationJpaRepository repository;

    @BeforeEach
    void datos() {
        repository.deleteAll();
        eventos.deleteAll();
        comandos.email(Sembrador.envelope("ORDEN_CREADA", "ORD-0200", "ana-uuid", null, "Providencia"), "email.send");
        comandos.email(Sembrador.envelope("ORDEN_LISTA", "ORD-0200", "ana-uuid", null, "Providencia"), "email.send");
    }

    @Test
    void filtraPorClienteYPorSucursal() throws Exception {
        mockMvc.perform(get("/api/notificaciones").param("cliente", "ana-uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("ORDEN_LISTA"))
                .andExpect(jsonPath("$[0].leida").value(false));

        mockMvc.perform(get("/api/notificaciones").param("sucursal", "Providencia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("ORDEN_CREADA"));

        mockMvc.perform(get("/api/notificaciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void unFiltroEnBlancoOAmbosDevuelve400() throws Exception {
        mockMvc.perform(get("/api/notificaciones").param("cliente", ""))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/notificaciones").param("cliente", "ana-uuid").param("sucursal", "Providencia"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void marcarLeidasDevuelveCuantasYLasDejaLeidas() throws Exception {
        mockMvc.perform(put("/api/notificaciones/leidas").param("cliente", "ana-uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcadas").value(1));

        mockMvc.perform(get("/api/notificaciones").param("cliente", "ana-uuid"))
                .andExpect(jsonPath("$[0].leida").value(true));
        mockMvc.perform(get("/api/notificaciones").param("sucursal", "Providencia"))
                .andExpect(jsonPath("$[0].leida").value(false));
    }

    // ---------- marcar UN aviso como leído (Spec 034) ----------

    private Long idDe(String destinatarioTipo) {
        return repository.findAllByOrderByIdDesc().stream()
                .filter(n -> n.getDestinatarioTipo().equals(destinatarioTipo)).findFirst().orElseThrow().getId();
    }

    private boolean leida(Long id) {
        return repository.findById(id).orElseThrow().isLeida();
    }

    @Test
    void marcarUnAvisoNoToqueLosDemas() throws Exception {
        Long delCliente = idDe("CLIENTE");
        Long deLaSucursal = idDe("SUCURSAL");

        mockMvc.perform(put("/api/notificaciones/{id}/leida", delCliente).param("cliente", "ana-uuid"))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertTrue(leida(delCliente));
        org.junit.jupiter.api.Assertions.assertFalse(leida(deLaSucursal));
    }

    @Test
    void marcarUnAvisoDeOtroDestinatarioDa404YNoCambiaNada() throws Exception {
        Long delCliente = idDe("CLIENTE");
        Long deLaSucursal = idDe("SUCURSAL");

        // otro cliente intenta marcar el aviso de Ana
        mockMvc.perform(put("/api/notificaciones/{id}/leida", delCliente).param("cliente", "otro-uuid"))
                .andExpect(status().isNotFound());
        // una sucursal intenta marcar un aviso de cliente, y un cliente uno de sucursal
        mockMvc.perform(put("/api/notificaciones/{id}/leida", delCliente).param("sucursal", "Providencia"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/notificaciones/{id}/leida", deLaSucursal).param("cliente", "ana-uuid"))
                .andExpect(status().isNotFound());
        // otra sucursal
        mockMvc.perform(put("/api/notificaciones/{id}/leida", deLaSucursal).param("sucursal", "Maipú"))
                .andExpect(status().isNotFound());

        org.junit.jupiter.api.Assertions.assertFalse(leida(delCliente));
        org.junit.jupiter.api.Assertions.assertFalse(leida(deLaSucursal));
    }

    @Test
    void unAvisoInexistenteDa404() throws Exception {
        mockMvc.perform(put("/api/notificaciones/{id}/leida", 999999).param("cliente", "ana-uuid"))
                .andExpect(status().isNotFound());
    }

    @Test
    void marcarDosVecesElMismoAvisoNoEsError() throws Exception {
        Long delCliente = idDe("CLIENTE");

        mockMvc.perform(put("/api/notificaciones/{id}/leida", delCliente).param("cliente", "ana-uuid"))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/notificaciones/{id}/leida", delCliente).param("cliente", "ana-uuid"))
                .andExpect(status().isNoContent());
    }

    @Test
    void elDestinatarioNoDistingueMayusculas() throws Exception {
        Long delCliente = idDe("CLIENTE");

        mockMvc.perform(put("/api/notificaciones/{id}/leida", delCliente).param("cliente", "ANA-UUID"))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(leida(delCliente));
    }

    @Test
    void sinFiltroConFiltroEnBlancoODobleDa400() throws Exception {
        Long delCliente = idDe("CLIENTE");

        // no existe el "todos" al marcar uno: siempre hay que decir de quién es
        mockMvc.perform(put("/api/notificaciones/{id}/leida", delCliente)).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/notificaciones/{id}/leida", delCliente).param("cliente", ""))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/notificaciones/{id}/leida", delCliente).param("cliente", "ana-uuid").param("sucursal", "Providencia"))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertFalse(leida(delCliente));
    }

    @Test
    void unIdQueNoEsNumeroDa400() throws Exception {
        mockMvc.perform(put("/api/notificaciones/{id}/leida", "abc").param("cliente", "ana-uuid"))
                .andExpect(status().isBadRequest());
    }
}
