package me.zyouime.zalupareport.manager;

import me.zyouime.zalupareport.client.ZalupareportClient;
import me.zyouime.zalupareport.config.ModConfig;
import net.minecraft.client.MinecraftClient;
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
        IDLE,
        SEARCHING,
        DOING_SPY,
        DOING_FIND,
        CONNECTING_SERVER,
        DOING_PLAYTIME,
        CHECKING_PLAYTIME_LOOP,
        WAITING_SPYFRZ,
        WAITING_REPORT_OPEN,
        CLOSING_STEP1,
        CLOSING_STEP2,
        CLOSING_STEP3,
        CLOSING_STEP4,
        REOPENING
    }

    /**
     * Причина завершения:
     * PLAYER_AFK       -> слот 14 (idx 13), в чат "afk"
     * PLAYER_BANNED    -> слот 16 (idx 15), в чат ничего
     * PLAYER_UNFROZEN  -> слот 14 (idx 13), в чат "-"
     * PLAYER_AFK_SERVER-> слот 14 (idx 13), в чат "afk" (сервер сказал "Игрок афк")
     */
    public enum CloseReason {
        PLAYER_AFK,
        PLAYER_BANNED,
        PLAYER_UNFROZEN,
        PLAYER_AFK_SERVER
    }

    private final ModConfig config;
    private final MinecraftClient client = MinecraftClient.getInstance();

    private final Pattern nickPattern = Pattern.compile("\u0438\u0433\u0440\u043e\u043a\u0430\\s+(\\w+)");
    private final Pattern timePattern = Pattern.compile(
        "(?:(\\d+)\\s\u0447\\.,\\s)?(?:(\\d+)\\s\u043c\\.,\\s)?\\d+\\s\u0441\u0435\u043a\\.\\s\\("
        + "(?:(\\d+)\\s\u0447\\.,\\s)?(?:(\\d+)\\s\u043c\\.,\\s)?\\d+\\s\u0441\u0435\u043a\\.\\)"
    );
    private final Pattern findPattern = Pattern.compile(
        "\u0418\u0433\u0440\u043e\u043a\\s+\\S+\\s+\u043d\u0430\u0445\u043e\u0434\u0438\u0442\u0441\u044f \u043d\u0430 \u0441\u0435\u0440\u0432\u0435\u0440\u0435\\s+(\\S+)"
    );
    private final Pattern activityPattern = Pattern.compile(
        "\u041f\u043e\u0441\u043b\u0435\u0434\u043d\u044f\u044f \u0430\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u044c:\\s*(?:(\\d+)\\s*\u0447\\.?,\\s*)?(?:(\\d+)\\s*\u043c\\.?,\\s*)?(\\d+)\\s*\u0441(?:\u0435\u043a)?\\.\\s*\u043d\u0430\u0437\u0430\u0434"
    );
    private final Pattern l2anarchyP = Pattern.compile("^l2anarchy(\\d*)$");
    private final Pattern lanarchyP = Pattern.compile("^lanarchy(\\d*)$");
    private final Pattern anarchyP = Pattern.compile("^anarchy(\\d*)$");

    // Бан (входящие/исходящие)
    private final Pattern banIncoming = Pattern.compile("(?i)(?:hm sban|banip)");
    private final Pattern banOutgoing = Pattern.compile("(?i)^/(?:hm sban|banip)");

    // Анфриз/spy выход (входящие/исходящие)
    private final Pattern unfrzIncoming = Pattern.compile("(?i)(?:hm unfrz|hm unfreezing|hm spy)");
    private final Pattern unfrzOutgoing = Pattern.compile("(?i)^/(?:hm unfrz|hm unfreezing|hm spy)");

    // "Открываем репорт ..."
    private final Pattern openReportPattern = Pattern.compile("\u00a7b\u041e\u0442\u043a\u0440\u044b\u0432\u0430\u0435\u043c \u0440\u0435\u043f\u043e\u0440\u0442");

    // "Игрок афк проследите сами"
    private final Pattern afkServerPattern = Pattern.compile("\u00a7c\u0418\u0433\u0440\u043e\u043a \u0430\u0444\u043a");

    public State state = State.IDLE;
    private String currentNick;
    private long ptStart;
    private boolean waitFind;
    private boolean waitPt;
    private boolean foundAny;
    private volatile boolean cancelled = false;
    private boolean scanningPlaytime = false;
    private CloseReason closeReason = CloseReason.PLAYER_AFK;

    public AutoCallManager(ZalupareportClient mod) {
        config = mod.config;
    }

    public void reset() {
        cancelled = true;
        state = State.IDLE;
        currentNick = null;
        ptStart = 0;
        waitFind = false;
        waitPt = false;
        foundAny = false;
        scanningPlaytime = false;
        closeReason = CloseReason.PLAYER_AFK;
        CommandQueue.clear();
    }

    public boolean isActive() {
        return (config.autoCall || config.autoCheck) && state != State.IDLE;
    }

    public boolean isScanningPlaytime() {
        return scanningPlaytime;
    }

    public void startAutoCall() {
        if (!config.autoCall && !config.autoCheck) return;
        cancelled = false;
        state = State.SEARCHING;
        foundAny = false;
        search();
    }

    private void search() {
        if (cancelled) { state = State.IDLE; return; }
        if (client.player == null || client.player.currentScreenHandler == null) {
            msg("\u00a7c[Auto] \u041e\u0448\u0438\u0431\u043a\u0430: \u043c\u0435\u043d\u044e \u0437\u0430\u043a\u0440\u044b\u0442\u043e");
            state = State.IDLE;
            return;
        }

        List<ItemStack> items = client.player.currentScreenHandler.getStacks();
        int slot = -1;
        String nick = null;

        for (int i = 0; i < Math.min(45, items.size()); i++) {
            ItemStack is = items.get(i);
            if (is.isEmpty() || is.getItem().equals(Items.SKELETON_SKULL)) continue;
            String itemName = is.getName().getString();
            if (is.getNbt() == null || is.getNbt().isEmpty()) continue;
            if (is.getNbt().getCompound("display") == null) continue;
            NbtElement nbt = is.getNbt().getCompound("display").get("Lore");
            if (nbt == null) continue;
            String nbtString = nbt.asString();

            Matcher nm = nickPattern.matcher(itemName);
            if (!nm.find()) continue;
            String n = nm.group(1);

            String det = config.detects.stream()
                .filter(s -> !s.isEmpty() && nbtString.toLowerCase().contains(s.toLowerCase()))
                .findFirst().orElse("zalupa");
            if (!config.detects.isEmpty() && det.equals("zalupa")) continue;

            Matcher tm = timePattern.matcher(nbtString);
            if (!tm.find()) continue;
            int allM = getGroup(tm, 1) * 60 + getGroup(tm, 2);
            int actM = getGroup(tm, 3) * 60 + getGroup(tm, 4);
            ModConfig.PlayTime pt = config.playTime;
            boolean allOk = allM < pt.allTime;
            boolean lastOk = actM < pt.activeTime;
            if (pt.checkActiveTime && !pt.checkAllTime && !lastOk) continue;
            if (!pt.checkActiveTime && pt.checkAllTime && !allOk) continue;
            if (pt.checkActiveTime && pt.checkAllTime && (!allOk || !lastOk)) continue;

            slot = i;
            nick = n;
            break;
        }

        if (slot != -1 && nick != null) {
            foundAny = true;
            currentNick = nick.trim();
            client.interactionManager.clickSlot(
                client.player.currentScreenHandler.syncId,
                slot, 0, SlotActionType.PICKUP, client.player
            );
            client.keyboard.setClipboard(currentNick);
            msg("\u00a7a[Auto] \u0412\u0437\u044f\u043b \u0440\u0435\u043f\u043e\u0440\u0442: \u00a7e" + currentNick);
            state = State.DOING_SPY;
            delay(this::doSpy, 1000);
        } else {
            boolean hasNext = items.size() > 53 && items.get(53) != null && !items.get(53).isEmpty();
            if (hasNext) {
                client.interactionManager.clickSlot(
                    client.player.currentScreenHandler.syncId,
                    53, 0, SlotActionType.PICKUP, client.player
                );
                delay(this::search, 1000);
            } else {
                if (!foundAny) {
                    if (client.player != null) client.player.closeHandledScreen();
                    msg("\u00a7c[Auto] \u0420\u0435\u043f\u043e\u0440\u0442\u044b \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u044b");
                }
                state = State.IDLE;
            }
        }
    }

    private int getGroup(Matcher m, int g) {
        return m.group(g) == null ? 0 : Integer.parseInt(m.group(g));
    }

    /**
     * Входящие сообщения чата
     */
    public void onChatMessage(String message) {
        if (state == State.IDLE || cancelled) return;

        // === /find ответ ===
        if (state == State.DOING_FIND && waitFind) {
            Matcher m = findPattern.matcher(message);
            if (m.find()) {
                waitFind = false;
                connect(m.group(1).trim().toLowerCase());
                return;
            }
        }

        // === /playtime ответ ===
        if (config.autoCheck
            && (state == State.DOING_PLAYTIME || state == State.CHECKING_PLAYTIME_LOOP)
            && waitPt) {
            if (message.contains("\u041f\u043e\u0441\u043b\u0435\u0434\u043d\u044f\u044f \u0430\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u044c")) {
                Matcher m = activityPattern.matcher(message);
                if (m.find()) {
                    waitPt = false;
                    int h = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
                    int min = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
                    int sec = Integer.parseInt(m.group(3));
                    handlePlaytimeResult(h * 3600 + min * 60 + sec);
                    return;
                }
            }
        }

        // === "Открываем репорт ..." -> начинаем отслеживание ===
        if (config.autoCheck && state == State.WAITING_SPYFRZ) {
            if (openReportPattern.matcher(message).find()) {
                msg("\u00a7e[Auto] \u0420\u0435\u043f\u043e\u0440\u0442 \u043e\u0442\u043a\u0440\u044b\u0442. \u041e\u0442\u0441\u043b\u0435\u0436\u0438\u0432\u0430\u044e...");
                state = State.WAITING_REPORT_OPEN;
                return;
            }
        }

        // === Отслеживание после "Открываем репорт" ===
        if (config.autoCheck && state == State.WAITING_REPORT_OPEN) {
            // "Игрок афк проследите сами"
            if (afkServerPattern.matcher(message).find()) {
                msg("\u00a7e[Auto] \u0421\u0435\u0440\u0432\u0435\u0440: \u0438\u0433\u0440\u043e\u043a \u0430\u0444\u043a. \u0417\u0430\u0432\u0435\u0440\u0448\u0430\u044e...");
                closeReason = CloseReason.PLAYER_AFK_SERVER;
                state = State.CLOSING_STEP1;
                delay(this::closeStep1, 1000);
                return;
            }

            // /hm sban или /banip -> бан
            if (banIncoming.matcher(message).find()) {
                msg("\u00a7a[Auto] \u0411\u0430\u043d. \u0417\u0430\u0432\u0435\u0440\u0448\u0430\u044e...");
                closeReason = CloseReason.PLAYER_BANNED;
                state = State.CLOSING_STEP1;
                delay(this::closeStep1, 1000);
                return;
            }

            // /hm unfrz /hm unfreezing /hm spy -> анфриз
            if (unfrzIncoming.matcher(message).find()) {
                msg("\u00a7e[Auto] \u0410\u043d\u0444\u0440\u0438\u0437/spy. \u0417\u0430\u0432\u0435\u0440\u0448\u0430\u044e...");
                closeReason = CloseReason.PLAYER_UNFROZEN;
                state = State.CLOSING_STEP1;
                delay(this::closeStep1, 1000);
                return;
            }
        }

        // === Старый WAITING_SPYFRZ (до "Открываем репорт") ===
        if (config.autoCheck && state == State.WAITING_SPYFRZ) {
            // "Игрок афк проследите сами"
            if (afkServerPattern.matcher(message).find()) {
                msg("\u00a7e[Auto] \u0421\u0435\u0440\u0432\u0435\u0440: \u0438\u0433\u0440\u043e\u043a \u0430\u0444\u043a. \u0417\u0430\u0432\u0435\u0440\u0448\u0430\u044e...");
                closeReason = CloseReason.PLAYER_AFK_SERVER;
                state = State.CLOSING_STEP1;
                delay(this::closeStep1, 1000);
                return;
            }

            if (banIncoming.matcher(message).find()) {
                msg("\u00a7a[Auto] \u0411\u0430\u043d. \u0417\u0430\u0432\u0435\u0440\u0448\u0430\u044e...");
                closeReason = CloseReason.PLAYER_BANNED;
                state = State.CLOSING_STEP1;
                delay(this::closeStep1, 1000);
                return;
            }

            if (unfrzIncoming.matcher(message).find()) {
                msg("\u00a7e[Auto] \u0410\u043d\u0444\u0440\u0438\u0437/spy. \u0417\u0430\u0432\u0435\u0440\u0448\u0430\u044e...");
                closeReason = CloseReason.PLAYER_UNFROZEN;
                state = State.CLOSING_STEP1;
                delay(this::closeStep1, 1000);
                return;
            }
        }
    }

    /**
     * Исходящие команды игрока
     */
    public void onOutgoingCommand(String command) {
        if (state == State.IDLE || cancelled) return;

        if (config.autoCheck && (state == State.WAITING_SPYFRZ || state == State.WAITING_REPORT_OPEN)) {
            if (banOutgoing.matcher(command).find()) {
                msg("\u00a7a[Auto] \u0412\u044b \u0437\u0430\u0431\u0430\u043d\u0438\u043b\u0438. \u0417\u0430\u0432\u0435\u0440\u0448\u0430\u044e...");
                closeReason = CloseReason.PLAYER_BANNED;
                state = State.CLOSING_STEP1;
                delay(this::closeStep1, 2000);
                return;
            }
            if (unfrzOutgoing.matcher(command).find()) {
                msg("\u00a7e[Auto] \u0410\u043d\u0444\u0440\u0438\u0437/spy. \u0417\u0430\u0432\u0435\u0440\u0448\u0430\u044e...");
                closeReason = CloseReason.PLAYER_UNFROZEN;
                state = State.CLOSING_STEP1;
                delay(this::closeStep1, 2000);
                return;
            }
        }
    }

    private void doSpy() {
        if (cancelled || currentNick == null) { state = State.IDLE; return; }
        msg("\u00a7e[Auto] /hm spy " + currentNick);
        CommandQueue.add("hm spy " + currentNick);
        state = State.DOING_FIND;
        delay(this::doFind, 3000);
    }

    private void doFind() {
        if (cancelled || currentNick == null) { state = State.IDLE; return; }
        waitFind = true;
        msg("\u00a7e[Auto] /find " + currentNick);
        CommandQueue.add("find " + currentNick);
        delay(() -> {
            if (waitFind && state == State.DOING_FIND) {
                waitFind = false;
                msg("\u00a7c[Auto] \u0422\u0430\u0439\u043c\u0430\u0443\u0442 /find");
                if (config.autoCheck) {
                    closeReason = CloseReason.PLAYER_AFK;
                    state = State.CLOSING_STEP1;
                    delay(AutoCallManager.this::closeStep1, 1000);
                } else {
                    state = State.IDLE;
                }
            }
        }, 10000);
    }

    private void connect(String srv) {
        if (cancelled) { state = State.IDLE; return; }
        String command = null;
        Matcher m2 = l2anarchyP.matcher(srv);
        Matcher m1 = lanarchyP.matcher(srv);
        Matcher m3 = anarchyP.matcher(srv);

        if (m2.matches()) {
            String idx = m2.group(1);
            command = "ln120 " + (idx == null || idx.isEmpty() ? "1" : idx);
        } else if (m1.matches()) {
            String idx = m1.group(1);
            command = "ln " + (idx == null || idx.isEmpty() ? "1" : idx);
        } else if (m3.matches()) {
            String idx = m3.group(1);
            command = "cn " + (idx == null || idx.isEmpty() ? "1" : idx);
        }

        if (command == null) {
            msg("\u00a7c[Auto] \u041d\u0435\u0438\u0437\u0432. \u0441\u0435\u0440\u0432\u0435\u0440: " + srv);
            if (config.autoCheck) {
                closeReason = CloseReason.PLAYER_AFK;
                state = State.CLOSING_STEP1;
                delay(this::closeStep1, 1000);
            } else {
                state = State.IDLE;
            }
            return;
        }

        msg("\u00a7e[Auto] /" + command);
        CommandQueue.add(command);

        if (config.autoCall && !config.autoCheck) {
            msg("\u00a7a[Auto] \u0410\u0432\u0442\u043e\u0412\u044b\u0437\u043e\u0432 \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d.");
            state = State.IDLE;
            return;
        }
        if (config.autoCheck) {
            state = State.CONNECTING_SERVER;
            msg("\u00a7e[Auto] \u0416\u0434\u0443 10\u0441...");
            delay(this::startPlaytimeCheck, 10000);
        }
    }

    private void startPlaytimeCheck() {
        if (cancelled) { state = State.IDLE; return; }
        state = State.DOING_PLAYTIME;
        ptStart = System.currentTimeMillis();
        scanningPlaytime = true;
        msg("\u00a7e[Auto] \u041f\u043b\u0435\u0439\u0442\u0430\u0439\u043c...");
        sendPlaytime();
    }

    private void sendPlaytime() {
        if (cancelled || currentNick == null) { scanningPlaytime = false; state = State.IDLE; return; }
        waitPt = true;
        CommandQueue.add("playtime " + currentNick);
    }

    private void handlePlaytimeResult(int lastActivitySec) {
        if (cancelled) { scanningPlaytime = false; state = State.IDLE; return; }

        if (lastActivitySec < 7) {
            scanningPlaytime = false;
            msg("\u00a7a[Auto] \u0410\u043a\u0442\u0438\u0432\u0435\u043d (" + lastActivitySec + "\u0441)! /hm spyfrz");
            CommandQueue.add("hm spyfrz");
            state = State.WAITING_SPYFRZ;
        } else {
            if (state == State.DOING_PLAYTIME) {
                state = State.CHECKING_PLAYTIME_LOOP;
                ptStart = System.currentTimeMillis();
                msg("\u00a7e[Auto] \u0410\u0424\u041a (" + lastActivitySec + "\u0441). 30\u0441...");
            }
            long elapsed = System.currentTimeMillis() - ptStart;
            if (elapsed < 30000) {
                delay(() -> {
                    if (state == State.CHECKING_PLAYTIME_LOOP && !cancelled) sendPlaytime();
                }, 3000);
            } else {
                scanningPlaytime = false;
                msg("\u00a7c[Auto] \u0410\u0424\u041a >30\u0441. \u041f\u0440\u043e\u043f\u0443\u0441\u043a\u0430\u044e...");
                closeReason = CloseReason.PLAYER_AFK;
                state = State.CLOSING_STEP1;
                delay(this::closeStep1, 1000);
            }
        }
    }

    // ===== ЗАВЕРШЕНИЕ РЕПОРТА =====

    private void closeStep1() {
        if (cancelled) { state = State.IDLE; return; }
        scanningPlaytime = false;
        msg("\u00a7e[Auto] /reportlist");
        CommandQueue.add("reportlist");
        state = State.CLOSING_STEP2;
        delay(this::closeStep2, 2000);
    }

    private void closeStep2() {
        if (cancelled) { state = State.IDLE; return; }
        if (client.player == null || client.player.currentScreenHandler == null) {
            msg("\u00a7c[Auto] \u041c\u0435\u043d\u044e \u043d\u0435 \u043e\u0442\u043a\u0440\u044b\u0442\u043e (step2)");
            state = State.IDLE;
            return;
        }
        msg("\u00a7e[Auto] \u041b\u041a\u041c \u0441\u043b\u043e\u0442 47");
        client.interactionManager.clickSlot(
            client.player.currentScreenHandler.syncId,
            46, 0, SlotActionType.PICKUP, client.player
        );
        state = State.CLOSING_STEP3;
        delay(this::closeStep3, 1000);
    }

    /**
     * Клик по слоту зависит от причины:
     * AFK / AFK_SERVER / UNFROZEN -> слот 14 (индекс 13)
     * BANNED -> слот 16 (индекс 15)
     */
    private void closeStep3() {
        if (cancelled) { state = State.IDLE; return; }
        if (client.player == null || client.player.currentScreenHandler == null) {
            msg("\u00a7c[Auto] \u041c\u0435\u043d\u044e \u043d\u0435 \u043e\u0442\u043a\u0440\u044b\u0442\u043e (step3)");
            state = State.IDLE;
            return;
        }

        int slotIndex;
        String slotName;
        switch (closeReason) {
            case PLAYER_BANNED:
                slotIndex = 15;  // слот 16
                slotName = "16";
                break;
            case PLAYER_AFK:
            case PLAYER_AFK_SERVER:
            case PLAYER_UNFROZEN:
            default:
                slotIndex = 13;  // слот 14
                slotName = "14";
                break;
        }

        msg("\u00a7e[Auto] \u041b\u041a\u041c \u0441\u043b\u043e\u0442 " + slotName + " (" + closeReason.name() + ")");
        client.interactionManager.clickSlot(
            client.player.currentScreenHandler.syncId,
            slotIndex, 0, SlotActionType.PICKUP, client.player
        );
        state = State.CLOSING_STEP4;
        delay(this::closeStep4, 1000);
    }

    /**
     * Отправляем сообщение в чат:
     * AFK / AFK_SERVER -> "afk"
     * UNFROZEN -> "-"
     * BANNED -> ничего
     */
    private void closeStep4() {
        if (cancelled) { state = State.IDLE; return; }

        // Закрываем GUI
        client.execute(() -> {
            if (client.player != null && client.currentScreen != null) {
                client.player.closeHandledScreen();
            }
        });

        delay(() -> {
            if (cancelled) { state = State.IDLE; return; }

            // Определяем что писать в чат
            String chatMsg = null;
            switch (closeReason) {
                case PLAYER_AFK:
                case PLAYER_AFK_SERVER:
                    chatMsg = "afk";
                    break;
                case PLAYER_UNFROZEN:
                    chatMsg = "-";
                    break;
                case PLAYER_BANNED:
                    // Ничего не пишем
                    chatMsg = null;
                    break;
            }

            if (chatMsg != null) {
                final String toSend = chatMsg;
                msg("\u00a7e[Auto] \u041e\u0442\u043f\u0440\u0430\u0432\u043b\u044f\u044e '" + toSend + "'");
                client.execute(() -> {
                    if (client.getNetworkHandler() != null) {
                        client.getNetworkHandler().sendChatMessage(toSend);
                    }
                });
            }

            msg("\u00a7a[Auto] \u0420\u0435\u043f\u043e\u0440\u0442 \u0437\u0430\u0432\u0435\u0440\u0448\u0451\u043d.");

            // /hm spy
            delay(() -> {
                if (cancelled) { state = State.IDLE; return; }
                msg("\u00a7e[Auto] /hm spy");
                CommandQueue.add("hm spy");
                state = State.REOPENING;
                delay(AutoCallManager.this::reopen, 1500);
            }, 1000);
        }, 500);
    }

    private void reopen() {
        if (cancelled) { state = State.IDLE; return; }
        if (!config.autoCheck) { state = State.IDLE; return; }
        foundAny = false;
        currentNick = null;
        closeReason = CloseReason.PLAYER_AFK;
        msg("\u00a7a[Auto] \u0418\u0449\u0443 \u0441\u043b\u0435\u0434\u0443\u044e\u0449\u0438\u0439...");
        CommandQueue.add("reportlist");
        delay(() -> {
            if (cancelled) { state = State.IDLE; return; }
            state = State.SEARCHING;
            search();
        }, 1500);
    }

    private void msg(String m) {
        if (client.player != null) client.execute(() -> client.player.sendMessage(Text.of(m)));
    }

    private void delay(Runnable r, long ms) {
        Thread t = new Thread(() -> {
            try { Thread.sleep(ms); } catch (InterruptedException e) { return; }
            if (!cancelled) client.execute(r);
        });
        t.setDaemon(true);
        t.start();
    }
}
