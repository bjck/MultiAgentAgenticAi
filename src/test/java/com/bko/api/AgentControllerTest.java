package com.bko.api;

import com.bko.entity.ScheduledAgent;
import com.bko.entity.ScheduledAgentRun;
import com.bko.orchestration.service.AgentLibraryService;
import com.bko.orchestration.service.AgentQueryService;
import com.bko.orchestration.service.ScheduledAgentExecutionService;
import com.bko.repository.PromptLogRepository;
import com.bko.repository.ScheduledAgentRepository;
import com.bko.repository.ScheduledAgentRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScheduledAgentRepository agentRepository;

    @MockitoBean
    private ScheduledAgentRunRepository runRepository;

    @MockitoBean
    private PromptLogRepository promptLogRepository;

    @MockitoBean
    private AgentLibraryService agentLibraryService;

    @MockitoBean
    private AgentQueryService agentQueryService;

    @MockitoBean
    private ScheduledAgentExecutionService scheduledAgentExecutionService;

    @MockitoBean(name = "orchestrationExecutor")
    private ExecutorService orchestrationExecutor;

    @Test
    void listAgentsReturnsOk() throws Exception {
        when(agentRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/agents"))
                .andExpect(status().isOk());
    }

    @Test
    void getAgentReturnsDetailView() throws Exception {
        UUID id = UUID.randomUUID();
        ScheduledAgent agent = new ScheduledAgent();
        agent.setId(id);
        agent.setName("Test Agent");
        when(agentRepository.findById(id)).thenReturn(Optional.of(agent));
        when(runRepository.findByAgentOrderByStartedAtDesc(any(ScheduledAgent.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/agents/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Test Agent"));
    }

    @Test
    void createAgentValidatesAndDelegatesToService() throws Exception {
        when(agentLibraryService.createAgent(any())).thenReturn(new ScheduledAgent());

        String body = """
                {
                  "name": "Arxiv Hourly",
                  "objectivePrompt": "Fetch latest cs.SE papers.",
                  "rawScheduleInput": "every hour"
                }
                """;

        mockMvc.perform(post("/api/agents")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void queryAgentUsesService() throws Exception {
        UUID id = UUID.randomUUID();
        when(agentQueryService.queryAgent(any(), any()))
                .thenReturn(new AgentController.AgentQueryResponse("answer", 1));

        String body = """
                {
                  "query": "Summarize recent papers"
                }
                """;

        mockMvc.perform(post("/api/agents/{id}/query", id)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("answer"))
                .andExpect(jsonPath("$.documentsConsidered").value(1));
    }

    @Test
    void runNowReturnsAcceptedAndSchedulesExecution() throws Exception {
        UUID id = UUID.randomUUID();
        ScheduledAgent agent = new ScheduledAgent();
        agent.setId(id);
        when(agentRepository.findById(id)).thenReturn(Optional.of(agent));

        mockMvc.perform(post("/api/agents/{id}/run", id))
                .andExpect(status().isAccepted());
    }

    @Test
    void deleteAgentRemovesEntity() throws Exception {
        UUID id = UUID.randomUUID();
        when(agentRepository.existsById(id)).thenReturn(true);

        mockMvc.perform(delete("/api/agents/{id}", id))
                .andExpect(status().isNoContent());

        verify(agentRepository).deleteById(id);
    }
}
