package me.zyouime.zalupareport.mixin;

import me.zyouime.zalupareport.client.ZalupareportClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    @Inject(method = "sendChatMessage(Ljava/lang/String;Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void onSendChatMessage(String message, Text preview, CallbackInfo ci) {
        ZalupareportClient inst = ZalupareportClient.getInstance();
        if (inst != null && inst.autoCallManager != null) {
            inst.autoCallManager.onChatMessage(message);
        }
    }
    
    @Inject(method = "sendCommand(Ljava/lang/String;)Z", at = @At("HEAD"))
    private void onSendCommand(String command, CallbackInfoReturnable<Boolean> cir) {
        ZalupareportClient inst = ZalupareportClient.getInstance();
        if (inst != null && inst.autoCallManager != null) {
            inst.autoCallManager.onChatMessage("/" + command);
        }
    }
}
