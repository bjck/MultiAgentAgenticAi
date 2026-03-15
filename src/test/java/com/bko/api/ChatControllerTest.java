package com.bko.api;

import com.bko.orchestration.OrchestratorService;
import com.bko.orchestration.model.OrchestrationResult;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.PlanDraft;
import com.bko.stream.OrchestrationStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerTest {

    @Mock
    private OrchestratorService orchestratorService;

    @Mock
    private OrchestrationStreamService streamService;

    @Mock
    private ExecutorService orchestrationExecutor;

    @InjectMocks
    private ChatController chatController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Configure the executor to run tasks immediately for testing CompletableFuture.runAsync
        doAnswer((Answer<Void>) invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(orchestrationExecutor).execute(any(Runnable.class));
    }

    @Test
    void testChat() {
        ChatRequest request = new ChatRequest("test message", "test provider", "test model");
        OrchestratorPlan mockPlan = new OrchestratorPlan("Test Plan", List.of());
        PlanDraft mockDraft = new PlanDraft("plan-123", "session-123", mockPlan, List.of(), List.of(), "PLANNED");
        when(orchestratorService.plan(any(String.class), any(String.class), any(String.class))).thenReturn(mockDraft);

        PlanResponse response = chatController.chat(request);

        assertNotNull(response);
        assertEquals(mockDraft.planId(), response.planId());
        assertEquals(mockDraft.plan().objective(), response.objective());
        verify(orchestratorService).plan(eq("test message"), eq("test provider"), eq("test model"));
    }

    @Test
    void testPlan() {
        ChatRequest request = new ChatRequest("test message for plan", "test provider for plan", "test model for plan");
        OrchestratorPlan mockPlan = new OrchestratorPlan("Another Test Plan", List.of());
        PlanDraft mockDraft = new PlanDraft("plan-456", "session-456", mockPlan, List.of(), List.of(), "PLANNED");
        when(orchestratorService.plan(any(String.class), any(String.class), any(String.class))).thenReturn(mockDraft);

        PlanResponse response = chatController.plan(request);

        assertNotNull(response);
        assertEquals(mockDraft.planId(), response.planId());
        assertEquals(mockDraft.plan().objective(), response.objective());
        verify(orchestratorService).plan(eq("test message for plan"), eq("test provider for plan"), eq("test model for plan"));
    }

    @Test
    void testExecute() {
        PlanExecuteRequest request = new PlanExecuteRequest("plan-789", "test feedback", "exec provider", "exec model");
        OrchestratorPlan mockPlan = new OrchestratorPlan("Test Plan", List.of());
        OrchestrationResult mockResult = new OrchestrationResult(mockPlan, List.of(), "Test Result");
        when(orchestratorService.executePlan(any(String.class), any(String.class), any(String.class), any(String.class))).thenReturn(mockResult);

        ChatResponse response = chatController.execute(request);

        assertNotNull(response);
        assertEquals(mockResult.finalAnswer(), response.finalAnswer());
        verify(orchestratorService).executePlan(eq("plan-789"), eq("test feedback"), eq("exec provider"), eq("exec model"));
    }

    @Test
    void testStream() {
        ChatRequest request = new ChatRequest("stream message", "stream provider", "stream model");
        String runId = "run-123";
        when(streamService.createRun()).thenReturn(runId);

        ChatStreamResponse response = chatController.stream(request);

        assertNotNull(response);
        assertEquals(runId, response.runId());
        verify(streamService).createRun();
        verify(streamService).emitStatus(eq(runId), eq("Queued"));
        verify(orchestratorService).planStreaming(eq(request.message()), eq(request.provider()), eq(request.model()), eq(runId));
    }

    @Test
    void testExecuteStream() {
        PlanExecuteRequest request = new PlanExecuteRequest("plan-stream-456", "exec stream feedback", "exec stream provider", "exec stream model");
        String runId = "run-456";
        when(streamService.createRun()).thenReturn(runId);

        ChatStreamResponse response = chatController.executeStream(request);

        assertNotNull(response);
        assertEquals(runId, response.runId());
        verify(streamService).createRun();
        verify(streamService).emitStatus(eq(runId), eq("Queued"));
        verify(orchestratorService).executePlanStreaming(eq(request.planId()), eq(request.feedback()), eq(request.provider()), eq(request.model()), eq(runId));
    }

    @Test
    void testCancelStreamByPath() {
        String runId = "run-cancel-1";
        when(streamService.cancelRun(eq(runId))).thenReturn(true);

        CancelRunResponse response = chatController.cancelStream(runId);

        assertNotNull(response);
        assertEquals("success", response.status());
        verify(streamService).cancelRun(eq(runId));
    }

    @Test
    void testCancelStreamByPathNotFound() {
        String runId = "run-cancel-2";
        when(streamService.cancelRun(eq(runId))).thenReturn(false);

        CancelRunResponse response = chatController.cancelStream(runId);

        assertNotNull(response);
        assertEquals("not-found", response.status());
        verify(streamService).cancelRun(eq(runId));
    }

    @Test
    void testCancelStreamByRequestBody() {
        CancelRunRequest request = new CancelRunRequest("run-cancel-3");
        when(streamService.cancelRun(eq(request.runId()))).thenReturn(true);

        CancelRunResponse response = chatController.cancelStream(request);

        assertNotNull(response);
        assertEquals("success", response.status());
        verify(streamService).cancelRun(eq(request.runId()));
    }

    @Test
    void testCancelStreamByRequestBodyNotFound() {
        CancelRunRequest request = new CancelRunRequest("run-cancel-4");
        when(streamService.cancelRun(eq(request.runId()))).thenReturn(false);

        CancelRunResponse response = chatController.cancelStream(request);

        assertNotNull(response);
        assertEquals("not-found", response.status());
        verify(streamService).cancelRun(eq(request.runId()));
    }
}
