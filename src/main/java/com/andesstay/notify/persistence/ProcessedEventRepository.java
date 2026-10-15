package com.andesstay.notify.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repositorio JPA para {@link ProcessedEvent}.
 * Spring Data genera la implementación automáticamente.
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
}
