package dev.universaladmin.action;

/**
 * Observes {@link ActionEvent}s published by {@link ActionExecutor}. A
 * listener throwing never breaks the pipeline - see {@link ActionExecutor#subscribe}.
 */
@FunctionalInterface
public interface ActionEventListener {

    void onActionEvent(ActionEvent event);
}
