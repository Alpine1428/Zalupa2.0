package me.zyouime.zalupareport.render.shader;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgram;
public class AbstractShader {
    protected final ShaderProgram shader;
    public AbstractShader(ShaderProgram shader) { this.shader = shader; }
    public void bind() { RenderSystem.setShader(() -> shader); }
    public ShaderProgram getShader() { return shader; }
}
