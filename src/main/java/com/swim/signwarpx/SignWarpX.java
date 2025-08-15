package com.swim.signwarpx;

import com.swim.signwarpx.groups.WarpGroup;
import com.swim.signwarpx.gui.WarpGuiListener;
import com.swim.signwarpx.utils.VersionCheckerUtils;
import com.swim.signwarpx.web.WebApiManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

@SuppressWarnings("FieldCanBeLocal")
public final class SignWarpX extends JavaPlugin implements Listener {
    private final String currentVersion = "1.2.4"; // 請在此處填寫插件版本
    private WebApiManager webApiManager;

    // 添加靜態方法供快速存取
    public static SignWarpX getInstance() {
        return JavaPlugin.getPlugin(SignWarpX.class);
    }

    public void onEnable() {
        saveDefaultConfig();
        setupFiles();
        setupDatabase();
        setupCommands();
        setupListeners();
        setupWebServer();
        VersionCheckerUtils.checkVersion(this, currentVersion, null);
    }

    private void setupDatabase() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            Warp.createTable();
            Warp.createInvitesTable();
            WarpGroup.createTables();
            TeleportHistory.createTable();
        });
    }

    private void setupCommands() {
        PluginCommand command = getCommand("signwarp");
        if (command != null) {
            SWCommand swCommand = new SWCommand(this);
            command.setExecutor(swCommand);
            command.setTabCompleter(swCommand);
        } else {
            getLogger().warning("Command 'signwarp' not found!");
        }
    }

    private void setupListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new EventListener(this), this);
        pluginManager.registerEvents(new WarpGuiListener(this), this);
        pluginManager.registerEvents(this, this);
    }

    private void setupWebServer() {
        if (getConfig().getBoolean("web.enabled", false)) {
            try {
                this.webApiManager = new WebApiManager(this);
                this.webApiManager.startWebServer();
                getLogger().info("Web server started.");
            } catch (Exception e) {
                getLogger().severe("Failed to start web server: " + e.getMessage());
            }
        } else {
            getLogger().info("Web server is disabled in the config.");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) {
            VersionCheckerUtils.checkVersion(this, currentVersion, player);
        }
        // Run this task asynchronously to avoid blocking the main thread.
        Bukkit.getScheduler().runTaskLaterAsynchronously(this, () -> player.sendMessage("§a歡迎使用Swim的§bSignWarpX"), 20L); // 20 ticks = 1 秒
    }

    private void setupFiles() {
        saveResourceIfNotExists("web/index.html");
        saveResourceIfNotExists("web/app.js");
        saveResourceIfNotExists("languages/config-zh_tw.yml");
        saveResourceIfNotExists("languages/config-en_us.yml");
    }

    private void saveResourceIfNotExists(String resourcePath) {
        File resourceFile = new File(getDataFolder(), resourcePath);
        if (!resourceFile.getParentFile().exists()) {
            if (!resourceFile.getParentFile().mkdirs()) {
                getLogger().warning("Could not create parent directories for " + resourceFile.getAbsolutePath());
                return;
            }
        }

        if (!resourceFile.exists()) {
            try {
                saveResource(resourcePath, false);
            } catch (Exception e) {
                getLogger().warning("Could not save resource file: " + e.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        if (this.webApiManager != null) {
            try {
                this.webApiManager.stopWebServer();
            } catch (Exception ignored) {
            }
        }
    }

    // 添加這個方法供其他類別使用
    public WebApiManager getWebApiManager() {
        return this.webApiManager;
    }

    // 添加設置 WebApiManager 的方法
    public void setWebApiManager(WebApiManager webApiManager) {
        this.webApiManager = webApiManager;
    }
}
