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
        // Кнопка по центру
        this.addDrawableChild(ButtonWidget.builder(
            Text.of(getButtonText()),
            button -> {
                config.autoCall = !config.autoCall;
                button.setMessage(Text.of(getButtonText()));
                me.shedaniel.autoconfig.AutoConfig.getConfigHolder(ModConfig.class).save();
            }
        )
        .dimensions(this.width / 2 - 100, this.height / 2 - 12, 200, 24)
        .build());
    }

    private String getButtonText() {
        return "АвтоВызов: " + (config.autoCall ? "§aВКЛ" : "§cВЫКЛ");
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1. Принудительно рисуем черный полупрозрачный фон
        context.fill(0, 0, this.width, this.height, 0xAA000000);
        
        // 2. Рисуем кнопки
        super.render(context, mouseX, mouseY, delta);
        
        // 3. Рисуем текст
        context.drawCenteredTextWithShadow(this.textRenderer, "§6Секретное меню ZalupaReport", this.width / 2, this.height / 2 - 40, 0xFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
