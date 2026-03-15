package com.bko.orchestration.service;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Resolves tool permissions from the database role policy table.
 * No application.yml tool configuration is used.
 */
@Service
public class ToolAccessPolicy {

    /**
     * Temporary shim preserved for backwards compatibility.
     * Actual tool access is now configured centrally and no longer depends on DB roles.
     */
    public enum Phase { ORCHESTRATOR, WORKER }

    public List<String> allowedToolNames() {
        return List.of();
    }
}
