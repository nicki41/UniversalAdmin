package dev.universaladmin.voicechat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** A muted player sees a notice at most every few seconds, however many packets they send; the plugin needs API 1.4. */
class VoiceNoticesTest {

    private final AtomicLong now = new AtomicLong(1_000);
    private final VoiceNotices notices = new VoiceNotices(null, null, now::get);

    @Test
    void oneNoticePerIntervalAndPlayer() {
        UUID steve = UUID.randomUUID();
        UUID alex = UUID.randomUUID();

        assertTrue(notices.due(steve));
        assertFalse(notices.due(steve));
        assertTrue(notices.due(alex));

        now.addAndGet(VoiceNotices.INTERVAL.toNanos() - 1);
        assertFalse(notices.due(steve));
        now.addAndGet(1);
        assertTrue(notices.due(steve));

        notices.forget(alex);
        assertTrue(notices.due(alex));
    }

    @Test
    void versionsCompareNumerically() {
        assertTrue(VoiceChatPlugin.compare("1.10", "1.4") > 0);
        assertTrue(VoiceChatPlugin.compare("1.3", "1.4") < 0);
        assertEquals(0, VoiceChatPlugin.compare("1.4", "1.4.0"));
    }
}
