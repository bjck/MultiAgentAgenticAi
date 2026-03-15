package com.bko.repository;

import com.bko.entity.McpServer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface McpServerRepository extends JpaRepository<McpServer, UUID> {

    @Query("select m from McpServer m where m.enabled = true order by lower(m.name)")
    List<McpServer> findEnabled();

    Optional<McpServer> findByNameIgnoreCase(String name);
}
