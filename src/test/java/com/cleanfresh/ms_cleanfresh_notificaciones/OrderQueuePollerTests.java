package com.cleanfresh.ms_cleanfresh_notificaciones;

import com.cleanfresh.ms_cleanfresh_notificaciones.messaging.OrderQueuePoller;
import com.cleanfresh.ms_cleanfresh_notificaciones.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderQueuePollerTests {

    private static final String QUEUE_URL = "http://localhost:9324/000000000000/cleanfresh-ordenes";

    private final SqsClient sqs = mock(SqsClient.class);
    private final NotificationService service = mock(NotificationService.class);
    private final OrderQueuePoller poller = new OrderQueuePoller(sqs, service, QUEUE_URL);

    private void colaConMensaje(String body) {
        Message mensaje = Message.builder().messageId("m-1").receiptHandle("rh-1").body(body).build();
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(mensaje).build());
    }

    @Test
    void eliminaElMensajeDeLaColaDespuesDeProcesarloBien() {
        colaConMensaje("{\"tipo\":\"ORDEN_CREADA\",\"numeroOrden\":\"ORD-0012\"}");

        poller.poll();

        verify(service).procesar("{\"tipo\":\"ORDEN_CREADA\",\"numeroOrden\":\"ORD-0012\"}");
        ArgumentCaptor<DeleteMessageRequest> captor = ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(sqs).deleteMessage(captor.capture());
        assertEquals("rh-1", captor.getValue().receiptHandle());
        assertEquals(QUEUE_URL, captor.getValue().queueUrl());
    }

    @Test
    void siElMensajeFallaNoLoEliminaYSeguraAndando() {
        colaConMensaje("esto no es json");
        doThrow(new IllegalArgumentException("invalido")).when(service).procesar(any());

        poller.poll();

        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }
}
