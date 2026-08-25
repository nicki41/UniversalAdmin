package dev.universaladmin.modules.performance;

import java.time.Instant;

/** One entry of {@link PerformanceHistory} - the handful of numbers worth trending over a short window. */
public record PerformanceSample(Instant timestamp, double tps1m, double mspt, long usedMemoryBytes) {
}
