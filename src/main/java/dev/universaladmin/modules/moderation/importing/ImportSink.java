package dev.universaladmin.modules.moderation.importing;

/**
 * Where a {@link PunishmentImporter} delivers what it reads, one entry at a
 * time - so neither the preview nor the import holds a whole source in
 * memory.
 */
public interface ImportSink {

    void punishment(ImportedPunishment punishment);

    void note(ImportedNote note);

    /** An entry that cannot be imported, and why - counted and shown in the preview. */
    void skip(SkipReason reason);

    enum SkipReason {
        /** No player and no IP address to attach it to. */
        NO_PLAYER,
        /** A kind of entry UniversalAdmin has no equivalent for (an IP-only mute, a wildcard IP ban). */
        UNSUPPORTED,
        /** Unreadable data (an unknown type, a broken date). */
        INVALID
    }
}
