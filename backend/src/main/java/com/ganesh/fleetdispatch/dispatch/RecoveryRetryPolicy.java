package com.ganesh.fleetdispatch.dispatch;

/** Exponential recovery backoff with a deterministic cap and bounded attempts. */
public record RecoveryRetryPolicy(long initialDelayMillis, double multiplier, long maxDelayMillis, int maxAttempts) {
    public RecoveryRetryPolicy {
        if (initialDelayMillis <= 0 || !Double.isFinite(multiplier) || multiplier < 1.0
                || maxDelayMillis < initialDelayMillis || maxAttempts <= 0) {
            throw new IllegalArgumentException("invalid recovery retry policy");
        }
    }

    public long delayMillis(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        double delay = initialDelayMillis * Math.pow(multiplier, attempt - 1);
        return Math.min(maxDelayMillis, delay >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) delay);
    }

    public boolean exhausted(int attempt) {
        return attempt >= maxAttempts;
    }

    public static RecoveryRetryPolicy defaultPolicy() {
        return new RecoveryRetryPolicy(250L, 2.0, 10_000L, 8);
    }
}
