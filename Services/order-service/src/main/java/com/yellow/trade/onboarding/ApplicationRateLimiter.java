package com.yellow.trade.onboarding;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Five applications per caller per hour, on the one route that creates rows
 * without a login.
 *
 * In memory, so per instance: behind more than one instance a caller gets five
 * from each. Recorded in the security review; Redis is the upgrade, as the auth
 * service's login throttle already does.
 */
@Component
public class ApplicationRateLimiter {

    static final int LIMIT = 5;
    static final Duration WINDOW = Duration.ofHours(1);
    /** Callers who never come back would otherwise stay in the map forever. */
    private static final int SWEEP_ABOVE = 10_000;

    private record Window(Instant start, int count) {
        boolean expiredAt(Instant now) {
            return !now.isBefore(start.plus(WINDOW));
        }
    }

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public ApplicationRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Counts this attempt; false once the caller is over the limit for the current window. */
    public boolean tryAcquire(String caller) {
        Instant now = clock.instant();
        if (windows.size() > SWEEP_ABOVE) {
            windows.values().removeIf(w -> w.expiredAt(now));
        }
        Window window = windows.compute(caller, (key, old) ->
                old == null || old.expiredAt(now) ? new Window(now, 1) : new Window(old.start(), old.count() + 1));
        return window.count() <= LIMIT;
    }
}
