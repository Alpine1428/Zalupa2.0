package me.zyouime.zalupareport.mixin;

import me.zyouime.zalupareport.client.ZalupareportClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class OutgoingChatMixin {

    /**
     * Перехватывает ВСЕ исходящие команды (то что игрок сам вводит через /)
     * Сигнатура: sendCommand(String command) - команда БЕЗ слеша
     */
    @Inject(method = "sendCommand", at = @At("HEAD"))
    private void onSendCommand(String command, CallbackInfo ci) {
        ZalupareportClient inst = ZalupareportClient.getInstance();
        if (inst != null && inst.autoCallManager != null) {
            // Передаём как "/" + command чтобы паттерн banPattern мог найти
            inst.autoCallManager.onOutgoingCommand("/" + command);
        }
    }

    /**
     * Перехватывает ВСЕ исходящие сообщения чата (обычные сообщения, не команды)
     */
    @Inject(method = "sendChatMessage", at = @At("HEAD"))
    private void onSendChatMessage(String message, CallbackInfo ci) {
        ZalupareportClient inst = ZalupareportClient.getInstance();
        if (inst != null && inst.autoCallManager != null) {
            inst.autoCallManager.onOutgoingCommand(message);
        }
    }
}
