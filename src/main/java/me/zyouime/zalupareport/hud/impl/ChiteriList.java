package me.zyouime.zalupareport.hud.impl;

import me.zyouime.zalupareport.client.ZalupareportClient;
import me.zyouime.zalupareport.hud.api.ListWidget;
import me.zyouime.zalupareport.manager.ZalupaManager;
import me.zyouime.zalupareport.render.RenderHelper;
import me.zyouime.zalupareport.render.animation.Anim;
import me.zyouime.zalupareport.render.animation.OutBack;
import me.zyouime.zalupareport.render.font.FontRenderers;
import me.zyouime.zalupareport.util.SlotSChiterom;
import me.zyouime.zalupareport.util.WidgetAnim;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import java.awt.*;

public class ChiteriList extends ListWidget<ChiteriList.ItemEntry> {

    private final OutBack anim = new OutBack(20);
    private final OutBack trashAnim = new OutBack(25);
    private float currentHeight, animHeight;
    private float offsetX, offsetY;
    private boolean isDragging;
    private final Identifier trash = new Identifier("zalupareport", "textures/trash.png");

    public ChiteriList(float x, float y, float width, float height) { super(x, y, width, height, 2, 17); }

    @Override
    public void render(DrawContext context, double mouseX, double mouseY) {
        float animProgress = (float) anim.getAnimationd();
        MatrixStack matrixStack = context.getMatrices();
        WidgetAnim arcAnim = WidgetAnim.getAnim(this.getScreenX(), this.getScreenY(), this.width, this.animHeight, 6, animProgress);
        WidgetAnim roundAnim = WidgetAnim.getAnim(this.getScreenX() + 1, this.getScreenY() + 1, this.width - 2, this.animHeight - 2, 5, animProgress);
        RenderHelper.drawArc(matrixStack, arcAnim.x(), arcAnim.y(), arcAnim.width(), arcAnim.height(), arcAnim.radius(), 1 * animProgress, Color.LIGHT_GRAY);
        RenderHelper.drawRoundedRect(matrixStack, roundAnim.x(), roundAnim.y(), roundAnim.width(), roundAnim.height(), roundAnim.radius(), new Color(32, 32, 32, 196));
        matrixStack.push();
        float textScale = 1 * animProgress;
        matrixStack.scale(textScale, textScale, 1);
        FontRenderers.mainFont.drawString(matrixStack, "Zalupa\u00a76Reports", (this.getScreenX() + 8) / textScale, (this.getScreenY() + 7) / textScale, Color.WHITE.getRGB());
        matrixStack.pop();
        WidgetAnim textureAnim = WidgetAnim.getAnim(((this.getScreenX() + this.width) - 15), (this.getScreenY() + 4), 10, 10, 0, (float) trashAnim.getAnimationd());
        RenderHelper.drawTexture(matrixStack, textureAnim.x(), textureAnim.y(), textureAnim.width(), textureAnim.height(), 0, 0, 512, 512, 512, 512, trash);
        anim.update(true);
        trashAnim.update(true);
        this.renderList(context);
    }

    public float getScreenX() { return MinecraftClient.getInstance().getWindow().getScaledWidth() / 2f + this.x; }
    public float getScreenY() { return MinecraftClient.getInstance().getWindow().getScaledHeight() / 2f + this.y; }
    @Override public float getHeight() { return currentHeight; }

    public void updateHeight() {
        this.currentHeight = Math.min(this.height, ((this.getEntryHeight() + this.spacing) * (this.entries.isEmpty() ? 1 : this.entries.size() + 1)));
        this.animHeight = Anim.fast(this.animHeight, currentHeight, 35);
    }

