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
            "Поиск читеров нах", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "Залупа Репорт"));

    @Override
    public void onInitializeClient() {
        instance = this;
        AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
        config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
        manager = new ZalupaManager(this);
        autoCallManager = new AutoCallManager(this);
        this.registerEvents();
    }

    private void registerEvents() {
        AutoConfig.getConfigHolder(ModConfig.class).registerSaveListener((configHolder, modConfig) -> {
            manager.list.setHeight(modConfig.height);
            return ActionResult.PASS;
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            manager.resetAll();
            autoCallManager.reset();
        });
        CoreShaderRegistrationCallback.EVENT.register(context -> {
            String namespace = "zalupareport";
            context.register(new Identifier(namespace, "rectangle"), VertexFormats.POSITION, shader -> {
                MyShaders.RECTANGLE_SHADER = new RectangleShader(shader);
            });
            context.register(new Identifier(namespace, "arc"), VertexFormats.POSITION, shader -> {
                MyShaders.ARC_SHADER = new ArcShader(shader);
            });
        });
    }

    public static ZalupareportClient getInstance() {
        return instance;
    }
}
