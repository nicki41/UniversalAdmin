package dev.universaladmin.core.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimpleRegistryTest {

    @Test
    void registersAndLooksUpByKey() {
        Registry<String, Integer> registry = new SimpleRegistry<>();

        registry.register("answer", 42);

        assertTrue(registry.contains("answer"));
        assertEquals(42, registry.get("answer").orElseThrow());
    }

    @Test
    void rejectsDuplicateKeys() {
        Registry<String, Integer> registry = new SimpleRegistry<>();
        registry.register("answer", 42);

        assertThrows(IllegalStateException.class, () -> registry.register("answer", 43));
    }

    @Test
    void unregisterRemovesTheEntry() {
        Registry<String, Integer> registry = new SimpleRegistry<>();
        registry.register("answer", 42);

        registry.unregister("answer");

        assertFalse(registry.contains("answer"));
    }
}
