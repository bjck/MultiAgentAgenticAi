package com.bko.orchestration.service;

import com.bko.orchestration.api.EventProcessingService;
import com.bko.orchestration.model.OrchestratorPlan;
import com.bko.orchestration.model.PlanDraft;
import com.bko.orchestration.model.TaskSpec;
import com.bko.orchestration.model.WorkerResult;
import com.bko.stream.OrchestrationStreamService;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class EventProcessingServiceImpl implements EventProcessingService {

    private final OrchestrationStreamService streamService;

    public EventProcessingServiceImpl(OrchestrationStreamService streamService) {
        this.streamService = streamService;
    }

    @Override
    public boolean isCancelled(@Nullable String streamId) {
        return streamId != null && streamService.isCancelled(streamId);
    }

    @Override
    public void emitSession(@Nullable String streamId, String sessionId) {
        if (streamId != null) {
            streamService.emitSession(streamId, sessionId);
        }
    }

    @Override
    public void emitStatus(@Nullable String streamId, String status) {
        if (streamId != null) {
            streamService.emitStatus(streamId, status);
        }
    }

    @Override
    public void emitTaskStart(@Nullable String streamId, TaskSpec task) {
        if (streamId != null) {
            streamService.emitTaskStart(streamId, task);
        }
    }

    @Override
    public void emitTaskOutput(@Nullable String streamId, WorkerResult result) {
        if (streamId != null) {
            streamService.emitTaskOutput(streamId, result);
        }
    }

    @Override
    public void emitTaskComplete(@Nullable String streamId, WorkerResult result) {
        if (streamId != null) {
            streamService.emitTaskComplete(streamId, result);
        }
    }

    @Override
    public void emitPlan(@Nullable String streamId, OrchestratorPlan plan) {
        if (streamId != null) {
            streamService.emitPlan(streamId, plan);
        }
    }

    @Override
    public void emitPlanUpdate(@Nullable String streamId, OrchestratorPlan plan) {
        if (streamId != null) {
            streamService.emitPlanUpdate(streamId, plan);
        }
    }

    @Override
    public void emitPlanDraft(@Nullable String streamId, PlanDraft draft) {
        if (streamId != null) {
            streamService.emitPlanDraft(streamId, draft);
        }
    }

    @Override
    public void emitFinalAnswer(@Nullable String streamId, String answer) {
        if (streamId != null) {
            streamService.emitFinalAnswer(streamId, answer);
        }
    }

    @Override
    public void emitRunComplete(@Nullable String streamId, String status) {
        if (streamId != null) {
            streamService.emitRunComplete(streamId, status);
        }
    }

    @Override
    public void emitError(@Nullable String streamId, String message) {
        if (streamId != null) {
            streamService.emitError(streamId, message);
        }
    }
}
