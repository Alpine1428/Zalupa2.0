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
    private float buttonX, buttonY, buttonW, buttonH;

    public SecretMenuScreen() {
        super(Text.literal(""));
        this.config = ZalupareportClient.getInstance().config;
    }

    @Override
    protected void init() {
        super.init();
        buttonW = 160;
        buttonH = 24;
        buttonX = (this.width - buttonW) / 2f;
        buttonY = (this.height - buttonH) / 2f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        MatrixStack matrixStack = context.getMatrices();

        float panelW = 200;
        float panelH = 80;
        float panelX = (this.width - panelW) / 2f;
        float panelY = (this.height - panelH) / 2f;

        RenderHelper.drawRoundedRect(matrixStack, panelX, panelY, panelW, panelH, 6,
                new Color(20, 20, 20, 220));
        RenderHelper.drawRoundedRect(matrixStack, panelX + 2, panelY + 2, panelW - 4, panelH - 4, 5,
                new Color(40, 40, 40, 200));

        boolean hovered = mouseX >= buttonX && mouseX <= buttonX + buttonW
                && mouseY >= buttonY && mouseY <= buttonY + buttonH;

        Color btnColor;
        if (config.autoCall) {
            btnColor = hovered ? new Color(0, 180, 0, 200) : new Color(0, 140, 0, 200);
        } else {
            btnColor = hovered ? new Color(180, 0, 0, 200) : new Color(140, 0, 0, 200);
        }

        RenderHelper.drawRoundedRect(matrixStack, buttonX, buttonY, buttonW, buttonH, 4, btnColor);

        String text = "АвтоВызов: " + (config.autoCall ? "§aВКЛ" : "§cВЫКЛ");
        FontRenderers.mainFont.drawCenteredString(matrixStack, text,
                buttonX + buttonW / 2f, buttonY + 8, Color.WHITE.getRGB());

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= buttonX && mouseX <= buttonX + buttonW
                && mouseY >= buttonY && mouseY <= buttonY + buttonH) {
            config.autoCall = !config.autoCall;
            me.shedaniel.autoconfig.AutoConfig.getConfigHolder(ModConfig.class).save();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
