package me.zyouime.zalupareport.manager;

import net.minecraft.client.MinecraftClient;
import java.util.LinkedList;
import java.util.Queue;

public class CommandQueue {
    private static final Queue<String> queue = new LinkedList<>();
    private static int tickCounter = 0;
    
    // Задержка между командами в тиках (20 тиков = 1 секунда)
    // Ставим 5 тиков (0.25 сек) чтобы сервер успевал
    private static final int DELAY = 5;

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
            
            // В Fabric 1.20.1 метод называется sendCommand
            // Он принимает строку БЕЗ слэша
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendCommand(cmd);
            }
        }
    }
}
