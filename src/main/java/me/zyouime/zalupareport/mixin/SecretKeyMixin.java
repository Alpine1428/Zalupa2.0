package me.zyouime.zalupareport.mixin;

import me.zyouime.zalupareport.screen.SecretMenuScreen;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class SecretKeyMixin {
    @Inject(method = "onKey", at = @At("HEAD"))
    private void onKey(long window, int key, int scancode, int action, int mods, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS && key == GLFW.GLFW_KEY_RIGHT_CONTROL) {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.player != null && !(c.currentScreen instanceof SecretMenuScreen))
                c.execute(() -> c.setScreen(new SecretMenuScreen()));
        }
    }
}
