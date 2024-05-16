package org.pythonchik.dfmodels;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class Dfmodels extends JavaPlugin {
    public static Message message;
    private static FileConfiguration config;
    public static Message getMessage(){return message;}
    Plugin plugin = this;

    @Override
    public void onEnable() {
        // Plugin startup logic
        loadConfig();
        message = new Message(this);
        getCommand("model").setExecutor(new dfmodel(this,config));
        getCommand("model").setTabCompleter(new dfmodel(this,config));
        getServer().getPluginManager().registerEvents(new dfmodel(this,config),this);
    }

    @Override
    public void onDisable() {
        saveConfig();
    }
    public void loadConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }
        config = null;
        config = YamlConfiguration.loadConfiguration(configFile);
    }
    public void saveConfig() {
        try {
            config.save(new File(getDataFolder(), "config.yml"));
        } catch (Exception ignored) {}
    }
}
