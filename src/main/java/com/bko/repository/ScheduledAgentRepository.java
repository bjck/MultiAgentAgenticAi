package com.bko.repository;

import com.bko.entity.ScheduledAgent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ScheduledAgentRepository extends JpaRepository<ScheduledAgent, UUID> {

    List<ScheduledAgent> findByEnabledIsTrueAndNextRunAtLessThanEqual(OffsetDateTime cutoff);
}

