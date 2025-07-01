package com.swim.signwarp;

import com.swim.signwarp.gui.WarpGuiListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class SignWarp extends JavaPlugin implements Listener {

    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        setupLanguageFiles();
        // Initialize database and migrate table if needed
        Warp.createTable();
        Warp.createInvitesTable();// Create the invites table if it doesn't exist
        WarpGroup.createTables();
        // Register commands and tab completer
        PluginCommand command = getCommand("signwarp");
        if (command != null) {
            SWCommand swCommand = new SWCommand(this);
            command.setExecutor(swCommand);
            command.setTabCompleter(swCommand);
        } else {
            getLogger().warning("Command 'signwarp' not found!");
        }
        // Register event listener
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new EventListener(this), this);
        pluginManager.registerEvents(new WarpGuiListener(this), this);
        pluginManager.registerEvents(this, this);

    }
    private void setupLanguageFiles() {
        File languagesDir = new File(getDataFolder(), "languages");

        // 檢查並建立 languages 目錄
        if (!languagesDir.exists()) {
            boolean created = languagesDir.mkdirs();
            if (!created) {
                getLogger().warning("無法建立 languages 資料夾");
                return; // 如果無法建立資料夾，就不繼續複製檔案
            }
        }

        // 複製語言檔案（如果不存在的話）
        File zhTwConfig = new File(languagesDir, "config-zh_TW.yml");
        if (!zhTwConfig.exists()) {
            try {
                saveResource("languages/config-zh_TW.yml", false);
            } catch (Exception e) {
                getLogger().warning("Failed to copy the language file: " + e.getMessage());
            }
        }
        File enUSConfig = new File(languagesDir, "config-en_US.yml");
        if (!enUSConfig.exists()) {
            try {
                saveResource("languages/config-en_US.yml", false);
            } catch (Exception e) {
                getLogger().warning("Failed to copy the language file: " + e.getMessage());
            }
        }

        // 如果日後要添加其他語言檔案，可以在這裡繼續添加
        // 例如：
        // File enConfig = new File(languagesDir, "config-en.yml");
        // if (!enConfig.exists()) {
        //     saveResource("languages/config-en.yml", false);
        // }
        }
    @Override
    public void onDisable() {
    }
}
