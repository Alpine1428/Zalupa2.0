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
        IDLE, SEARCHING, DOING_SPY, DOING_FIND, 
        CONNECTING_SERVER, 
        DOING_PLAYTIME, CHECKING_PLAYTIME_LOOP, WAITING_SPYFRZ,
        CLOSING_STEP1, CLOSING_STEP2, CLOSING_STEP3, REOPENING
    }

    private final ModConfig config;
    private final MinecraftClient client = MinecraftClient.getInstance();

    private final Pattern nickPattern = Pattern.compile("игрока\\s+(\\w+)");
    private final Pattern timePattern = Pattern.compile("(?:(\\d+)\\sч\\.,\\s)?(?:(\\d+)\\sм\\.,\\s)?\\d+\\sсек\\.\\s\\((?:(\\d+)\\sч\\.,\\s)?(?:(\\d+)\\sм\\.,\\s)?\\d+\\sсек\\.\\)");
    
    private final Pattern findPattern = Pattern.compile("Игрок\\s+\\S+\\s+находится на сервере\\s+(\\S+)");
    private final Pattern activityPattern = Pattern.compile("Последняя активность:\\s*(?:(\\d+)\\s*ч\\.,\\s*)?(?:(\\d+)\\s*м\\.,\\s*)?(\\d+)\\s*сек\\.");
    
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

    public boolean isActive() {
        return (config.autoCall || config.autoCheck) && state != State.IDLE;
    }

    public void startAutoCall() {
        if (!config.autoCall && !config.autoCheck) return;
        state = State.SEARCHING;
        foundAny = false;
        search();
    }

    private void search() {
        if (client.player == null || client.player.currentScreenHandler == null) {
            msg("Ошибка: нет меню");
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

            String det = config.detects.stream().filter(s -> !s.isEmpty() && nbtString.toLowerCase().contains(s.toLowerCase())).findFirst().orElse("zalupa");
            if (!config.detects.isEmpty() && det.equals("zalupa")) continue;

            Matcher tm = timePattern.matcher(nbtString);
            if (!tm.find()) continue;
            int allM = getGroup(tm, 1)*60 + getGroup(tm, 2);
            int actM = getGroup(tm, 3)*60 + getGroup(tm, 4);
            ModConfig.PlayTime pt = config.playTime;
            boolean all = allM < pt.allTime, last = actM < pt.activeTime;
            
            boolean activeCheck = pt.checkActiveTime && !pt.checkAllTime;
            boolean allCheck = !pt.checkActiveTime && pt.checkAllTime;
            boolean bothCheck = pt.checkActiveTime && pt.checkAllTime;

            if ((activeCheck && !last) || (allCheck && !all) || (bothCheck && (!all || !last))) continue;

            slot = i;
            nick = n;
            break;
        }

        if (slot != -1 && nick != null) {
            foundAny = true;
            currentNick = nick;
            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, client.player);
            client.keyboard.setClipboard(nick);
            msg("Взял репорт на " + nick);
            
            state = State.DOING_SPY;
            delay(this::doSpy, 1000);
        } else {
            boolean next = items.size() > 45 && items.get(45) != null && !items.get(45).isEmpty();
            if (next) {
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, 53, 0, SlotActionType.PICKUP, client.player);
                delay(this::search, 1000);
            } else {
                if (!foundAny) {
                    if (client.player != null) client.player.closeHandledScreen();
                    msg("Репорты не найдены");
                }
                state = State.IDLE;
            }
        }
    }

    private int getGroup(Matcher m, int g) { return m.group(g)==null?0:Integer.parseInt(m.group(g)); }

    public void onChatMessage(String message) {
        if (state == State.IDLE) return;

        if (state == State.DOING_FIND && waitFind) {
            Matcher m = findPattern.matcher(message);
            if (m.find()) {
                waitFind = false;
                connect(m.group(1));
            }
        }

        if (config.autoCheck && (state == State.DOING_PLAYTIME || state == State.CHECKING_PLAYTIME_LOOP) && waitPt) {
            if (message.contains("Последняя активность")) {
                Matcher m = activityPattern.matcher(message);
                if (m.find()) {
                    waitPt = false;
                    int h = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
                    int min = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
                    int sec = Integer.parseInt(m.group(3));
                    int totalSec = h * 3600 + min * 60 + sec;
                    handlePt(totalSec);
                }
            }
        }

        if (config.autoCheck && state == State.WAITING_SPYFRZ) {
            String lower = message.toLowerCase();
            if (lower.contains("/hm sban") || lower.contains("/banip") || 
                lower.contains("/hm unfrz") || lower.contains("/hm unfreezing") ||
                lower.contains("hm sban") || lower.contains("banip")) {
                
                msg("Обнаружена команда бана/разбана. Завершаю репорт...");
                state = State.CLOSING_STEP1;
                delay(this::close1, 1000);
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
                msg("Не нашел сервер для " + currentNick);
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
            
            if (config.autoCall) {
                msg("Перешел на сервер. АвтоВызов завершен.");
                state = State.IDLE;
                return;
            }

            if (config.autoCheck) {
                state = State.CONNECTING_SERVER;
                delay(this::startPt, 10000);
            }
        } else {
            msg("Неизвестный сервер: " + srv);
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
            msg("Игрок активен! Заморозил. Жду бана/разбана...");
        } else {
            if (state == State.DOING_PLAYTIME) {
                state = State.CHECKING_PLAYTIME_LOOP;
                ptStart = System.currentTimeMillis();
            }
            
            if (System.currentTimeMillis() - ptStart < 30000) {
                delay(() -> {
                    if (state == State.CHECKING_PLAYTIME_LOOP) sendPt();
                }, 3000);
            } else {
                msg("Игрок афк. Закрываю репорт...");
                state = State.CLOSING_STEP1;
                delay(this::close1, 1000);
            }
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
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, 47, 0, SlotActionType.PICKUP, client.player);
        state = State.CLOSING_STEP3;
        delay(this::close3, 1500);
    }

    private void close3() {
        if (client.player == null || client.player.currentScreenHandler == null) {
            state = State.IDLE; return;
        }
        client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, 16, 0, SlotActionType.PICKUP, client.player);
        
        delay(() -> {
            chat("-");
            state = State.REOPENING;
            delay(this::reopen, 1500);
        }, 500);
    }

    private void reopen() {
        if (!config.autoCheck) { state = State.IDLE; return; }
        foundAny = false;
        cmd("reportlist");
        delay(() -> {
            state = State.SEARCHING;
            search();
        }, 1500);
    }

    private void msg(String m) { if (client.player != null) client.player.sendMessage(Text.of(m)); }
    private void cmd(String c) { if (client.getNetworkHandler() != null) client.getNetworkHandler().sendCommand(c); }
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
