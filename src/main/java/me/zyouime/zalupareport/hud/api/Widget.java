package me.zyouime.zalupareport.hud.api;

import net.minecraft.client.gui.DrawContext;

public abstract class Widget {

    protected float x, y, width, height;

    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    public boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) { return false; }
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) { return false; }
    public void render(DrawContext context, double mouseX, double mouseY) {}
    public abstract void resetAnim();

    public void setX(float x) { this.x = x; }
    public void setY(float y) { this.y = y; }
    public void setWidth(float width) { this.width = width; }
    public void setHeight(float height) { this.height = height; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
}
