package dev.universaladmin.modules.server.action;

/** {@code subtitle} may be blank. Fade-in/stay/fade-out use {@link BroadcastTitleAction}'s fixed defaults. */
public record BroadcastTitleInput(String title, String subtitle) {
}
