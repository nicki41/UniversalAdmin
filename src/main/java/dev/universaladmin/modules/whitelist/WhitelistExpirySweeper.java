package dev.universaladmin.modules.whitelist;

import dev.universaladmin.action.ActionContext;
import dev.universaladmin.action.ActionExecutor;
import dev.universaladmin.action.Actor;
import dev.universaladmin.action.Source;
import dev.universaladmin.modules.whitelist.action.WhitelistActionIds;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Finds every {@link WhitelistEntry} UniversalAdmin owns ({@link
 * WhitelistSource#UNIVERSAL_ADMIN}) whose {@link WhitelistEntry#expiresAt()}
 * has passed and removes it - always through {@code
 * ActionExecutor.execute(WhitelistActionIds.REMOVE, ...)} rather than
 * talking to the repository directly, so an automatic expiry gets exactly
 * the same audit entry an admin-initiated removal would (with {@code
 * Actor.system("whitelist-expiry")} standing in for the admin). Never
 * touches a native whitelist entry with no matching row - see
 * docs/user/modules/whitelist.md's "Ownership & the expiry sweep" section.
 */
public final class WhitelistExpirySweeper {

    private final WhitelistEntryRepository repository;
    private final ActionExecutor actionExecutor;

    public WhitelistExpirySweeper(WhitelistEntryRepository repository, ActionExecutor actionExecutor) {
        this.repository = repository;
        this.actionExecutor = actionExecutor;
    }

    /** @return how many entries were removed */
    public CompletableFuture<Integer> sweep() {
        return repository.findAll().thenCompose(this::removeExpired);
    }

    private CompletableFuture<Integer> removeExpired(List<WhitelistEntry> entries) {
        Instant now = Instant.now();
        List<WhitelistEntry> expired = entries.stream()
                .filter(entry -> entry.source() == WhitelistSource.UNIVERSAL_ADMIN && entry.isExpired(now))
                .toList();
        if (expired.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        ActionContext systemContext = new ActionContext(Actor.system("whitelist-expiry"), Source.SYSTEM);
        CompletableFuture<?>[] removals = expired.stream()
                .map(entry -> actionExecutor.execute(WhitelistActionIds.REMOVE, systemContext, entry.playerId()))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(removals).thenApply(ignored -> expired.size());
    }
}
