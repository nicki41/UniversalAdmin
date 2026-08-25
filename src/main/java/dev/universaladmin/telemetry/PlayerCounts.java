package dev.universaladmin.telemetry;

/**
 * The only per-heartbeat measurement: two numbers.
 *
 * <p>{@code online} feeds the aggregate "players online across all
 * installations" figure; {@code max} gives it scale (a full 20-slot server and
 * a quarter-full 500-slot server are different signals). Both are plain
 * counts - no player identity of any kind (name, UUID, IP, address) is read
 * here, and none can be reconstructed from a number.
 *
 * <p>Read on the Paper main thread, because {@code Server#getOnlinePlayers()}
 * is main-thread state - see {@link TelemetryService}.
 */
public record PlayerCounts(int online, int max) {

    public PlayerCounts {
        if (online < 0) {
            throw new IllegalArgumentException("online must not be negative (was " + online + ")");
        }
        if (max < 0) {
            throw new IllegalArgumentException("max must not be negative (was " + max + ")");
        }
    }
}
