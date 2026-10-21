package com.cleanfresh.ms_cleanfresh_notificaciones.config;

import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuración de RabbitMQ de este servicio; solo se activa con RabbitMQ habilitado (app.rabbit.enabled=true).
 * Sin él, el servicio arranca igual y no consume nada.
 *
 * <ul>
 *   <li>Activa la revisión periódica de las DLQ.</li>
 *   <li>Le pone nombre a la conexión y a cada consumidor (Spec 038): en el panel de RabbitMQ, la pestaña
 *       <b>Connections</b> muestra {@code ms-cleanfresh-notificaciones} y en cada cola, <b>Consumers</b>, el
 *       consumidor se llama {@code ms-cleanfresh-notificaciones.q.cmd.email}. Así se ve de un vistazo que este
 *       servicio es el que está consumiendo, en vez de una IP y un puerto.</li>
 * </ul>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true")
public class RabbitConfig {

    public static final String NOMBRE_SERVICIO = "ms-cleanfresh-notificaciones";

    /** Nombre que RabbitMQ muestra para la conexión de este servicio. */
    @Bean
    public ConnectionNameStrategy nombreDeLaConexion() {
        return conexionFactory -> NOMBRE_SERVICIO;
    }

    /** Etiqueta de cada consumidor: {@code ms-cleanfresh-notificaciones.<cola>}. */
    @Bean
    public ContainerCustomizer<SimpleMessageListenerContainer> etiquetasDeConsumidor() {
        return contenedor -> contenedor.setConsumerTagStrategy(cola -> NOMBRE_SERVICIO + "." + cola);
    }
}
