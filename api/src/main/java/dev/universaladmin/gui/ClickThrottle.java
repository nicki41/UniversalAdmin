package dev.universaladmin.gui;

/**
 * "At most one accepted event per {@code minIntervalNanos}" - the
 * double-click guard behind {@link GuiView#acceptButtonClick(long)}. Kept
 * separate from {@link GuiView} (which needs a live server to construct) so
 * the timing rule itself is unit-testable.
 */
final class ClickThrottle {

    private final long minIntervalNanos;
    private boolean acquiredBefore;
    private long lastAcquiredNanos;

    ClickThrottle(long minIntervalNanos) {
        this.minIntervalNanos = minIntervalNanos;
    }

    /** Whether an event at {@code nowNanos} ({@link System#nanoTime()}) is accepted; an accepted event restarts the interval. */
    synchronized boolean tryAcquire(long nowNanos) {
        if (acquiredBefore && nowNanos - lastAcquiredNanos < minIntervalNanos) {
            return false;
        }
        acquiredBefore = true;
        lastAcquiredNanos = nowNanos;
        return true;
    }
}
