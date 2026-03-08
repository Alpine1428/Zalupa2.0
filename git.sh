#!/bin/bash

# git.sh — применяет изменения и пушит на GitHub

cd "$(dirname "$0")"

# ===== 1. Обновляем AutoCallManager.java =====
cat > "src/main/java/me/zyouime/zalupareport/manager/AutoCallManager.java" << 'JAVAEOF'
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
        CHECKING_PLAYTIME_LOOP,
        WAITING_SPYFRZ,
        WAITING_REPORT_OPEN,
        CLOSING_STEP1,
        CLOSING_STEP2,
        CLOSING_STEP3,
        CLOSING_STEP4,
        REOPENING
    }

    public enum CloseReason {
        PLAYER_AFK,
        PLAYER_BANNED,
        PLAYER_UNFROZEN,
        PLAYER_AFK_SERVER,
        PLAYER_OFFLINE
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
    private final Pattern banIncoming = Pattern.compile("(?i)(?:hm sban|banip)");
    private final Pattern banOutgoing = Pattern.compile("(?i)^/(?:hm sban|banip)");

    private final Pattern unfrzIncoming = Pattern.compile("(?i)(?:hm unfrz|hm unfreezing)");
    private final Pattern unfrzOutgoing = Pattern.compile("(?i)^/(?:hm unfrz|hm unfreezing)");

    private final Pattern openReportPattern = Pattern.compile("\u00a7b\u041e\u0442\u043a\u0440\u044b\u0432\u0430\u0435\u043c \u0440\u0435\u043f\u043e\u0440\u0442");
    private final Pattern afkServerPattern = Pattern.compile("\u00a7c\u0418\u0433\u0440\u043e\u043a \u0430\u0444\u043a");

    // Паттерн для определения оффлайн-сессии: "Текущая сессия: (Оффлайн)"
    private final Pattern offlineSessionPattern = Pattern.compile("\u0422\u0435\u043a\u0443\u0449\u0430\u044f \u0441\u0435\u0441\u0441\u0438\u044f:.*\\(\u041e\u0444\u0444\u043b\u0430\u0439\u043d\\)");

    public State state = State.IDLE;
    private String currentNick;
    private long playtimeLoopStartTime;
    private boolean waitFind;
    private boolean waitPt;
    private boolean foundAny;
    private volatile boolean cancelled = false;
    private boolean scanningPlaytime = false;
    private CloseReason closeReason = CloseReason.PLAYER_AFK;
    private int playtimeCheckCount = 0;

    private boolean ignoreNextSpyfrz = false;
    private long spyfrzSentTime = 0;
    private static final long SPYFRZ_IGNORE_WINDOW_MS = 3000;

    public AutoCallManager(ZalupareportClient mod) { config = mod.config; }

    public void reset() {
        cancelled = true;
        state = State.IDLE;
        currentNick = null;
        playtimeLoopStartTime = 0;
        waitFind = false;
        waitPt = false;
        foundAny = false;
        scanningPlaytime = false;
        closeReason = CloseReason.PLAYER_AFK;
        playtimeCheckCount = 0;
        ignoreNextSpyfrz = false;
        spyfrzSentTime = 0;
        CommandQueue.clear();
    }

    public boolean isActive() { return (config.autoCall || config.autoCheck) && state != State.IDLE; }
    public boolean isScanningPlaytime() { return scanningPlaytime; }

    public void startAutoCall() {
        if (!config.autoCall && !config.autoCheck) return;
        cancelled = false;
        state = State.SEARCHING;
        foundAny = false;
        search();
    }

    // ==================== ПОИСК В МЕНЮ ====================

    private void search() {
        if (cancelled) { state = State.IDLE; return; }
        if (client.player == null || client.player.currentScreenHandler == null) {
            msg("\u00a7c[Auto] \u041e\u0448\u0438\u0431\u043a\u0430: \u043c\u0435\u043d\u044e \u0437\u0430\u043a\u0440\u044b\u0442\u043e");
            state = State.IDLE; return;
        }
        List<ItemStack> items = client.player.currentScreenHandler.getStacks();
        int slot = -1; String nick = null;
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
            boolean allOk = allM < pt.allTime, lastOk = actM < pt.activeTime;
            if (pt.checkActiveTime && !pt.checkAllTime && !lastOk) continue;
            if (!pt.checkActiveTime && pt.checkAllTime && !allOk) continue;
            if (pt.checkActiveTime && pt.checkAllTime && (!allOk || !lastOk)) continue;
            slot = i; nick = n; break;
        }
        if (slot != -1 && nick != null) {
            foundAny = true; currentNick = nick.trim();
            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, client.player);
            client.keyboard.setClipboard(currentNick);
            msg("\u00a7a[Auto] \u0412\u0437\u044f\u043b: \u00a7e" + currentNick);
            state = State.DOING_SPY; delay(this::doSpy, 1000);
        } else {
            boolean hasNext = items.size() > 53 && items.get(53) != null && !items.get(53).isEmpty();
            if (hasNext) {
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, 53, 0, SlotActionType.PICKUP, client.player);
                delay(this::search, 1000);
            } else {
                if (!foundAny) {
                    if (client.player != null) client.player.closeHandledScreen();
                    msg("\u00a7c[Auto] \u041d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d\u043e");
                }
                state = State.IDLE;
            }
        }
    }

    private int getGroup(Matcher m, int g) { return m.group(g) == null ? 0 : Integer.parseInt(m.group(g)); }

    // ==================== ОБРАБОТКА ВХОДЯЩЕГО ЧАТА ====================

    public void onChatMessage(String message) {
        if (state == State.IDLE || cancelled) return;

        // Ответ на /find
        if (state == State.DOING_FIND && waitFind) {
            Matcher m = findPattern.matcher(message);
            if (m.find()) {
                waitFind = false;
                connect(m.group(1).trim().toLowerCase());
                return;
            }
        }

        // Проверка оффлайн-сессии во время проверки плейтайма
        if (config.autoCheck && state == State.CHECKING_PLAYTIME_LOOP) {
            if (offlineSessionPattern.matcher(message).find()) {
                waitPt = false;
                scanningPlaytime = false;
                msg("\u00a7c[Auto] \u0418\u0433\u0440\u043e\u043a \u043e\u0444\u0444\u043b\u0430\u0439\u043d! \u0417\u0430\u043a\u0440\u044b\u0432\u0430\u044e \u0440\u0435\u043f\u043e\u0440\u0442...");
                closeReason = CloseReason.PLAYER_OFFLINE;
                state = State.CLOSING_STEP1;
                delay(this::closeStep1, 1000);
                return;
            }
        }

        // Ответ на /playtime
        if (config.autoCheck && state == State.CHECKING_PLAYTIME_LOOP && waitPt) {
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

        // "Открываем репорт" в WAITING_SPYFRZ
        if (config.autoCheck && state == State.WAITING_SPYFRZ) {
            if (openReportPattern.matcher(message).find()) {
                msg("\u00a7e[Auto] \u0420\u0435\u043f\u043e\u0440\u0442 \u043e\u0442\u043a\u0440\u044b\u0442. \u041e\u0442\u0441\u043b\u0435\u0436\u0438\u0432\u0430\u044e...");
                state = State.WAITING_REPORT_OPEN;
                return;
            }
        }

        // Отслеживание бана/анфриза/афк сервера ТОЛЬКО в WAITING_REPORT_OPEN и WAITING_SPYFRZ
        if (config.autoCheck && (state == State.WAITING_REPORT_OPEN || state == State.WAITING_SPYFRZ)) {

            if (afkServerPattern.matcher(message).find()) {
                msg("\u00a7e[Auto] \u0421\u0435\u0440\u0432\u0435\u0440: \u0430\u0444\u043a. \u0417\u0430\u0432\u0435\u0440\u0448\u0430\u044e...");
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
                if (!isSpyfrzEcho()) {
                    msg("\u00a7e[Auto] \u0410\u043d\u0444\u0440\u0438\u0437. \u0417\u0430\u0432\u0435\u0440\u0448\u0430\u044e...");
                    closeReason = CloseReason.PLAYER_UNFROZEN;
                    state = State.CLOSING_STEP1;
                    delay(this::closeStep1, 1000);
                    return;
                }
            }
        }
    }

    // ==================== ОБРАБОТКА ИСХОДЯЩИХ КОМАНД ====================

    public void onOutgoingCommand(String command) {
        if (state == State.IDLE || cancelled) return;

        if (command.toLowerCase().contains("hm spyfrz")) {
            ignoreNextSpyfrz = true;
            spyfrzSentTime = System.currentTimeMillis();
            return;
        }

        if (config.autoCheck && (state == State.WAITING_SPYFRZ || state == State.WAITING_REPORT_OPEN)) {
            if (banOutgoing.matcher(command).find()) {
                msg("\u00a7a[Auto] \u0411\u0430\u043d. \u0417\u0430\u0432\u0435\u0440\u0448\u0430\u044e...");
                closeReason = CloseReason.PLAYER_BANNED;
                state = State.CLOSING_STEP1;
                delay(this::closeStep1, 2000);
                return;
            }
            if (unfrzOutgoing.matcher(command).find()) {
                msg("\u00a7e[Auto] \u0410\u043d\u0444\u0440\u0438\u0437. \u0417\u0430\u0432\u0435\u0440\u0448\u0430\u044e...");
                closeReason = CloseReason.PLAYER_UNFROZEN;
                state = State.CLOSING_STEP1;
                delay(this::closeStep1, 2000);
                return;
            }
        }
    }

    private boolean isSpyfrzEcho() {
        if (ignoreNextSpyfrz && (System.currentTimeMillis() - spyfrzSentTime) < SPYFRZ_IGNORE_WINDOW_MS) {
            ignoreNextSpyfrz = false;
            return true;
        }
        return false;
    }

    // ==================== SPY & FIND ====================

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

    // ==================== ПОДКЛЮЧЕНИЕ ====================

    private void connect(String srv) {
        if (cancelled) { state = State.IDLE; return; }
        String command = null;
        Matcher m2 = l2anarchyP.matcher(srv), m1 = lanarchyP.matcher(srv), m3 = anarchyP.matcher(srv);
        if (m2.matches()) { String idx = m2.group(1); command = "ln120 " + (idx == null || idx.isEmpty() ? "1" : idx); }
        else if (m1.matches()) { String idx = m1.group(1); command = "ln " + (idx == null || idx.isEmpty() ? "1" : idx); }
        else if (m3.matches()) { String idx = m3.group(1); command = "cn " + (idx == null || idx.isEmpty() ? "1" : idx); }
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
            msg("\u00a7a[Auto] \u0417\u0430\u0432\u0435\u0440\u0448\u0435\u043d.");
            state = State.IDLE;
            return;
        }

        if (config.autoCheck) {
            state = State.CONNECTING_SERVER;
            msg("\u00a7e[Auto] \u041f\u043e\u0434\u043a\u043b\u044e\u0447\u0435\u043d\u0438\u0435, \u0436\u0434\u0443 10\u0441...");
            delay(this::startPlaytimeLoop, 10000);
        }
    }

    // ==================== ПРОВЕРКА ПЛЕЙТАЙМА (30 СЕК ЦИКЛ) ====================

    private void startPlaytimeLoop() {
        if (cancelled) { state = State.IDLE; return; }
        state = State.CHECKING_PLAYTIME_LOOP;
        playtimeLoopStartTime = System.currentTimeMillis();
        playtimeCheckCount = 0;
        scanningPlaytime = true;
        ignoreNextSpyfrz = false;
        msg("\u00a7e[Auto] \u041d\u0430\u0447\u0438\u043d\u0430\u044e \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0443 \u0430\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u0438 (30\u0441)...");
        sendPlaytimeRequest();
    }

    private void sendPlaytimeRequest() {
        if (cancelled || currentNick == null) {
            scanningPlaytime = false;
            state = State.IDLE;
            return;
        }
        waitPt = true;
        playtimeCheckCount++;
        CommandQueue.add("playtime " + currentNick);
    }

    private void handlePlaytimeResult(int lastActivitySec) {
        if (cancelled) {
            scanningPlaytime = false;
            state = State.IDLE;
            return;
        }

        if (state != State.CHECKING_PLAYTIME_LOOP) return;

        long elapsed = System.currentTimeMillis() - playtimeLoopStartTime;

        if (lastActivitySec < 7) {
            scanningPlaytime = false;
            msg("\u00a7a[Auto] \u0418\u0433\u0440\u043e\u043a \u0430\u043a\u0442\u0438\u0432\u0435\u043d! (" + lastActivitySec
                + "\u0441, \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0430 #" + playtimeCheckCount
                + ", " + (elapsed / 1000) + "\u0441 \u043f\u0440\u043e\u0448\u043b\u043e)");
            msg("\u00a7a[Auto] /hm spyfrz \u2014 \u0436\u0434\u0443 \u0440\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442 \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0438...");

            ignoreNextSpyfrz = true;
            spyfrzSentTime = System.currentTimeMillis();

            CommandQueue.add("hm spyfrz");

            state = State.WAITING_SPYFRZ;

        } else {
            msg("\u00a7e[Auto] \u0410\u0424\u041a (" + lastActivitySec
                + "\u0441, \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0430 #" + playtimeCheckCount
                + ", " + (elapsed / 1000) + "\u0441/30\u0441)");

            if (elapsed < 30000) {
                delay(() -> {
                    if (state == State.CHECKING_PLAYTIME_LOOP && !cancelled) {
                        sendPlaytimeRequest();
                    }
                }, 3000);
            } else {
                scanningPlaytime = false;
                msg("\u00a7c[Auto] \u0418\u0433\u0440\u043e\u043a \u0431\u044b\u043b \u0410\u0424\u041a \u0432\u0441\u0435 30\u0441 ("
                    + playtimeCheckCount + " \u043f\u0440\u043e\u0432\u0435\u0440\u043e\u043a). \u0417\u0430\u043a\u0440\u044b\u0432\u0430\u044e...");
                closeReason = CloseReason.PLAYER_AFK;
                state = State.CLOSING_STEP1;
                delay(this::closeStep1, 1000);
            }
        }
    }

    // ==================== ЗАКРЫТИЕ РЕПОРТА ====================

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
            msg("\u00a7c[Auto] \u041e\u0448\u0438\u0431\u043a\u0430 step2");
            state = State.IDLE;
            return;
        }
        msg("\u00a7e[Auto] \u041a\u043b\u0438\u043a \u0441\u043b\u043e\u0442 47");
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, 46, 0, SlotActionType.PICKUP, client.player);
        state = State.CLOSING_STEP3;
        delay(this::closeStep3, 1000);
    }

    private void closeStep3() {
        if (cancelled) { state = State.IDLE; return; }
        if (client.player == null || client.player.currentScreenHandler == null) {
            msg("\u00a7c[Auto] \u041e\u0448\u0438\u0431\u043a\u0430 step3");
            state = State.IDLE;
            return;
        }
        int slotIndex;
        String slotName;
        switch (closeReason) {
            case PLAYER_BANNED:
                slotIndex = 15; slotName = "16"; break;
            case PLAYER_AFK: case PLAYER_AFK_SERVER: case PLAYER_UNFROZEN: case PLAYER_OFFLINE: default:
                slotIndex = 13; slotName = "14"; break;
        }
        msg("\u00a7e[Auto] \u041a\u043b\u0438\u043a \u0441\u043b\u043e\u0442 " + slotName + " (" + closeReason.name() + ")");
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slotIndex, 0, SlotActionType.PICKUP, client.player);
        state = State.CLOSING_STEP4;
        delay(this::closeStep4, 1000);
    }

    private void closeStep4() {
        if (cancelled) { state = State.IDLE; return; }

        client.execute(() -> {
            if (client.player != null && client.currentScreen != null) {
                client.player.closeHandledScreen();
            }
        });

        delay(() -> {
            if (cancelled) { state = State.IDLE; return; }

            String chatMsg;
            switch (closeReason) {
                case PLAYER_AFK:
                case PLAYER_AFK_SERVER:
                case PLAYER_OFFLINE:
                    chatMsg = "afk";
                    break;
                case PLAYER_BANNED:
                case PLAYER_UNFROZEN:
                default:
                    chatMsg = "-";
                    break;
            }

            msg("\u00a7e[Auto] \u041e\u0442\u043f\u0440\u0430\u0432\u043a\u0430 \u0432 \u0447\u0430\u0442: '" + chatMsg + "'");

            final String toSend = chatMsg;
            client.execute(() -> {
                if (client.getNetworkHandler() != null) {
                    client.getNetworkHandler().sendChatMessage(toSend);
                }
            });

            msg("\u00a7a[Auto] \u0420\u0435\u043f\u043e\u0440\u0442 \u0437\u0430\u043a\u0440\u044b\u0442.");

            delay(() -> {
                if (cancelled) { state = State.IDLE; return; }
                msg("\u00a7e[Auto] /hm spy (\u0441\u043d\u044f\u0442\u0438\u0435)");
                CommandQueue.add("hm spy");
                state = State.REOPENING;
                delay(AutoCallManager.this::reopen, 1500);
            }, 1500);
        }, 800);
    }

    // ==================== СЛЕДУЮЩИЙ РЕПОРТ ====================

    private void reopen() {
        if (cancelled) { state = State.IDLE; return; }
        if (!config.autoCheck) { state = State.IDLE; return; }
        foundAny = false;
        currentNick = null;
        closeReason = CloseReason.PLAYER_AFK;
        playtimeCheckCount = 0;
        ignoreNextSpyfrz = false;
        msg("\u00a7a[Auto] \u0421\u043b\u0435\u0434\u0443\u044e\u0449\u0438\u0439 \u0440\u0435\u043f\u043e\u0440\u0442...");
        CommandQueue.add("reportlist");
        delay(() -> {
            if (cancelled) { state = State.IDLE; return; }
            state = State.SEARCHING;
            search();
        }, 1500);
    }

    // ==================== УТИЛИТЫ ====================

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
JAVAEOF

