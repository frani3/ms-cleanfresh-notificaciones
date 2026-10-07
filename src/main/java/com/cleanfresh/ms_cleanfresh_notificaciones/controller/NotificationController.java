package com.cleanfresh.ms_cleanfresh_notificaciones.controller;

import com.cleanfresh.ms_cleanfresh_notificaciones.dto.NotificationResponse;
import com.cleanfresh.ms_cleanfresh_notificaciones.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Consulta de avisos. No valida JWT: confía en que solo el BFF le habla, y es
 * el BFF quien decide qué filtro aplica a cada rol.
 */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationResponse> listar(
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String sucursal) {
        try {
            return service.listar(cliente, sucursal);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PutMapping("/leidas")
    public Map<String, Integer> marcarLeidas(
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String sucursal) {
        try {
            return Map.of("marcadas", service.marcarLeidas(cliente, sucursal));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
