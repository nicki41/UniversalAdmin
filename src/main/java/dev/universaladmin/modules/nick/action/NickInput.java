package dev.universaladmin.modules.nick.action;

import java.util.UUID;

/**
 * A nick to put on {@code targetId}.
 *
 * @param targetName the target's real name
 * @param name       the nick, {@code null} for a random one
 * @param skin       which skin to wear with it
 */
public record NickInput(UUID targetId, String targetName, String name, Skin skin) {

    public NickInput {
        skin = skin == null ? Skin.ofName() : skin;
    }

    /**
     * @param account the account for {@link Kind#ACCOUNT}, {@code null} otherwise
     */
    public record Skin(Kind kind, String account) {

        /** The skin of the account named like the nick, if there is one (and {@code nick.skin-of-name} is on). */
        public static Skin ofName() {
            return new Skin(Kind.OF_NAME, null);
        }

        public static Skin own() {
            return new Skin(Kind.OWN, null);
        }

        public static Skin random() {
            return new Skin(Kind.RANDOM, null);
        }

        public static Skin of(String account) {
            return new Skin(Kind.ACCOUNT, account);
        }
    }

    public enum Kind {
        OF_NAME,
        OWN,
        RANDOM,
        ACCOUNT
    }
}
