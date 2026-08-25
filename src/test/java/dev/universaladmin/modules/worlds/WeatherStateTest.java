package dev.universaladmin.modules.worlds;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Pure enum-mapping logic - Bukkit has no single "weather" concept, just two independent booleans, see {@link WeatherState}. */
class WeatherStateTest {

    @Test
    void ofMapsEveryStormThunderCombinationCorrectly() {
        assertEquals(WeatherState.CLEAR, WeatherState.of(false, false));
        assertEquals(WeatherState.RAIN, WeatherState.of(true, false));
        assertEquals(WeatherState.THUNDER, WeatherState.of(true, true));
        // Thundering without storm doesn't happen in vanilla, but the mapping
        // still has to resolve to something sane rather than throw.
        assertEquals(WeatherState.THUNDER, WeatherState.of(false, true));
    }

    @Test
    void eachStateCarriesTheStormAndThunderFlagsItWasBuiltFrom() {
        assertEquals(false, WeatherState.CLEAR.storm());
        assertEquals(false, WeatherState.CLEAR.thundering());
        assertEquals(true, WeatherState.RAIN.storm());
        assertEquals(false, WeatherState.RAIN.thundering());
        assertEquals(true, WeatherState.THUNDER.storm());
        assertEquals(true, WeatherState.THUNDER.thundering());
    }
}
