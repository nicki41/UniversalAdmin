package dev.universaladmin.voicechat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.universaladmin.status.MuteKind;
import dev.universaladmin.status.PlayerStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Who gets through the microphone gate: voice and full mutes never, vanished staff only in the staff group. */
class VoiceGateTest {

    private static final UUID STEVE = UUID.randomUUID();

    private final FakeStatus status = new FakeStatus();
    private final AtomicBoolean silenceVanished = new AtomicBoolean(true);
    private final VoiceGate gate = new VoiceGate(status, silenceVanished::get);

    @Test
    void aVoiceOrFullMuteBlocksAChatMuteDoesNot() {
        status.mutes.put(STEVE, new PlayerStatus.Mute("Spam", null, MuteKind.CHAT));
        assertTrue(gate.check(STEVE, false).isEmpty());

        status.mutes.put(STEVE, new PlayerStatus.Mute("Shouting", Instant.now().plusSeconds(60), MuteKind.VOICE));
        VoiceGate.Block block = gate.check(STEVE, false).orElseThrow();
        assertEquals(VoiceGate.Reason.MUTED, block.reason());
        assertEquals("Shouting", block.mute().reason());

        status.mutes.put(STEVE, new PlayerStatus.Mute("Both", null));
        assertEquals(VoiceGate.Reason.MUTED, gate.check(STEVE, true).orElseThrow().reason());
    }

    @Test
    void vanishedStaffAreSilentExceptInTheStaffGroupOrWhenSwitchedOff() {
        status.vanished.add(STEVE);

        assertEquals(VoiceGate.Reason.VANISHED, gate.check(STEVE, false).orElseThrow().reason());
        assertTrue(gate.check(STEVE, true).isEmpty());

        silenceVanished.set(false);
        assertTrue(gate.check(STEVE, false).isEmpty());
    }

    @Test
    void everyoneElseTalks() {
        assertTrue(gate.check(UUID.randomUUID(), false).isEmpty());
    }

    private static final class FakeStatus implements PlayerStatus {

        private final Map<UUID, Mute> mutes = new HashMap<>();
        private final Set<UUID> vanished = new HashSet<>();

        @Override
        public boolean vanished(UUID playerId) {
            return vanished.contains(playerId);
        }

        @Override
        public boolean staffMode(UUID playerId) {
            return false;
        }

        @Override
        public boolean frozen(UUID playerId) {
            return false;
        }

        @Override
        public boolean godmode(UUID playerId) {
            return false;
        }

        @Override
        public Optional<Mute> mute(UUID playerId) {
            return Optional.ofNullable(mutes.get(playerId));
        }

        @Override
        public int activeWarnings(UUID playerId) {
            return 0;
        }

        @Override
        public int punishments(UUID playerId) {
            return 0;
        }

        @Override
        public int vanishedOnline() {
            return vanished.size();
        }
    }
}
