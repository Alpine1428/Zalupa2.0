package me.zyouime.zalupareport.screen;

import me.zyouime.zalupareport.client.ZalupareportClient;
import me.zyouime.zalupareport.config.ModConfig;
import me.zyouime.zalupareport.render.RenderHelper;
import me.zyouime.zalupareport.render.font.FontRenderers;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import java.awt.*;

public class SecretMenuScreen extends Screen {
    private final ModConfig config;
    private float bx, by, bw, bh;

    public SecretMenuScreen() {
        super(Text.literal(""));
        config = ZalupareportClient.getInstance().config;
    }

    @Override protected void init() {
        bw = 160; bh = 24; bx = (width - bw) / 2f; by = (height - bh) / 2f;
    }

    @Override public void render(DrawContext ctx, int mx, int my, float d) {
        renderBackground(ctx);
        MatrixStack ms = ctx.getMatrices();
        float pw = 200, ph = 80, px = (width-pw)/2f, py = (height-ph)/2f;
        RenderHelper.drawRoundedRect(ms, px, py, pw, ph, 6, new Color(20,20,20,220));
        RenderHelper.drawRoundedRect(ms, px+2, py+2, pw-4, ph-4, 5, new Color(40,40,40,200));
        boolean hover = mx>=bx && mx<=bx+bw && my>=by && my<=by+bh;
        Color c = config.autoCall ? (hover ? new Color(0,180,0,200) : new Color(0,140,0,200)) : (hover ? new Color(180,0,0,200) : new Color(140,0,0,200));
        RenderHelper.drawRoundedRect(ms, bx, by, bw, bh, 4, c);
        String t = "\u0410\u0432\u0442\u043e\u0412\u044b\u0437\u043e\u0432: " + (config.autoCall ? "\u0412\u041a\u041b" : "\u0412\u042b\u041a\u041b");
        FontRenderers.mainFont.drawCenteredString(ms, t, bx+bw/2f, by+8, Color.WHITE.getRGB());
        super.render(ctx, mx, my, d);
    }

    @Override public boolean mouseClicked(double mx, double my, int b) {
        if (b==0 && mx>=bx && mx<=bx+bw && my>=by && my<=by+bh) {
            config.autoCall = !config.autoCall;
            me.shedaniel.autoconfig.AutoConfig.getConfigHolder(ModConfig.class).save();
            return true;
        }
        return super.mouseClicked(mx, my, b);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean keyPressed(int k, int s, int m) {
        if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || k == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) { close(); return true; }
        return super.keyPressed(k, s, m);
    }
}
