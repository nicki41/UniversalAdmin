package dev.universaladmin.settings;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.localization.MessageKey;
import dev.universaladmin.scheduler.TaskScheduler;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Changes (or, with a {@code null} value, resets) one setting in-game - the
 * single path behind the settings GUI and {@code /admin settings set|reset}.
 * Writes {@code config.yml} off the main thread, like {@link ReloadConfigAction}.
 *
 * <p>Sensitive settings ({@link SettingDefinition#sensitive()}, e.g. the
 * database password) can never be changed this way - only in the file.
 */
public final class SetSettingAction implements Action<SetSettingAction.Input, SettingChange> {

    public static final ActionId ID = ActionId.core("settings.set");

    /**
     * @param path  the setting's config path, e.g. {@code moderation.reasons.presets}
     * @param value the new value as typed, or {@code null} to reset it to its default
     */
    public record Input(String path, String value) {
    }

    private final SettingsService settings;
    private final SettingRegistry registry;
    private final TaskScheduler scheduler;

    public SetSettingAction(SettingsService settings, SettingRegistry registry, TaskScheduler scheduler) {
        this.settings = settings;
        this.registry = registry;
        this.scheduler = scheduler;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<SettingChange>> execute(ActionContext context, Input input) {
        Optional<SettingDefinition<?>> definition = registry.byPath(input.path());
        if (definition.isEmpty()) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    ActionResult.FailureReason.NOT_FOUND, MessageKey.of("settings.action.unknown"), input.path()));
        }
        if (definition.get().sensitive()) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    ActionResult.FailureReason.NOT_PERMITTED, MessageKey.of("settings.action.sensitive"), input.path()));
        }
        return scheduler.supplyAsync(() -> {
            try {
                SettingChange change = input.value() == null
                        ? settings.reset(input.path())
                        : settings.set(input.path(), input.value());
                return ActionResult.success(change);
            } catch (SettingParseException e) {
                return ActionResult.<SettingChange>failure(ActionResult.FailureReason.VALIDATION,
                        MessageKey.of("settings.action.invalid"), input.path(), e.getMessage());
            }
        });
    }
}
