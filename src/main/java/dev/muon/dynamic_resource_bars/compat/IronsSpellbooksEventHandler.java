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
public class IronsSpellbooksEventHandler {
    #if FORGELIKE
    @SubscribeEvent
    public static void onRender(#if NEO RenderGuiLayerEvent.Pre #else RenderGuiOverlayEvent #endif event) {
        var config = ModConfigManager.getClient();
        if (config.manaBarBehavior == ManaBarBehavior.IRONS_SPELLBOOKS) {
            #if NEO
            if (event.getName().getNamespace().equals("irons_spellbooks") &&
                event.getName().getPath().equals("mana_overlay")) {
            #elif FORGE
            if (event.getOverlay().id() != null &&
                    event.getOverlay().id().getNamespace().equals("irons_spellbooks") &&
                    event.getOverlay().id().getPath().equals("mana_overlay")) {
            #endif
                event.setCanceled(true);
                // Mana bar is now handled by the centralized BarRenderManager
                // No need to render here as it will be rendered in the correct order
            }
        }
    }
    #endif
} 