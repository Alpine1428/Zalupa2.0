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
        CLOSING_STEP1,
        CLOSING_STEP2,
        CLOSING_STEP3,
        REOPENING
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
        "\u041f\u043e\u0441\u043b\u0435\u0434\u043d\u044f\u044f \u0430\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u044c:\\s*(?:(\\d+)\\s*\u0447\\.?,\\s*)?(?:(\\d+)\\s*\u043c\\.?,\\s*)?(\\d+)\\s*\u0441\u0435\u043a\\."
    );

    // Порядок важен: l2anarchy проверяется раньше lanarchy
    private final Pattern l2anarchyP = Pattern.compile("^l2anarchy(\\d*)$");
    private final Pattern lanarchyP = Pattern.compile("^lanarchy(\\d*)$");
    private final Pattern anarchyP = Pattern.compile("^anarchy(\\d*)$");

    private final Pattern banPattern = Pattern.compile(
        "(?i)(?:/hm sban|/banip|/hm unfrz|/hm unfreezing|hm sban|banip)"
    );

    public State state = State.IDLE;
    private String currentNick;
    private long ptStart;
    private boolean waitFind;
    private boolean waitPt;
    private boolean foundAny;
    private volatile boolean cancelled = false;

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
        CommandQueue.clear();
    }

    public boolean isActive() {
        return (config.autoCall || config.autoCheck) && state != State.IDLE;
    }

    /**
     * Zapusk avtomaticheskoj obrabotki.
     * Vyzyvaetsya po knopke (Right Shift) v menyu reportov.
     */
    public void startAutoCall() {
        if (!config.autoCall && !config.autoCheck) return;
        cancelled = false;
        state = State.SEARCHING;
        foundAny = false;
        search();
    }

    /**
     * Poisk podhodyaschego reporta v tekuschem otkrytom menyu.
     */
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

            // Proverka detektov
            String det = config.detects.stream()
                .filter(s -> !s.isEmpty() && nbtString.toLowerCase().contains(s.toLowerCase()))
                .findFirst()
                .orElse("zalupa");
            if (!config.detects.isEmpty() && det.equals("zalupa")) continue;

            // Proverka plejtajma iz lora
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

            // Klik po reportu (beryom ego)
            client.interactionManager.clickSlot(
                client.player.currentScreenHandler.syncId,
                slot, 0, SlotActionType.PICKUP, client.player
            );
            client.keyboard.setClipboard(currentNick);

            msg("\u00a7a[Auto] \u0412\u0437\u044f\u043b \u0440\u0435\u043f\u043e\u0440\u0442: \u00a7e" + currentNick);

            state = State.DOING_SPY;
            delay(this::doSpy, 1000);
        } else {
            // Probuem sleduyushchuyu stranicu
            boolean hasNext = items.size() > 53
                && items.get(53) != null
                && !items.get(53).isEmpty();

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
     * Obrabotka vhodyashchih soobshchenij chata.
     * Vyzyvaetsya iz ChatMessageMixin.
     */
    public void onChatMessage(String message) {
        if (state == State.IDLE || cancelled) return;

        // === Otvet na /find ===
        if (state == State.DOING_FIND && waitFind) {
            Matcher m = findPattern.matcher(message);
            if (m.find()) {
                waitFind = false;
                String server = m.group(1).trim().toLowerCase();
                connect(server);
                return;
            }
        }

        // === Otvet na /playtime (dlya autoCheck) ===
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
                    int totalSec = h * 3600 + min * 60 + sec;
                    handlePlaytimeResult(totalSec);
                    return;
                }
            }
        }

        // === Ozhidanie dejstvij moderacii posle /hm spyfrz (tolko autoCheck) ===
        if (config.autoCheck && state == State.WAITING_SPYFRZ) {
            Matcher bm = banPattern.matcher(message);
            if (bm.find()) {
                msg("\u00a7a[Auto] \u041e\u0431\u043d\u0430\u0440\u0443\u0436\u0435\u043d\u043e \u0434\u0435\u0439\u0441\u0442\u0432\u0438\u0435 \u043c\u043e\u0434\u0435\u0440\u0430\u0446\u0438\u0438. \u0417\u0430\u0432\u0435\u0440\u0448\u0430\u044e \u0440\u0435\u043f\u043e\u0440\u0442...");
                state = State.CLOSING_STEP1;
                delay(this::closeStep1, 1000);
            }
        }
    }

    /**
     * Shag: /hm spy nik
     */
    private void doSpy() {
        if (cancelled || currentNick == null) { state = State.IDLE; return; }
        CommandQueue.add("hm spy " + currentNick);
        state = State.DOING_FIND;
        delay(this::doFind, 3000);
    }

    /**
     * Shag: /find nik
     */
    private void doFind() {
        if (cancelled || currentNick == null) { state = State.IDLE; return; }
        waitFind = true;
        CommandQueue.add("find " + currentNick);

        // Tajmaut na otvet /find
        delay(() -> {
            if (waitFind && state == State.DOING_FIND) {
                waitFind = false;
                msg("\u00a7c[Auto] \u0421\u0435\u0440\u0432\u0435\u0440 \u043d\u0435 \u043d\u0430\u0439\u0434\u0435\u043d (\u0442\u0430\u0439\u043c\u0430\u0443\u0442)");
                if (config.autoCheck) {
                    state = State.CLOSING_STEP1;
                    delay(AutoCallManager.this::closeStep1, 1000);
                } else {
                    state = State.IDLE;
                }
            }
        }, 10000);
    }

    /**
     * Opredelyaem tip servera i podklyuchaemsya.
     * Esli indeks otsutstvuet -> stavim 1.
     */
    private void connect(String srv) {
        if (cancelled) { state = State.IDLE; return; }

        String command = null;

        // Proveryaem l2anarchy RANSHE lanarchy
        Matcher m2 = l2anarchyP.matcher(srv);
        Matcher m1 = lanarchyP.matcher(srv);
        Matcher m3 = anarchyP.matcher(srv);

        if (m2.matches()) {
            String idx = m2.group(1);
            if (idx == null || idx.isEmpty()) idx = "1";
            command = "ln120 " + idx;
        } else if (m1.matches()) {
            String idx = m1.group(1);
            if (idx == null || idx.isEmpty()) idx = "1";
            command = "ln " + idx;
        } else if (m3.matches()) {
            String idx = m3.group(1);
            if (idx == null || idx.isEmpty()) idx = "1";
            command = "cn " + idx;
        }

        if (command == null) {
            msg("\u00a7c[Auto] \u041d\u0435\u0438\u0437\u0432\u0435\u0441\u0442\u043d\u044b\u0439 \u0441\u0435\u0440\u0432\u0435\u0440: " + srv);
            if (config.autoCheck) {
                state = State.CLOSING_STEP1;
                delay(this::closeStep1, 1000);
            } else {
                state = State.IDLE;
            }
            return;
        }

        CommandQueue.add(command);

        if (config.autoCall && !config.autoCheck) {
            // AvtoVyzov (1 raz) - prosto podklyuchaemsya i vsyo
            msg("\u00a7a[Auto] \u041f\u0435\u0440\u0435\u0445\u043e\u0434 \u043d\u0430 \u0441\u0435\u0440\u0432\u0435\u0440. \u0410\u0432\u0442\u043e\u0412\u044b\u0437\u043e\u0432 \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d.");
            state = State.IDLE;
            return;
        }

        if (config.autoCheck) {
            // AvtoProverka (cikl) - zhdyom 10 sek, potom proveryaem playtime
            state = State.CONNECTING_SERVER;
            msg("\u00a7e[Auto] \u0416\u0434\u0443 10 \u0441\u0435\u043a \u043f\u043e\u0441\u043b\u0435 \u043f\u0435\u0440\u0435\u0445\u043e\u0434\u0430...");
            delay(this::startPlaytimeCheck, 10000);
        }
    }

    /**
     * Nachalo proverki plejtajma.
     */
    private void startPlaytimeCheck() {
        if (cancelled) { state = State.IDLE; return; }
        state = State.DOING_PLAYTIME;
        ptStart = System.currentTimeMillis();
        sendPlaytime();
    }

    /**
     * Otpravka /playtime nik
     */
    private void sendPlaytime() {
        if (cancelled || currentNick == null) { state = State.IDLE; return; }
        waitPt = true;
        CommandQueue.add("playtime " + currentNick);
    }

    /**
     * Obrabotka rezultata /playtime.
     * @param lastActivitySec - poslednyaya aktivnost v sekundah
     */
    private void handlePlaytimeResult(int lastActivitySec) {
        if (cancelled) { state = State.IDLE; return; }

        if (lastActivitySec < 7) {
            // Igrok AKTIVEN -> frizim
            msg("\u00a7a[Auto] \u0418\u0433\u0440\u043e\u043a \u0430\u043a\u0442\u0438\u0432\u0435\u043d (" + lastActivitySec + "\u0441). \u0424\u0440\u0438\u0437\u0438\u043c!");
            CommandQueue.add("hm spyfrz");
            state = State.WAITING_SPYFRZ;
            // Zhdyom bana/anfriza v onChatMessage
        } else {
            // Igrok AFK
            if (state == State.DOING_PLAYTIME) {
                // Pervyj raz - nachinaem cikl proverki (30 sekund)
                state = State.CHECKING_PLAYTIME_LOOP;
                ptStart = System.currentTimeMillis();
                msg("\u00a7e[Auto] \u0418\u0433\u0440\u043e\u043a \u0410\u0424\u041a (" + lastActivitySec + "\u0441). \u041f\u0440\u043e\u0432\u0435\u0440\u044f\u044e 30 \u0441\u0435\u043a...");
            }

            long elapsed = System.currentTimeMillis() - ptStart;
            if (elapsed < 30000) {
                // Eshchyo yest vremya - povtoryaem cherez 3 sek
                delay(() -> {
                    if (state == State.CHECKING_PLAYTIME_LOOP && !cancelled) {
                        sendPlaytime();
                    }
                }, 3000);
            } else {
                // 30 sekund proshlo, igrok vsyo eshchyo AFK -> zavershaem report
                msg("\u00a7e[Auto] \u0418\u0433\u0440\u043e\u043a \u0410\u0424\u041a >30\u0441. \u041f\u0440\u043e\u043f\u0443\u0441\u043a\u0430\u044e...");
                state = State.CLOSING_STEP1;
                delay(this::closeStep1, 1000);
            }
        }
    }

    // ===== ZAVERSHENIE REPORTA =====

    /**
     * Shag 1: /reportlist (otkryvaet menyu reportov)
     */
    private void closeStep1() {
        if (cancelled) { state = State.IDLE; return; }
        CommandQueue.add("reportlist");
        state = State.CLOSING_STEP2;
        delay(this::closeStep2, 1500);
    }

    /**
     * Shag 2: klik po slotu 47 (menyu dejstvij)
     */
    private void closeStep2() {
        if (cancelled) { state = State.IDLE; return; }
        if (client.player == null || client.player.currentScreenHandler == null) {
            msg("\u00a7c[Auto] \u041e\u0448\u0438\u0431\u043a\u0430: \u043c\u0435\u043d\u044e \u043d\u0435 \u043e\u0442\u043a\u0440\u044b\u043b\u043e\u0441\u044c (step2)");
            state = State.IDLE;
            return;
        }
        client.interactionManager.clickSlot(
            client.player.currentScreenHandler.syncId,
            47, 0, SlotActionType.PICKUP, client.player
        );
        state = State.CLOSING_STEP3;
        delay(this::closeStep3, 1500);
    }

    /**
     * Shag 3: klik po slotu 16 (zakryt report), zatem "-" v chat
     */
    private void closeStep3() {
        if (cancelled) { state = State.IDLE; return; }
        if (client.player == null || client.player.currentScreenHandler == null) {
            msg("\u00a7c[Auto] \u041e\u0448\u0438\u0431\u043a\u0430: \u043c\u0435\u043d\u044e \u043d\u0435 \u043e\u0442\u043a\u0440\u044b\u043b\u043e\u0441\u044c (step3)");
            state = State.IDLE;
            return;
        }
        client.interactionManager.clickSlot(
            client.player.currentScreenHandler.syncId,
            16, 0, SlotActionType.PICKUP, client.player
        );

        // Otpravlyaem "-" v chat i perehodim k reopening
        delay(() -> {
            if (cancelled) { state = State.IDLE; return; }
            sendChatMessage("-");
            msg("\u00a7a[Auto] \u0420\u0435\u043f\u043e\u0440\u0442 \u0437\u0430\u0432\u0435\u0440\u0448\u0451\u043d.");
            state = State.REOPENING;
            delay(AutoCallManager.this::reopen, 1500);
        }, 500);
    }

    /**
     * Pereotkrytie /reportlist i poisk sleduyushchego reporta (dlya autoCheck).
     */
    private void reopen() {
        if (cancelled) { state = State.IDLE; return; }
        if (!config.autoCheck) {
            state = State.IDLE;
            return;
        }

        foundAny = false;
        currentNick = null;
        msg("\u00a7a[Auto] \u0418\u0449\u0443 \u0441\u043b\u0435\u0434\u0443\u044e\u0449\u0438\u0439 \u0440\u0435\u043f\u043e\u0440\u0442...");
        CommandQueue.add("reportlist");

        delay(() -> {
            if (cancelled) { state = State.IDLE; return; }
            state = State.SEARCHING;
            search();
        }, 1500);
    }

    // ===== UTILITY =====

    private void msg(String m) {
        if (client.player != null) {
            client.execute(() -> client.player.sendMessage(Text.of(m)));
        }
    }

    private void sendChatMessage(String m) {
        if (client.player == null) return;
        client.execute(() -> {
            if (client.getNetworkHandler() != null) {
                client.getNetworkHandler().sendChatMessage(m);
            }
        });
    }

    private void delay(Runnable r, long ms) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                return;
            }
            if (!cancelled) {
                client.execute(r);
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
