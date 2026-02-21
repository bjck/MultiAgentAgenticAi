package com.bko.api;

import com.bko.orchestration.OrchestratorService;
import com.bko.stream.OrchestrationStreamService;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(MockitoJUnitRunner.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Mock
    private OrchestratorService orchestratorService;

    @Mock
    private OrchestrationStreamService orchestrationStreamService;

    @Test
    void testCancelStream() throws Exception {
        String runId = "test-run-id";

        doNothing().when(orchestrationStreamService).cancelRun(runId);

        mockMvc.perform(post("/api/chat/cancel/{runId}", runId))
                .andExpect(status().isOk());

        verify(orchestrationStreamService).cancelRun(runId);
    }
}
