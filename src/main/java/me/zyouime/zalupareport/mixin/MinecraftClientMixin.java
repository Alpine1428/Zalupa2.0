package me.zyouime.zalupareport.mixin;

import me.zyouime.zalupareport.render.font.FontRenderer;
import me.zyouime.zalupareport.render.font.FontRenderers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;
import java.io.IOException;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Shadow public abstract Window getWindow();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(CallbackInfo info) {
        try {
            FontRenderers.mainFont = FontRenderers.create("sf_medium",
                    FontRenderer.getSizeToScale(16f, (int) this.getWindow().getScaleFactor()));
        } catch (IOException | FontFormatException e) {
            throw new RuntimeException(e);
        }
    }
}
