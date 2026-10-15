package com.andesstay.notify.messaging.retry;

import com.andesstay.notify.TestData;
import com.andesstay.notify.event.ReservationEnvelope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetryPublisher — reencola con contador incrementado")
class RetryPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    @DisplayName("Publica en la misma cola (exchange por defecto) con x-retry-count + 1")
    void republishesToSameQueueWithIncrementedHeader() throws Exception {
        ReservationEnvelope envelope = TestData.envelope(UUID.randomUUID());
        ArgumentCaptor<MessagePostProcessor> processor = ArgumentCaptor.forClass(MessagePostProcessor.class);

        new RetryPublisher(rabbitTemplate).republish("q.cmd.email", envelope, 1);

        verify(rabbitTemplate).convertAndSend(eq(""), eq("q.cmd.email"), eq((Object) envelope), processor.capture());

        Message message = new Message(new byte[0], new MessageProperties());
        processor.getValue().postProcessMessage(message);
        Integer header = message.getMessageProperties().getHeader(RetryPublisher.RETRY_HEADER);
        assertEquals(2, header.intValue());
    }
}
