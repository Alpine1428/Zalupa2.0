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

    private final Pattern nickPattern = Pattern.compile("игрока\\s+(\\w+)");
    private final Pattern timePattern = Pattern.compile("(?:(\\d+)\\sч\\.,\\s)?(?:(\\d+)\\sм\\.,\\s)?\\d+\\sсек\\.\\s\\((?:(\\d+)\\sч\\.,\\s)?(?:(\\d+)\\sм\\.,\\s)?\\d+\\sсек\\.\\)");
    private final ModConfig config;
    public boolean reportScreen;
    private final MinecraftClient client = MinecraftClient.getInstance();
    public final ChiteriList list;

    public ZalupaManager(ZalupareportClient zalupareportClient) {
        this.config = zalupareportClient.config;
        this.list = new ChiteriList(config.x, config.y, 120, config.height);
    }

    public void checkReports() {
        boolean found = false;
        List<ItemStack> items = client.player.currentScreenHandler.getStacks();
        for (int i = 0; i < 45; i++) {
            ItemStack itemStack = items.get(i);
            if (itemStack.getItem().equals(Items.SKELETON_SKULL)) {
                continue;
            }
            String itemName = itemStack.getName().getString();
            if (itemStack.getNbt() == null || itemStack.getNbt().isEmpty()
                    || itemStack.getNbt().getCompound("display") == null
                    || itemStack.getNbt().getCompound("display").getString("Lore") == null) {
                continue;
            }
            NbtElement nbt = itemStack.getNbt().getCompound("display").get("Lore");
            if (nbt == null || nbt.asString() == null) {
                continue;
            }
            String nbtString = nbt.asString();
            Matcher nick = nickPattern.matcher(itemName);
            String detect = config.detects.stream()
                    .filter(string -> !string.isEmpty() && nbtString.toLowerCase().contains(string.toLowerCase()))
                    .findFirst()
                    .orElse("zalupa");
            boolean isHasDetect = config.detects.isEmpty() || !detect.equals("zalupa");
            if (nick.find() && isHasDetect) {
                String nickName = nick.group(1);
                Matcher timeMatcher = this.timePattern.matcher(nbtString);
                if (timeMatcher.find()) {
                    int allHours = this.getTimeFromPattern(timeMatcher, 1);
                    int allMinutes = this.getTimeFromPattern(timeMatcher, 2);
                    int lastActiveHours = this.getTimeFromPattern(timeMatcher, 3);
                    int lastActiveMinutes = this.getTimeFromPattern(timeMatcher, 4);
                    int allMinutesTotal = allHours * 60 + allMinutes;
                    int lastActiveMinutesTotal = lastActiveHours * 60 + lastActiveMinutes;
                    ModConfig.PlayTime playTime = config.playTime;
                    boolean all = allMinutesTotal < playTime.allTime;
                    boolean lastActive = lastActiveMinutesTotal < playTime.activeTime;
                    boolean checkOnlyActive = playTime.checkActiveTime && !playTime.checkAllTime;
                    boolean checkOnlyAll = !playTime.checkActiveTime && playTime.checkAllTime;
                    boolean checkAll = (playTime.checkActiveTime && playTime.checkAllTime) && (!all || !lastActive);
                    if ((checkOnlyActive && !lastActive) || (checkOnlyAll && !all) || checkAll) {
                        continue;
                    }
                    ChiteriList.ItemEntry entry = new ChiteriList.ItemEntry(new SlotSChiterom(nickName, detect));
                    if (!this.list.contains(entry)) {
                        this.list.addEntry(entry);
                    }
                    if (!found) {
                        found = true;
                    }
                }
            }
        }
        if (!found) {
            this.sendMsg("Ничего не нашел");
        }
    }

    private int getTimeFromPattern(Matcher matcher, int group) {
        return matcher.group(group) == null ? 0 : Integer.parseInt(matcher.group(group));
    }

    public void resetAll() {
        this.reportScreen = false;
        this.list.clear();
        this.list.updateHeight();
    }

    public void sendMsg(String msg) {
        client.player.sendMessage(Text.of(msg));
    }

    public boolean clickPoChiteru(SlotSChiterom slotSChiterom) {
        client.keyboard.setClipboard(slotSChiterom.nickName());
        int slotChit = this.slotChitera(slotSChiterom.nickName());
        if (slotChit != -1) {
            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId,
                    slotChit, 0, SlotActionType.PICKUP, client.player);
            if (config.printPlayTime) {
                client.getNetworkHandler().sendCommand("playtime " + slotSChiterom.nickName());
            }
            this.sendMsg("Скопировал ник " + slotSChiterom.nickName() + " и кликнул по слоту с читером нах!");
            if (config.autoSpy) {
                this.spy(slotSChiterom.nickName());
            }
            return true;
        } else {
            this.sendMsg("Не нашел слот с этим ником");
        }
        return false;
    }

    private int slotChitera(String chiter) {
        List<ItemStack> items = client.player.currentScreenHandler.getStacks();
        for (int i = 0; i < 45; i++) {
            String itemName = items.get(i).getName().getString();
            Matcher nick = nickPattern.matcher(itemName);
            if (nick.find() && nick.group(1).equals(chiter)) {
                return i;
            }
        }
        return -1;
    }

    public void closeScreen() {
        if (this.reportScreen) {
            this.reportScreen = false;
            config.x = list.getX();
            config.y = list.getY();
            if (config.clearIsClose) {
                this.resetAll();
            }
            this.list.resetAnim();
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
