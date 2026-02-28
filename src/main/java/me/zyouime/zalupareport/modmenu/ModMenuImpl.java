package me.zyouime.zalupareport.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.autoconfig.AutoConfig;
import me.zyouime.zalupareport.config.ModConfig;

public class ModMenuImpl implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return f -> AutoConfig.getConfigScreen(ModConfig.class, f).get();
    }
}
