package dev.universaladmin.modules.nick;

/** Another player has this nick already - the unique name index of {@code ua_nicks} refused it. */
public final class NickNameTakenException extends RuntimeException {

    private final String name;

    public NickNameTakenException(String name, Throwable cause) {
        super("The nick " + name + " is taken", cause);
        this.name = name;
    }

    public String name() {
        return name;
    }
}
