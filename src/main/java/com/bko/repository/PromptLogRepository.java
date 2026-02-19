package com.bko.repository;

import com.bko.entity.PromptLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PromptLogRepository extends JpaRepository<PromptLog, UUID> {
}
