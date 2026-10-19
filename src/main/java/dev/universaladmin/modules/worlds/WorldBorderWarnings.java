package dev.universaladmin.modules.worlds;

import java.lang.reflect.Method;
import org.bukkit.WorldBorder;

/**
 * The world border's warning time across Paper versions. Paper 1.21.11
 * added tick-based {@code get/setWarningTimeTicks} and deprecated the
 * second-based {@code get/setWarningTime} for removal; 1.21.7 - 1.21.10 only
 * have the second-based pair. Picks whichever the running server has, once.
 */
public final class WorldBorderWarnings {

    private static final int TICKS_PER_SECOND = 20;
    private static final Method SET_TICKS = method("setWarningTimeTicks", int.class);
    private static final Method GET_TICKS = method("getWarningTimeTicks");
    private static final Method SET_SECONDS = method("setWarningTime", int.class);
    private static final Method GET_SECONDS = method("getWarningTime");

    private WorldBorderWarnings() {
    }

    public static void setSeconds(WorldBorder border, int seconds) {
        if (SET_TICKS != null) {
            invoke(SET_TICKS, border, seconds * TICKS_PER_SECOND);
        } else {
            invoke(SET_SECONDS, border, seconds);
        }
    }

    public static int seconds(WorldBorder border) {
        if (GET_TICKS != null) {
            return ((Number) invoke(GET_TICKS, border)).intValue() / TICKS_PER_SECOND;
        }
        return ((Number) invoke(GET_SECONDS, border)).intValue();
    }

    private static Method method(String name, Class<?>... parameters) {
        try {
            return WorldBorder.class.getMethod(name, parameters);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    private static Object invoke(Method method, WorldBorder border, Object... args) {
        if (method == null) {
            throw new IllegalStateException("This server's WorldBorder has no warning-time method");
        }
        try {
            return method.invoke(border, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not access the world border warning time", e);
        }
    }
}
