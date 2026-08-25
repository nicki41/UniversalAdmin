package dev.universaladmin.modules.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Pure in-memory ring-buffer logic, no Bukkit involved - see {@link PerformanceHistory}'s javadoc for why this stays unbounded-time but bounded-count. */
class PerformanceHistoryTest {

    @Test
    void isEmptyBeforeAnySampleIsRecorded() {
        PerformanceHistory history = new PerformanceHistory();

        assertTrue(history.isEmpty());
        assertTrue(history.averageTps().isEmpty());
    }

    @Test
    void averagesEveryRecordedSampleWhileUnderCapacity() {
        PerformanceHistory history = new PerformanceHistory();

        history.record(new PerformanceSample(Instant.EPOCH, 20.0, 40.0, 1000L));
        history.record(new PerformanceSample(Instant.EPOCH, 10.0, 60.0, 1000L));

        assertEquals(15.0, history.averageTps().getAsDouble(), 0.0001);
        assertEquals(50.0, history.averageMspt().getAsDouble(), 0.0001);
        assertEquals(2, history.recent().size());
    }

    @Test
    void evictsTheOldestSampleOnceCapacityIsExceeded() {
        PerformanceHistory history = new PerformanceHistory();
        for (int i = 0; i < PerformanceHistory.CAPACITY; i++) {
            history.record(new PerformanceSample(Instant.EPOCH, 20.0, 0.0, 0L));
        }
        // One more sample past capacity, distinguishable from the filler value above.
        history.record(new PerformanceSample(Instant.EPOCH, 0.0, 0.0, 0L));

        assertEquals(PerformanceHistory.CAPACITY, history.recent().size());
        assertEquals(0.0, history.recent().get(history.recent().size() - 1).tps1m());
        // The overall average has to have moved away from a flat 20.0 once the first, oldest 20.0 sample was pushed out.
        assertTrue(history.averageTps().getAsDouble() < 20.0);
    }
}
