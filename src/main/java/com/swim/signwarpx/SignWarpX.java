package com.swim.signwarpx;

import com.swim.signwarpx.Commands.SWCommand;
import com.swim.signwarpx.gui.WarpGuiListener;
import com.swim.signwarpx.utils.ApiVersionManager;
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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class SignWarpX extends JavaPlugin implements Listener {
    private final String currentVersion = "1.2.7"; // 請在此處填寫插件版本
    private WebApiManager webApiManager;

    // 添加靜態方法供快速存取
    public static SignWarpX getInstance() {
        return JavaPlugin.getPlugin(SignWarpX.class);
    }

    public void onEnable() {
        // Initialize API version manager first
        ApiVersionManager.getInstance();
        
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
        
        // 檢查並更新玩家名稱（異步執行避免阻塞主線程）
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> updatePlayerNameIfChanged(player));
        
        if (player.isOp()) {
            VersionCheckerUtils.checkVersion(this, currentVersion, player);
            // 只對OP顯示歡迎訊息
            Bukkit.getScheduler().runTaskLaterAsynchronously(this, () -> player.sendMessage("§aWelcome to Swim's §bSignWarpX"), 20L); // 20 ticks = 1 秒
        }
    }

    /**
     * 檢查並更新玩家名稱（如果有更改）
     */
    private void updatePlayerNameIfChanged(Player player) {
        String currentName = player.getName();
        String playerUuid = player.getUniqueId().toString();
        
        // 從資料庫中查詢是否存在該 UUID 的記錄
        boolean nameChanged = checkPlayerNameChanged(playerUuid, currentName);
        
        if (nameChanged) {
            getLogger().info("Updating player name for UUID " + playerUuid + " to: " + currentName);
            
            // 更新所有相關表格中的玩家名稱
            TeleportHistory.updatePlayerName(playerUuid, currentName);
            Warp.updatePlayerName(playerUuid, currentName);
            WarpGroup.updatePlayerName(playerUuid, currentName);
            
            getLogger().info("Player name update completed for: " + currentName);
        }
    }

    /**
     * 檢查玩家名稱是否有更改
     */
    private boolean checkPlayerNameChanged(String playerUuid, String currentName) {
        // 從傳送歷史中查詢最近的玩家名稱記錄
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder() + File.separator + "warps.db")) {
            String sql = "SELECT player_name FROM teleport_history WHERE player_uuid = ? ORDER BY teleported_at DESC LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    String storedName = rs.getString("player_name");
                    return !currentName.equals(storedName);
                }
            }
            
            // 如果傳送歷史中沒有記錄，檢查 warps 表
            String warpSql = "SELECT creator FROM warps WHERE creator_uuid = ? LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(warpSql)) {
                pstmt.setString(1, playerUuid);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    String storedName = rs.getString("creator");
                    return !currentName.equals(storedName);
                }
            }
            
            // 如果 warps 表中也沒有記錄，檢查群組表
            String groupSql = "SELECT owner_name FROM warp_groups WHERE owner_uuid = ? LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(groupSql)) {
                pstmt.setString(1, playerUuid);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    String storedName = rs.getString("owner_name");
                    return !currentName.equals(storedName);
                }
            }
            
        } catch (SQLException e) {
            getLogger().warning("Failed to check player name change: " + e.getMessage());
        }
        
        // 如果沒有找到任何記錄，返回 false（不需要更新）
        return false;
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

    // 添加獲取版本的方法
    public String getCurrentVersion() {
        return this.currentVersion;
    }
}