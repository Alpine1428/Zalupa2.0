package me.zyouime.zalupareport.screen;

import me.zyouime.zalupareport.client.ZalupareportClient;
import me.zyouime.zalupareport.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class SecretMenuScreen extends Screen {
    private final ModConfig config;

    public SecretMenuScreen() {
        super(Text.of("Secret Menu"));
        this.config = ZalupareportClient.getInstance().config;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Кнопка АвтоВызов
        this.addDrawableChild(ButtonWidget.builder(
            Text.of(getAutoCallText()),
            button -> {
                config.autoCall = !config.autoCall;
                if (config.autoCall) config.autoCheck = false;
                button.setMessage(Text.of(getAutoCallText()));
                this.clearChildren(); this.init();
                me.shedaniel.autoconfig.AutoConfig.getConfigHolder(ModConfig.class).save();
            }
        ).dimensions(centerX - 100, centerY - 28, 200, 24).build());

        // Кнопка АвтоПроверка
        this.addDrawableChild(ButtonWidget.builder(
            Text.of(getAutoCheckText()),
            button -> {
                config.autoCheck = !config.autoCheck;
                if (config.autoCheck) config.autoCall = false;
                button.setMessage(Text.of(getAutoCheckText()));
                this.clearChildren(); this.init();
                me.shedaniel.autoconfig.AutoConfig.getConfigHolder(ModConfig.class).save();
            }
        ).dimensions(centerX - 100, centerY + 4, 200, 24).build());
    }

    private String getAutoCallText() { return "АвтоВызов (1 раз): " + (config.autoCall ? "§aВКЛ" : "§cВЫКЛ"); }
    private String getAutoCheckText() { return "АвтоПроверка (Цикл): " + (config.autoCheck ? "§aВКЛ" : "§cВЫКЛ"); }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, "§6Секретное меню ZalupaReport", this.width / 2, this.height / 2 - 50, 0xFFFFFF);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean keyPressed(int k, int s, int m) {
        if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_RIGHT_CONTROL) { this.close(); return true; }
        return super.keyPressed(k, s, m);
    }
}
