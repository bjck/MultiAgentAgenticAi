package com.bko.repository;

import com.bko.entity.PromptLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for managing {@link PromptLog} entities.
 */
public interface PromptLogRepository extends JpaRepository<PromptLog, UUID> {

    @Query("SELECT pl FROM PromptLog pl WHERE pl.session.id = :sessionId ORDER BY pl.createdAt ASC")
    List<PromptLog> findBySessionIdOrderByCreatedAtAsc(@Param("sessionId") UUID sessionId);

    @Query("SELECT pl FROM PromptLog pl JOIN FETCH pl.session WHERE pl.session.id IN :sessionIds ORDER BY pl.createdAt ASC")
    List<PromptLog> findBySession_IdInOrderByCreatedAtAsc(@Param("sessionIds") Collection<UUID> sessionIds);
}
