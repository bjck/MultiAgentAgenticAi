package com.bko.orchestration.service;

import com.bko.api.AgentController.AgentQueryRequest;
import com.bko.entity.ExternalDocument;
import com.bko.entity.ScheduledAgent;
import com.bko.orchestration.OrchestratorService;
import com.bko.orchestration.model.OrchestrationResult;
import com.bko.repository.ExternalDocumentRepository;
import com.bko.repository.ScheduledAgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentQueryServiceTest {

    private ScheduledAgentRepository agentRepository;
    private ExternalDocumentRepository externalDocumentRepository;
    private OrchestratorService orchestratorService;
    private AgentQueryService service;

    @BeforeEach
    void setUp() {
        agentRepository = mock(ScheduledAgentRepository.class);
        externalDocumentRepository = mock(ExternalDocumentRepository.class);
        orchestratorService = mock(OrchestratorService.class);
        service = new AgentQueryService(agentRepository, externalDocumentRepository, orchestratorService);
    }

    @Test
    void queryAgentBuildsPromptAndReturnsAnswer() {
        UUID agentId = UUID.randomUUID();
        ScheduledAgent agent = new ScheduledAgent();
        agent.setId(agentId);
        agent.setName("Arxiv Summary Agent");
        agent.setObjectivePrompt("Summarize recent software engineering papers.");
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));

        ExternalDocument doc = ExternalDocument.builder()
                .source("arxiv")
                .sourceId("1234.5678")
                .title("Test Paper")
                .abstractText("This is a test abstract.")
                .url("https://example.org/abs/1234.5678")
                .build();
        when(externalDocumentRepository
                .findBySourcePublishedAtBetweenOrderBySourcePublishedAtDescCreatedAtDesc(any(OffsetDateTime.class), any(OffsetDateTime.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(doc));

        when(orchestratorService.orchestrate(any(String.class), any(), any()))
                .thenReturn(new OrchestrationResult(null, List.of(), "Final summary answer"));

        AgentQueryRequest request = new AgentQueryRequest(
                "Summarize the last few days.",
                OffsetDateTime.now().minusDays(3),
                OffsetDateTime.now(),
                10
        );

        var response = service.queryAgent(agentId, request);

        assertEquals("Final summary answer", response.answer());
        assertEquals(1, response.documentsConsidered());
        verify(orchestratorService).orchestrate(any(String.class), any(), any());
    }
}

