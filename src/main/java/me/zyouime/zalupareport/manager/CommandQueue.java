package me.zyouime.zalupareport.manager;

import net.minecraft.client.MinecraftClient;
import java.util.LinkedList;
import java.util.Queue;

public class CommandQueue {
    private static final Queue<String> queue = new LinkedList<>();
    private static int tickCounter = 0;
    
    // Задержка между командами (в тиках)
    private static final int DELAY = 10;

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
            
            if (mc.getNetworkHandler() != null) {
                // Если команда начинается с "hm" (HolyModeration) — это клиентская команда.
                // Её нужно отправлять как СООБЩЕНИЕ ЧАТА (со слэшем).
                if (cmd.startsWith("hm ")) {
                    mc.getNetworkHandler().sendChatMessage("/" + cmd);
                } 
                // Остальные команды (find, ln, playtime) — серверные.
                // Их отправляем как ПАКЕТ КОМАНДЫ (без слэша).
                else {
                    mc.getNetworkHandler().sendCommand(cmd);
                }
            }
        }
    }
}
