package me.zyouime.zalupareport.render.font;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import me.zyouime.zalupareport.mixin.INativeImageMixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryUtil;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

class GlyphMap {
    final char fromIncl, toExcl; final Font font; final Identifier bindToTexture; final int pixelPadding;
    private final Char2ObjectArrayMap<Glyph> glyphs = new Char2ObjectArrayMap<>();
    int width, height; boolean generated = false;
    public GlyphMap(char from, char to, Font font, Identifier id, int padding) { fromIncl = from; toExcl = to; this.font = font; bindToTexture = id; pixelPadding = padding; }
    public Glyph getGlyph(char c) { if (!generated) generate(); return glyphs.get(c); }
    public void destroy() { MinecraftClient.getInstance().getTextureManager().destroyTexture(bindToTexture); glyphs.clear(); width = -1; height = -1; generated = false; }
    public boolean contains(char c) { return c >= fromIncl && c < toExcl; }
    private Font getFontForGlyph(char c) { font.canDisplay(c); return font; }
    public void generate() {
        if (generated) return;
        int range = toExcl - fromIncl - 1; int charsVert = (int) (Math.ceil(Math.sqrt(range)) * 1.5);
        glyphs.clear(); int gen = 0, charNX = 0, maxX = 0, maxY = 0, curX = 0, curY = 0, curRowMaxY = 0;
        List<Glyph> gl = new ArrayList<>(); AffineTransform af = new AffineTransform(); FontRenderContext frc = new FontRenderContext(af, true, false);
        while (gen <= range) {
            char cc = (char) (fromIncl + gen); Font f = getFontForGlyph(cc); Rectangle2D sb = f.getStringBounds(String.valueOf(cc), frc);
            int w = (int) Math.ceil(sb.getWidth()), h = (int) Math.ceil(sb.getHeight()); gen++;
            maxX = Math.max(maxX, curX + w); maxY = Math.max(maxY, curY + h);
            if (charNX >= charsVert) { curX = 0; curY += curRowMaxY + pixelPadding; charNX = 0; curRowMaxY = 0; }
            curRowMaxY = Math.max(curRowMaxY, h); gl.add(new Glyph(curX, curY, w, h, cc, this)); curX += w + pixelPadding; charNX++;
        }
        BufferedImage bi = new BufferedImage(Math.max(maxX + pixelPadding, 1), Math.max(maxY + pixelPadding, 1), BufferedImage.TYPE_INT_ARGB);
        width = bi.getWidth(); height = bi.getHeight();
        Graphics2D g2d = bi.createGraphics(); g2d.setColor(new Color(255, 255, 255, 0)); g2d.fillRect(0, 0, width, height); g2d.setColor(Color.WHITE);
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (Glyph glyph : gl) { g2d.setFont(getFontForGlyph(glyph.value())); FontMetrics fm = g2d.getFontMetrics(); g2d.drawString(String.valueOf(glyph.value()), glyph.u(), glyph.v() + fm.getAscent()); glyphs.put(glyph.value(), glyph); }
        registerBufferedImageTexture(bindToTexture, bi); generated = true;
    }
    public static void registerBufferedImageTexture(Identifier i, BufferedImage bi) {
        try {
            int ow = bi.getWidth(), oh = bi.getHeight();
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, ow, oh, false);
            @SuppressWarnings("DataFlowIssue") long ptr = ((INativeImageMixin) (Object) image).getPointer();
            IntBuffer buf = MemoryUtil.memIntBuffer(ptr, image.getWidth() * image.getHeight());
            WritableRaster ra = bi.getRaster(); ColorModel cm = bi.getColorModel(); int nb = ra.getNumBands(); int dt = ra.getDataBuffer().getDataType();
            Object d = switch (dt) { case DataBuffer.TYPE_BYTE -> new byte[nb]; case DataBuffer.TYPE_USHORT -> new short[nb]; case DataBuffer.TYPE_INT -> new int[nb]; case DataBuffer.TYPE_FLOAT -> new float[nb]; case DataBuffer.TYPE_DOUBLE -> new double[nb]; default -> throw new IllegalArgumentException("Unknown: " + dt); };
            for (int y = 0; y < oh; y++) for (int x = 0; x < ow; x++) { ra.getDataElements(x, y, d); buf.put(cm.getAlpha(d) << 24 | cm.getBlue(d) << 16 | cm.getGreen(d) << 8 | cm.getRed(d)); }
            NativeImageBackedTexture tex = new NativeImageBackedTexture(image); tex.upload();
            if (RenderSystem.isOnRenderThread()) MinecraftClient.getInstance().getTextureManager().registerTexture(i, tex);
            else RenderSystem.recordRenderCall(() -> MinecraftClient.getInstance().getTextureManager().registerTexture(i, tex));
        } catch (Throwable e) { e.printStackTrace(); }
    }
}
