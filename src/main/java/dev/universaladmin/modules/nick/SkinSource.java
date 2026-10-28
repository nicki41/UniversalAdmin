package dev.universaladmin.modules.nick;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Where the skins of nicks come from: the Mojang-signed skin of an existing account. */
public interface SkinSource {

    /** The skin of the account named {@code account}; empty if there is no such account. Runs off the main thread. */
    CompletableFuture<Optional<NickRecord.Skin>> skinOf(String account);
}
