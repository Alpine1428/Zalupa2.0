package me.zyouime.zalupareport.mixin;

import me.zyouime.zalupareport.client.ZalupareportClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayNetworkHandler.class)
public class OutgoingChatMixin {

    /**
     * sendCommand возвращает boolean -> нужен CallbackInfoReturnable
     */
    @Inject(method = "sendCommand", at = @At("HEAD"))
    private void onSendCommand(String command, CallbackInfoReturnable<Boolean> cir) {
        ZalupareportClient inst = ZalupareportClient.getInstance();
        if (inst != null && inst.autoCallManager != null) {
            inst.autoCallManager.onOutgoingCommand("/" + command);
        }
    }

    /**
     * sendChatMessage возвращает void -> CallbackInfo
     */
    @Inject(method = "sendChatMessage", at = @At("HEAD"))
    private void onSendChatMessage(String message, CallbackInfo ci) {
        ZalupareportClient inst = ZalupareportClient.getInstance();
        if (inst != null && inst.autoCallManager != null) {
            inst.autoCallManager.onOutgoingCommand(message);
        }
    }
}
