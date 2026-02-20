package com.bko.api;

import com.bko.orchestration.OrchestratorService;
import com.bko.orchestration.model.OrchestrationResult;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.stream.OrchestrationStreamService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final OrchestratorService orchestratorService;
    private final OrchestrationStreamService streamService;
    private final ExecutorService orchestrationExecutor;

    public ChatController(OrchestratorService orchestratorService,
                          OrchestrationStreamService streamService,
                          @Qualifier("orchestrationExecutor") ExecutorService orchestrationExecutor) {
        this.orchestratorService = orchestratorService;
        this.streamService = streamService;
        this.orchestrationExecutor = orchestrationExecutor;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        OrchestrationResult result = orchestratorService.orchestrate(request.message(), request.provider(), request.model());
        return ChatResponse.from(result);
    }

    @PostMapping("/plan")
    public PlanResponse plan(@Valid @RequestBody ChatRequest request) {
        OrchestratorPlan plan = orchestratorService.plan(request.message(), request.provider(), request.model());
        return PlanResponse.from(plan);
    }

    @PostMapping("/stream")
    public ChatStreamResponse stream(@Valid @RequestBody ChatRequest request) {
        String runId = streamService.createRun();
        streamService.emitStatus(runId, "Queued");
        CompletableFuture.runAsync(() -> {
            try {
                orchestratorService.orchestrateStreaming(request.message(), request.provider(), request.model(), runId);
            } catch (Exception ex) {
                streamService.emitError(runId, ex.getMessage());
                streamService.emitRunComplete(runId, "FAILED");
            }
        }, orchestrationExecutor);
        return new ChatStreamResponse(runId, Instant.now());
    }
}
