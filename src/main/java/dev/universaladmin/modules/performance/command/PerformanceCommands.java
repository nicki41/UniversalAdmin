package dev.universaladmin.modules.performance.command;

import dev.universaladmin.command.AdminCommand;
import dev.universaladmin.command.Arguments;
import dev.universaladmin.command.BaseAdminCommand;
import dev.universaladmin.command.CommandInvocation;
import dev.universaladmin.core.time.DurationParser;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.modules.performance.EntityOverviewSnapshot;
import dev.universaladmin.modules.performance.EntityTypeCount;
import dev.universaladmin.modules.performance.PerformancePermissions;
import dev.universaladmin.modules.performance.PerformanceSamplingService;
import dev.universaladmin.modules.performance.PerformanceSnapshot;
import dev.universaladmin.modules.performance.WorldPerformanceSnapshot;
import dev.universaladmin.modules.performance.action.ClearEntitiesAction;
import dev.universaladmin.modules.performance.action.ClearEntitiesInput;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.bukkit.entity.EntityType;

/**
 * {@code /admin perf} (TPS, MSPT, memory at a glance) and
 * {@code /admin entities [clear <type|all> [world] confirm]}. Both read the
 * module's cached sample - nothing is recomputed per command.
 */
public final class PerformanceCommands {

    private static final long MEGABYTE = 1024L * 1024L;
    private static final int TOP_TYPES = 10;

    private PerformanceCommands() {
    }

    public static List<AdminCommand> all(PerformanceSamplingService sampling) {
        return List.of(new Perf(sampling), new Entities(sampling));
    }

    /** {@code /admin perf} - also reachable as {@code /lag}. */
    static final class Perf extends BaseAdminCommand {

        private final PerformanceSamplingService sampling;

        Perf(PerformanceSamplingService sampling) {
            super("perf", PerformancePermissions.VIEW, MessageKey.of("commands.perf.usage"), MessageKey.of("commands.perf.description"));
            this.sampling = sampling;
            aliases("performance", "lag", "tps");
            shortcuts("lag");
        }

        @Override
        public void execute(CommandInvocation invocation) {
            PerformanceSnapshot snapshot = sampling.snapshot();
            invocation.line("commands.perf.header");
            invocation.line("commands.perf.tps", colorTps(snapshot.tps1m()), colorTps(snapshot.tps5m()), colorTps(snapshot.tps15m()));
            invocation.line("commands.perf.mspt", "%.1f".formatted(snapshot.mspt()));
            invocation.line("commands.perf.memory", snapshot.usedMemoryBytes() / MEGABYTE, snapshot.maxMemoryBytes() / MEGABYTE,
                    "%.0f".formatted(snapshot.usedMemoryPercent()));
            invocation.line("commands.perf.load", snapshot.onlinePlayers(), snapshot.worldCount(), snapshot.loadedChunks(),
                    snapshot.entityCount());
            invocation.line("commands.perf.uptime", DurationParser.format(snapshot.uptime()));
        }

        private static String colorTps(double tps) {
            return "%.1f".formatted(Math.min(tps, 20.0));
        }
    }

    /** {@code /admin entities} (top types and per-world counts) or {@code /admin entities clear <type|all> [world] confirm}. */
    static final class Entities extends BaseAdminCommand {

        private final PerformanceSamplingService sampling;

        Entities(PerformanceSamplingService sampling) {
            super("entities", PerformancePermissions.VIEW, MessageKey.of("commands.entities.usage"),
                    MessageKey.of("commands.entities.description"));
            this.sampling = sampling;
        }

        @Override
        public void execute(CommandInvocation invocation) {
            if (invocation.arg(0).map(arg -> arg.equalsIgnoreCase("clear")).orElse(false)) {
                clear(invocation);
                return;
            }
            EntityOverviewSnapshot overview = sampling.entityOverview();
            invocation.line("commands.entities.header", overview.totalEntities());
            overview.byType().stream().limit(TOP_TYPES).forEach(count -> invocation.line("commands.entities.type-entry",
                    count.type().name().toLowerCase(Locale.ROOT), count.count()));
            for (WorldPerformanceSnapshot world : overview.byWorld()) {
                invocation.line("commands.entities.world-entry", world.worldName(), world.entities(), world.loadedChunks());
            }
        }

        private void clear(CommandInvocation invocation) {
            if (!invocation.hasPermission(PerformancePermissions.ENTITY_CLEAR)) {
                invocation.reply("error.no-permission");
                return;
            }
            if (invocation.argCount() < 2) {
                invocation.usage(this);
                return;
            }
            String typeArg = invocation.args().get(1);
            Set<EntityType> types;
            if (typeArg.equalsIgnoreCase("all")) {
                types = EnumSet.allOf(EntityType.class);
            } else {
                Optional<EntityType> type = Arguments.enumValue(EntityType.class, typeArg);
                if (type.isEmpty()) {
                    invocation.reply("commands.entities.unknown-type", typeArg);
                    return;
                }
                types = EnumSet.of(type.get());
            }
            String world = null;
            if (invocation.argCount() > 2 && !invocation.args().get(2).equalsIgnoreCase("confirm")) {
                world = invocation.args().get(2);
                if (Arguments.world(world).isEmpty()) {
                    invocation.reply("command.error.world-not-found", world);
                    return;
                }
            }
            int preview = sampling.previewClearCount(types, world);
            if (preview == 0) {
                invocation.reply("performance.gui.entities.nothing-to-clear");
                return;
            }
            if (!invocation.confirmed()) {
                invocation.reply("commands.entities.preview", preview);
                return;
            }
            invocation.run(ClearEntitiesAction.ID, new ClearEntitiesInput(types, world));
        }

        @Override
        public List<String> complete(CommandInvocation invocation) {
            int count = invocation.argCount();
            if (count <= 1) {
                return List.of("clear");
            }
            if (!invocation.args().get(0).equalsIgnoreCase("clear")) {
                return List.of();
            }
            return switch (count) {
                case 2 -> {
                    List<String> types = new ArrayList<>();
                    types.add("all");
                    sampling.entityOverview().byType().stream()
                            .map(EntityTypeCount::type)
                            .filter(type -> !sampling.isProtected(type))
                            .map(type -> type.name().toLowerCase(Locale.ROOT))
                            .forEach(types::add);
                    yield types;
                }
                case 3 -> {
                    List<String> options = new ArrayList<>(Arguments.worldNames());
                    options.add("confirm");
                    yield options;
                }
                case 4 -> List.of("confirm");
                default -> List.of();
            };
        }
    }
}
