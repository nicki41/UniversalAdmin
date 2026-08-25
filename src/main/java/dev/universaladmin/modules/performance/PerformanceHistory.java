package dev.universaladmin.modules.performance;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.OptionalDouble;

/**
 * A short, bounded, in-memory-only trend window - deliberately not
 * persisted, see ROADMAP.md: "Core v1 muss noch keine lange Metric History
 * speichern... persistente Charts sind primär später Web-App Feature."
 * Capacity is a fixed sample count rather than a time span so memory use
 * stays predictable regardless of the configured refresh interval.
 *
 * <p>Only ever touched from {@link PerformanceSamplingService#sample()},
 * which always runs on the main thread (see its javadoc) - no
 * synchronization needed.
 */
public final class PerformanceHistory {

    /** ~10 minutes of samples at the default 5s refresh interval - enough for a short trend, not a chart. */
    public static final int CAPACITY = 120;

    private final Deque<PerformanceSample> samples = new ArrayDeque<>(CAPACITY);

    public void record(PerformanceSample sample) {
        if (samples.size() == CAPACITY) {
            samples.removeFirst();
        }
        samples.addLast(sample);
    }

    /** Oldest first. */
    public List<PerformanceSample> recent() {
        return List.copyOf(samples);
    }

    public OptionalDouble averageTps() {
        return samples.stream().mapToDouble(PerformanceSample::tps1m).average();
    }

    public OptionalDouble averageMspt() {
        return samples.stream().mapToDouble(PerformanceSample::mspt).average();
    }

    public boolean isEmpty() {
        return samples.isEmpty();
    }
}
