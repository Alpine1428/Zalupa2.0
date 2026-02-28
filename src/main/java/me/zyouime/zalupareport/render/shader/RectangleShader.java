package me.zyouime.zalupareport.render.shader;

import net.minecraft.client.gl.ShaderProgram;
import java.awt.*;

public class RectangleShader extends AbstractShader {
    public RectangleShader(ShaderProgram shader) { super(shader); }
    public void setUniforms(float x, float y, float w, float h, float r, Color c) {
        shader.getUniform("rectPos").set(x, y); shader.getUniform("rectSize").set(w, h);
        shader.getUniform("radius").set(r);
        shader.getUniform("rectColor").set(c.getRed()/255f, c.getGreen()/255f, c.getBlue()/255f, c.getAlpha()/255f);
    }
}
