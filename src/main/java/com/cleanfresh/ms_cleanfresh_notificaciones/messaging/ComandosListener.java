package com.cleanfresh.ms_cleanfresh_notificaciones.messaging;

import com.cleanfresh.ms_cleanfresh_notificaciones.service.ComandoService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Un listener independiente por cola de trabajo (Spec 033): avisos, tickets de taller y boletas. Cada uno
 * tiene su propia cola y su propia DLQ, así que un fallo en uno no afecta a los otros.
 */
@Component
@ConditionalOnProperty(name = "app.rabbit.enabled", havingValue = "true")
public class ComandosListener {

    private final GestorMensajes gestor;
    private final ComandoService comandos;

    public ComandosListener(GestorMensajes gestor, ComandoService comandos) {
        this.gestor = gestor;
        this.comandos = comandos;
    }

    @RabbitListener(queues = "q.cmd.email")
    public void email(Message message, Channel channel) throws IOException {
        gestor.consumir(message, channel, "email", comandos::email);
    }

    @RabbitListener(queues = "q.cmd.kitchen")
    public void kitchen(Message message, Channel channel) throws IOException {
        gestor.consumir(message, channel, "kitchen", comandos::kitchen);
    }

    @RabbitListener(queues = "q.cmd.invoice")
    public void invoice(Message message, Channel channel) throws IOException {
        gestor.consumir(message, channel, "invoice", comandos::invoice);
    }
}
