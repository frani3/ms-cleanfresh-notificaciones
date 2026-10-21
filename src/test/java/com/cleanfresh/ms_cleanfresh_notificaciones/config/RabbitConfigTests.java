package com.cleanfresh.ms_cleanfresh_notificaciones.config;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.ConsumerTagStrategy;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Spec 038: el panel de RabbitMQ muestra a este servicio por su nombre (conexión y consumidores). */
class RabbitConfigTests {

    private final RabbitConfig config = new RabbitConfig();

    @Test
    void laConexionSeLlamaComoElServicio() throws Exception {
        assertEquals("ms-cleanfresh-notificaciones", config.nombreDeLaConexion().obtainNewConnectionName(new CachingConnectionFactory()));
    }

    @Test
    void cadaConsumidorLlevaElNombreDelServicioYDeSuCola() {
        SimpleMessageListenerContainer contenedor = new SimpleMessageListenerContainer();

        config.etiquetasDeConsumidor().configure(contenedor);

        ConsumerTagStrategy estrategia = (ConsumerTagStrategy) ReflectionTestUtils.getField(contenedor, "consumerTagStrategy");
        assertNotNull(estrategia);
        assertEquals("ms-cleanfresh-notificaciones.q.cmd.email", estrategia.createConsumerTag("q.cmd.email"));
        assertEquals("ms-cleanfresh-notificaciones.q.cmd.kitchen", estrategia.createConsumerTag("q.cmd.kitchen"));
        assertEquals("ms-cleanfresh-notificaciones.q.cmd.invoice", estrategia.createConsumerTag("q.cmd.invoice"));
    }
}
