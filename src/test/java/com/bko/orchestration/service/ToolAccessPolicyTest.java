package com.bko.orchestration.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolAccessPolicyTest {

    @Test
    void allowedToolNames_returnsEmptyListInSimplifiedMode() {
        ToolAccessPolicy policy = new ToolAccessPolicy();
        List<String> allowed = policy.allowedToolNames();
        assertThat(allowed).isEmpty();
    }
}

