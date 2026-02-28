package me.zyouime.zalupareport.mixin;

import me.zyouime.zalupareport.client.ZalupareportClient;
import me.zyouime.zalupareport.config.ModConfig;
import me.zyouime.zalupareport.hud.impl.ChiteriList;
import me.zyouime.zalupareport.manager.ZalupaManager;
import me.zyouime.zalupareport.screen.SecretMenuScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = HandledScreen.class, priority = 990)
public abstract class HandledScreenMixin<T extends ScreenHandler> extends Screen implements ScreenHandlerProvider<T> {

    private ModConfig config = ZalupareportClient.getInstance().config;
    private ZalupaManager manager = ZalupareportClient.getInstance().manager;
    private ChiteriList list = manager.list;

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(ScreenHandler handler, PlayerInventory inventory, Text title, CallbackInfo ci) {
        if (title.getString().equals(config.screenName)) {
            manager.reportScreen = true;
        } else if (manager.reportScreen) {
            manager.closeScreen();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (manager.reportScreen && manager.list.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (manager.reportScreen && manager.list.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void mouseReleased(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (manager.reportScreen && manager.list.mouseReleased(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (manager.reportScreen) {
            list.render(context, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (manager.reportScreen) {
            return list.mouseScrolled(mouseX, mouseY, amount);
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        ZalupareportClient instance = ZalupareportClient.getInstance();
        if (manager.reportScreen) {
            if (keyCode == ((KeybindingAccessor) instance.bindToStart).getBoundKey().getCode()) {
                if (config.autoCall) {
                    instance.autoCallManager.startAutoCall();
                } else {
                    manager.checkReports();
                }
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) {
                net.minecraft.client.MinecraftClient.getInstance().setScreen(new SecretMenuScreen());
            }
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void close(CallbackInfo ci) {
        manager.closeScreen();
    }
}
