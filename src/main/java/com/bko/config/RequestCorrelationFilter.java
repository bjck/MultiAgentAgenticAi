package com.bko.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Adds request correlation data into MDC so structured logs can be traced
 * across API requests, background orchestration runs, and websocket actions.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    private static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = firstNonBlank(request.getHeader(HEADER_REQUEST_ID), UUID.randomUUID().toString());
        String traceId = firstNonBlank(request.getHeader(HEADER_TRACE_ID), requestId);
        String runId = request.getParameter("runId");
        String sessionId = request.getParameter("sessionId");

        MDC.put("requestId", requestId);
        MDC.put("traceId", traceId);
        MDC.put("httpMethod", request.getMethod());
        MDC.put("httpPath", request.getRequestURI());
        if (StringUtils.hasText(runId)) {
            MDC.put("runId", runId);
        }
        if (StringUtils.hasText(sessionId)) {
            MDC.put("sessionId", sessionId);
        }

        response.setHeader(HEADER_REQUEST_ID, requestId);
        response.setHeader(HEADER_TRACE_ID, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private static String firstNonBlank(String first, String fallback) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        return fallback;
    }
}
