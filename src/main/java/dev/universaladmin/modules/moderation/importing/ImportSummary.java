package dev.universaladmin.modules.moderation.importing;

import dev.universaladmin.modules.moderation.PunishmentType;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * What an import would write (preview) or wrote (run): punishments per type
 * and how many of them are in force, notes, players, what was skipped and
 * what earlier runs already imported.
 */
public record ImportSummary(
        String source,
        String location,
        Map<PunishmentType, Integer> punishments,
        Map<PunishmentType, Integer> active,
        int notes,
        int players,
        Map<ImportSink.SkipReason, Integer> skipped,
        int alreadyImported,
        Duration took) {

    public ImportSummary {
        punishments = Map.copyOf(punishments);
        active = Map.copyOf(active);
        skipped = Map.copyOf(skipped);
    }

    public int totalPunishments() {
        return punishments.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int totalSkipped() {
        return skipped.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int count(PunishmentType type) {
        return punishments.getOrDefault(type, 0);
    }

    public int activeCount(PunishmentType type) {
        return active.getOrDefault(type, 0);
    }

    /** Mutable tally the service fills while it streams a source. */
    static final class Tally {

        private final Map<PunishmentType, Integer> punishments = new EnumMap<>(PunishmentType.class);
        private final Map<PunishmentType, Integer> active = new EnumMap<>(PunishmentType.class);
        private final Map<ImportSink.SkipReason, Integer> skipped = new EnumMap<>(ImportSink.SkipReason.class);
        private int notes;
        private int alreadyImported;

        void punishment(ImportedPunishment punishment, boolean activeNow) {
            punishments.merge(punishment.type(), 1, Integer::sum);
            if (activeNow) {
                active.merge(punishment.type(), 1, Integer::sum);
            }
        }

        void note() {
            notes++;
        }

        void skip(ImportSink.SkipReason reason) {
            skipped.merge(reason, 1, Integer::sum);
        }

        void alreadyImported() {
            alreadyImported++;
        }

        int written() {
            return punishments.values().stream().mapToInt(Integer::intValue).sum() + notes;
        }

        ImportSummary toSummary(String source, String location, int players, Duration took) {
            return new ImportSummary(source, location, punishments, active, notes, players, skipped, alreadyImported, took);
        }
    }
}
