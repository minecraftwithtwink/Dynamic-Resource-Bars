package dev.muon.dynamic_resource_bars.mixin.compat.rpgmana;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
#if FABRIC && UPTO_20_1
import dev.muon.dynamic_resource_bars.config.ModConfigManager;
import dev.muon.dynamic_resource_bars.util.ManaBarBehavior;

import com.cleannrooster.rpgmana.client.InGameHud;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InGameHud.class, remap = false)
#else
@Mixin(Minecraft.class)
#endif
public class InGameHudMixin {
    #if FABRIC && UPTO_20_1
    @Inject(method = "onHudRender", at = @At("HEAD"), cancellable = true)
    public void cancelManaOverlay(GuiGraphics drawContext, float tickDelta, CallbackInfo ci) {
        var config = ModConfigManager.getClient();
        if (config.manaBarBehavior == ManaBarBehavior.RPG_MANA) {
            ci.cancel();
            // Mana bar is now handled by the centralized BarRenderManager
            // No need to render here as it will be rendered in the correct order
        }
    }
    #endif
} 