package com.bko.repository;

import com.bko.entity.OrchestratorPlanLog;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrchestratorPlanLogRepository extends JpaRepository<OrchestratorPlanLog, UUID> {

    @EntityGraph(attributePaths = {"tasks", "session"})
    Optional<OrchestratorPlanLog> findWithTasksById(UUID id);
}
