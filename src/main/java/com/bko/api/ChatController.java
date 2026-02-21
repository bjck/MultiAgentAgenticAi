package com.bko.api;

import com.bko.orchestration.OrchestratorService;
import com.bko.stream.OrchestrationStreamService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PathVariable;
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
    public PlanResponse chat(@Valid @RequestBody ChatRequest request) {
        var draft = orchestratorService.plan(request.message(), request.provider(), request.model());
        return PlanResponse.from(draft);
    }

    @PostMapping("/plan")
    public PlanResponse plan(@Valid @RequestBody ChatRequest request) {
        var draft = orchestratorService.plan(request.message(), request.provider(), request.model());
        return PlanResponse.from(draft);
    }

    @PostMapping("/execute")
    public ChatResponse execute(@Valid @RequestBody PlanExecuteRequest request) {
        var result = orchestratorService.executePlan(request.planId(), request.feedback(), request.provider(), request.model());
        return ChatResponse.from(result);
    }

    @PostMapping("/stream")
    public ChatStreamResponse stream(@Valid @RequestBody ChatRequest request) {
        String runId = streamService.createRun();
        streamService.emitStatus(runId, "Queued");
        CompletableFuture.runAsync(() -> {
            try {
                orchestratorService.planStreaming(request.message(), request.provider(), request.model(), runId);
            } catch (Exception ex) {
                streamService.emitError(runId, ex.getMessage());
                streamService.emitRunComplete(runId, "FAILED");
            }
        }, orchestrationExecutor);
        return new ChatStreamResponse(runId, Instant.now());
    }

    @PostMapping("/execute/stream")
    public ChatStreamResponse executeStream(@Valid @RequestBody PlanExecuteRequest request) {
        String runId = streamService.createRun();
        streamService.emitStatus(runId, "Queued");
        CompletableFuture.runAsync(() -> {
            try {
                orchestratorService.executePlanStreaming(request.planId(), request.feedback(),
                        request.provider(), request.model(), runId);
            } catch (Exception ex) {
                streamService.emitError(runId, ex.getMessage());
                streamService.emitRunComplete(runId, "FAILED");
            }
        }, orchestrationExecutor);
        return new ChatStreamResponse(runId, Instant.now());
    }

    @PostMapping("/cancel/{runId}")
    public CancelRunResponse cancelStream(@PathVariable String runId) {
        boolean cancelled = streamService.cancelRun(runId);
        return cancelled ? CancelRunResponse.success() : CancelRunResponse.notFound();
    }

    @PostMapping("/cancel")
    public CancelRunResponse cancelStream(@Valid @RequestBody CancelRunRequest request) {
        boolean cancelled = streamService.cancelRun(request.runId());
        return cancelled ? CancelRunResponse.success() : CancelRunResponse.notFound();
    }
}
