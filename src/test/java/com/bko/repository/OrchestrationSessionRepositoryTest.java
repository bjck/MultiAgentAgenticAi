package com.bko.repository;

import com.bko.entity.OrchestrationSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OrchestrationSessionRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private OrchestrationSessionRepository orchestrationSessionRepository;

    @Test
    void testSaveAndFind() {
        OrchestrationSession session = OrchestrationSession.builder()
                .userPrompt("Hello world")
                .provider("google")
                .model("gemini-1.5-flash")
                .status("COMPLETED")
                .build();

        OrchestrationSession saved = orchestrationSessionRepository.save(session);
        assertNotNull(saved.getId());

        Optional<OrchestrationSession> found = orchestrationSessionRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Hello world", found.get().getUserPrompt());
    }
}
