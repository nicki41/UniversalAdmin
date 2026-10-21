package dev.universaladmin.modules.moderation.importing;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Collects what an importer emits, for assertions. */
final class RecordingSink implements ImportSink {

    final List<ImportedPunishment> punishments = new ArrayList<>();
    final List<ImportedNote> notes = new ArrayList<>();
    final Map<SkipReason, Integer> skipped = new EnumMap<>(SkipReason.class);

    @Override
    public void punishment(ImportedPunishment punishment) {
        punishments.add(punishment);
    }

    @Override
    public void note(ImportedNote note) {
        notes.add(note);
    }

    @Override
    public void skip(SkipReason reason) {
        skipped.merge(reason, 1, Integer::sum);
    }

    ImportedPunishment bySourceId(String sourceId) {
        return find(sourceId).orElseThrow(() -> new AssertionError("no punishment " + sourceId + " in " + punishments));
    }

    Optional<ImportedPunishment> find(String sourceId) {
        return punishments.stream().filter(punishment -> punishment.sourceId().equals(sourceId)).findFirst();
    }

    int skipped(SkipReason reason) {
        return skipped.getOrDefault(reason, 0);
    }
}
