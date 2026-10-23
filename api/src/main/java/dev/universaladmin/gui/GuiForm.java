package dev.universaladmin.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;

/**
 * Several inputs in one Paper dialog - text fields, sliders, checkboxes and
 * option lists - with a submit and a cancel button. Where
 * {@link GuiTextInput} asks for one value, a form asks for everything a
 * step needs at once (see ADR 0023).
 *
 * <pre>{@code
 * GuiForm.builder(title)
 *         .message(intro)
 *         .text("reason", reasonLabel, "", 255)
 *         .multilineText("notes", notesLabel, "", 255, 4)
 *         .toggle("kick", kickLabel, false)
 *         .open(viewer, submitLabel, cancelLabel,
 *                 response -> apply(response.text("reason"), response.toggle("kick")),
 *                 () -> page.open(viewer));
 * }</pre>
 *
 * <p>A form only collects values; checking them is the job of the action it
 * leads to, as with every other input. The submit and cancel callbacks are
 * single-use and expire after a few minutes, so an old form can't be
 * replayed; Paper runs them on the main thread, like a click.
 *
 * @since 1.4
 */
public final class GuiForm {

    private static final int INPUT_WIDTH = 300;
    private static final Duration CALLBACK_LIFETIME = Duration.ofMinutes(5);
    private static final Pattern KEY = Pattern.compile("[a-z0-9_]+");

    private GuiForm() {
    }

    public static Builder builder(Component title) {
        return new Builder(title);
    }

    /** One entry of a {@link Builder#choice choice}: {@code id} is what {@link Response#choice} returns. */
    public record Option(String id, Component label) {

        public Option {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
        }
    }

    /** Collects the inputs of one form; {@link #open} shows it. */
    public static final class Builder {

        private final Component title;
        private final List<DialogBody> body = new ArrayList<>();
        private final List<DialogInput> inputs = new ArrayList<>();
        private final Set<String> keys = new HashSet<>();

        private Builder(Component title) {
            this.title = Objects.requireNonNull(title, "title");
        }

        /** A line of text above the inputs. */
        public Builder message(Component text) {
            body.add(DialogBody.plainMessage(text));
            return this;
        }

        /** A single-line text field. */
        public Builder text(String key, Component label, String initial, int maxLength) {
            inputs.add(textInput(key, label, initial, maxLength).build());
            return this;
        }

        /** A text field with up to {@code lines} visible lines. */
        public Builder multilineText(String key, Component label, String initial, int maxLength, int lines) {
            int height = Math.min(512, Math.max(1, lines) * 12 + 8);
            inputs.add(textInput(key, label, initial, maxLength)
                    .multiline(TextDialogInput.MultilineOptions.create(Math.max(1, lines), height))
                    .build());
            return this;
        }

        /** A slider from {@code min} to {@code max} in steps of {@code step}. */
        public Builder number(String key, Component label, float min, float max, float step, float initial) {
            if (!(min < max) || step <= 0) {
                throw new IllegalArgumentException("Invalid range " + min + ".." + max + " step " + step);
            }
            inputs.add(DialogInput.numberRange(key(key), label, min, max)
                    .step(step)
                    .initial(Math.max(min, Math.min(max, initial)))
                    .width(INPUT_WIDTH)
                    .build());
            return this;
        }

        /** A checkbox. */
        public Builder toggle(String key, Component label, boolean initial) {
            inputs.add(DialogInput.bool(key(key), label).initial(initial).build());
            return this;
        }

        /**
         * A button that cycles through {@code options}.
         *
         * @param initialId the option selected at first; the first one if {@code null} or unknown
         */
        public Builder choice(String key, Component label, List<Option> options, String initialId) {
            if (options.isEmpty()) {
                throw new IllegalArgumentException("A choice needs at least one option");
            }
            boolean known = options.stream().anyMatch(option -> option.id().equals(initialId));
            List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                Option option = options.get(i);
                boolean initial = known ? option.id().equals(initialId) : i == 0;
                entries.add(SingleOptionDialogInput.OptionEntry.create(option.id(), option.label(), initial));
            }
            inputs.add(DialogInput.singleOption(key(key), label, entries).width(INPUT_WIDTH).build());
            return this;
        }

