package dev.muon.dynamic_resource_bars.render;

import dev.muon.dynamic_resource_bars.config.ModConfigManager;
import dev.muon.dynamic_resource_bars.render.AirBarRenderer;
import dev.muon.dynamic_resource_bars.render.ArmorBarRenderer;
import dev.muon.dynamic_resource_bars.render.HealthBarRenderer;
import dev.muon.dynamic_resource_bars.render.StaminaBarRenderer;
import dev.muon.dynamic_resource_bars.render.ManaBarRenderer;
import dev.muon.dynamic_resource_bars.util.BarRenderBehavior;
import dev.muon.dynamic_resource_bars.util.BarRenderOrder;
import dev.muon.dynamic_resource_bars.util.ManaBarBehavior;
import dev.muon.dynamic_resource_bars.compat.ManaProviderManager;
import dev.muon.dynamic_resource_bars.util.ManaProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;

#if NEWER_THAN_20_1
import net.minecraft.client.DeltaTracker;
#endif

/**
 * Centralized render manager that handles the render order for all bar groups.
 * Ensures bars render in the correct order based on their BarRenderOrder.
 */
public class BarRenderManager {

    /**
     * Renders all enabled bars in the correct order.
     * This method should be called from the main overlay system.
     */
    public static void renderAllBars(GuiGraphics graphics, Player player, 
            #if NEWER_THAN_20_1 DeltaTracker deltaTracker #else float partialTicks #endif) {
        
        if (player == null || Minecraft.getInstance().options.hideGui) {
            return;
        }

        var config = ModConfigManager.getClient();

        // Render bars in order from lowest to highest render order
        for (BarRenderOrder order : BarRenderOrder.values()) {
            switch (order) {
                case MANA:
                    renderManaBar(graphics, player, #if NEWER_THAN_20_1 deltaTracker #else partialTicks #endif);
                    break;
                case STAMINA:
                    renderStaminaBar(graphics, player, #if NEWER_THAN_20_1 deltaTracker #else partialTicks #endif);
                    break;
                case HEALTH:
                    renderHealthBar(graphics, player, #if NEWER_THAN_20_1 deltaTracker #else partialTicks #endif);
                    break;
                case ARMOR:
                    renderArmorBar(graphics, player);
                    break;
                case AIR:
                    renderAirBar(graphics, player, #if NEWER_THAN_20_1 deltaTracker #else partialTicks #endif);
                    break;
                default:
                    // BACKGROUND and FOREGROUND are placeholder orders for future use
                    break;
            }
        }
    }

    /**
     * Renders the mana bar if enabled and a mana provider is available.
     */
    private static void renderManaBar(GuiGraphics graphics, Player player, 
            #if NEWER_THAN_20_1 DeltaTracker deltaTracker #else float partialTicks #endif) {
        
        var config = ModConfigManager.getClient();
        if (config.manaBarBehavior == ManaBarBehavior.OFF) {
            return;
        }

        ManaProvider manaProvider = ManaProviderManager.getProviderForBehavior(config.manaBarBehavior);
        if (manaProvider != null && manaProvider.getMaxMana() > 0) {
            ManaBarRenderer.render(graphics, #if NEWER_THAN_20_1 deltaTracker #else partialTicks #endif, manaProvider, player);
        }
    }

    /**
     * Renders the stamina bar if enabled.
     */
    private static void renderStaminaBar(GuiGraphics graphics, Player player, 
            #if NEWER_THAN_20_1 DeltaTracker deltaTracker #else float partialTicks #endif) {
        
        var config = ModConfigManager.getClient();
        if (config.enableStaminaBar) {
            StaminaBarRenderer.render(graphics, player, #if NEWER_THAN_20_1 deltaTracker #else partialTicks #endif);
        }
    }

    /**
     * Renders the health bar if enabled.
     */
    private static void renderHealthBar(GuiGraphics graphics, Player player, 
            #if NEWER_THAN_20_1 DeltaTracker deltaTracker #else float partialTicks #endif) {
        
        var config = ModConfigManager.getClient();
        if (config.enableHealthBar) {
            HealthBarRenderer.render(graphics, player, player.getMaxHealth(), player.getHealth(), 
                    (int) player.getAbsorptionAmount(), #if NEWER_THAN_20_1 deltaTracker #else partialTicks #endif);
        }
    }

    /**
     * Renders the armor bar if enabled and set to custom behavior.
     */
    private static void renderArmorBar(GuiGraphics graphics, Player player) {
        var config = ModConfigManager.getClient();
        if (config.armorBarBehavior == BarRenderBehavior.CUSTOM) {
            ArmorBarRenderer.render(graphics, player);
        }
    }

    /**
     * Renders the air bar if enabled and set to custom behavior.
     */
    private static void renderAirBar(GuiGraphics graphics, Player player, 
            #if NEWER_THAN_20_1 DeltaTracker deltaTracker #else float partialTicks #endif) {
        
        var config = ModConfigManager.getClient();
        if (config.airBarBehavior == BarRenderBehavior.CUSTOM) {
            AirBarRenderer.render(graphics, player, #if NEWER_THAN_20_1 deltaTracker #else partialTicks #endif);
        }
    }
} 