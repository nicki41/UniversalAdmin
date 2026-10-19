package dev.universaladmin.modules.worlds.action;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WorldActionSupportTest {

    @Test
    void dayTicksBecomeTheInGameClock() {
        assertEquals("06:00", WorldActionSupport.clock(0));
        assertEquals("12:00", WorldActionSupport.clock(6000));
        assertEquals("18:00", WorldActionSupport.clock(12000));
        assertEquals("00:00", WorldActionSupport.clock(18000));
        assertEquals("06:30", WorldActionSupport.clock(500));
        // the full world time counts days - only the time of day matters
        assertEquals("12:00", WorldActionSupport.clock(24000L * 3 + 6000));
    }

    @Test
    void numbersHaveNoGroupingAndAtMostOneDecimal() {
        assertEquals("12000", WorldActionSupport.number(12000));
        assertEquals("-1234", WorldActionSupport.number(-1234));
        assertEquals("2.5", WorldActionSupport.number(2.5));
        assertEquals("0.3", WorldActionSupport.number(0.25));
    }
}