# ===== 2. Обновляем ChatMessageMixin.java =====
cat > "src/main/java/me/zyouime/zalupareport/mixin/ChatMessageMixin.java" << 'JAVAEOF'
package me.zyouime.zalupareport.mixin;

import me.zyouime.zalupareport.client.ZalupareportClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatMessageMixin {

    @Inject(
        method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onAddMessage(Text message, MessageSignatureData sig, MessageIndicator ind, CallbackInfo ci) {
        ZalupareportClient inst = ZalupareportClient.getInstance();
        if (inst == null || inst.autoCallManager == null) return;

        String msg = message.getString();

        inst.autoCallManager.onChatMessage(msg);

        if (inst.autoCallManager.isScanningPlaytime()) {
            if (isPlaytimeMessage(msg)) {
                ci.cancel();
            }
        }
    }

    private boolean isPlaytimeMessage(String msg) {
        if (msg.contains("------------------PlayTimeAPI------------------")) return true;
        if (msg.contains("---------------------------------------------------")) return true;
        if (msg.startsWith("\u0410\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u044c ")) return true;
        if (msg.contains("\u041e\u0431\u0449\u0435\u0435 \u0432\u0440\u0435\u043c\u044f \u0432 \u0438\u0433\u0440\u0435:")) return true;
        if (msg.contains("\u041e\u0431\u0449\u0435\u0435 \u0432\u0440\u0435\u043c\u044f \u0430\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u0438 \u0432 \u0438\u0433\u0440\u0435:")) return true;
        if (msg.contains("\u0422\u0435\u043a\u0443\u0449\u0430\u044f \u0441\u0435\u0441\u0441\u0438\u044f:")) return true;
        if (msg.contains("\u0412\u0440\u0435\u043c\u044f \u0431\u0435\u0437\u0434\u0435\u0439\u0441\u0442\u0432\u0438\u044f:")) return true;
        if (msg.contains("\u041f\u043e\u0441\u043b\u0435\u0434\u043d\u044f\u044f \u0430\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u044c:")) return true;
        if (msg.contains("\u041f\u043e\u0441\u043b\u0435\u0434\u043d\u0438\u0439 \u0432\u0445\u043e\u0434 \u043d\u0430 \u0430\u043d\u0430\u0440\u0445\u0438\u044e:")) return true;
        if (msg.trim().isEmpty()) return true;
        return false;
    }
}
JAVAEOF

# ===== 3. Git операции =====
echo "=== Инициализация git (если нужно) ==="
if [ ! -d ".git" ]; then
    git init
    git remote add origin https://github.com/Alpine1428/Zalupa2.0.git
fi

echo "=== Добавляем изменения ==="
git add -A

echo "=== Коммит ==="
git commit -m "fix: handle offline session during playtime check - close report with afk

- Added PLAYER_OFFLINE to CloseReason enum
- Added offlineSessionPattern to detect 'Текущая сессия: (Оффлайн)'
- When offline detected during CHECKING_PLAYTIME_LOOP: stop playtime scan, close report (slot 14 + chat 'afk')
- PLAYER_OFFLINE mapped to slot 13 (index) = slot 14 and chat message 'afk'"

echo "=== Пуш на GitHub ==="
git branch -M main
git push -u origin main

echo "=== Готово! ==="