package me.zyouime.zalupareport.mixin;

import me.zyouime.zalupareport.client.ZalupareportClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatMessageMixin {

    @Inject(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onAddMessage(Text message, MessageSignatureData sig, MessageIndicator ind, CallbackInfo ci) {
        ZalupareportClient inst = ZalupareportClient.getInstance();
        if (inst == null || inst.autoCallManager == null) return;

        String msg = message.getString();

        inst.autoCallManager.onChatMessage(msg);

        if (inst.autoCallManager.isScanningPlaytime()) {
            if (isPlaytimeMessage(msg)) {
                ci.cancel();
            }
        }
    }

    private boolean isPlaytimeMessage(String msg) {
        if (msg.contains("------------------PlayTimeAPI------------------")) return true;
        if (msg.contains("---------------------------------------------------")) return true;
        if (msg.startsWith("\u0410\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u044c ")) return true;
        if (msg.contains("\u041e\u0431\u0449\u0435\u0435 \u0432\u0440\u0435\u043c\u044f \u0432 \u0438\u0433\u0440\u0435:")) return true;
        if (msg.contains("\u041e\u0431\u0449\u0435\u0435 \u0432\u0440\u0435\u043c\u044f \u0430\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u0438 \u0432 \u0438\u0433\u0440\u0435:")) return true;
        if (msg.contains("\u0422\u0435\u043a\u0443\u0449\u0430\u044f \u0441\u0435\u0441\u0441\u0438\u044f:")) return true;
        if (msg.contains("\u0412\u0440\u0435\u043c\u044f \u0431\u0435\u0437\u0434\u0435\u0439\u0441\u0442\u0432\u0438\u044f:")) return true;
        if (msg.contains("\u041f\u043e\u0441\u043b\u0435\u0434\u043d\u044f\u044f \u0430\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u044c:")) return true;
        if (msg.contains("\u041f\u043e\u0441\u043b\u0435\u0434\u043d\u0438\u0439 \u0432\u0445\u043e\u0434 \u043d\u0430 \u0430\u043d\u0430\u0440\u0445\u0438\u044e:")) return true;
        if (msg.trim().isEmpty()) return true;
        return false;
    }
}
