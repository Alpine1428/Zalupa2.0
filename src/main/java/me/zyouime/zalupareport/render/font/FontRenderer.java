package me.zyouime.zalupareport.render.font;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.chars.Char2IntArrayMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import java.awt.*;
import java.io.Closeable;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FontRenderer implements Closeable {
    private static final Char2IntArrayMap colorCodes = new Char2IntArrayMap() {{
        put('0', 0x000000); put('1', 0x0000AA); put('2', 0x00AA00); put('3', 0x00AAAA);
        put('4', 0xAA0000); put('5', 0xAA00AA); put('6', 0xFFAA00); put('7', 0xAAAAAA);
        put('8', 0x555555); put('9', 0x5555FF); put('A', 0x55FF55); put('B', 0x55FFFF);
        put('C', 0xFF5555); put('D', 0xFF55FF); put('E', 0xFFFF55); put('F', 0xFFFFFF);
    }};
    private static final ExecutorService ASYNC_WORKER = Executors.newCachedThreadPool();
    private final Object2ObjectMap<Identifier, ObjectList<DrawEntry>> GLYPH_PAGE_CACHE = new Object2ObjectOpenHashMap<>();
    private final float originalSize;
    private final ObjectList<GlyphMap> maps = new ObjectArrayList<>();
    private final Char2ObjectArrayMap<Glyph> allGlyphs = new Char2ObjectArrayMap<>();
    private final int charsPerPage, padding;
    private final String prebakeGlyphs;
    private int scaleMul = 0;
    private Font font;
    private int previousGameScale = -1;
    private Future<Void> prebakeGlyphsFuture;
    private boolean initialized;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public FontRenderer(Font font, float sizePx, int charsPerPage, int padding, @Nullable String prebake) {
        this.originalSize = sizePx; this.charsPerPage = charsPerPage; this.padding = padding; this.prebakeGlyphs = prebake;
        init(font, sizePx);
    }
    public FontRenderer(Font font, float sizePx) { this(font, sizePx, 256, 5, null); }
    private static int floorNearestMulN(int x, int n) { return n * (int) Math.floor((double) x / (double) n); }

    public static String stripControlCodes(String text) {
        char[] chars = text.toCharArray(); StringBuilder f = new StringBuilder();
        for (int i = 0; i < chars.length; i++) { if (chars[i] == '\u00a7') { i++; continue; } f.append(chars[i]); }
        return f.toString();
    }

    private void sizeCheck() { int gs = (int) mc.getWindow().getScaleFactor(); if (gs != previousGameScale) { close(); init(font, getSizeToScale(originalSize, gs)); } }

    public static float getSizeToScale(float originalSize, int mcScale) {
        float r = originalSize;
        switch (mcScale) { case 1 -> r *= 2.2f; case 3 -> r /= 1.2f; case 4 -> r /= 1.5f; }
        return r;
    }

    public void init(Font font, float sizePx) {
        if (initialized) throw new IllegalStateException("Double init");
        initialized = true; previousGameScale = (int) mc.getWindow().getScaleFactor(); scaleMul = previousGameScale;
        this.font = font.deriveFont(sizePx * scaleMul);
        if (prebakeGlyphs != null && !prebakeGlyphs.isEmpty()) prebakeGlyphsFuture = prebake();
    }

    private Future<Void> prebake() { return ASYNC_WORKER.submit(() -> { for (char c : prebakeGlyphs.toCharArray()) { if (Thread.interrupted()) break; locateGlyph1(c); } return null; }); }
    private GlyphMap generateMap(char from, char to) { GlyphMap gm = new GlyphMap(from, to, font, randomIdentifier(), padding); maps.add(gm); return gm; }
    private Glyph locateGlyph0(char glyph) { for (GlyphMap map : maps) if (map.contains(glyph)) return map.getGlyph(glyph); int base = floorNearestMulN(glyph, charsPerPage); return generateMap((char) base, (char) (base + charsPerPage)).getGlyph(glyph); }
    private Glyph locateGlyph1(char glyph) { return allGlyphs.computeIfAbsent(glyph, this::locateGlyph0); }

    public void drawString(MatrixStack stack, String s, double x, double y, int color) {
        drawString(stack, s, (float) x, (float) y, ((color >> 16) & 0xff) / 255f, ((color >> 8) & 0xff) / 255f, (color & 0xff) / 255f, ((color >> 24) & 0xff) / 255f);
    }
    public void drawString(MatrixStack stack, String s, double x, double y, Color color) {
        drawString(stack, s, (float) x, (float) y, color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha());
    }
    public void drawString(MatrixStack stack, String s, float x, float y, float r, float g, float b, float a) { drawString(stack, s, x, y, r, g, b, a, false, 0); }

    public void drawString(MatrixStack stack, String s, float x, float y, float r, float g, float b, float a, boolean gradient, int offset) {
        if (prebakeGlyphsFuture != null && !prebakeGlyphsFuture.isDone()) { try { prebakeGlyphsFuture.get(); } catch (Exception ignored) {} }
        sizeCheck(); float r2 = r, g2 = g, b2 = b;
        stack.push(); y -= 3f; stack.translate(x, y, 0); stack.scale(1f / scaleMul, 1f / scaleMul, 1f);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        Matrix4f mat = stack.peek().getPositionMatrix(); char[] chars = s.toCharArray();
        float xOff = 0, yOff = 0; boolean inSel = false; int lineStart = 0;
        synchronized (GLYPH_PAGE_CACHE) {
            for (int i = 0; i < chars.length; i++) {
                char c = chars[i];
                if (inSel) { inSel = false; char c1 = Character.toUpperCase(c);
                    if (colorCodes.containsKey(c1)) { int[] col = RGBIntToRGB(colorCodes.get(c1)); r2 = col[0]/255f; g2 = col[1]/255f; b2 = col[2]/255f; }
                    else if (c1 == 'R') { r2 = r; g2 = g; b2 = b; } continue; }
                if (c == '\u00a7') { inSel = true; continue; }
                if (c == '\n') { yOff += getStringHeight(s.substring(lineStart, i)) * scaleMul; xOff = 0; lineStart = i + 1; continue; }
                Glyph glyph = locateGlyph1(c);
                if (glyph != null) { if (glyph.value() != ' ') GLYPH_PAGE_CACHE.computeIfAbsent(glyph.owner().bindToTexture, k -> new ObjectArrayList<>()).add(new DrawEntry(xOff, yOff, r2, g2, b2, glyph)); xOff += glyph.width(); }
            }
            for (Identifier id : GLYPH_PAGE_CACHE.keySet()) {
                RenderSystem.setShaderTexture(0, id);
                BufferBuilder bb = Tessellator.getInstance().getBuffer(); bb.begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                for (DrawEntry obj : GLYPH_PAGE_CACHE.get(id)) {
                    Glyph gl = obj.toDraw; GlyphMap ow = gl.owner();
                    float u1 = (float) gl.u() / ow.width, v1 = (float) gl.v() / ow.height, u2 = (float) (gl.u() + gl.width()) / ow.width, v2 = (float) (gl.v() + gl.height()) / ow.height;
                    bb.vertex(mat, obj.atX, obj.atY + gl.height(), 0).texture(u1, v2).color(obj.r, obj.g, obj.b, a).next();
                    bb.vertex(mat, obj.atX + gl.width(), obj.atY + gl.height(), 0).texture(u2, v2).color(obj.r, obj.g, obj.b, a).next();
                    bb.vertex(mat, obj.atX + gl.width(), obj.atY, 0).texture(u2, v1).color(obj.r, obj.g, obj.b, a).next();
                    bb.vertex(mat, obj.atX, obj.atY, 0).texture(u1, v1).color(obj.r, obj.g, obj.b, a).next();
                }
                BufferRenderer.drawWithGlobalProgram(bb.end());
            }
            GLYPH_PAGE_CACHE.clear();
        }
        stack.pop();
    }

    public void drawCenteredString(MatrixStack stack, String s, double x, double y, int color) {
        drawString(stack, s, (float)(x - getStringWidth(s) / 2f), (float) y, ((color>>16)&0xff)/255f, ((color>>8)&0xff)/255f, (color&0xff)/255f, ((color>>24)&0xff)/255f);
    }
    public void drawCenteredString(MatrixStack stack, String s, double x, double y, Color color) { drawString(stack, s, (float)(x - getStringWidth(s) / 2f), (float) y, color.getRed()/255f, color.getGreen()/255f, color.getBlue()/255f, color.getAlpha()/255f); }
    public void drawCenteredString(MatrixStack stack, String s, float x, float y, float r, float g, float b, float a) { drawString(stack, s, x - getStringWidth(s) / 2f, y, r, g, b, a); }
    public void drawCenteredYString(MatrixStack ms, String s, double x, double y, Color c) { drawString(ms, s, x, y + getStringHeight(s), c.getRGB()); }

    public float getStringWidth(String text) {
        char[] c = stripControlCodes(text).toCharArray(); float cur = 0, max = 0;
        for (char c1 : c) { if (c1 == '\n') { max = Math.max(cur, max); cur = 0; continue; } Glyph g = locateGlyph1(c1); cur += (g == null ? 0 : g.width()) / (float) scaleMul; }
        return Math.max(cur, max);
    }

    public float getStringHeight(String text) {
        char[] c = stripControlCodes(text).toCharArray(); if (c.length == 0) c = new char[]{' '};
        float cur = 0, prev = 0;
        for (char c1 : c) { if (c1 == '\n') { if (cur == 0) cur = locateGlyph1(' ').height() / (float) scaleMul; prev += cur; cur = 0; continue; } Glyph g = locateGlyph1(c1); cur = Math.max((g == null ? 0 : g.height()) / (float) scaleMul, cur); }
        return cur + prev;
    }

    public Font getFont() { return font; }
    public float getOriginalSize() { return originalSize; }

    @Override public void close() {
        try { if (prebakeGlyphsFuture != null && !prebakeGlyphsFuture.isDone() && !prebakeGlyphsFuture.isCancelled()) { prebakeGlyphsFuture.cancel(true); try { prebakeGlyphsFuture.get(); } catch (Exception ignored) {} prebakeGlyphsFuture = null; }
        for (GlyphMap m : maps) m.destroy(); maps.clear(); allGlyphs.clear(); initialized = false; } catch (Exception e) { e.printStackTrace(); }
    }

    @Contract(value = "-> new", pure = true) public static @NotNull Identifier randomIdentifier() { return Identifier.of("universalmod", "temp/" + randomString(32)); }
    private static String randomString(int len) { return IntStream.range(0, len).mapToObj(i -> String.valueOf((char) new Random().nextInt('a', 'z' + 1))).collect(Collectors.joining()); }
    @Contract(value = "_ -> new", pure = true) public static int @NotNull [] RGBIntToRGB(int in) { return new int[]{in >> 16 & 0xFF, in >> 8 & 0xFF, in & 0xFF}; }
    public float getFontHeight(String str) { return getStringHeight(str); }
    public void drawGradientString(MatrixStack stack, String s, float x, float y, int offset) { drawString(stack, s, x, y, 255, 255, 255, 255, true, offset); }
    record DrawEntry(float atX, float atY, float r, float g, float b, Glyph toDraw) {}
}
