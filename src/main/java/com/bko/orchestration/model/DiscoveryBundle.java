package com.bko.orchestration.model;

import java.util.List;

public record DiscoveryBundle(List<TaskSpec> tasks, List<WorkerResult> results) {
    public DiscoveryBundle() {
        this(List.of(), List.of());
    }
}
