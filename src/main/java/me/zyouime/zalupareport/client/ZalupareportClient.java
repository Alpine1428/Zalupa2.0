package me.zyouime.zalupareport.client;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import me.zyouime.zalupareport.config.ModConfig;
import me.zyouime.zalupareport.manager.ZalupaManager;
import me.zyouime.zalupareport.manager.AutoCallManager;
import me.zyouime.zalupareport.render.shader.ArcShader;
import me.zyouime.zalupareport.render.shader.MyShaders;
import me.zyouime.zalupareport.render.shader.RectangleShader;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class ZalupareportClient implements ClientModInitializer {
    private static ZalupareportClient instance;
    public ModConfig config;
    public ZalupaManager manager;
    public AutoCallManager autoCallManager;
    public KeyBinding bindToStart = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "\u041f\u043e\u0438\u0441\u043a \u0447\u0438\u0442\u0435\u0440\u043e\u0432 \u043d\u0430\u0445",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT,
            "\u0417\u0430\u043b\u0443\u043f\u0430 \u0420\u0435\u043f\u043e\u0440\u0442"));

    @Override
    public void onInitializeClient() {
        instance = this;
        AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
        config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        manager = new ZalupaManager(this);
        autoCallManager = new AutoCallManager(this);
        registerEvents();
    }

    private void registerEvents() {
        AutoConfig.getConfigHolder(ModConfig.class).registerSaveListener((h, c) -> {
            manager.list.setHeight(c.height);
            return ActionResult.PASS;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            manager.resetAll();
            autoCallManager.reset();
        });
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            context.register(new Identifier("zalupareport", "rectangle"), VertexFormats.POSITION,
                    shader -> MyShaders.RECTANGLE_SHADER = new RectangleShader(shader));
            context.register(new Identifier("zalupareport", "arc"), VertexFormats.POSITION,
                    shader -> MyShaders.ARC_SHADER = new ArcShader(shader));
        });
    }

    public static ZalupareportClient getInstance() { return instance; }
}
