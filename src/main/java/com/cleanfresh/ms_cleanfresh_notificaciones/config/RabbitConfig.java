package com.cleanfresh.ms_cleanfresh_notificaciones.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa la revisión periódica de las DLQ solo cuando RabbitMQ está habilitado (app.rabbit.enabled=true).
 * Sin él, el servicio arranca igual y no consume nada.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true")
public class RabbitConfig {
}
