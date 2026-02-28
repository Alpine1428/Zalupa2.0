package me.zyouime.zalupareport.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import java.awt.*;
import static me.zyouime.zalupareport.render.shader.MyShaders.*;

public class RenderHelper {
    private static final Tessellator tessellator = Tessellator.getInstance();

    public static void drawTexture(MatrixStack matrixStack, float x, float y, float width, float height, float u, float v, float regionWidth, float regionHeight, float textureWidth, float textureHeight, Identifier texture) {
        Matrix4f matrix4f = matrixStack.peek().getPositionMatrix();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, texture); RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        bufferBuilder.vertex(matrix4f, x, y, 0).texture(u / textureHeight, v / textureHeight).next();
        bufferBuilder.vertex(matrix4f, x, y + height, 0).texture(u / textureWidth, (v + regionHeight) / textureHeight).next();
        bufferBuilder.vertex(matrix4f, x + width, y + height, 0).texture((u + regionWidth) / textureWidth, (v + regionHeight) / textureHeight).next();
        bufferBuilder.vertex(matrix4f, x + width, y, 0).texture((u + regionWidth) / textureWidth, v / textureHeight).next();
        tessellator.draw(); RenderSystem.disableBlend();
    }

    public static void drawArc(MatrixStack matrixStack, float x, float y, float width, float height, float radius, float arcWidth, Color color) {
        setupRender(); ARC_SHADER.bind(); ARC_SHADER.setUniforms(x, y, width, height, radius, arcWidth, color);
        drawShader(matrixStack, x, y, width, height); endRender();
    }

    private static void drawShader(MatrixStack matrixStack, float x, float y, float width, float height) {
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        Matrix4f matrix4f = matrixStack.peek().getPositionMatrix();
        bufferBuilder.vertex(matrix4f, x, y + height, 0.0F).next();
        bufferBuilder.vertex(matrix4f, x + width, y + height, 0.0F).next();
        bufferBuilder.vertex(matrix4f, x + width, y, 0.0F).next();
        bufferBuilder.vertex(matrix4f, x, y, 0.0F).next();
        tessellator.draw();
    }

    public static void drawRoundedRect(MatrixStack matrixStack, float x, float y, float width, float height, float radius, Color color) {
        setupRender(); drawRoundedRectWithoutSetup(matrixStack, x, y, width, height, radius, color); endRender();
    }

    public static void drawRoundedRectWithoutSetup(MatrixStack matrixStack, float x, float y, float width, float height, float radius, Color color) {
        RECTANGLE_SHADER.bind(); RECTANGLE_SHADER.setUniforms(x, y, width, height, radius, color);
        drawShader(matrixStack, x, y, width, height);
    }

    private static void endRender() { RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend(); }
    private static void setupRender() { RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); }
}
