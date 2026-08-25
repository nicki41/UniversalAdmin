package dev.universaladmin.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

/**
 * Free-text input for the "Search" flow. Minecraft inventories have no text
 * field, and this project rules out packet hacks/ProtocolLib/NMS (see
 * docs/development/architecture-rules.md) - the Paper client Dialog API is the stable, non-packet-hack
 * way to ask a player to type something, confirmed available on the target
 * Paper version (see the "Search" section of docs/development/gui-framework.md
 * for the alternatives this was weighed against and why they lost).
 *
 * <p>The submit/cancel callbacks below are single-use
 * ({@code ClickCallback.Options.uses(1)}) and expire after a few minutes, so
 * a stale dialog a player never responds to can't be replayed or linger.
 *
 * <p><b>Threading:</b> Paper invokes a dialog's {@code DialogActionCallback}
 * on the main thread (it's a direct response to a player action, the same
 * as a command or a click event) - so {@code onSubmit}/{@code onCancel} run
 * on the main thread too. If a caller needs to do blocking IO in response,
 * it must hop off via {@link dev.universaladmin.scheduler.TaskScheduler}
 * itself, the same as any other main-thread entry point - see
 * docs/architecture/threading.md.
 */
public final class GuiTextInput {

    private static final int INPUT_WIDTH = 250;
    private static final Duration CALLBACK_LIFETIME = Duration.ofMinutes(2);

    private GuiTextInput() {
    }

    /**
     * Shows {@code viewer} a single-line text prompt.
     *
     * @param label        shown above the text field
     * @param initialValue pre-filled text, e.g. the previous search query
     * @param submitLabel  label of the confirm button
     * @param cancelLabel  label of the cancel button
     * @param onSubmit     called with the entered text (never {@code null}, may be empty)
     * @param onCancel     called if the player cancels or closes the dialog without submitting
     */
    public static void request(
            Player viewer,
            Component title,
            Component label,
            String initialValue,
            Component submitLabel,
            Component cancelLabel,
            Consumer<String> onSubmit,
            Runnable onCancel) {
        String inputKey = "value";

        ActionButton submit = ActionButton.builder(submitLabel)
                .action(DialogAction.customClick(
                        (view, audience) -> onSubmit.accept(nullToEmpty(view.getText(inputKey))),
                        singleUse()))
                .build();
        ActionButton cancel = ActionButton.builder(cancelLabel)
                .action(DialogAction.customClick((view, audience) -> onCancel.run(), singleUse()))
                .build();

        DialogBase base = DialogBase.builder(title)
                .body(List.of(DialogBody.plainMessage(label)))
                .inputs(List.of(DialogInput.text(inputKey, Component.empty())
                        .initial(initialValue == null ? "" : initialValue)
                        .width(INPUT_WIDTH)
                        .labelVisible(false)
                        .build()))
                .canCloseWithEscape(true)
                .build();

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(base)
                .type(DialogType.multiAction(List.of(submit, cancel), cancel, 2)));

        viewer.showDialog(dialog);
    }

    private static ClickCallback.Options singleUse() {
        return ClickCallback.Options.builder().uses(1).lifetime(CALLBACK_LIFETIME).build();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
