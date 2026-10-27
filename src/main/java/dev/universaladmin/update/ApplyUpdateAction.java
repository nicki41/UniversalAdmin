package dev.universaladmin.update;

import dev.universaladmin.action.Action;
import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionId;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.scheduler.TaskScheduler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Backs {@code /admin update} - fetches the latest release of UniversalAdmin
 * and of every extension with an update source ({@link UpdateTargets}) and
 * stages each newer one in {@code plugins/update/} (see
 * {@link UpdateDownloader}), applied by the server at the next restart. Unlike
 * the automatic download it also takes a new major version: a staff member
 * asked for it.
 */
public final class ApplyUpdateAction implements Action<Void, List<UpdateApplyResult>> {

    public static final ActionId ID = ActionId.core("update.apply");

    private final TaskScheduler scheduler;
    private final UpdateTargets targets;
    private final Function<String, GitHubReleaseClient> clients;
    private final UpdateStager stager;
    private final BiConsumer<String, String> onStaged;

    /**
     * @param clients  one release client per repository ({@code "owner/name"})
     * @param onStaged told (target name, version) about every staged update - the update check's notices then say "restart"
     */
    public ApplyUpdateAction(TaskScheduler scheduler, UpdateTargets targets, Function<String, GitHubReleaseClient> clients,
            UpdateStager stager, BiConsumer<String, String> onStaged) {
        this.scheduler = scheduler;
        this.targets = targets;
        this.clients = clients;
        this.stager = stager;
        this.onStaged = onStaged;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public CompletableFuture<ActionResult<List<UpdateApplyResult>>> execute(ActionContext context, Void input) {
        return scheduler.supplyAsync(() -> {
            List<UpdateApplyResult> results = new ArrayList<>();
            for (UpdateTarget target : targets.all()) {
                try {
                    GitHubRelease release = clients.apply(target.repository()).fetchLatest();
                    if (!SemanticVersion.isNewer(release.version(), target.currentVersion())) {
                        results.add(new UpdateApplyResult(target.name(), false, target.currentVersion()));
                        continue;
                    }
                    stager.stage(target, release);
                    onStaged.accept(target.name(), release.version());
                    results.add(new UpdateApplyResult(target.name(), true, release.version()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ActionResult.<List<UpdateApplyResult>>failure(ActionResult.FailureReason.INTERNAL_ERROR,
                            "Interrupted while updating " + target.name() + ".");
                } catch (IOException e) {
                    return ActionResult.<List<UpdateApplyResult>>failure(ActionResult.FailureReason.INTERNAL_ERROR,
                            "Could not update " + target.name() + ": " + e.getMessage());
                }
            }
            return ActionResult.success(List.copyOf(results));
        });
    }
}
