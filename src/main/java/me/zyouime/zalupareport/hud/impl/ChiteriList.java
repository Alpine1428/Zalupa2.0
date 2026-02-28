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
        FontRenderers.mainFont.drawString(matrixStack, "Zalupa§6Reports", (this.getScreenX() + 8) / textScale, (this.getScreenY() + 7) / textScale, Color.WHITE.getRGB());
        matrixStack.pop();
        WidgetAnim textureAnim = WidgetAnim.getAnim(((this.getScreenX() + this.width) - 15), (this.getScreenY() + 4), 10, 10, 0, (float) trashAnim.getAnimationd());
        RenderHelper.drawTexture(matrixStack, textureAnim.x(), textureAnim.y(), textureAnim.width(), textureAnim.height(), 0, 0, 512, 512, 512, 512, trash);
        anim.update(true);
        trashAnim.update(true);
        this.renderList(context);
    }

    public float getScreenX() { return MinecraftClient.getInstance().getWindow().getScaledWidth() / 2f + this.x; }
    public float getScreenY() { return MinecraftClient.getInstance().getWindow().getScaledHeight() / 2f + this.y; }

    @Override
    public float getHeight() { return currentHeight; }

    public void updateHeight() {
        this.currentHeight = Math.min(this.height, ((this.getEntryHeight() + this.spacing) * (this.entries.isEmpty() ? 1 : this.entries.size() + 1)));
        this.animHeight = Anim.fast(this.animHeight, currentHeight, 35);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.getScreenX() && mouseX <= this.getScreenX() + width && mouseY >= this.getScreenY() && mouseY <= this.getScreenY() + this.getHeight();
    }

    @Override
    public float getScrollMax() {
        float totalHeight = (float) entries.stream().mapToDouble(entry -> entry.height).sum();
        float allSpacing = spacing * entries.size();
        return Math.max(0, (totalHeight + allSpacing) - (currentHeight - 18));
    }

    @Override
    public void resetAnim() {
        this.anim.reset(); this.trashAnim.reset();
        for (ItemEntry entry : this.entries) entry.resetAnim();
        this.animHeight = currentHeight;
    }

    @Override
    public boolean isEntryVisible(ItemEntry entry) {
        return entry.y + entry.height >= this.getScreenY() + 18 && entry.y <= this.getScreenY() + currentHeight;
    }

    @Override
    public void renderList(DrawContext context) {
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= (this.getScreenX() + this.width) - 15 && mouseX <= ((this.getScreenX() + this.width) - 15) + 10 && mouseY >= this.getScreenY() + 4 && mouseY <= this.getScreenY() + 4 + 10) {
            this.clear(); this.trashAnim.reset(); this.resetScroll(); return true;
        }
        ChiteriList.ItemEntry entry = this.getEntryAtPos(mouseX, mouseY);
        if (entry != null) {
            ZalupaManager manager = ZalupareportClient.getInstance().manager;
            SlotSChiterom click = entry.getSlotSChiterom();
            if (manager.clickPoChiteru(click)) { this.remove(entry); this.resetScroll(); }
            return true;
        } else {
            float listX = this.getScreenX(); float listY = this.getScreenY();
            if (mouseX >= listX && mouseX <= listX + this.getWidth() && mouseY >= listY && mouseY <= listY + this.getHeight()) {
                isDragging = true; offsetX = (float) mouseX - listX; offsetY = (float) mouseY - listY; return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.isDragging) { this.isDragging = false; return true; }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.isDragging) {
            Window window = MinecraftClient.getInstance().getWindow();
            float scaledCenterX = window.getScaledWidth() / 2f; float scaledCenterY = window.getScaledHeight() / 2f;
            float x = (float) mouseX - scaledCenterX - offsetX; float y = (float) mouseY - scaledCenterY - offsetY;
            x = Math.max(-scaledCenterX, Math.min(x, scaledCenterX - this.getWidth()));
            y = Math.max(-scaledCenterY, Math.min(y, scaledCenterY - this.getHeight()));
            this.setX(x); this.setY(y); return true;
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

        @Override
        public void render(DrawContext context) {
            float animProgress = (float) anim.getAnimationd();
            MatrixStack matrixStack = context.getMatrices();
            WidgetAnim backAnim = WidgetAnim.getAnim(this.x + 2, this.y + 2, this.width - 4, this.height - 4, 4, animProgress);
            RenderHelper.drawRoundedRect(matrixStack, backAnim.x(), backAnim.y(), backAnim.width(), backAnim.height(), backAnim.radius(), new Color(100, 100, 100, 96));
            String nick = slotSChiterom.nickName();
            String detect = "§f(§6" + slotSChiterom.detect() + "§f)";
            float nickWidth = FontRenderers.mainFont.getStringWidth(nick);
            float detectWidth = FontRenderers.mainFont.getStringWidth(detect);
            float ogranichitel = this.width - detectWidth - 20;
            if (nickWidth > ogranichitel) {
                char[] c = nick.toCharArray(); StringBuilder sb = new StringBuilder(); int index = 0;
                while (index < c.length && FontRenderers.mainFont.getStringWidth(sb + "...") < ogranichitel) sb.append(c[index++]);
                if (index < c.length) sb.append("...");
                nick = sb.toString();
            }
            matrixStack.push();
            float scale = 1 * animProgress; matrixStack.scale(scale, scale, 1);
            float fontY = (this.y + 6) / scale;
            FontRenderers.mainFont.drawString(matrixStack, nick, (this.x + 5) / scale, fontY, Color.WHITE.getRGB());
            FontRenderers.mainFont.drawString(matrixStack, detect, (this.x - 5 + (this.width - detectWidth)) / scale, fontY, Color.WHITE.getRGB());
            matrixStack.pop();
            anim.update(true);
        }

        public void resetAnim() { anim.reset(); }

        @Override
        public boolean equals(Object obj) { return obj instanceof ItemEntry entry && entry.getSlotSChiterom().equals(slotSChiterom); }
        public SlotSChiterom getSlotSChiterom() { return slotSChiterom; }
    }
}
