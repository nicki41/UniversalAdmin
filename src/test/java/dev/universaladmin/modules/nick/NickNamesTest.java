package dev.universaladmin.modules.nick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NickNamesTest {

    @Test
    void aNickIsAValidMinecraftNameAndNoKeyword() {
        assertTrue(NickNames.valid("Notch"));
        assertTrue(NickNames.valid("a_b"));
        assertTrue(NickNames.valid("Sixteen_Letters1"));
        assertFalse(NickNames.valid("ab"));
        assertFalse(NickNames.valid("Seventeen_Letters"));
        assertFalse(NickNames.valid("with space"));
        assertFalse(NickNames.valid("Ümlaut"));
        assertFalse(NickNames.valid("Random"));
        assertFalse(NickNames.valid("off"));
        assertFalse(NickNames.valid(null));
    }

    @Test
    void madeUpNamesAreValidAndFree() {
        Random random = new Random(7);
        Set<String> taken = Set.of("swiftfox", "quietwolf");
        for (int i = 0; i < 500; i++) {
            String name = NickNames.random(List.of(), candidate -> taken.contains(candidate.toLowerCase()), random).orElseThrow();
            assertTrue(NickNames.valid(name), name);
            assertFalse(taken.contains(name.toLowerCase()), name);
        }
    }

    @Test
    void aConfiguredPoolIsUsedAndATakenNameIsSkipped() {
        Optional<String> name = NickNames.random(List.of("Bob", "Alice"), "Bob"::equals, new Random(1));
        assertEquals(Optional.of("Alice"), name);
    }

    @Test
    void emptyWhenEveryNameIsTaken() {
        assertEquals(Optional.empty(), NickNames.random(List.of("Bob"), candidate -> true, new Random(1)));
    }
}
