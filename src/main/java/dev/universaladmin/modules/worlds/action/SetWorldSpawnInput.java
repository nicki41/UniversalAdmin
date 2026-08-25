package dev.universaladmin.modules.worlds.action;

/** {@code x}/{@code y}/{@code z}/{@code yaw} {@code null} means "use the acting player's current location". */
public record SetWorldSpawnInput(String worldName, Double x, Double y, Double z, Float yaw) {

    public static SetWorldSpawnInput atActorLocation(String worldName) {
        return new SetWorldSpawnInput(worldName, null, null, null, null);
    }
}
