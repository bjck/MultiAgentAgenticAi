package com.bko.repository;

import com.bko.entity.AgentRole;
import com.bko.entity.PhaseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing {@link AgentRole} entities.
 * Provides methods for querying agent roles based on phase, code, and default status.
 */
@Repository
public interface AgentRoleRepository extends JpaRepository<AgentRole, UUID> {
    /**
     * Finds an {@link AgentRole} by its phase and code.
     *
     * @param phase The phase type of the agent role.
     * @param code The unique code of the agent role.
     * @return An {@link Optional} containing the found {@link AgentRole}, or empty if not found.
     */
    Optional<AgentRole> findByPhaseAndCode(PhaseType phase, String code);

    /**
     * Finds the default {@link AgentRole} for a given phase.
     *
     * @param phase The phase type.
     * @return An {@link Optional} containing the default {@link AgentRole} for the specified phase, or empty if not found.
     */
    Optional<AgentRole> findByPhaseAndIsDefaultTrue(PhaseType phase);
}
