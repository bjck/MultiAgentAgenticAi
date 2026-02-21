package com.bko.orchestration.model;

import java.util.List;

public record AdvisoryBundle(List<TaskSpec> tasks, List<WorkerResult> results) {
    public AdvisoryBundle() {
        this(List.of(), List.of());
    }
}
