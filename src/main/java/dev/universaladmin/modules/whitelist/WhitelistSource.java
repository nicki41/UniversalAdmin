package dev.universaladmin.modules.whitelist;

/**
 * Who "owns" a {@link WhitelistEntry} row - only ever {@link #UNIVERSAL_ADMIN}
 * today, but a real enum rather than an implicit "a row exists, therefore we
 * own it" assumption. This is what the expiry sweep and the join-time check
 * assert before touching anything: a native whitelist entry with no matching
 * row (added via vanilla {@code /whitelist add}, a hand-edited {@code
 * whitelist.json}, or another plugin) is never represented by a row at all,
 * so nothing automatic ever considers it - see
 * docs/user/modules/whitelist.md's "Ownership & the expiry sweep" section.
 */
public enum WhitelistSource {
    UNIVERSAL_ADMIN
}
