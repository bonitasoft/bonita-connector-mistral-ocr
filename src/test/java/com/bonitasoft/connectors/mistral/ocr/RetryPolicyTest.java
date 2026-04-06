package com.bonitasoft.connectors.mistral.ocr;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

class RetryPolicyTest {

    @Test
    void shouldSucceedOnFirstAttempt() throws MistralOcrException {
        var policy = new RetryPolicy(3);
        String result = policy.execute(() -> "ok");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void shouldRetryOnRetryableException() throws MistralOcrException {
        var policy = new TestableRetryPolicy(3);
        AtomicInteger attempts = new AtomicInteger(0);

        String result = policy.execute(() -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                throw new MistralOcrException("Rate limited", 429, true);
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldNotRetryOnNonRetryableException() {
        var policy = new TestableRetryPolicy(3);

        assertThatThrownBy(() -> policy.execute(() -> {
            throw new MistralOcrException("Unauthorized", 401, false);
        })).isInstanceOf(MistralOcrException.class)
                .hasMessageContaining("Unauthorized");
    }

    @Test
    void shouldExhaustRetriesAndThrow() {
        var policy = new TestableRetryPolicy(2);

        assertThatThrownBy(() -> policy.execute(() -> {
            throw new MistralOcrException("Server error", 500, true);
        })).isInstanceOf(MistralOcrException.class)
                .hasMessageContaining("Server error");
    }

    @Test
    void shouldIdentifyRetryableStatusCodes() {
        assertThat(RetryPolicy.isRetryableStatusCode(429)).isTrue();
        assertThat(RetryPolicy.isRetryableStatusCode(500)).isTrue();
        assertThat(RetryPolicy.isRetryableStatusCode(502)).isTrue();
        assertThat(RetryPolicy.isRetryableStatusCode(503)).isTrue();
        assertThat(RetryPolicy.isRetryableStatusCode(400)).isFalse();
        assertThat(RetryPolicy.isRetryableStatusCode(401)).isFalse();
        assertThat(RetryPolicy.isRetryableStatusCode(404)).isFalse();
    }

    @Test
    void shouldCalculateExponentialBackoff() {
        var policy = new RetryPolicy(3);
        long wait0 = policy.calculateWait(0);
        long wait1 = policy.calculateWait(1);
        long wait2 = policy.calculateWait(2);

        // Exponential: base * 2^attempt + jitter
        // wait0: 1000 + [0, 500) -> [1000, 1500)
        assertThat(wait0).isBetween(1000L, 1500L);
        // wait1: 2000 + [0, 1000) -> [2000, 3000)
        assertThat(wait1).isBetween(2000L, 3000L);
        // wait2: 4000 + [0, 2000) -> [4000, 6000)
        assertThat(wait2).isBetween(4000L, 6000L);
    }

    /** Retry policy that does not actually sleep. */
    private static class TestableRetryPolicy extends RetryPolicy {
        TestableRetryPolicy(int maxRetries) {
            super(maxRetries);
        }

        @Override
        void sleep(long millis) {
            // No-op for tests
        }
    }
}
