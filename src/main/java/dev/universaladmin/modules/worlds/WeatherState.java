package dev.universaladmin.modules.worlds;

import org.bukkit.World;

/**
 * Bukkit has no single "weather" enum - it's two independent booleans
 * ({@link World#hasStorm()}/{@link World#isThundering()}). This is the one
 * piece of actual logic in the module worth naming and testing on its own -
 * see {@code WeatherStateTest}.
 */
public enum WeatherState {
    CLEAR(false, false),
    RAIN(true, false),
    THUNDER(true, true);

    private final boolean storm;
    private final boolean thundering;

    WeatherState(boolean storm, boolean thundering) {
        this.storm = storm;
        this.thundering = thundering;
    }

    public boolean storm() {
        return storm;
    }

    public boolean thundering() {
        return thundering;
    }

    /** {@code thundering} wins even if {@code storm} is somehow {@code false} - not a vanilla-reachable combination, but this still has to resolve to something sane rather than throw. */
    public static WeatherState of(boolean storm, boolean thundering) {
        if (thundering) {
            return THUNDER;
        }
        return storm ? RAIN : CLEAR;
    }

    public void applyTo(World world) {
        world.setStorm(storm);
        world.setThundering(thundering);
    }
}