        /**
         * Shows the form.
         *
         * @param onSubmit called with the entered values
         * @param onCancel called if the player cancels or closes the form
         */
        public void open(Player viewer, Component submitLabel, Component cancelLabel,
                Consumer<Response> onSubmit, Runnable onCancel) {
            ActionButton submit = ActionButton.builder(submitLabel)
                    .action(DialogAction.customClick((view, audience) -> onSubmit.accept(Response.of(view)), singleUse()))
                    .build();
            ActionButton cancel = ActionButton.builder(cancelLabel)
                    .action(DialogAction.customClick((view, audience) -> onCancel.run(), singleUse()))
                    .build();
            DialogBase base = DialogBase.builder(title)
                    .body(List.copyOf(body))
                    .inputs(List.copyOf(inputs))
                    .canCloseWithEscape(true)
                    .build();
            viewer.showDialog(Dialog.create(factory -> factory.empty()
                    .base(base)
                    .type(DialogType.multiAction(List.of(submit, cancel), cancel, 2))));
        }

        private TextDialogInput.Builder textInput(String key, Component label, String initial, int maxLength) {
            return DialogInput.text(key(key), label)
                    .initial(initial == null ? "" : initial)
                    .maxLength(Math.max(1, maxLength))
                    .width(INPUT_WIDTH);
        }

        private String key(String key) {
            if (!KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("Form keys are lower-case letters, digits and '_': " + key);
            }
            if (!keys.add(key)) {
                throw new IllegalArgumentException("Duplicate form key: " + key);
            }
            return key;
        }

        private static ClickCallback.Options singleUse() {
            return ClickCallback.Options.builder().uses(1).lifetime(CALLBACK_LIFETIME).build();
        }
    }

    /** The values of a submitted form, read by the keys they were added with. */
    public static final class Response {

        // One reader per input type: the dialog's numeric values (sliders,
        // checkboxes) convert into each other, so a value must be read as
        // the type its input produces.
        private final Function<String, String> texts;
        private final Function<String, Float> numbers;
        private final Function<String, Boolean> toggles;

        private Response(Function<String, String> texts, Function<String, Float> numbers, Function<String, Boolean> toggles) {
            this.texts = texts;
            this.numbers = numbers;
            this.toggles = toggles;
        }

        static Response of(DialogResponseView view) {
            return new Response(view::getText, view::getFloat, view::getBoolean);
        }

        /** Values from a map (strings, numbers, booleans) - for tests of code that handles a form. */
        public static Response of(Map<String, ?> values) {
            Map<String, ?> copy = Map.copyOf(values);
            return new Response(
                    key -> copy.get(key) instanceof String text ? text : null,
                    key -> copy.get(key) instanceof Number number ? number.floatValue() : null,
                    key -> copy.get(key) instanceof Boolean bool ? bool : null);
        }

        /** A text field's or a choice's value; {@code ""} if there is none. */
        public String text(String key) {
            String value = texts.apply(key);
            return value == null ? "" : value;
        }

        /** A text field's value without leading and trailing spaces; {@code null} if it is blank. */
        public String textOrNull(String key) {
            String value = text(key).trim();
            return value.isEmpty() ? null : value;
        }

        /** A slider's value; {@code 0} if there is none. */
        public float number(String key) {
            Float value = numbers.apply(key);
            return value == null ? 0f : value;
        }

        /** A checkbox's value; {@code false} if there is none. */
        public boolean toggle(String key) {
            return Boolean.TRUE.equals(toggles.apply(key));
        }

        /** The id of a choice's selected {@link Option}; {@code ""} if there is none. */
        public String choice(String key) {
            return text(key);
        }
    }
}
