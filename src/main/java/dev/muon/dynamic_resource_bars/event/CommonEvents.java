package dev.muon.dynamic_resource_bars.event;

#if NEWER_THAN_20_1
import dev.muon.dynamic_resource_bars.config.ModConfigManager;
import dev.muon.dynamic_resource_bars.render.BarRenderManager;
import dev.muon.dynamic_resource_bars.util.BarRenderBehavior;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import fuzs.puzzleslib.api.client.core.v1.ClientAbstractions;
import fuzs.puzzleslib.api.event.v1.core.EventResult;
import net.minecraft.client.DeltaTracker;

// 1.21.1 only
public class CommonEvents {
    public static EventResult onRenderPlayerHealth(Minecraft minecraft, GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        var config = ModConfigManager.getClient();
        if (!config.enableHealthBar) {
            return EventResult.PASS; // Let vanilla render
        }

        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return EventResult.PASS;
        }

        // Use the centralized render manager to handle all bars in correct order
        BarRenderManager.renderAllBars(guiGraphics, player, deltaTracker);

        // Update GUI heights for proper spacing
        ClientAbstractions.INSTANCE.addGuiLeftHeight(minecraft.gui, config.healthBackgroundHeight + 1);
        return EventResult.INTERRUPT;
    }

    public static EventResult onRenderHunger(Minecraft minecraft, GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        var config = ModConfigManager.getClient();
        if (!config.enableStaminaBar) {
            return EventResult.PASS;
        }

        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return EventResult.PASS;
        }

        // Stamina bar is now handled by BarRenderManager
        ClientAbstractions.INSTANCE.addGuiRightHeight(minecraft.gui, config.staminaBackgroundHeight + 1);

        return EventResult.INTERRUPT;
    }

    public static EventResult onRenderArmor(Minecraft minecraft, GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        var config = ModConfigManager.getClient();
        BarRenderBehavior armorBehavior = config.armorBarBehavior;

        if (armorBehavior == BarRenderBehavior.VANILLA) {
            return EventResult.PASS;
        }

        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return EventResult.INTERRUPT;
        }

        // Armor bar is now handled by BarRenderManager
        if (armorBehavior == BarRenderBehavior.CUSTOM) {
            ClientAbstractions.INSTANCE.addGuiLeftHeight(minecraft.gui, config.armorBackgroundHeight + 1);
        }
        return EventResult.INTERRUPT;
    }

    public static EventResult onRenderAir(Minecraft minecraft, GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        var config = ModConfigManager.getClient();
        BarRenderBehavior airBehavior = config.airBarBehavior;

        if (airBehavior == BarRenderBehavior.VANILLA) {
            return EventResult.PASS;
        }

        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return EventResult.INTERRUPT;
        }

        // Air bar is now handled by BarRenderManager
        if (airBehavior == BarRenderBehavior.CUSTOM) {
            ClientAbstractions.INSTANCE.addGuiRightHeight(minecraft.gui, config.airBackgroundHeight + 1);
        }
        return EventResult.INTERRUPT;
    }

    public static EventResult onRenderMountHealth(Minecraft minecraft, GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        var config = ModConfigManager.getClient();
        // Cancel mount health rendering if stamina bar is enabled (since it handles mount health)
        if (!config.enableStaminaBar) {
            return EventResult.PASS;
        }

        return EventResult.INTERRUPT;
    }
}
#endif