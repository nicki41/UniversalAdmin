package dev.universaladmin.gui;

import dev.universaladmin.core.registry.Registry;
import dev.universaladmin.core.registry.SimpleRegistry;
import java.util.Optional;

/** Registry of every {@link GuiPage} known to the platform, keyed by {@link GuiPageId}. */
public final class GuiRegistry {

    private final Registry<GuiPageId, GuiPage> delegate = new SimpleRegistry<>();

    public void register(GuiPage page) {
        delegate.register(page.id(), page);
    }

    /** For a module's disable cleanup - see {@link dev.universaladmin.module.ModuleResources}. */
    public void unregister(GuiPageId id) {
        delegate.unregister(id);
    }

    public Optional<GuiPage> get(GuiPageId id) {
        return delegate.get(id);
    }
}
