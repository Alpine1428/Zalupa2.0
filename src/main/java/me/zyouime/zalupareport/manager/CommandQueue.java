package me.zyouime.zalupareport.manager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import java.util.LinkedList;
import java.util.Queue;

public class CommandQueue {
    private static final Queue<String> queue = new LinkedList<>();
    private static int tickCounter = 0;
    
    private static final int DELAY = 15; // Чуть увеличим задержку (0.75с)

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
            
            // Если команда HM - эмулируем ввод через экран чата
            if (cmd.startsWith("hm ")) {
                if (mc.currentScreen == null) {
                    mc.setScreen(new ChatScreen("/" + cmd));
                    // Сразу отправляем (имитация Enter)
                    if (mc.currentScreen instanceof ChatScreen chat) {
                        chat.sendMessage("/" + cmd, true);
                        mc.setScreen(null); // Закрываем чат
                    }
                } else {
                    // Если экран уже открыт (например, инвентарь), просто шлем в чат
                    if (mc.getNetworkHandler() != null)
                        mc.getNetworkHandler().sendChatMessage("/" + cmd);
                }
            } 
            // Остальные команды - пакетом
            else {
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendCommand(cmd);
                }
            }
        }
    }
}
