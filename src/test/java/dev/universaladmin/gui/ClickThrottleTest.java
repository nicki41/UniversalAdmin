package dev.universaladmin.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClickThrottleTest {

    @Test
    void acceptsTheFirstClickAndRejectsADoubleClickWithinTheInterval() {
        ClickThrottle throttle = new ClickThrottle(150);

        assertTrue(throttle.tryAcquire(1_000));
        assertFalse(throttle.tryAcquire(1_100));
        assertTrue(throttle.tryAcquire(1_150));
    }

    @Test
    void aRejectedClickDoesNotExtendTheInterval() {
        ClickThrottle throttle = new ClickThrottle(150);

        assertTrue(throttle.tryAcquire(0));
        assertFalse(throttle.tryAcquire(140));
        assertTrue(throttle.tryAcquire(160));
    }

    @Test
    void worksForNegativeNanoTimeValues() {
        // System.nanoTime() may legitimately be negative.
        ClickThrottle throttle = new ClickThrottle(150);

        assertTrue(throttle.tryAcquire(-1_000));
        assertFalse(throttle.tryAcquire(-900));
        assertTrue(throttle.tryAcquire(-800));
    }
}
