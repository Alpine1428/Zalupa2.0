package me.zyouime.zalupareport.render.font;

import me.zyouime.zalupareport.client.ZalupareportClient;
import org.jetbrains.annotations.NotNull;
import java.awt.*;
import java.io.IOException;
import java.util.Objects;

public class FontRenderers {
    public static FontRenderer mainFont;
    public static @NotNull FontRenderer create(String font, float size) throws IOException, FontFormatException {
        return new FontRenderer(Font.createFont(Font.TRUETYPE_FONT, Objects.requireNonNull(
                ZalupareportClient.class.getClassLoader().getResourceAsStream("assets/zalupareport/fonts/" + font + ".ttf")))
                .deriveFont(Font.PLAIN, size / 2f), size / 2f);
    }
}
