package com.bko.repository;

import com.bko.entity.WorkerResultLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository interface for managing {@link WorkerResultLog} entities.
 */
public interface WorkerResultLogRepository extends JpaRepository<WorkerResultLog, UUID> {
}
