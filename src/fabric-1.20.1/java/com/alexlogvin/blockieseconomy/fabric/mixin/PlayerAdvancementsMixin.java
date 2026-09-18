package com.alexlogvin.blockieseconomy.fabric.mixin;

import com.alexlogvin.blockieseconomy.fabric.AdvancementBridge;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Supplies the advancement-earned event Fabric API lacks (Minecraft 1.20.1).
 *
 * <p>On 1.21+ the target takes an {@code AdvancementHolder}; that variant lives in
 * {@code src/fabric-1.21.1}.
 */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    @Shadow
    private ServerPlayer player;

    /**
     * {@code award} also fires for partial criterion progress, so the completion check
     * matters: without it a player would be paid repeatedly for one advancement.
     */
    @Inject(method = "award", at = @At("RETURN"))
    private void blockiesEconomy$onAward(Advancement advancement, String criterion,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        AdvancementProgress progress =
                ((PlayerAdvancements) (Object) this).getOrStartProgress(advancement);
        if (progress.isDone()) {
            AdvancementBridge.fire(player, advancement.getId());
        }
    }
}
