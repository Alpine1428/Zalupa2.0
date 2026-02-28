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
        IDLE,
        SEARCHING,
        WAITING_TAKE_MSG,
        DOING_SPY,
        DOING_FIND,
        CONNECTING_SERVER,
        DOING_PLAYTIME,
        CHECKING_PLAYTIME_LOOP,
        WAITING_SPYFRZ,
        CLOSING_REPORT_STEP1,
        CLOSING_REPORT_STEP2,
        CLOSING_REPORT_STEP3,
        REOPENING
    }

    private final ZalupareportClient mod;
    private final ModConfig config;
    private final MinecraftClient client = MinecraftClient.getInstance();

    private final Pattern nickPattern = Pattern.compile("игрока\\s+(\\w+)");
    private final Pattern findPattern = Pattern.compile("Игрок\\s+\\S+\\s+находится на сервере\\s+(\\S+)");
    private final Pattern lastActivityPattern = Pattern.compile("Последняя активность:\\s*(?:(\\d+)\\s*ч\\.,\\s*)?(?:(\\d+)\\s*м\\.,\\s*)?(\\d+)\\s*сек\\.");
    private final Pattern serverLanarchyPattern = Pattern.compile("lanarchy(\\d+)");
    private final Pattern serverL2anarchyPattern = Pattern.compile("l2anarchy(\\d+)");
    private final Pattern serverAnarchyPattern = Pattern.compile("anarchy(\\d+)");

    public State state = State.IDLE;
    private String currentNick = null;
    private long stateStartTime = 0;
    private long playtimeCheckStart = 0;
    private boolean waitingForPlaytimeResponse = false;
    private boolean waitingForFindResponse = false;
    private boolean foundAnyReport = false;

    public AutoCallManager(ZalupareportClient mod) {
        this.mod = mod;
        this.config = mod.config;
    }

    public void reset() {
        state = State.IDLE;
        currentNick = null;
        stateStartTime = 0;
        playtimeCheckStart = 0;
        waitingForPlaytimeResponse = false;
        waitingForFindResponse = false;
        foundAnyReport = false;
    }

    public boolean isActive() {
        return config.autoCall && state != State.IDLE;
    }

    public void startAutoCall() {
        if (!config.autoCall) return;
        state = State.SEARCHING;
        foundAnyReport = false;
        searchAndTakeReport();
    }

    private void searchAndTakeReport() {
        if (client.player == null || client.player.currentScreenHandler == null) {
            sendMsg("Ошибка: нет открытого меню");
            state = State.IDLE;
            return;
        }

        List<ItemStack> items = client.player.currentScreenHandler.getStacks();
        int foundSlot = -1;
        String foundNick = null;

        for (int i = 0; i < Math.min(45, items.size()); i++) {
            ItemStack itemStack = items.get(i);
            if (itemStack.isEmpty() || itemStack.getItem().equals(Items.SKELETON_SKULL)) {
                continue;
            }
            String itemName = itemStack.getName().getString();
            if (itemStack.getNbt() == null || itemStack.getNbt().isEmpty()) continue;
            if (itemStack.getNbt().getCompound("display") == null) continue;

            NbtElement nbt = itemStack.getNbt().getCompound("display").get("Lore");
            if (nbt == null) continue;
            String nbtString = nbt.asString();

            Matcher nick = nickPattern.matcher(itemName);
            String detect = config.detects.stream()
                    .filter(s -> !s.isEmpty() && nbtString.toLowerCase().contains(s.toLowerCase()))
                    .findFirst()
                    .orElse("zalupa");
            boolean isHasDetect = config.detects.isEmpty() || !detect.equals("zalupa");

            if (nick.find() && isHasDetect) {
                foundSlot = i;
                foundNick = nick.group(1);
                break;
            }
        }

        if (foundSlot != -1 && foundNick != null) {
            foundAnyReport = true;
            currentNick = foundNick;
            client.interactionManager.clickSlot(
                    client.player.currentScreenHandler.syncId,
                    foundSlot, 0, SlotActionType.PICKUP, client.player);
            client.keyboard.setClipboard(foundNick);
            sendMsg("Скопировал ник " + foundNick + " и кликнул по слоту с читером нах!");
            state = State.WAITING_TAKE_MSG;
            stateStartTime = System.currentTimeMillis();
            // Сразу переходим к spy через задержку
            scheduleDelayed(() -> {
                state = State.DOING_SPY;
                doSpy();
            }, 1000);
        } else {
            // Проверяем следующую страницу
            boolean hasNextPage = false;
            if (items.size() > 45) {
                ItemStack slot45 = items.get(45);
                if (slot45 != null && !slot45.isEmpty()) {
                    hasNextPage = true;
                }
            }

            if (hasNextPage) {
                // Кликаем слот 53 (54-й слот, индексация с 0)
                client.interactionManager.clickSlot(
                        client.player.currentScreenHandler.syncId,
                        53, 0, SlotActionType.PICKUP, client.player);
                scheduleDelayed(this::searchAndTakeReport, 1000);
            } else {
                if (!foundAnyReport) {
                    if (client.player != null) {
                        client.player.closeHandledScreen();
                    }
                    sendMsg("Репорты не найдены");
                }
                state = State.IDLE;
            }
        }
    }

    public void onChatMessage(String message) {
        if (state == State.IDLE || !config.autoCall) return;

        switch (state) {
            case DOING_FIND -> {
                if (waitingForFindResponse) {
                    Matcher findMatcher = findPattern.matcher(message);
                    if (findMatcher.find()) {
                        waitingForFindResponse = false;
                        String server = findMatcher.group(1);
                        handleServerConnect(server);
                    }
                }
            }
            case DOING_PLAYTIME, CHECKING_PLAYTIME_LOOP -> {
                if (waitingForPlaytimeResponse) {
                    Matcher activityMatcher = lastActivityPattern.matcher(message);
                    if (activityMatcher.find()) {
                        waitingForPlaytimeResponse = false;
                        int hours = activityMatcher.group(1) != null ? Integer.parseInt(activityMatcher.group(1)) : 0;
                        int minutes = activityMatcher.group(2) != null ? Integer.parseInt(activityMatcher.group(2)) : 0;
                        int seconds = Integer.parseInt(activityMatcher.group(3));
                        int totalSeconds = hours * 3600 + minutes * 60 + seconds;
                        handlePlaytimeResult(totalSeconds);
                    }
                }
            }
            case WAITING_SPYFRZ -> {
                String lower = message.toLowerCase();
                if (lower.contains("hm sban") || lower.contains("banip") || lower.contains("hm unfrz")) {
                    state = State.CLOSING_REPORT_STEP1;
                    scheduleDelayed(this::closeReportStep1, 500);
                }
            }
            default -> {}
        }
    }

    private void doSpy() {
        if (currentNick == null) { state = State.IDLE; return; }
        sendCommand("hm spy " + currentNick);
        state = State.DOING_FIND;
        scheduleDelayed(this::doFind, 500);
    }

    private void doFind() {
        if (currentNick == null) { state = State.IDLE; return; }
        waitingForFindResponse = true;
        sendCommand("find " + currentNick);
        scheduleDelayed(() -> {
            if (waitingForFindResponse && state == State.DOING_FIND) {
                waitingForFindResponse = false;
                sendMsg("Не удалось найти сервер игрока " + currentNick);
                state = State.IDLE;
            }
        }, 10000);
    }

    private void handleServerConnect(String server) {
        String command = null;
        Matcher lanarchy = serverLanarchyPattern.matcher(server);
        Matcher l2anarchy = serverL2anarchyPattern.matcher(server);
        Matcher anarchy = serverAnarchyPattern.matcher(server);

        if (lanarchy.find()) {
            command = "ln " + lanarchy.group(1);
        } else if (l2anarchy.find()) {
            command = "ln120 " + l2anarchy.group(1);
        } else if (anarchy.find()) {
            command = "cn " + anarchy.group(1);
        }

        if (command != null) {
            sendCommand(command);
            state = State.CONNECTING_SERVER;
            stateStartTime = System.currentTimeMillis();
            scheduleDelayed(this::doPlaytimeAfterConnect, 10000);
        } else {
            sendMsg("Неизвестный сервер: " + server);
            state = State.IDLE;
        }
    }

    private void doPlaytimeAfterConnect() {
        state = State.DOING_PLAYTIME;
        playtimeCheckStart = System.currentTimeMillis();
        sendPlaytimeCommand();
    }

    private void sendPlaytimeCommand() {
        if (currentNick == null) { state = State.IDLE; return; }
        waitingForPlaytimeResponse = true;
        sendCommand("playtime " + currentNick);
    }

    private void handlePlaytimeResult(int totalSeconds) {
        if (totalSeconds < 7) {
            sendCommand("hm spyfrz");
            state = State.WAITING_SPYFRZ;
            stateStartTime = System.currentTimeMillis();
        } else {
            long elapsed = System.currentTimeMillis() - playtimeCheckStart;
            if (state == State.DOING_PLAYTIME) {
                state = State.CHECKING_PLAYTIME_LOOP;
                playtimeCheckStart = System.currentTimeMillis();
                elapsed = 0;
            }

            if (elapsed < 30000) {
                scheduleDelayed(() -> {
                    if (state == State.CHECKING_PLAYTIME_LOOP) {
                        sendPlaytimeCommand();
                    }
                }, 3000);
            } else {
                sendMsg("Игрок афк проследите сами");
                state = State.CLOSING_REPORT_STEP1;
                scheduleDelayed(this::closeReportStep1, 1000);
            }
        }
    }

    private void closeReportStep1() {
        sendCommand("reportlist");
        state = State.CLOSING_REPORT_STEP2;
        scheduleDelayed(this::closeReportStep2, 1500);
    }

    private void closeReportStep2() {
        if (client.player == null || client.player.currentScreenHandler == null) {
            sendMsg("Ошибка: меню не открылось");
            state = State.IDLE;
            return;
        }
        client.interactionManager.clickSlot(
                client.player.currentScreenHandler.syncId,
                47, 0, SlotActionType.PICKUP, client.player);
        state = State.CLOSING_REPORT_STEP3;
        scheduleDelayed(this::closeReportStep3, 1500);
    }

    private void closeReportStep3() {
        if (client.player == null || client.player.currentScreenHandler == null) {
            sendMsg("Ошибка: второе меню не открылось");
            state = State.IDLE;
            return;
        }
        client.interactionManager.clickSlot(
                client.player.currentScreenHandler.syncId,
                16, 0, SlotActionType.PICKUP, client.player);
        scheduleDelayed(() -> {
            sendChatMessage("-");
            state = State.REOPENING;
            scheduleDelayed(this::reopenReportList, 1500);
        }, 500);
    }

    private void reopenReportList() {
        foundAnyReport = false;
        sendCommand("reportlist");
        scheduleDelayed(() -> {
            state = State.SEARCHING;
            searchAndTakeReport();
        }, 1500);
    }

    private void sendMsg(String msg) {
        if (client.player != null) {
            client.player.sendMessage(Text.of(msg));
        }
    }

    private void sendCommand(String command) {
        if (client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendCommand(command);
        }
    }

    private void sendChatMessage(String message) {
        if (client.player == null) return;
        client.execute(() -> {
            client.setScreen(null);
            ChatScreen screen = new ChatScreen("");
            client.setScreen(screen);
            screen.sendMessage(message, true);
            client.setScreen(null);
        });
    }

    private void scheduleDelayed(Runnable action, long delayMs) {
        Thread thread = new Thread(() -> {
            try { Thread.sleep(delayMs); } catch (InterruptedException e) { return; }
            client.execute(action);
        });
        thread.setDaemon(true);
        thread.start();
    }
}
