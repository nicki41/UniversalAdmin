package dev.universaladmin.status;

/**
 * Where a mute silences a player: in the text chat, in a voice chat, or
 * both. The voice chat part needs an extension that connects one (see
 * {@link dev.universaladmin.voice.VoiceChat}); every mute issued before
 * UniversalAdmin 1.5 is {@link #FULL}.
 *
 * @since 1.4
 */
public enum MuteKind {

    /** Only the text chat. */
    CHAT,

    /** Only the voice chat. */
    VOICE,

    /** The text chat and the voice chat. */
    FULL;

    /** Whether this kind of mute blocks the text chat. */
    public boolean coversChat() {
        return this != VOICE;
    }

    /** Whether this kind of mute blocks the voice chat. */
    public boolean coversVoice() {
        return this != CHAT;
    }
}
