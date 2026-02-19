package com.bko.orchestration.service;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A ToolCallbackProvider wrapper that filters the delegate provider's callbacks by allowed tool names.
 * Name matching is case-insensitive. If the allowed set is empty, provides no tools.
 */
public class FilteringToolCallbackProvider implements ToolCallbackProvider {

    private final ToolCallbackProvider delegate;
    private final Set<String> allowedNames;

    public FilteringToolCallbackProvider(ToolCallbackProvider delegate, List<String> allowedNames) {
        this.delegate = delegate;
        this.allowedNames = allowedNames == null ? Set.of() : allowedNames.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        if (delegate == null) return new ToolCallback[0];
        ToolCallback[] callbacks = delegate.getToolCallbacks();
        if (callbacks == null || callbacks.length == 0) return new ToolCallback[0];
        if (allowedNames.isEmpty()) return new ToolCallback[0];
        return java.util.Arrays.stream(callbacks)
                .filter(cb -> allowedNames.contains(extractName(cb)))
                .toArray(ToolCallback[]::new);
    }

    private String extractName(ToolCallback cb) {
        if (cb == null) return "";
        try {
            Method m = cb.getClass().getMethod("getName");
            Object v = m.invoke(cb);
            if (v != null) return v.toString().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {}
        try {
            Method m = cb.getClass().getMethod("name");
            Object v = m.invoke(cb);
            if (v != null) return v.toString().toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {}
        String s = cb.toString();
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
