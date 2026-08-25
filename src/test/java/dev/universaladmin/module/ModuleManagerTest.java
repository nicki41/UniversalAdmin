package dev.universaladmin.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.ActionRegistry;
import dev.universaladmin.audit.AuditService;
import dev.universaladmin.core.ServiceRegistry;
import dev.universaladmin.core.UniversalAdmin;
import dev.universaladmin.gui.GuiFramework;
import dev.universaladmin.gui.GuiRegistry;
import dev.universaladmin.gui.GuiSessionManager;
import dev.universaladmin.gui.MaterialIconProvider;
import dev.universaladmin.localization.MessageService;
import dev.universaladmin.notification.NotificationService;
import dev.universaladmin.permission.PermissionRegistry;
import dev.universaladmin.scheduler.TaskScheduler;
import dev.universaladmin.settings.SettingRegistry;
import dev.universaladmin.settings.SettingsService;
import dev.universaladmin.storage.StorageService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link ModuleManager}/{@link ModuleRegistry} without a running
 * Paper server - only {@link Plugin} and the other Bukkit-facing services
 * are mocked, since none of the test modules below actually call into them
 * (they only touch {@link ModuleContext#resources()}'s plain-Closeable path).
 */
class ModuleManagerTest {

    private ModuleManager managerWithRegistry(ModuleRegistry registry) {
        UniversalAdmin platform = new UniversalAdmin(
                mock(Plugin.class),
                "0.1.0-TEST",
                Instant.now(),
                Logger.getLogger("ModuleManagerTest"),
                new SettingRegistry(),
                mock(SettingsService.class),
                mock(TaskScheduler.class),
                mock(StorageService.class),
                new ServiceRegistry(),
                new ActionRegistry(),
                mock(ActionExecutor.class),
                new GuiRegistry(),
                new GuiFramework(new GuiSessionManager(), new MaterialIconProvider(Logger.getLogger("ModuleManagerTest"))),
                new PermissionRegistry(),
                registry,
                mock(AuditService.class),
                mock(MessageService.class),
                mock(NotificationService.class));
        return new ModuleManager(platform);
    }

    @Test
    void registeringAModuleMarksItDiscovered() {
        ModuleRegistry registry = new ModuleRegistry();
        ModuleManager manager = managerWithRegistry(registry);
        RecordingModule a = new RecordingModule("a");

        manager.register(a);

        assertEquals(ModuleState.DISCOVERED, registry.state(a.descriptor().id()));
        assertTrue(registry.all().contains(a));
    }

    @Test
    void rejectsDuplicateModuleId() {
        ModuleRegistry registry = new ModuleRegistry();
        ModuleManager manager = managerWithRegistry(registry);
        manager.register(new RecordingModule("a"));

        assertThrows(IllegalStateException.class, () -> manager.register(new RecordingModule("a")));
    }

    @Test
    void loadThenEnableRunInOrderAndTransitionState() {
        ModuleRegistry registry = new ModuleRegistry();
        ModuleManager manager = managerWithRegistry(registry);
        RecordingModule a = new RecordingModule("a");
        manager.register(a);

        manager.loadAll();
        assertEquals(ModuleState.LOADED, registry.state(a.descriptor().id()));

        manager.enableAll();
        assertEquals(ModuleState.ENABLED, registry.state(a.descriptor().id()));

        assertEquals(List.of("a:load", "a:enable"), a.calls());
    }

    @Test
    void aFailingModuleIsIsolatedFromOthers() {
        ModuleRegistry registry = new ModuleRegistry();
        ModuleManager manager = managerWithRegistry(registry);
        RecordingModule failing = new RecordingModule("failing").failOnEnable();
        RecordingModule ok = new RecordingModule("ok");
        manager.register(failing);
        manager.register(ok);

        manager.loadAll();
        manager.enableAll();

        assertEquals(ModuleState.FAILED, registry.state(failing.descriptor().id()));
        assertEquals(ModuleState.ENABLED, registry.state(ok.descriptor().id()));
        assertTrue(ok.calls().contains("ok:enable"));
    }

    @Test
    void disableReleasesResourcesEvenWhenOnDisableThrows() {
        ModuleRegistry registry = new ModuleRegistry();
        ModuleManager manager = managerWithRegistry(registry);
        List<String> events = new ArrayList<>();
        Module module = new Module() {
            private final ModuleDescriptor descriptor =
                    ModuleDescriptor.builder(ModuleId.of("test", "a"), "A").build();

            @Override
            public ModuleDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public void onEnable(ModuleContext context) {
                context.resources().closeable(() -> events.add("closed"));
            }

            @Override
            public void onDisable(ModuleContext context) {
                events.add("onDisable");
                throw new RuntimeException("boom-disable");
            }
        };
        manager.register(module);
        manager.loadAll();
        manager.enableAll();

        manager.disableAll();

        assertEquals(List.of("onDisable", "closed"), events);
        assertEquals(ModuleState.DISABLED, registry.state(module.descriptor().id()));
    }

    @Test
    void dependenciesAreEnabledBeforeDependents() {
        ModuleRegistry registry = new ModuleRegistry();
        ModuleManager manager = managerWithRegistry(registry);
        List<String> sharedLog = new ArrayList<>();
        ModuleId aId = ModuleId.of("test", "a");
        ModuleId bId = ModuleId.of("test", "b");
        RecordingModule a = new RecordingModule("a", Set.of(), sharedLog);
        RecordingModule b = new RecordingModule("b", Set.of(aId), sharedLog);

        // Register the dependent first to prove the order comes from the
        // dependency graph, not from registration order.
        manager.register(b);
        manager.register(a);

        manager.loadAll();
        manager.enableAll();

        assertEquals(List.of("a:load", "b:load", "a:enable", "b:enable"), sharedLog);
        assertEquals(ModuleState.ENABLED, registry.state(aId));
        assertEquals(ModuleState.ENABLED, registry.state(bId));
    }

    @Test
    void circularDependencyFailsLoadAllLoudly() {
        ModuleRegistry registry = new ModuleRegistry();
        ModuleManager manager = managerWithRegistry(registry);
        ModuleId aId = ModuleId.of("test", "a");
        ModuleId bId = ModuleId.of("test", "b");
        manager.register(new RecordingModule("a", Set.of(bId), new ArrayList<>()));
        manager.register(new RecordingModule("b", Set.of(aId), new ArrayList<>()));

        assertThrows(IllegalStateException.class, manager::loadAll);
    }

    @Test
    void missingDependencyFailsTheModuleWithoutEnablingIt() {
        ModuleRegistry registry = new ModuleRegistry();
        ModuleManager manager = managerWithRegistry(registry);
        ModuleId missing = ModuleId.of("test", "missing");
        RecordingModule needsMissing = new RecordingModule("needs-missing", Set.of(missing), new ArrayList<>());
        manager.register(needsMissing);

        manager.loadAll();
        assertEquals(ModuleState.FAILED, registry.state(needsMissing.descriptor().id()));

        manager.enableAll();
        assertFalse(needsMissing.calls().contains("needs-missing:enable"));
    }

    private static final class RecordingModule implements Module {

        private final ModuleDescriptor descriptor;
        private final List<String> calls;
        private boolean failOnLoad;
        private boolean failOnEnable;

        RecordingModule(String name) {
            this(name, Set.of(), new ArrayList<>());
        }

        RecordingModule(String name, Set<ModuleId> dependencies, List<String> calls) {
            this.descriptor = ModuleDescriptor.builder(ModuleId.of("test", name), name)
                    .dependsOn(dependencies.toArray(new ModuleId[0]))
                    .build();
            this.calls = calls;
        }

        RecordingModule failOnLoad() {
            failOnLoad = true;
            return this;
        }

        RecordingModule failOnEnable() {
            failOnEnable = true;
            return this;
        }

        List<String> calls() {
            return calls;
        }

        @Override
        public ModuleDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public void onLoad(ModuleContext context) {
            calls.add(name() + ":load");
            if (failOnLoad) {
                throw new RuntimeException("boom-load");
            }
        }

        @Override
        public void onEnable(ModuleContext context) {
            calls.add(name() + ":enable");
            if (failOnEnable) {
                throw new RuntimeException("boom-enable");
            }
        }

        @Override
        public void onDisable(ModuleContext context) {
            calls.add(name() + ":disable");
        }

        private String name() {
            return descriptor.id().key().name();
        }
    }
}
