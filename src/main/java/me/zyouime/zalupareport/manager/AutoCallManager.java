package me.zyouime.zalupareport.manager;

import me.zyouime.zalupareport.client.ZalupareportClient;
import me.zyouime.zalupareport.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtElement;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoCallManager {

    public enum State {
        IDLE, SEARCHING, DOING_SPY, DOING_FIND, CONNECTING_SERVER,
        DOING_PLAYTIME, CHECKING_PLAYTIME_LOOP, WAITING_SPYFRZ,
        CLOSING_STEP1, CLOSING_STEP2, CLOSING_STEP3, REOPENING
    }

    private final ModConfig config;
    private final MinecraftClient client = MinecraftClient.getInstance();

    // Паттерн для ника из названия предмета
    private final Pattern nickPattern = Pattern.compile("\u0438\u0433\u0440\u043e\u043a\u0430\\s+(\\w+)");

    // Паттерн для времени из лора
    private final Pattern timePattern = Pattern.compile(
        "(?:(\\d+)\\s\u0447\\.,\\s)?(?:(\\d+)\\s\u043c\\.,\\s)?\\d+\\s\u0441\u0435\u043a\\.\\s\\(" +
        "(?:(\\d+)\\s\u0447\\.,\\s)?(?:(\\d+)\\s\u043c\\.,\\s)?\\d+\\s\u0441\u0435\u043a\\.\\)");

    // Паттерны для чата
    private final Pattern findPattern = Pattern.compile(
        "\u0418\u0433\u0440\u043e\u043a\\s+\\S+\\s+\u043d\u0430\u0445\u043e\u0434\u0438\u0442\u0441\u044f \u043d\u0430 \u0441\u0435\u0440\u0432\u0435\u0440\u0435\\s+(\\S+)");
    private final Pattern activityPattern = Pattern.compile(
        "\u041f\u043e\u0441\u043b\u0435\u0434\u043d\u044f\u044f \u0430\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u044c:\\s*(?:(\\d+)\\s*\u0447\\.,\\s*)?(?:(\\d+)\\s*\u043c\\.,\\s*)?(\\d+)\\s*\u0441\u0435\u043a\\.");
    private final Pattern lanarchyP = Pattern.compile("lanarchy(\\d+)");
    private final Pattern l2anarchyP = Pattern.compile("l2anarchy(\\d+)");
    private final Pattern anarchyP = Pattern.compile("anarchy(\\d+)");

    public State state = State.IDLE;
    private String currentNick;
    private long ptStart;
    private boolean waitFind, waitPt, foundAny;

    public AutoCallManager(ZalupareportClient mod) {
        config = mod.config;
    }

    public void reset() {
        state = State.IDLE;
        currentNick = null;
        ptStart = 0;
        waitFind = false;
        waitPt = false;
        foundAny = false;
    }

    public void startAutoCall() {
        if (!config.autoCall) return;
        state = State.SEARCHING;
        foundAny = false;
        search();
    }

    /**
     * Поиск репорта с учетом ВСЕХ фильтров:
     * 1. Пропускаем SKELETON_SKULL
     * 2. Проверяем ключевые слова (detects) в лоре
     * 3. Проверяем плейтайм (allTime, activeTime)
     */
    private void search() {
        if (client.player == null || client.player.currentScreenHandler == null) {
            msg("\u041e\u0448\u0438\u0431\u043a\u0430: \u043d\u0435\u0442 \u043c\u0435\u043d\u044e");
            state = State.IDLE;
            return;
        }

        List<ItemStack> items = client.player.currentScreenHandler.getStacks();
        int foundSlot = -1;
        String foundNick = null;

        for (int i = 0; i < Math.min(45, items.size()); i++) {
            ItemStack itemStack = items.get(i);

            // Пропускаем пустые и черепа
            if (itemStack.isEmpty() || itemStack.getItem().equals(Items.SKELETON_SKULL)) {
                continue;
            }

            String itemName = itemStack.getName().getString();

            // Проверяем NBT
            if (itemStack.getNbt() == null || itemStack.getNbt().isEmpty()) continue;
            if (itemStack.getNbt().getCompound("display") == null) continue;

            NbtElement nbt = itemStack.getNbt().getCompound("display").get("Lore");
            if (nbt == null) continue;
            String nbtString = nbt.asString();
            if (nbtString == null) continue;

            // === ФИЛЬТР 1: Ник игрока ===
            Matcher nickMatcher = nickPattern.matcher(itemName);
            if (!nickMatcher.find()) continue;
            String nickName = nickMatcher.group(1);

            // === ФИЛЬТР 2: Ключевые слова (detects) ===
            String detect = config.detects.stream()
                    .filter(s -> !s.isEmpty() && nbtString.toLowerCase().contains(s.toLowerCase()))
                    .findFirst()
                    .orElse("zalupa");

            boolean isHasDetect = config.detects.isEmpty() || !detect.equals("zalupa");
            if (!isHasDetect) continue;

            // === ФИЛЬТР 3: Плейтайм ===
            Matcher timeMatcher = timePattern.matcher(nbtString);
            if (!timeMatcher.find()) continue;

            int allHours = getGroup(timeMatcher, 1);
            int allMinutes = getGroup(timeMatcher, 2);
            int lastActiveHours = getGroup(timeMatcher, 3);
            int lastActiveMinutes = getGroup(timeMatcher, 4);

            int allMinutesTotal = allHours * 60 + allMinutes;
            int lastActiveMinutesTotal = lastActiveHours * 60 + lastActiveMinutes;

            ModConfig.PlayTime pt = config.playTime;
            boolean allOk = allMinutesTotal < pt.allTime;
            boolean lastOk = lastActiveMinutesTotal < pt.activeTime;

            boolean checkOnlyActive = pt.checkActiveTime && !pt.checkAllTime;
            boolean checkOnlyAll = !pt.checkActiveTime && pt.checkAllTime;
            boolean checkBoth = pt.checkActiveTime && pt.checkAllTime;

            // Если проверка не прошла — пропускаем этот репорт
            if (checkOnlyActive && !lastOk) continue;
            if (checkOnlyAll && !allOk) continue;
            if (checkBoth && (!allOk || !lastOk)) continue;

            // Все фильтры пройдены!
            foundSlot = i;
            foundNick = nickName;
            break;
        }

        if (foundSlot != -1 && foundNick != null) {
            foundAny = true;
            currentNick = foundNick;
            client.interactionManager.clickSlot(
                    client.player.currentScreenHandler.syncId,
                    foundSlot, 0, SlotActionType.PICKUP, client.player);
            client.keyboard.setClipboard(foundNick);
            msg("\u0421\u043a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u043b \u043d\u0438\u043a " + foundNick +
                " \u0438 \u043a\u043b\u0438\u043a\u043d\u0443\u043b \u043f\u043e \u0441\u043b\u043e\u0442\u0443 \u0441 \u0447\u0438\u0442\u0435\u0440\u043e\u043c \u043d\u0430\u0445!");
            delay(() -> {
                state = State.DOING_SPY;
                doSpy();
            }, 1000);
        } else {
            // Проверяем следующую страницу
            boolean hasNextPage = items.size() > 45
                    && items.get(45) != null
                    && !items.get(45).isEmpty();

            if (hasNextPage) {
                client.interactionManager.clickSlot(
                        client.player.currentScreenHandler.syncId,
                        53, 0, SlotActionType.PICKUP, client.player);
                delay(this::search, 1000);
            } else {
                if (!foundAny) {
                    if (client.player != null) client.player.closeHandledScreen();
                    msg("\u0420\u0435\u043f\u043e\u0440\u0442\u044b \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u044b");
                }
                state = State.IDLE;
            }
        }
    }

    private int getGroup(Matcher m, int g) {
        return m.group(g) == null ? 0 : Integer.parseInt(m.group(g));
    }

    public void onChatMessage(String message) {
        if (state == State.IDLE || !config.autoCall) return;

        if (state == State.DOING_FIND && waitFind) {
            Matcher m = findPattern.matcher(message);
            if (m.find()) {
                waitFind = false;
                connect(m.group(1));
            }
        }

        if ((state == State.DOING_PLAYTIME || state == State.CHECKING_PLAYTIME_LOOP) && waitPt) {
            Matcher m = activityPattern.matcher(message);
            if (m.find()) {
                waitPt = false;
                int h = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
                int min = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
                int sec = Integer.parseInt(m.group(3));
                handlePt(h * 3600 + min * 60 + sec);
            }
        }

        if (state == State.WAITING_SPYFRZ) {
            String l = message.toLowerCase();
            if (l.contains("hm sban") || l.contains("banip") || l.contains("hm unfrz")) {
                state = State.CLOSING_STEP1;
                delay(this::close1, 500);
            }
        }
    }

    private void doSpy() {
        if (currentNick == null) { state = State.IDLE; return; }
        cmd("hm spy " + currentNick);
        state = State.DOING_FIND;
        delay(this::doFind, 500);
    }

    private void doFind() {
        if (currentNick == null) { state = State.IDLE; return; }
        waitFind = true;
        cmd("find " + currentNick);
        delay(() -> {
            if (waitFind && state == State.DOING_FIND) {
                waitFind = false;
                msg("\u041d\u0435 \u043d\u0430\u0448\u0435\u043b \u0441\u0435\u0440\u0432\u0435\u0440 " + currentNick);
                state = State.IDLE;
            }
        }, 10000);
    }

    private void connect(String srv) {
        String c = null;
        Matcher m1 = lanarchyP.matcher(srv);
        Matcher m2 = l2anarchyP.matcher(srv);
        Matcher m3 = anarchyP.matcher(srv);
        if (m1.find()) c = "ln " + m1.group(1);
        else if (m2.find()) c = "ln120 " + m2.group(1);
        else if (m3.find()) c = "cn " + m3.group(1);

        if (c != null) {
            cmd(c);
            state = State.CONNECTING_SERVER;
            delay(this::startPt, 10000);
        } else {
            msg("\u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u044b\u0439 \u0441\u0435\u0440\u0432\u0435\u0440: " + srv);
            state = State.IDLE;
        }
    }

    private void startPt() {
        state = State.DOING_PLAYTIME;
        ptStart = System.currentTimeMillis();
        sendPt();
    }

    private void sendPt() {
        if (currentNick == null) { state = State.IDLE; return; }
        waitPt = true;
        cmd("playtime " + currentNick);
    }

    private void handlePt(int sec) {
        if (sec < 7) {
            cmd("hm spyfrz");
            state = State.WAITING_SPYFRZ;
            return;
        }
        if (state == State.DOING_PLAYTIME) {
            state = State.CHECKING_PLAYTIME_LOOP;
            ptStart = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - ptStart < 30000) {
            delay(() -> { if (state == State.CHECKING_PLAYTIME_LOOP) sendPt(); }, 3000);
        } else {
            msg("\u0418\u0433\u0440\u043e\u043a \u0430\u0444\u043a \u043f\u0440\u043e\u0441\u043b\u0435\u0434\u0438\u0442\u0435 \u0441\u0430\u043c\u0438");
            state = State.CLOSING_STEP1;
            delay(this::close1, 1000);
        }
    }

    private void close1() {
        cmd("reportlist");
        state = State.CLOSING_STEP2;
        delay(this::close2, 1500);
    }

    private void close2() {
        if (client.player == null || client.player.currentScreenHandler == null) {
            state = State.IDLE; return;
        }
        client.interactionManager.clickSlot(
                client.player.currentScreenHandler.syncId,
                47, 0, SlotActionType.PICKUP, client.player);
        state = State.CLOSING_STEP3;
        delay(this::close3, 1500);
    }

    private void close3() {
        if (client.player == null || client.player.currentScreenHandler == null) {
            state = State.IDLE; return;
        }
        client.interactionManager.clickSlot(
                client.player.currentScreenHandler.syncId,
                16, 0, SlotActionType.PICKUP, client.player);
        delay(() -> {
            chat("-");
            state = State.REOPENING;
            delay(this::reopen, 1500);
        }, 500);
    }

    private void reopen() {
        foundAny = false;
        cmd("reportlist");
        delay(() -> {
            state = State.SEARCHING;
            search();
        }, 1500);
    }

    private void msg(String m) {
        if (client.player != null) client.player.sendMessage(Text.of(m));
    }

    private void cmd(String c) {
        if (client.getNetworkHandler() != null) client.getNetworkHandler().sendCommand(c);
    }

    private void chat(String m) {
        if (client.player == null) return;
        client.execute(() -> {
            client.setScreen(null);
            ChatScreen s = new ChatScreen("");
            client.setScreen(s);
            s.sendMessage(m, true);
            client.setScreen(null);
        });
    }

    private void delay(Runnable r, long ms) {
        Thread t = new Thread(() -> {
            try { Thread.sleep(ms); } catch (InterruptedException e) { return; }
            client.execute(r);
        });
        t.setDaemon(true);
        t.start();
    }
}
