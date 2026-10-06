package com.cleanfresh.ms_cleanfresh_notificaciones.messaging;

import com.cleanfresh.ms_cleanfresh_notificaciones.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

/**
 * Lee la cola de eventos de órdenes con long polling. Un mensaje se elimina
 * solo después de procesarse bien; si falla, queda en la cola y SQS lo vuelve
 * a entregar cuando vence su tiempo de visibilidad.
 */
@Component
@ConditionalOnProperty(name = "app.sqs.enabled", havingValue = "true")
public class OrderQueuePoller {

    private static final Logger log = LoggerFactory.getLogger(OrderQueuePoller.class);

    private final SqsClient sqs;
    private final NotificationService notificationService;
    private final String queueUrl;

    public OrderQueuePoller(SqsClient sqs,
                            NotificationService notificationService,
                            @Value("${app.sqs.queue-url}") String queueUrl) {
        this.sqs = sqs;
        this.notificationService = notificationService;
        this.queueUrl = queueUrl;
    }

    @Scheduled(fixedDelayString = "${app.sqs.poll-delay-ms:1000}")
    public void poll() {
        List<Message> mensajes;
        try {
            mensajes = sqs.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(10)
                    .build()).messages();
        } catch (SdkException e) {
            log.warn("No se pudo leer la cola de SQS: {}", e.getMessage());
            return;
        }

        for (Message mensaje : mensajes) {
            try {
                notificationService.procesar(mensaje.body());
                sqs.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(mensaje.receiptHandle())
                        .build());
            } catch (RuntimeException e) {
                log.error("No se pudo procesar el mensaje {}; queda en la cola para reintento",
                        mensaje.messageId(), e);
            }
        }
    }
}
