package com.bko.repository;

import com.bko.entity.OrchestratorPlanLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing {@link OrchestratorPlanLog} entities.
 * Provides methods for querying orchestration plan logs.
 */
public interface OrchestratorPlanLogRepository extends JpaRepository<OrchestratorPlanLog, UUID> {

    /**
     * Finds an {@link OrchestratorPlanLog} by its ID, eagerly fetching associated tasks and session.
     *
     * @param id The UUID of the orchestration plan log.
     * @return An {@link Optional} containing the found {@link OrchestratorPlanLog} with its tasks and session, or empty if not found.
     */
    @EntityGraph(attributePaths = {"tasks", "session"})
    Optional<OrchestratorPlanLog> findWithTasksById(UUID id);
}
