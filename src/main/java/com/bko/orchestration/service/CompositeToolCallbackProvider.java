package com.bko.orchestration.service;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CompositeToolCallbackProvider implements ToolCallbackProvider {

    private final List<ToolCallbackProvider> providers;

    public CompositeToolCallbackProvider(List<ToolCallbackProvider> providers) {
        this.providers = providers == null ? List.of() : List.copyOf(providers);
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        if (providers.isEmpty()) {
            return new ToolCallback[0];
        }
        List<ToolCallback> callbacks = new ArrayList<>();
        for (ToolCallbackProvider provider : providers) {
            if (provider == null) {
                continue;
            }
            ToolCallback[] batch = provider.getToolCallbacks();
            if (batch != null && batch.length > 0) {
                callbacks.addAll(Arrays.asList(batch));
            }
        }
        return callbacks.toArray(new ToolCallback[0]);
    }
}
