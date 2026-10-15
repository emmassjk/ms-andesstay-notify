package com.andesstay.notify.messaging.consumer.housekeeping;

import com.andesstay.notify.TestData;
import com.andesstay.notify.event.ReservationEnvelope;
import com.andesstay.notify.messaging.retry.RetryPublisher;
import com.andesstay.notify.services.HousekeepingService;
import com.andesstay.notify.services.IdempotencyRegistry;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias del HousekeepingListener.
 * Verifica la lógica de ACK/NACK, reintento e idempotencia sin levantar contexto Spring.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HousekeepingListener — lógica de ACK/NACK")
class HousekeepingListenerTest {

    private static final String QUEUE = "q.cmd.housekeeping";

    @Mock private HousekeepingService housekeepingService;
    @Mock private IdempotencyRegistry idempotencyRegistry;
    @Mock private RetryPublisher retryPublisher;
    @Mock private Channel channel;

    private HousekeepingListener listener;

    @BeforeEach
    void setUp() {
        listener = new HousekeepingListener(housekeepingService, idempotencyRegistry,
                TestData.rabbitProperties(), retryPublisher);
    }

    @Test
    @DisplayName("Procesa exitosamente y hace basicAck")
    void successfulProcessingCallsBasicAck() throws Exception {
        UUID eventId = UUID.randomUUID();
        ReservationEnvelope envelope = TestData.envelope(eventId);
        when(idempotencyRegistry.alreadyProcessed(eventId)).thenReturn(false);

        listener.onHousekeepingMessage(envelope, channel, 1L, 0);

        verify(housekeepingService).createHousekeepingTicket(envelope);
        verify(idempotencyRegistry).markAsProcessed(eventId);
        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Duplicado hace basicAck silencioso sin invocar el servicio")
    void duplicateEventCallsAckWithoutProcessing() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(idempotencyRegistry.alreadyProcessed(eventId)).thenReturn(true);

        listener.onHousekeepingMessage(TestData.envelope(eventId), channel, 2L, 0);

        verify(housekeepingService, never()).createHousekeepingTicket(any());
        verify(channel).basicAck(2L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Envelope nulo hace basicNack con requeue=false (DLQ)")
    void nullEnvelopeCallsNackToDlq() throws Exception {
        listener.onHousekeepingMessage(null, channel, 3L, 0);

        verify(channel).basicNack(3L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("Dato inválido (error no recuperable) va directo a DLQ sin reintentar")
    void nonRecoverableErrorGoesStraightToDlq() throws Exception {
        UUID eventId = UUID.randomUUID();
        ReservationEnvelope envelope = TestData.envelope(eventId);
        when(idempotencyRegistry.alreadyProcessed(eventId)).thenReturn(false);
        doThrow(new IllegalArgumentException("email inválido")).when(housekeepingService).createHousekeepingTicket(any());

        listener.onHousekeepingMessage(envelope, channel, 6L, 0);

        verify(channel).basicNack(6L, false, false);
        verify(retryPublisher, never()).republish(any(), any(), anyInt());
    }

    @Test
    @DisplayName("Error recuperable con reintentos disponibles republica con contador y hace basicAck")
    void recoverableErrorWithRetriesAvailableRepublishes() throws Exception {
        UUID eventId = UUID.randomUUID();
        ReservationEnvelope envelope = TestData.envelope(eventId);
        when(idempotencyRegistry.alreadyProcessed(eventId)).thenReturn(false);
        doThrow(new RuntimeException("timeout")).when(housekeepingService).createHousekeepingTicket(any());

        listener.onHousekeepingMessage(envelope, channel, 4L, 0); // retryCount=0, max=3

        verify(retryPublisher).republish(QUEUE, envelope, 0);
        verify(channel).basicAck(4L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Si falla la republicación, el mensaje va a la DLQ (no se pierde)")
    void republishFailureGoesToDlq() throws Exception {
        UUID eventId = UUID.randomUUID();
        ReservationEnvelope envelope = TestData.envelope(eventId);
        when(idempotencyRegistry.alreadyProcessed(eventId)).thenReturn(false);
        doThrow(new RuntimeException("timeout")).when(housekeepingService).createHousekeepingTicket(any());
        doThrow(new AmqpException("broker caído")).when(retryPublisher).republish(any(), any(), anyInt());

        listener.onHousekeepingMessage(envelope, channel, 7L, 1);

        verify(channel).basicNack(7L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("Error recuperable con reintentos agotados hace basicNack requeue=false (DLQ)")
    void recoverableErrorWithNoRetriesGoesToDlq() throws Exception {
        UUID eventId = UUID.randomUUID();
        ReservationEnvelope envelope = TestData.envelope(eventId);
        when(idempotencyRegistry.alreadyProcessed(eventId)).thenReturn(false);
        doThrow(new RuntimeException("timeout")).when(housekeepingService).createHousekeepingTicket(any());

        listener.onHousekeepingMessage(envelope, channel, 5L, 3); // retryCount=3, max=3

        verify(channel).basicNack(5L, false, false);
        verify(retryPublisher, never()).republish(any(), any(), anyInt());
    }

    @Test
    @DisplayName("Si la BD de idempotencia falla se trata como error recuperable (no bucle infinito)")
    void idempotencyDbFailureIsRecoverable() throws Exception {
        UUID eventId = UUID.randomUUID();
        ReservationEnvelope envelope = TestData.envelope(eventId);
        when(idempotencyRegistry.alreadyProcessed(eventId)).thenThrow(new RuntimeException("db caída"));

        listener.onHousekeepingMessage(envelope, channel, 8L, 0);

        verify(retryPublisher).republish(QUEUE, envelope, 0);
        verify(channel).basicAck(8L, false);
        verify(housekeepingService, never()).createHousekeepingTicket(any());
    }
}
