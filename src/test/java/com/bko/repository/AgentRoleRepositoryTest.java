package com.bko.repository;

import com.bko.entity.AgentRole;
import com.bko.entity.PhaseType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AgentRoleRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private AgentRoleRepository agentRoleRepository;

    @Test
    void testSaveAndFind() {
        AgentRole role = AgentRole.builder()
                .code("test-role")
                .displayName("Test Role")
                .phase(PhaseType.WORKER)
                .active(true)
                .build();

        AgentRole saved = agentRoleRepository.save(role);
        assertNotNull(saved.getId());

        Optional<AgentRole> found = agentRoleRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("test-role", found.get().getCode());
    }
}
