package dev.universaladmin.modules.worlds.command;

import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.Arguments;
import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.worlds.WeatherState;
import dev.universaladmin.modules.worlds.WorldInfoService;
import dev.universaladmin.modules.worlds.WorldSummary;
import dev.universaladmin.modules.worlds.WorldsPermissions;
import dev.universaladmin.modules.worlds.action.SetGameRuleInput;
import dev.universaladmin.modules.worlds.action.SetWorldBorderCenterInput;
import dev.universaladmin.modules.worlds.action.SetWorldBorderSizeInput;
import dev.universaladmin.modules.worlds.action.SetWorldDifficultyInput;
import dev.universaladmin.modules.worlds.action.SetWorldSpawnInput;
import dev.universaladmin.modules.worlds.action.SetWorldTimeInput;
import dev.universaladmin.modules.worlds.action.SetWorldWeatherInput;
import dev.universaladmin.modules.worlds.action.WorldActionIds;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * {@code /admin worlds} lists every loaded world; {@code /admin world [world]
 * <info|tp|time|weather|difficulty|setspawn|border|gamerule> ...} changes
 * one. The world argument may be omitted by a player, meaning "the world I
 * am standing in".
 */
public final class WorldCommands {

    private static final List<String> ACTIONS =
            List.of("info", "tp", "time", "weather", "difficulty", "setspawn", "border", "gamerule");

    private static final Map<String, Long> TIME_NAMES = Map.of(
            "day", 1000L, "noon", 6000L, "sunset", 12000L, "night", 13000L, "midnight", 18000L, "sunrise", 23000L);

    private WorldCommands() {
    }

    public static List<AdminCommand> all(WorldInfoService info) {
        return List.of(new ListWorlds(info), new WorldControl(info));
    }

    /** {@code /admin worlds} - one line per loaded world. */
    static final class ListWorlds extends BaseAdminCommand {

        private final WorldInfoService info;

        ListWorlds(WorldInfoService info) {
            super("worlds", WorldsPermissions.VIEW, MessageKey.of("commands.worlds.usage"), MessageKey.of("commands.worlds.description"));
            this.info = info;
        }

        @Override
        public void execute(CommandInvocation invocation) {
            List<World> worlds = org.bukkit.Bukkit.getWorlds();
            invocation.line("commands.worlds.header", worlds.size());
            for (World world : worlds) {
                WorldSummary summary = info.summary(world);
                invocation.line("commands.worlds.entry", summary.name(), summary.environment().name().toLowerCase(Locale.ROOT),
                        summary.players(), summary.loadedChunks(), summary.entities());
            }
        }
    }

    /** {@code /admin world [world] <action> ...}. */
    static final class WorldControl extends BaseAdminCommand {

        private final WorldInfoService info;

        WorldControl(WorldInfoService info) {
            super("world", WorldsPermissions.VIEW, MessageKey.of("commands.world.usage"), MessageKey.of("commands.world.description"));
            this.info = info;
            aliases("w");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.argCount() < 1) {
                invocation.usage(this);
                return;
            }
            // Either "<world> <action> ..." or, for a player, "<action> ..." in the current world.
            int actionIndex;
            World world;
            String first = invocation.args().get(0).toLowerCase(Locale.ROOT);
            if (ACTIONS.contains(first) && invocation.player().isPresent()) {
                world = invocation.player().get().getWorld();
                actionIndex = 0;
            } else {
                Optional<World> named = Arguments.world(invocation.args().get(0));
                if (named.isEmpty()) {
                    invocation.reply("command.error.world-not-found", invocation.args().get(0));
                    return;
                }
                world = named.get();
                actionIndex = 1;
            }
            String action = invocation.arg(actionIndex).map(arg -> arg.toLowerCase(Locale.ROOT)).orElse("info");
            int valueIndex = actionIndex + 1;
            String name = world.getName();
            switch (action) {
                case "info" -> info(invocation, world);
                case "tp" -> {
                    if (invocation.player().isEmpty()) {
                        invocation.reply("command.error.player-only");
                        return;
                    }
                    invocation.run(WorldActionIds.TELEPORT_TO_SPAWN, name);
                }
                case "time" -> time(invocation, name, valueIndex);
                case "weather" -> weather(invocation, name, valueIndex);
                case "difficulty" -> difficulty(invocation, name, valueIndex);
                case "setspawn" -> {
                    if (invocation.player().isEmpty()) {
                        invocation.reply("command.error.player-only");
                        return;
                    }
                    invocation.run(WorldActionIds.SET_SPAWN, SetWorldSpawnInput.atActorLocation(name));
                }
                case "border" -> border(invocation, world, valueIndex);
                case "gamerule" -> gamerule(invocation, name, valueIndex);
                default -> invocation.usage(this);
            }
        }

        private void info(CommandInvocation invocation, World world) {
            WorldSummary summary = info.summary(world);
            invocation.line("commands.world.info.header", summary.name());
            invocation.line("commands.world.info.environment", summary.environment().name().toLowerCase(Locale.ROOT));
            invocation.line("commands.world.info.players", summary.players());
            invocation.line("commands.world.info.chunks-entities", summary.loadedChunks(), summary.entities());
            invocation.line("commands.world.info.difficulty", summary.difficulty().name().toLowerCase(Locale.ROOT));
            invocation.line("commands.world.info.time", summary.time());
            invocation.line("commands.world.info.weather",
                    WeatherState.of(summary.storm(), summary.thundering()).name().toLowerCase(Locale.ROOT));
            invocation.line("commands.world.info.border", "%.0f".formatted(world.getWorldBorder().getSize()));
        }

