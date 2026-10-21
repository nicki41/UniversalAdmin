package dev.universaladmin.modules.moderation.importing;

import java.util.Optional;
import java.util.Set;

/**
 * Reads the punishments of one other plugin (LiteBans, AdvancedBan, the
 * vanilla ban lists, EssentialsX). Implementations only read and translate;
 * {@link ImportService} decides what is new and writes it. Every method
 * blocks and is called off the main thread.
 */
public interface PunishmentImporter {

    /** Lower-case id used in {@code /admin import <id>} and stored with every imported row. */
    String id();

    /** Name for the user ("LiteBans"). */
    String displayName();

    /**
     * Where this source's data is on this server ("MySQL litebans@db:3306",
     * "plugins/LiteBans/litebans.mv.db") - empty if there is nothing to
     * import.
     */
    Optional<String> locate();

    /** Streams every entry to {@code sink}. */
    void read(ImportSink sink) throws ImportException;

    /**
     * What happens to the source's own entries after they were imported -
     * shown in the preview. Empty: they are left alone.
     */
    default Optional<String> afterImportNoteKey() {
        return Optional.empty();
    }

    /**
     * Called once after a successful import with the {@code sourceId}s that
     * were written in this run. The vanilla importer uses it to take the
     * moved entries off the vanilla ban lists.
     */
    default void afterImport(Set<String> importedSourceIds) throws ImportException {
    }
}
