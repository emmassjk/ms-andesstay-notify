package com.andesstay.notify.services;

import com.andesstay.notify.persistence.ProcessedEvent;
import com.andesstay.notify.persistence.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias del registro de idempotencia (con repositorio mockeado).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyRegistry")
class IdempotencyRegistryTest {

    @Mock
    private ProcessedEventRepository repository;

    private IdempotencyRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new IdempotencyRegistry(repository);
    }

    @Test
    @DisplayName("Un eventId nuevo no es duplicado")
    void newEventIdIsNotDuplicate() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(false);
        assertThat(registry.alreadyProcessed(id)).isFalse();
    }

    @Test
    @DisplayName("Un eventId marcado como procesado se detecta como duplicado")
    void markedEventIdIsDetectedAsDuplicate() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(true);
        assertThat(registry.alreadyProcessed(id)).isTrue();
    }

    @Test
    @DisplayName("Un eventId nulo no se considera duplicado")
    void nullEventIdIsNotDuplicate() {
        assertThat(registry.alreadyProcessed(null)).isFalse();
        verify(repository, never()).existsById(any());
    }

    @Test
    @DisplayName("Marcar null no lanza excepción ni llama al repositorio")
    void markNullDoesNotThrow() {
        registry.markAsProcessed(null);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("markAsProcessed llama a repository.save con el eventId correcto")
    void markAsProcessed_savesEventId() {
        UUID id = UUID.randomUUID();
        registry.markAsProcessed(id);
        verify(repository).save(argThat(e -> e.getEventId().equals(id)));
    }

    @Test
    @DisplayName("Múltiples eventIds son independientes")
    void multipleEventIdsAreIndependent() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(repository.existsById(id1)).thenReturn(true);
        when(repository.existsById(id2)).thenReturn(false);

        assertThat(registry.alreadyProcessed(id1)).isTrue();
        assertThat(registry.alreadyProcessed(id2)).isFalse();
    }
}
