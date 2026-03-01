package me.zyouime.zalupareport.manager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import java.util.LinkedList;
import java.util.Queue;

public class CommandQueue {

    private static final Queue<String> queue = new LinkedList<>();
    private static int tickCounter = 0;
    private static final int DELAY = 15;

    public static void add(String cmd) {
        queue.add(cmd);
    }

    public static void clear() {
        queue.clear();
        tickCounter = 0;
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        tickCounter++;
        if (tickCounter < DELAY) return;

        if (!queue.isEmpty()) {
            tickCounter = 0;
            String cmd = queue.poll();

            // ✅ ГАРАНТИРОВАННЫЙ СПОСОБ
            // Полная эмуляция ввода игрока

            mc.execute(() -> {
                ChatScreen chat = new ChatScreen("/" + cmd);
                mc.setScreen(chat);
                chat.sendMessage("/" + cmd, true);
                mc.setScreen(null);
            });
        }
    }
}
