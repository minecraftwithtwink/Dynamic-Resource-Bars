package dev.muon.dynamic_resource_bars.compat;

import dev.muon.dynamic_resource_bars.DynamicResourceBars;
import dev.muon.dynamic_resource_bars.config.ModConfigManager;
import dev.muon.dynamic_resource_bars.util.ManaBarBehavior;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

#if FORGE
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid = DynamicResourceBars.ID, value = Dist.CLIENT)
#endif
#if NEO
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.api.distmarker.Dist;

@EventBusSubscriber(modid = DynamicResourceBars.ID, value = Dist.CLIENT) #endif
public class ArsNouveauEventHandler {
    #if FORGELIKE
    @SubscribeEvent
    public static void onRender(#if NEO RenderGuiLayerEvent.Pre event #elif FORGE RenderGuiOverlayEvent.Pre event#endif ) {
        var config = ModConfigManager.getClient();
        if (config.manaBarBehavior == ManaBarBehavior.ARS_NOUVEAU) {
            boolean cancelEvent = false;
            #if NEO && NEWER_THAN_20_1
            if (event.getName().getNamespace().equals("ars_nouveau") && 
                event.getName().getPath().equals("mana_hud")) {
                cancelEvent = true;
            }
            #elif FORGE && UPTO_20_1
            if (event.getOverlay().id() != null &&
                event.getOverlay().id().getNamespace().equals("ars_nouveau") &&
                event.getOverlay().id().getPath().equals("mana_hud")) {
                cancelEvent = true;
            }
            #endif

            if (cancelEvent) {
                event.setCanceled(true);
                // Mana bar is now handled by the centralized BarRenderManager
                // No need to render here as it will be rendered in the correct order
            }
        }
    }
    #endif
}