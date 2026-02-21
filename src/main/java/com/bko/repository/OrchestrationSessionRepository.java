package com.bko.repository;

import com.bko.entity.OrchestrationSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository interface for managing {@link OrchestrationSession} entities.
 */
public interface OrchestrationSessionRepository extends JpaRepository<OrchestrationSession, UUID> {
}
