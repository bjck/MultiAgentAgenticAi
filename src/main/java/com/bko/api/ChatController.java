package com.bko.api;

import com.bko.orchestration.OrchestratorService;
import com.bko.orchestration.model.OrchestrationResult;
import com.bko.orchestration.model.OrchestratorPlan;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final OrchestratorService orchestratorService;

    public ChatController(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
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
}
