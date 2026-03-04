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

    // Паттерн ника из названия предмета в репортлисте
    private final Pattern nickPattern = Pattern.compile("\u0438\u0433\u0440\u043e\u043a\u0430\\s+(\\w+)");

    // Паттерн времени из лора предмета в репортлисте
    private final Pattern timePattern = Pattern.compile(
        "(?:(\\d+)\\s\u0447\\.,\\s)?(?:(\\d+)\\s\u043c\\.,\\s)?\\d+\\s\u0441\u0435\u043a\\.\\s\\("
        + "(?:(\\d+)\\s\u0447\\.,\\s)?(?:(\\d+)\\s\u043c\\.,\\s)?\\d+\\s\u0441\u0435\u043a\\.\\)"
    );

    // Паттерн ответа /find: "Игрок NickName находится на сервере lanarchy3"
    private final Pattern findPattern = Pattern.compile(
        "\u0418\u0433\u0440\u043e\u043a\\s+\\S+\\s+\u043d\u0430\u0445\u043e\u0434\u0438\u0442\u0441\u044f \u043d\u0430 \u0441\u0435\u0440\u0432\u0435\u0440\u0435\\s+(\\S+)"
    );

    // Паттерн: "Последняя активность: 0ч., 2м., 6с. назад"
    // Поддерживает форматы: Xч., Yм., Zс. | Xч., Yм., Zсек.
    private final Pattern activityPattern = Pattern.compile(
        "\u041f\u043e\u0441\u043b\u0435\u0434\u043d\u044f\u044f \u0430\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u044c:\\s*(?:(\\d+)\\s*\u0447\\.?,\\s*)?(?:(\\d+)\\s*\u043c\\.?,\\s*)?(\\d+)\\s*\u0441(?:\u0435\u043a)?\\.\\s*\u043d\u0430\u0437\u0430\u0434"
    );

    // Серверы: l2anarchy проверяется РАНЬШЕ lanarchy
    private final Pattern l2anarchyP = Pattern.compile("^l2anarchy(\\d*)$");
    private final Pattern lanarchyP = Pattern.compile("^lanarchy(\\d*)$");
    private final Pattern anarchyP = Pattern.compile("^anarchy(\\d*)$");

    // Команды модерации в чате
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
     * Запуск автоматической обработки.
     * Вызывается по кнопке (Right Shift) в меню репортов.
     */
    public void startAutoCall() {
        if (!config.autoCall && !config.autoCheck) return;
        cancelled = false;
        state = State.SEARCHING;
        foundAny = false;
        search();
    }

    /**
     * Поиск подходящего репорта в текущем открытом меню.
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

            // Проверка детектов
            String det = config.detects.stream()
                .filter(s -> !s.isEmpty() && nbtString.toLowerCase().contains(s.toLowerCase()))
                .findFirst()
                .orElse("zalupa");
            if (!config.detects.isEmpty() && det.equals("zalupa")) continue;

            // Проверка плейтайма из лора
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

            // Клик по репорту (берём его)
            client.interactionManager.clickSlot(
                client.player.currentScreenHandler.syncId,
                slot, 0, SlotActionType.PICKUP, client.player
            );
            client.keyboard.setClipboard(currentNick);

            msg("\u00a7a[Auto] \u0412\u0437\u044f\u043b \u0440\u0435\u043f\u043e\u0440\u0442: \u00a7e" + currentNick);

            state = State.DOING_SPY;
            delay(this::doSpy, 1000);
        } else {
            // Следующая страница
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
     * Обработка ВХОДЯЩИХ сообщений чата.
     * Вызывается из ChatMessageMixin при каждом новом сообщении.
     */
    public void onChatMessage(String message) {
        if (state == State.IDLE || cancelled) return;

        // === Ответ на /find ===
        if (state == State.DOING_FIND && waitFind) {
            Matcher m = findPattern.matcher(message);
            if (m.find()) {
                waitFind = false;
                String server = m.group(1).trim().toLowerCase();
                connect(server);
                return;
            }
        }

        // === Ответ на /playtime (для autoCheck) ===
        if (config.autoCheck
            && (state == State.DOING_PLAYTIME || state == State.CHECKING_PLAYTIME_LOOP)
            && waitPt) {
            // Ищем строку "Последняя активность: Xч., Yм., Zс. назад"
            if (message.contains("\u041f\u043e\u0441\u043b\u0435\u0434\u043d\u044f\u044f \u0430\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u044c")) {
                Matcher m = activityPattern.matcher(message);
                if (m.find()) {
                    waitPt = false;
                    int h = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
                    int min = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
                    int sec = Integer.parseInt(m.group(3));
                    int totalSec = h * 3600 + min * 60 + sec;
                    msg("\u00a7e[Auto] \u041f\u043e\u0441\u043b\u0435\u0434\u043d\u044f\u044f \u0430\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u044c: " + h + "\u0447. " + min + "\u043c. " + sec + "\u0441. (" + totalSec + " \u0441\u0435\u043a.)");
                    handlePlaytimeResult(totalSec);
                    return;
                }
            }
        }

        // === Ожидание действий модерации после /hm spyfrz (только autoCheck) ===
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
     * Шаг: /hm spy ник
     */
    private void doSpy() {
        if (cancelled || currentNick == null) { state = State.IDLE; return; }
        msg("\u00a7e[Auto] \u041e\u0442\u043f\u0440\u0430\u0432\u043b\u044f\u044e: /hm spy " + currentNick);
        CommandQueue.add("hm spy " + currentNick);
        state = State.DOING_FIND;
        delay(this::doFind, 3000);
    }

    /**
     * Шаг: /find ник
     */
    private void doFind() {
        if (cancelled || currentNick == null) { state = State.IDLE; return; }
        waitFind = true;
        msg("\u00a7e[Auto] \u041e\u0442\u043f\u0440\u0430\u0432\u043b\u044f\u044e: /find " + currentNick);
        CommandQueue.add("find " + currentNick);

        // Таймаут 10 сек
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
     * Определяем тип сервера и подключаемся.
     * Если индекс отсутствует -> ставим 1.
     */
    private void connect(String srv) {
        if (cancelled) { state = State.IDLE; return; }

        String command = null;

        // l2anarchy проверяется РАНЬШЕ lanarchy
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

        msg("\u00a7e[Auto] \u041f\u043e\u0434\u043a\u043b\u044e\u0447\u0430\u044e\u0441\u044c: /" + command);
        CommandQueue.add(command);

        if (config.autoCall && !config.autoCheck) {
            // АвтоВызов (1 раз)
            msg("\u00a7a[Auto] \u041f\u0435\u0440\u0435\u0445\u043e\u0434 \u043d\u0430 \u0441\u0435\u0440\u0432\u0435\u0440. \u0410\u0432\u0442\u043e\u0412\u044b\u0437\u043e\u0432 \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d.");
            state = State.IDLE;
            return;
        }

        if (config.autoCheck) {
            // АвтоПроверка (цикл)
            state = State.CONNECTING_SERVER;
            msg("\u00a7e[Auto] \u0416\u0434\u0443 10 \u0441\u0435\u043a \u043f\u043e\u0441\u043b\u0435 \u043f\u0435\u0440\u0435\u0445\u043e\u0434\u0430 \u043d\u0430 \u0441\u0435\u0440\u0432\u0435\u0440...");
            delay(this::startPlaytimeCheck, 10000);
        }
    }

    /**
     * Начало проверки плейтайма.
     */
    private void startPlaytimeCheck() {
        if (cancelled) { state = State.IDLE; return; }
        state = State.DOING_PLAYTIME;
        ptStart = System.currentTimeMillis();
        msg("\u00a7e[Auto] \u041d\u0430\u0447\u0438\u043d\u0430\u044e \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0443 \u043f\u043b\u0435\u0439\u0442\u0430\u0439\u043c\u0430...");
        sendPlaytime();
    }

    /**
     * Отправка /playtime ник
     */
    private void sendPlaytime() {
        if (cancelled || currentNick == null) { state = State.IDLE; return; }
        waitPt = true;
        msg("\u00a7e[Auto] \u041e\u0442\u043f\u0440\u0430\u0432\u043b\u044f\u044e: /playtime " + currentNick);
        CommandQueue.add("playtime " + currentNick);
    }

    /**
     * Обработка результата /playtime.
     * @param lastActivitySec - последняя активность в секундах
     */
    private void handlePlaytimeResult(int lastActivitySec) {
        if (cancelled) { state = State.IDLE; return; }

        if (lastActivitySec < 7) {
            // Игрок АКТИВЕН -> фризим
            msg("\u00a7a[Auto] \u0418\u0433\u0440\u043e\u043a \u0430\u043a\u0442\u0438\u0432\u0435\u043d (" + lastActivitySec + "\u0441)! \u0424\u0440\u0438\u0437\u0438\u043c: /hm spyfrz");
            CommandQueue.add("hm spyfrz");
            state = State.WAITING_SPYFRZ;
            // Ждём бана/анфриза в onChatMessage
        } else {
            // Игрок АФК - нужно проверять каждые 3 секунды в течение 30 секунд
            if (state == State.DOING_PLAYTIME) {
                // Первый раз АФК - начинаем цикл
                state = State.CHECKING_PLAYTIME_LOOP;
                ptStart = System.currentTimeMillis();
                msg("\u00a7e[Auto] \u0418\u0433\u0440\u043e\u043a \u0410\u0424\u041a (\u0430\u043a\u0442\u0438\u0432\u043d\u043e\u0441\u0442\u044c " + lastActivitySec + "\u0441). \u041f\u0440\u043e\u0432\u0435\u0440\u044f\u044e \u043a\u0430\u0436\u0434\u044b\u0435 3\u0441 \u0432 \u0442\u0435\u0447\u0435\u043d\u0438\u0438 30\u0441...");
            }

            long elapsed = System.currentTimeMillis() - ptStart;
            if (elapsed < 30000) {
                // Ещё есть время - ждём 3 секунды и повторяем
                msg("\u00a7e[Auto] \u0410\u0424\u041a (" + lastActivitySec + "\u0441). \u041f\u0440\u043e\u0448\u043b\u043e " + (elapsed / 1000) + "\u0441/30\u0441. \u041f\u043e\u0432\u0442\u043e\u0440 \u0447\u0435\u0440\u0435\u0437 3\u0441...");
                delay(() -> {
                    if (state == State.CHECKING_PLAYTIME_LOOP && !cancelled) {
                        sendPlaytime();
                    }
                }, 3000);
            } else {
                // 30 секунд прошло - пропускаем
                msg("\u00a7c[Auto] \u0418\u0433\u0440\u043e\u043a \u0410\u0424\u041a >30\u0441. \u041f\u0440\u043e\u043f\u0443\u0441\u043a\u0430\u044e, \u0437\u0430\u0432\u0435\u0440\u0448\u0430\u044e \u0440\u0435\u043f\u043e\u0440\u0442...");
                state = State.CLOSING_STEP1;
                delay(this::closeStep1, 1000);
            }
        }
    }

    // ===== ЗАВЕРШЕНИЕ РЕПОРТА =====

    /**
     * Шаг 1: /reportlist
     */
    private void closeStep1() {
        if (cancelled) { state = State.IDLE; return; }
        msg("\u00a7e[Auto] \u0417\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u0438\u0435 \u0440\u0435\u043f\u043e\u0440\u0442\u0430: /reportlist");
        CommandQueue.add("reportlist");
        state = State.CLOSING_STEP2;
        delay(this::closeStep2, 1500);
    }

    /**
     * Шаг 2: клик по слоту 47
     */
    private void closeStep2() {
        if (cancelled) { state = State.IDLE; return; }
        if (client.player == null || client.player.currentScreenHandler == null) {
            msg("\u00a7c[Auto] \u041e\u0448\u0438\u0431\u043a\u0430: \u043c\u0435\u043d\u044e \u043d\u0435 \u043e\u0442\u043a\u0440\u044b\u043b\u043e\u0441\u044c (step2)");
            state = State.IDLE;
            return;
        }
        msg("\u00a7e[Auto] \u041a\u043b\u0438\u043a \u0441\u043b\u043e\u0442 47");
        client.interactionManager.clickSlot(
            client.player.currentScreenHandler.syncId,
            47, 0, SlotActionType.PICKUP, client.player
        );
        state = State.CLOSING_STEP3;
        delay(this::closeStep3, 1500);
    }

    /**
     * Шаг 3: клик по слоту 16, потом "-" в чат
     */
    private void closeStep3() {
        if (cancelled) { state = State.IDLE; return; }
        if (client.player == null || client.player.currentScreenHandler == null) {
            msg("\u00a7c[Auto] \u041e\u0448\u0438\u0431\u043a\u0430: \u043c\u0435\u043d\u044e \u043d\u0435 \u043e\u0442\u043a\u0440\u044b\u043b\u043e\u0441\u044c (step3)");
            state = State.IDLE;
            return;
        }
        msg("\u00a7e[Auto] \u041a\u043b\u0438\u043a \u0441\u043b\u043e\u0442 16");
        client.interactionManager.clickSlot(
            client.player.currentScreenHandler.syncId,
            16, 0, SlotActionType.PICKUP, client.player
        );

        delay(() -> {
            if (cancelled) { state = State.IDLE; return; }
            msg("\u00a7e[Auto] \u041e\u0442\u043f\u0440\u0430\u0432\u043b\u044f\u044e '-' \u0432 \u0447\u0430\u0442");
            sendChatMessage("-");
            msg("\u00a7a[Auto] \u0420\u0435\u043f\u043e\u0440\u0442 \u0437\u0430\u0432\u0435\u0440\u0448\u0451\u043d.");
            state = State.REOPENING;
            delay(AutoCallManager.this::reopen, 1500);
        }, 500);
    }

    /**
     * Переоткрытие /reportlist и поиск следующего репорта.
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

    // ===== УТИЛИТЫ =====

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
