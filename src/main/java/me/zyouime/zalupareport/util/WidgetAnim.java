package me.zyouime.zalupareport.util;
public record WidgetAnim(float x, float y, float width, float height, float radius) {
    public static WidgetAnim getAnim(float x, float y, float w, float h, float r, float p) {
        float cx = x + w / 2f, cy = y + h / 2f;
        return new WidgetAnim(cx + (x - cx) * p, cy + (y - cy) * p, w * p, h * p, r * p);
    }
}
