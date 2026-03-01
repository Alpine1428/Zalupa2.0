package me.zyouime.zalupareport.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import java.util.List;

@Config(name = "zalupareport")
public class ModConfig implements ConfigData {
    public String screenName = "\u0416\u0430\u043b\u043e\u0431\u044b \u043d\u0430 \u0438\u0433\u0440\u043e\u043a\u043e\u0432";
    @ConfigEntry.Gui.CollapsibleObject
    public PlayTime playTime = new PlayTime();
    public boolean clearIsClose = true;
    public boolean printPlayTime = false;
    @ConfigEntry.Gui.Excluded
    public float x = -300;
    @ConfigEntry.Gui.Excluded
    public float y = -50;
    @ConfigEntry.BoundedDiscrete(min = 100, max = 200)
    public int height = 200;
    public boolean autoSpy = false;
    
    @ConfigEntry.Gui.Excluded
    public boolean autoCall = false; // АвтоВызов (1 раз)
    
    @ConfigEntry.Gui.Excluded
    public boolean autoCheck = false; // АвтоПроверка (цикл)

    public List<String> detects = List.of("CL-BP", "I-B", "MT-PC", "AimANLS");

    public static class PlayTime {
        public int allTime = 120;
        public boolean checkAllTime = false;
        public int activeTime = 120;
        public boolean checkActiveTime = true;
    }
}