    @Override public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.getScreenX() && mouseX <= this.getScreenX() + width && mouseY >= this.getScreenY() && mouseY <= this.getScreenY() + this.getHeight();
    }

    @Override public float getScrollMax() {
        float totalHeight = (float) entries.stream().mapToDouble(entry -> entry.height).sum();
        return Math.max(0, (totalHeight + spacing * entries.size()) - (currentHeight - 18));
    }

    @Override public void resetAnim() {
        this.anim.reset(); this.trashAnim.reset();
        for (ItemEntry entry : this.entries) entry.resetAnim();
        this.animHeight = currentHeight;
    }

    @Override public boolean isEntryVisible(ItemEntry entry) {
        return entry.y + entry.height >= this.getScreenY() + 18 && entry.y <= this.getScreenY() + currentHeight;
    }

    @Override public void renderList(DrawContext context) {
        this.animScroll(); this.updateHeight();
        float startOffset = this.getScreenY() + 18;
        context.enableScissor((int) this.getScreenX(), (int) startOffset + 2, (int) (this.getScreenX() + width), (int) (this.getScreenY() + currentHeight - 2));
        float offset = 0;
        for (ItemEntry entry : entries) {
            entry.updatePos(this.getScreenX(), (float) ((startOffset - getScrollAmount()) + offset), width, entry.height);
            if (this.isEntryVisible(entry)) entry.render(context);
            offset += entry.height + spacing;
        }
        context.disableScissor();
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= (this.getScreenX() + this.width) - 15 && mouseX <= ((this.getScreenX() + this.width) - 15) + 10
                && mouseY >= this.getScreenY() + 4 && mouseY <= this.getScreenY() + 4 + 10) {
            this.clear(); this.trashAnim.reset(); this.resetScroll(); return true;
        }
        ItemEntry entry = this.getEntryAtPos(mouseX, mouseY);
        if (entry != null) {
            ZalupaManager manager = ZalupareportClient.getInstance().manager;
            if (manager.clickPoChiteru(entry.getSlotSChiterom())) { this.remove(entry); this.resetScroll(); }
            return true;
        } else {
            float listX = this.getScreenX(); float listY = this.getScreenY();
            if (mouseX >= listX && mouseX <= listX + this.getWidth() && mouseY >= listY && mouseY <= listY + this.getHeight()) {
                isDragging = true; offsetX = (float) mouseX - listX; offsetY = (float) mouseY - listY; return true;
            }
        }
        return false;
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDragging) { isDragging = false; return true; } return false;
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDragging) {
            Window window = MinecraftClient.getInstance().getWindow();
            float cx = window.getScaledWidth() / 2f, cy = window.getScaledHeight() / 2f;
            float nx = Math.max(-cx, Math.min((float) mouseX - cx - offsetX, cx - this.getWidth()));
            float ny = Math.max(-cy, Math.min((float) mouseY - cy - offsetY, cy - this.getHeight()));
            this.setX(nx); this.setY(ny); return true;
        }
        return false;
    }

    public static class ItemEntry extends ListEntry<ItemEntry> {
        private final SlotSChiterom slotSChiterom;
        private final OutBack anim = new OutBack(20);

        public ItemEntry(SlotSChiterom slotSChiterom) {
            this.slotSChiterom = slotSChiterom;
            this.height = ZalupareportClient.getInstance().manager.list.getEntryHeight();
        }

        @Override public void render(DrawContext context) {
            float ap = (float) anim.getAnimationd();
            MatrixStack ms = context.getMatrices();
            WidgetAnim ba = WidgetAnim.getAnim(x + 2, y + 2, width - 4, height - 4, 4, ap);
            RenderHelper.drawRoundedRect(ms, ba.x(), ba.y(), ba.width(), ba.height(), ba.radius(), new Color(100, 100, 100, 96));
            String nick = slotSChiterom.nickName();
            String detect = "\u00a7f(\u00a76" + slotSChiterom.detect() + "\u00a7f)";
            float nw = FontRenderers.mainFont.getStringWidth(nick);
            float dw = FontRenderers.mainFont.getStringWidth(detect);
            float limit = width - dw - 20;
            if (nw > limit) {
                char[] c = nick.toCharArray(); StringBuilder sb = new StringBuilder(); int idx = 0;
                while (idx < c.length && FontRenderers.mainFont.getStringWidth(sb + "...") < limit) sb.append(c[idx++]);
                if (idx < c.length) sb.append("...");
                nick = sb.toString();
            }
            ms.push(); float sc = 1 * ap; ms.scale(sc, sc, 1); float fy = (y + 6) / sc;
            FontRenderers.mainFont.drawString(ms, nick, (x + 5) / sc, fy, Color.WHITE.getRGB());
            FontRenderers.mainFont.drawString(ms, detect, (x - 5 + (width - dw)) / sc, fy, Color.WHITE.getRGB());
            ms.pop(); anim.update(true);
        }

        public void resetAnim() { anim.reset(); }
        @Override public boolean equals(Object obj) { return obj instanceof ItemEntry e && e.getSlotSChiterom().equals(slotSChiterom); }
        public SlotSChiterom getSlotSChiterom() { return slotSChiterom; }
    }
}
