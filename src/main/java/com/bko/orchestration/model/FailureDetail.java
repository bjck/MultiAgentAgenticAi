package com.bko.orchestration.model;

public record FailureDetail(TaskSpec task, String reason) {
}