        private void time(CommandInvocation invocation, String world, int valueIndex) {
            if (invocation.argCount() <= valueIndex) {
                invocation.usage(this);
                return;
            }
            String raw = invocation.args().get(valueIndex).toLowerCase(Locale.ROOT);
            Long ticks = TIME_NAMES.get(raw);
            if (ticks == null) {
                Optional<Integer> parsed = invocation.integer(valueIndex);
                if (parsed.isEmpty()) {
                    return;
                }
                ticks = (long) parsed.get();
            }
            invocation.run(WorldActionIds.SET_TIME, new SetWorldTimeInput(world, ticks));
        }

        private void weather(CommandInvocation invocation, String world, int valueIndex) {
            Optional<WeatherState> state = invocation.arg(valueIndex).flatMap(raw -> Arguments.enumValue(WeatherState.class, raw));
            if (state.isEmpty()) {
                invocation.usage(this);
                return;
            }
            invocation.run(WorldActionIds.SET_WEATHER, new SetWorldWeatherInput(world, state.get()));
        }

        private void difficulty(CommandInvocation invocation, String world, int valueIndex) {
            Optional<Difficulty> difficulty = invocation.arg(valueIndex).flatMap(raw -> Arguments.enumValue(Difficulty.class, raw));
            if (difficulty.isEmpty()) {
                invocation.usage(this);
                return;
            }
            invocation.run(WorldActionIds.SET_DIFFICULTY, new SetWorldDifficultyInput(world, difficulty.get()));
        }

        /** {@code border <size> [seconds]} or {@code border center <x> <z>|here}. */
        private void border(CommandInvocation invocation, World world, int valueIndex) {
            if (invocation.argCount() <= valueIndex) {
                invocation.usage(this);
                return;
            }
            String name = world.getName();
            if (invocation.args().get(valueIndex).equalsIgnoreCase("center")) {
                double x;
                double z;
                if (invocation.arg(valueIndex + 1).map(arg -> arg.equalsIgnoreCase("here")).orElse(false)) {
                    Optional<Player> player = invocation.player();
                    if (player.isEmpty()) {
                        invocation.reply("command.error.player-only");
                        return;
                    }
                    x = player.get().getLocation().getX();
                    z = player.get().getLocation().getZ();
                } else {
                    if (invocation.argCount() < valueIndex + 3) {
                        invocation.usage(this);
                        return;
                    }
                    Optional<Double> parsedX = invocation.decimal(valueIndex + 1);
                    Optional<Double> parsedZ = parsedX.isPresent() ? invocation.decimal(valueIndex + 2) : Optional.empty();
                    if (parsedZ.isEmpty()) {
                        return;
                    }
                    x = parsedX.get();
                    z = parsedZ.get();
                }
                invocation.run(WorldActionIds.SET_BORDER_CENTER, new SetWorldBorderCenterInput(name, x, z));
                return;
            }
            Optional<Double> size = invocation.decimal(valueIndex);
            if (size.isEmpty()) {
                return;
            }
            Long seconds = null;
            if (invocation.argCount() > valueIndex + 1) {
                Optional<Integer> parsed = invocation.integer(valueIndex + 1);
                if (parsed.isEmpty()) {
                    return;
                }
                seconds = (long) parsed.get();
            }
            invocation.run(WorldActionIds.SET_BORDER_SIZE, new SetWorldBorderSizeInput(name, size.get(), seconds));
        }

        private void gamerule(CommandInvocation invocation, String world, int valueIndex) {
            if (invocation.argCount() < valueIndex + 2) {
                invocation.usage(this);
                return;
            }
            String rule = invocation.args().get(valueIndex);
            String value = invocation.args().get(valueIndex + 1);
            invocation.run(WorldActionIds.SET_GAME_RULE, new SetGameRuleInput(world, rule, value));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            int count = invocation.argCount();
            if (count <= 1) {
                List<String> options = new java.util.ArrayList<>(Arguments.worldNames());
                if (invocation.player().isPresent()) {
                    options.addAll(ACTIONS);
                }
                return options;
            }
            String first = invocation.args().get(0).toLowerCase(Locale.ROOT);
            boolean implicitWorld = ACTIONS.contains(first) && invocation.player().isPresent();
            int actionIndex = implicitWorld ? 0 : 1;
            if (count == actionIndex + 1) {
                return ACTIONS;
            }
            String action = invocation.args().get(actionIndex).toLowerCase(Locale.ROOT);
            int valuePosition = count - actionIndex - 1;
            return switch (action) {
                case "time" -> valuePosition == 1 ? List.copyOf(TIME_NAMES.keySet()) : List.of();
                case "weather" -> valuePosition == 1 ? Arguments.enumNames(WeatherState.class) : List.of();
                case "difficulty" -> valuePosition == 1 ? Arguments.enumNames(Difficulty.class) : List.of();
                case "border" -> valuePosition == 1 ? List.of("center", "1000", "5000", "10000") : List.of();
                case "gamerule" -> valuePosition == 1 ? gameRuleNames() : List.of();
                default -> List.of();
            };
        }

        /** Read from a live world, like the gamerule GUI - so rules added by newer game versions appear without a code change. */
        private static List<String> gameRuleNames() {
            List<World> worlds = org.bukkit.Bukkit.getWorlds();
            return worlds.isEmpty() ? List.of() : java.util.Arrays.stream(worlds.get(0).getGameRules()).sorted().toList();
        }
    }
}
