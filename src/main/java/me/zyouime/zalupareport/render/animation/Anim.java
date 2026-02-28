package me.zyouime.zalupareport.render.animation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;

public class Anim {
    private static float deltaTime() {
        return MinecraftClient.getInstance().getCurrentFps() > 5 ? (1f / MinecraftClient.getInstance().getCurrentFps()) : 0.016f;
    }
    public static float fast(float end, float start, float multiple) {
        float clampedDelta = MathHelper.clamp(deltaTime() * multiple, 0f, 1f);
        return (1f - clampedDelta) * end + clampedDelta * start;
    }
}
