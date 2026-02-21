package com.bko.api;

public record CancelRunResponse(
        String status,
        String message
) {
    public static CancelRunResponse success() {
        return new CancelRunResponse("success", "Run cancellation requested.");
    }

    public static CancelRunResponse notFound() {
        return new CancelRunResponse("not-found", "Run not found.");
    }
}
