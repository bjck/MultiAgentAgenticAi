package com.bko.repository;

import com.bko.entity.ScheduledAgent;
import com.bko.entity.ScheduledAgentRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScheduledAgentRunRepository extends JpaRepository<ScheduledAgentRun, UUID> {

    List<ScheduledAgentRun> findByAgentOrderByStartedAtDesc(ScheduledAgent agent);
}

