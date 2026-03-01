package me.zyouime.zalupareport.manager;

import net.minecraft.client.MinecraftClient;
import java.util.LinkedList;
import java.util.Queue;

public class CommandQueue {
    private static final Queue<String> queue = new LinkedList<>();
    private static int tickCounter = 0;
    private static final int DELAY = 15;

    public static void add(String cmd) { queue.add(cmd); }
    public static void clear() { queue.clear(); tickCounter = 0; }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        tickCounter++;
        if (tickCounter < DELAY) return;

        if (!queue.isEmpty()) {
            tickCounter = 0;
            String cmd = queue.poll();
            
            if (mc.getNetworkHandler() != null) {
                // 1. Пробуем выполнить как клиентскую команду через диспетчер
                // (если мод зарегистрировал её через Fabric API)
                try {
                    // commandDispatcher ждет команду БЕЗ слэша
                    boolean result = mc.getNetworkHandler().getCommandDispatcher().execute(cmd, mc.player.getCommandSource()) > 0;
                    if (result) return; // Успех
                } catch (Exception ignored) {
                    // Игнорируем ошибки парсинга
                }

                // 2. Если не вышло или это hm команда - шлем в чат как сообщение
                if (cmd.startsWith("hm ")) {
                    mc.getNetworkHandler().sendChatMessage("/" + cmd);
                } 
                // 3. Остальные (find, ln, playtime) шлем как пакеты
                else {
                    mc.getNetworkHandler().sendCommand(cmd);
                }
            }
        }
    }
}
