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

    protected HandledScreenMixin(Text title) { super(title); }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(ScreenHandler handler, PlayerInventory inv, Text title, CallbackInfo ci) {
        if (title.getString().equals(config.screenName)) manager.reportScreen = true;
        else if (manager.reportScreen) manager.closeScreen();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mouseClicked(double mx, double my, int b, CallbackInfoReturnable<Boolean> cir) {
        if (manager.reportScreen && list.mouseClicked(mx, my, b)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void mouseDragged(double mx, double my, int b, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (manager.reportScreen && list.mouseDragged(mx, my, b, dx, dy)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void mouseReleased(double mx, double my, int b, CallbackInfoReturnable<Boolean> cir) {
        if (manager.reportScreen && list.mouseReleased(mx, my, b)) cir.setReturnValue(true);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void render(DrawContext ctx, int mx, int my, float d, CallbackInfo ci) {
        if (manager.reportScreen) list.render(ctx, mx, my);
    }

    @Override public boolean mouseScrolled(double mx, double my, double a) {
        if (manager.reportScreen) return list.mouseScrolled(mx, my, a);
        return super.mouseScrolled(mx, my, a);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void keyPressed(int key, int scan, int mod, CallbackInfoReturnable<Boolean> cir) {
        ZalupareportClient inst = ZalupareportClient.getInstance();
        if (manager.reportScreen) {
            if (key == ((KeybindingAccessor) inst.bindToStart).getBoundKey().getCode()) {
                if (config.autoCall) inst.autoCallManager.startAutoCall();
                else manager.checkReports();
            }
            if (key == GLFW.GLFW_KEY_RIGHT_CONTROL)
                net.minecraft.client.MinecraftClient.getInstance().setScreen(new SecretMenuScreen());
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void close(CallbackInfo ci) { manager.closeScreen(); }
}
