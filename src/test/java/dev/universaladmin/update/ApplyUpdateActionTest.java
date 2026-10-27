package dev.universaladmin.update;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionResult;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@code /admin update} stages every plugin with a newer release - a new major version too, since a staff member asked. */
class ApplyUpdateActionTest {

    @Test
    void stagesEveryOutdatedPluginAndReportsTheRest() {
        UpdateTargets targets = new UpdateTargets();
        targets.add(new UpdateTarget("UniversalAdmin", UpdateTargets.CORE_REPOSITORY, UpdateTargets.CORE_ASSET_PREFIX, "1.6.0",
                Path.of("UniversalAdmin.jar")));
        targets.add(new UpdateTarget("UniversalAdminDiscord", "universaladmin-mc/universaladmin-discord", "universaladmin-discord-",
                "1.6.0", Path.of("discord.jar")));
        Map<String, String> latest = Map.of(UpdateTargets.CORE_REPOSITORY, "v2.0.0",
                "universaladmin-mc/universaladmin-discord", "v1.6.0");
        List<String> staged = new ArrayList<>();
        List<String> told = new ArrayList<>();
        ApplyUpdateAction action = new ApplyUpdateAction(new InlineTaskScheduler(), targets,
                repository -> () -> new GitHubRelease(latest.get(repository), "https://example.com", false, List.of()),
                (target, release) -> staged.add(target.name() + " " + release.version()),
                (name, version) -> told.add(name + " " + version));

        ActionResult<List<UpdateApplyResult>> result = action.execute(new ActionContext(Actor.console(), Source.COMMAND), null).join();

        List<UpdateApplyResult> results = ((ActionResult.Success<List<UpdateApplyResult>>) result).value();
        assertEquals(List.of(new UpdateApplyResult("UniversalAdmin", true, "2.0.0"),
                new UpdateApplyResult("UniversalAdminDiscord", false, "1.6.0")), results);
        assertEquals(List.of("UniversalAdmin 2.0.0"), staged);
        assertEquals(List.of("UniversalAdmin 2.0.0"), told);
    }
}
