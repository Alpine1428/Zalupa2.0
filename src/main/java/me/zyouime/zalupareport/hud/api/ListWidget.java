package me.zyouime.zalupareport.hud.api;

import me.zyouime.zalupareport.render.animation.Anim;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public abstract class ListWidget<E extends ListWidget.ListEntry<E>> extends Widget {

    private double scrollAmount, targetScrollAmount;
    private final float entryHeight;
    protected final float spacing;
    protected final List<E> entries = new ArrayList<>();

    public ListWidget(float x, float y, float width, float height, float spacing, float entryHeight) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        this.spacing = spacing; this.entryHeight = entryHeight;
    }

    @Override
    public void render(DrawContext context, double mouseX, double mouseY) { this.renderList(context); }
    public void addEntry(E entry) { this.entries.add(entry); }
    public void clear() { this.entries.clear(); }
    public boolean contains(E entry) { return this.entries.contains(entry); }
    public void remove(E entry) { this.entries.remove(entry); }

    public void renderList(DrawContext context) {
        this.animScroll();
        context.enableScissor((int) x, (int) y, (int) (x + width), (int) (y + height));
        float offset = 0;
        for (E entry : entries) {
            entry.updatePos(x, (float) ((y - scrollAmount) + offset), width, entry.height);
            if (this.isEntryVisible(entry)) entry.render(context);
            offset += entry.height + spacing;
        }
        context.disableScissor();
    }

    public boolean isEntryVisible(E entry) { return entry.y + entry.height >= y && entry.y <= y + height; }
    public void animScroll() { this.scrollAmount = Anim.fast((float) this.scrollAmount, (float) this.targetScrollAmount, 10f); }
    public List<E> getEntries() { return entries; }
    public void resetScroll() { scrollAmount = 0; targetScrollAmount = 0; }

    public float getScrollMax() {
        float totalHeight = (float) entries.stream().mapToDouble(entry -> entry.height).sum();
        float allSpacing = spacing * entries.size();
        return Math.max(0, (totalHeight + allSpacing) - this.getHeight());
    }

    public void setScrollAmount(double scrollAmount) {
        int i = (scrollAmount == 1) ? -1 : 1;
        this.scroll(this.targetScrollAmount + i * 24);
    }

    public void scroll(double amount) { this.targetScrollAmount = MathHelper.clamp(amount, 0, this.getScrollMax()); }
    public boolean isMouseOver(double mouseX, double mouseY) { return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (this.isMouseOver(mouseX, mouseY)) { this.setScrollAmount(amount); return true; }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    public E getEntryAtPos(double mouseX, double mouseY) {
        for (E entry : entries) { if (entry.isMouseOver(mouseX, mouseY) && this.isEntryVisible(entry)) return entry; }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (E entry : entries) { if (entry.mouseClicked(mouseX, mouseY, button) && this.isEntryVisible(entry)) return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void resetAnim() {}
    public double getScrollAmount() { return scrollAmount; }
    public double getTargetScrollAmount() { return targetScrollAmount; }
    public float getEntryHeight() { return entryHeight; }

    public static class ListEntry<T extends ListEntry<T>> {
        public float x, y, width, height;
        public void render(DrawContext context) {}
        public void updatePos(float x, float y, float width, float height) { this.x = x; this.y = y; this.width = width; this.height = height; }
        public boolean mouseClicked(double mouseX, double mouseY, int button) { return isMouseOver(mouseX, mouseY); }
        public boolean isMouseOver(double mouseX, double mouseY) { return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height; }
    }
}
