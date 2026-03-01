package me.zyouime.zalupareport.mixin;

import me.zyouime.zalupareport.client.ZalupareportClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    // Перехват отправки сообщений (когда игрок пишет /hm sban)
    @Inject(method = "sendChatMessage", at = @At("HEAD"))
    private void onSendChatMessage(String content, CallbackInfo ci) {
        ZalupareportClient inst = ZalupareportClient.getInstance();
        if (inst != null && inst.autoCallManager != null) {
            inst.autoCallManager.onChatMessage(content);
        }
    }
    
    // Перехват отправки команд (если /hm sban отправляется как команда)
    @Inject(method = "sendCommand", at = @At("HEAD"))
    private void onSendCommand(String command, CallbackInfo ci) {
        ZalupareportClient inst = ZalupareportClient.getInstance();
        if (inst != null && inst.autoCallManager != null) {
            // sendCommand принимает команду без слэша, добавляем его для проверки
            inst.autoCallManager.onChatMessage("/" + command);
        }
    }
}
