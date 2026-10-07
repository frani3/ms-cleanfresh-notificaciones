package com.cleanfresh.ms_cleanfresh_notificaciones;

import com.cleanfresh.ms_cleanfresh_notificaciones.repository.NotificationJpaRepository;
import com.cleanfresh.ms_cleanfresh_notificaciones.service.NotificationService;
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
    private NotificationService service;

    @Autowired
    private NotificationJpaRepository repository;

    @BeforeEach
    void datos() {
        repository.deleteAll();
        service.procesar("""
                {"tipo":"ORDEN_CREADA","numeroOrden":"ORD-0200","cliente":"ana-uuid",
                 "servicio":"Planchado","sucursal":"Providencia","total":9500.0,"fecha":"2026-10-07"}""");
        service.procesar("""
                {"tipo":"ORDEN_LISTA","numeroOrden":"ORD-0200","cliente":"ana-uuid",
                 "servicio":"Planchado","sucursal":"Providencia","total":9500.0,"fecha":"2026-10-07"}""");
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
}
