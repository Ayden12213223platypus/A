package com.ayden.itemexporter;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(ItemExporterMod.MOD_ID)
public final class ItemExporterMod {
    public static final String MOD_ID = "itemexporter";

    public ItemExporterMod(IEventBus modEventBus, ModContainer modContainer) {
        // All features are registered by the client-only event subscriber.
    }
}
