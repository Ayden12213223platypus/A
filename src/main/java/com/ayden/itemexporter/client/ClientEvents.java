package com.ayden.itemexporter.client;

import com.ayden.itemexporter.ItemExporterMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

@EventBusSubscriber(modid = ItemExporterMod.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        CommandRegistration.registerClientCommands(event);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        CaptureManager.onClientTick(event);
    }

    @SubscribeEvent
    public static void onRenderFramePost(RenderFrameEvent.Post event) {
        CaptureManager.onRenderFramePost(event);
    }
}
