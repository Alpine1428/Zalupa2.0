package me.zyouime.zalupareport.manager;

import me.shedaniel.autoconfig.AutoConfig;
import me.zyouime.zalupareport.client.ZalupareportClient;
import me.zyouime.zalupareport.config.ModConfig;
import me.zyouime.zalupareport.hud.impl.ChiteriList;
import me.zyouime.zalupareport.util.SlotSChiterom;
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

public class ZalupaManager {
    private final Pattern nickPattern = Pattern.compile("\u0438\u0433\u0440\u043e\u043a\u0430\\s+(\\w+)");
    private final Pattern timePattern = Pattern.compile("(?:(\\d+)\\s\u0447\\.,\\s)?(?:(\\d+)\\s\u043c\\.,\\s)?\\d+\\s\u0441\u0435\u043a\\.\\s\\((?:(\\d+)\\s\u0447\\.,\\s)?(?:(\\d+)\\s\u043c\\.,\\s)?\\d+\\s\u0441\u0435\u043a\\.\\)");
    private final ModConfig config;
    public boolean reportScreen;
    private final MinecraftClient client = MinecraftClient.getInstance();
    public final ChiteriList list;

    public ZalupaManager(ZalupareportClient z) {
        config = z.config;
        list = new ChiteriList(config.x, config.y, 120, config.height);
    }

    public void checkReports() {
        boolean found = false;
        List<ItemStack> items = client.player.currentScreenHandler.getStacks();
        for (int i = 0; i < 45; i++) {
            ItemStack is = items.get(i);
            if (is.getItem().equals(Items.SKELETON_SKULL)) continue;
            String itemName = is.getName().getString();
            if (is.getNbt() == null || is.getNbt().isEmpty()) continue;
            if (is.getNbt().getCompound("display") == null) continue;
            NbtElement nbt = is.getNbt().getCompound("display").get("Lore");
            if (nbt == null || nbt.asString() == null) continue;
            String nbtString = nbt.asString();
            Matcher nick = nickPattern.matcher(itemName);
            String detect = config.detects.stream()
                    .filter(s -> !s.isEmpty() && nbtString.toLowerCase().contains(s.toLowerCase()))
                    .findFirst().orElse("zalupa");
            boolean hasDetect = config.detects.isEmpty() || !detect.equals("zalupa");
            if (nick.find() && hasDetect) {
                String nickName = nick.group(1);
                Matcher tm = timePattern.matcher(nbtString);
                if (tm.find()) {
                    int aH = gtp(tm,1), aM = gtp(tm,2), lH = gtp(tm,3), lM = gtp(tm,4);
                    int allMin = aH*60+aM, lastMin = lH*60+lM;
                    ModConfig.PlayTime pt = config.playTime;
                    boolean all = allMin < pt.allTime, last = lastMin < pt.activeTime;
                    boolean onlyActive = pt.checkActiveTime && !pt.checkAllTime;
                    boolean onlyAll = !pt.checkActiveTime && pt.checkAllTime;
                    boolean both = pt.checkActiveTime && pt.checkAllTime && (!all || !last);
                    if ((onlyActive && !last) || (onlyAll && !all) || both) continue;
                    ChiteriList.ItemEntry entry = new ChiteriList.ItemEntry(new SlotSChiterom(nickName, detect));
                    if (!list.contains(entry)) list.addEntry(entry);
                    if (!found) found = true;
                }
            }
        }
        if (!found) sendMsg("\u041d\u0438\u0447\u0435\u0433\u043e \u043d\u0435 \u043d\u0430\u0448\u0435\u043b");
    }

    private int gtp(Matcher m, int g) { return m.group(g) == null ? 0 : Integer.parseInt(m.group(g)); }
    public void resetAll() { reportScreen = false; list.clear(); list.updateHeight(); }
    public void sendMsg(String msg) { client.player.sendMessage(Text.of(msg)); }

    public boolean clickPoChiteru(SlotSChiterom s) {
        client.keyboard.setClipboard(s.nickName());
        int slot = slotChitera(s.nickName());
        if (slot != -1) {
            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, slot, 0, SlotActionType.PICKUP, client.player);
            if (config.printPlayTime) client.getNetworkHandler().sendCommand("playtime " + s.nickName());
            sendMsg("\u0421\u043a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u043b \u043d\u0438\u043a " + s.nickName() + " \u0438 \u043a\u043b\u0438\u043a\u043d\u0443\u043b \u043f\u043e \u0441\u043b\u043e\u0442\u0443 \u0441 \u0447\u0438\u0442\u0435\u0440\u043e\u043c \u043d\u0430\u0445!");
            if (config.autoSpy) spy(s.nickName());
            return true;
        }
        sendMsg("\u041d\u0435 \u043d\u0430\u0448\u0435\u043b \u0441\u043b\u043e\u0442");
        return false;
    }

    private int slotChitera(String chiter) {
        List<ItemStack> items = client.player.currentScreenHandler.getStacks();
        for (int i = 0; i < 45; i++) {
            Matcher nick = nickPattern.matcher(items.get(i).getName().getString());
            if (nick.find() && nick.group(1).equals(chiter)) return i;
        }
        return -1;
    }

    public void closeScreen() {
        if (reportScreen) {
            reportScreen = false;
            config.x = list.getX(); config.y = list.getY();
            if (config.clearIsClose) resetAll();
            list.resetAnim();
            AutoConfig.getConfigHolder(ModConfig.class).save();
        }
    }

    private void spy(String nick) {
        client.setScreen(null);
        ChatScreen screen = new ChatScreen("");
        client.setScreen(screen);
        screen.sendMessage("/hm spy " + nick, false);
        client.setScreen(null);
    }
}
