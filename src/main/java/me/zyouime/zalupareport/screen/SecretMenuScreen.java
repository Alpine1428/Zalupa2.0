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
        // Добавляем обычную кнопку Minecraft
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
        this.renderBackground(context); // Затемнение фона
        super.render(context, mouseX, mouseY, delta); // Рендер кнопок
        
        // Рисуем заголовок по центру
        context.drawCenteredTextWithShadow(this.textRenderer, "Секретное меню", this.width / 2, this.height / 2 - 40, 0xFFFFFF);
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
