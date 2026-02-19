package com.bko.repository;

import com.bko.entity.AgentRole;
import com.bko.entity.PhaseType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentRoleRepository extends JpaRepository<AgentRole, UUID> {
    Optional<AgentRole> findByPhaseAndCode(PhaseType phase, String code);
    Optional<AgentRole> findByPhaseAndIsDefaultTrue(PhaseType phase);
}
