package com.swim.signwarpx;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public record TeleportHistory(String playerName, String playerUuid, String warpName, String fromWorld, String toWorld,
                              String teleportedAt) {
    private static final String DB_URL = "jdbc:sqlite:" + JavaPlugin.getPlugin(SignWarpX.class).getDataFolder() + File.separator + "warps.db";
    private static final Logger logger = JavaPlugin.getPlugin(SignWarpX.class).getLogger();

    /**
     * 創建傳送歷史表
     */
    public static void createTable() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "CREATE TABLE IF NOT EXISTS teleport_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "player_name TEXT NOT NULL, " +
                    "player_uuid TEXT NOT NULL, " +
                    "warp_name TEXT NOT NULL, " +
                    "from_world TEXT NOT NULL, " +
                    "to_world TEXT NOT NULL, " +
                    "teleported_at TEXT NOT NULL" +
                    ")";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }

            // 創建索引以提高查詢效能
            String indexSql1 = "CREATE INDEX IF NOT EXISTS idx_teleport_history_player_uuid ON teleport_history(player_uuid)";
            String indexSql2 = "CREATE INDEX IF NOT EXISTS idx_teleport_history_warp_name ON teleport_history(warp_name)";
            String indexSql3 = "CREATE INDEX IF NOT EXISTS idx_teleport_history_teleported_at ON teleport_history(teleported_at)";

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(indexSql1);
                stmt.execute(indexSql2);
                stmt.execute(indexSql3);
            }
        } catch (SQLException e) {
            logger.severe("Failed to create teleport_history table: " + e.getMessage());
            if (logger.isLoggable(Level.FINE)) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                logger.fine(sw.toString());
            }
        }
    }

    /**
     * 記錄傳送歷史
     */
    public static void recordTeleport(String playerName, String playerUuid, String warpName, String fromWorld, String toWorld) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "INSERT INTO teleport_history (player_name, player_uuid, warp_name, from_world, to_world, teleported_at) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerName);
                pstmt.setString(2, playerUuid);
                pstmt.setString(3, warpName);
                pstmt.setString(4, fromWorld);
                pstmt.setString(5, toWorld);
                pstmt.setString(6, LocalDateTime.now().toString());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Failed to record teleport history: " + e.getMessage());
        }
    }

    /**
     * 獲取總傳送統計
     */
    public static Map<String, Object> getTotalStats() {
        Map<String, Object> stats = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // 總傳送次數
            String totalSql = "SELECT COUNT(*) as total FROM teleport_history";
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(totalSql);
                if (rs.next()) {
                    stats.put("totalTeleports", rs.getInt("total"));
                }
            }

            // 今日傳送次數
            String todaySql = "SELECT COUNT(*) as today FROM teleport_history WHERE DATE(teleported_at) = DATE('now')";
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(todaySql);
                if (rs.next()) {
                    stats.put("todayTeleports", rs.getInt("today"));
                }
            }

            // 本週傳送次數
            String weekSql = "SELECT COUNT(*) as week FROM teleport_history WHERE DATE(teleported_at) >= DATE('now', '-7 days')";
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(weekSql);
                if (rs.next()) {
                    stats.put("weekTeleports", rs.getInt("week"));
                }
            }

            // 獨特玩家數量
            String uniquePlayersSql = "SELECT COUNT(DISTINCT player_uuid) as unique_players FROM teleport_history";
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(uniquePlayersSql);
                if (rs.next()) {
                    stats.put("uniquePlayers", rs.getInt("unique_players"));
                }
            }

        } catch (SQLException e) {
            logger.severe("Failed to get total teleport stats: " + e.getMessage());
        }
        return stats;
    }

    /**
     * 獲取最受歡迎的傳送點統計
     */
    public static List<Map<String, Object>> getPopularWarps(int limit) {
        List<Map<String, Object>> popularWarps = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT warp_name, COUNT(*) as usage_count FROM teleport_history GROUP BY warp_name ORDER BY usage_count DESC LIMIT ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, limit);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    Map<String, Object> warpStat = new HashMap<>();
                    warpStat.put("warpName", rs.getString("warp_name"));
                    warpStat.put("usageCount", rs.getInt("usage_count"));
                    popularWarps.add(warpStat);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get popular warps: " + e.getMessage());
        }
        return popularWarps;
    }

    /**
     * 獲取最活躍的玩家統計
     */
    public static List<Map<String, Object>> getActiveUsers(int limit) {
        List<Map<String, Object>> activeUsers = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT player_name, player_uuid, COUNT(*) as teleport_count FROM teleport_history GROUP BY player_uuid ORDER BY teleport_count DESC LIMIT ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, limit);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    Map<String, Object> userStat = new HashMap<>();
                    userStat.put("playerName", rs.getString("player_name"));
                    userStat.put("playerUuid", rs.getString("player_uuid"));
                    userStat.put("teleportCount", rs.getInt("teleport_count"));
                    activeUsers.add(userStat);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get active users: " + e.getMessage());
        }
        return activeUsers;
    }

    /**
     * 獲取每日傳送統計（最近N天）
     */
    public static List<Map<String, Object>> getDailyStats(int days) {
        List<Map<String, Object>> dailyStats = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT DATE(teleported_at) as date, COUNT(*) as count FROM teleport_history " +
                    "WHERE DATE(teleported_at) >= DATE('now', '-' || ? || ' days') " +
                    "GROUP BY DATE(teleported_at) ORDER BY date DESC";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, days);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    Map<String, Object> dayStat = new HashMap<>();
                    dayStat.put("date", rs.getString("date"));
                    dayStat.put("count", rs.getInt("count"));
                    dailyStats.add(dayStat);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get daily stats: " + e.getMessage());
        }
        return dailyStats;
    }

    /**
     * 獲取世界間傳送統計
     */
    public static List<Map<String, Object>> getWorldStats() {
        List<Map<String, Object>> worldStats = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT from_world, to_world, COUNT(*) as count FROM teleport_history " +
                    "GROUP BY from_world, to_world ORDER BY count DESC";
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);
                while (rs.next()) {
                    Map<String, Object> worldStat = new HashMap<>();
                    worldStat.put("fromWorld", rs.getString("from_world"));
                    worldStat.put("toWorld", rs.getString("to_world"));
                    worldStat.put("count", rs.getInt("count"));
                    worldStats.add(worldStat);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get world stats: " + e.getMessage());
        }
        return worldStats;
    }

    /**
     * 獲取最近的傳送記錄
     */
    public static List<TeleportHistory> getRecentTeleports(int limit) {
        List<TeleportHistory> recentTeleports = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM teleport_history ORDER BY teleported_at DESC LIMIT ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, limit);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    TeleportHistory history = new TeleportHistory(
                            rs.getString("player_name"),
                            rs.getString("player_uuid"),
                            rs.getString("warp_name"),
                            rs.getString("from_world"),
                            rs.getString("to_world"),
                            rs.getString("teleported_at")
                    );
                    recentTeleports.add(history);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get recent teleports: " + e.getMessage());
        }
        return recentTeleports;
    }

    /**
     * 獲取特定傳送點的傳送歷史
     */
    public static List<TeleportHistory> getWarpHistory(String warpName, int limit) {
        List<TeleportHistory> warpHistory = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM teleport_history WHERE warp_name = ? ORDER BY teleported_at DESC LIMIT ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, warpName);
                pstmt.setInt(2, limit);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    TeleportHistory history = new TeleportHistory(
                            rs.getString("player_name"),
                            rs.getString("player_uuid"),
                            rs.getString("warp_name"),
                            rs.getString("from_world"),
                            rs.getString("to_world"),
                            rs.getString("teleported_at")
                    );
                    warpHistory.add(history);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get warp history: " + e.getMessage());
        }
        return warpHistory;
    }

    /**
     * 獲取特定玩家在指定傳送點的傳送歷史
     */
    public static List<TeleportHistory> getWarpHistoryForPlayer(String warpName, String playerUuid, int limit) {
        List<TeleportHistory> warpHistory = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM teleport_history WHERE warp_name = ? AND player_uuid = ? ORDER BY teleported_at DESC LIMIT ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, warpName);
                pstmt.setString(2, playerUuid);
                pstmt.setInt(3, limit);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    TeleportHistory history = new TeleportHistory(
                            rs.getString("player_name"),
                            rs.getString("player_uuid"),
                            rs.getString("warp_name"),
                            rs.getString("from_world"),
                            rs.getString("to_world"),
                            rs.getString("teleported_at")
                    );
                    warpHistory.add(history);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get warp history for player: " + e.getMessage());
        }
        return warpHistory;
    }

    /**
     * 獲取玩家的傳送歷史
     */
    public static List<TeleportHistory> getPlayerHistory(String playerUuid, int limit) {
        List<TeleportHistory> playerHistory = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM teleport_history WHERE player_uuid = ? ORDER BY teleported_at DESC LIMIT ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid);
                pstmt.setInt(2, limit);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    TeleportHistory history = new TeleportHistory(
                            rs.getString("player_name"),
                            rs.getString("player_uuid"),
                            rs.getString("warp_name"),
                            rs.getString("from_world"),
                            rs.getString("to_world"),
                            rs.getString("teleported_at")
                    );
                    playerHistory.add(history);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get player history: " + e.getMessage());
        }
        return playerHistory;
    }

    /**
     * 獲取時段統計（24小時分布）
     */
    public static List<Map<String, Object>> getHourlyStats() {
        List<Map<String, Object>> hourlyStats = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT strftime('%H', teleported_at) as hour, COUNT(*) as count " +
                    "FROM teleport_history " +
                    "GROUP BY strftime('%H', teleported_at) " +
                    "ORDER BY hour";
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);
                while (rs.next()) {
                    Map<String, Object> hourlyStat = new HashMap<>();
                    hourlyStat.put("hour", rs.getString("hour"));
                    hourlyStat.put("count", rs.getInt("count"));
                    hourlyStats.add(hourlyStat);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get hourly stats: " + e.getMessage());
        }
        return hourlyStats;
    }

    /**
     * 獲取週間統計（星期分布）
     */
    public static List<Map<String, Object>> getWeeklyStats() {
        List<Map<String, Object>> weeklyStats = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT " +
                    "CASE strftime('%w', teleported_at) " +
                    "WHEN '0' THEN '星期日' " +
                    "WHEN '1' THEN '星期一' " +
                    "WHEN '2' THEN '星期二' " +
                    "WHEN '3' THEN '星期三' " +
                    "WHEN '4' THEN '星期四' " +
                    "WHEN '5' THEN '星期五' " +
                    "WHEN '6' THEN '星期六' " +
                    "END as day_name, " +
                    "strftime('%w', teleported_at) as day_num, " +
                    "COUNT(*) as count " +
                    "FROM teleport_history " +
                    "GROUP BY strftime('%w', teleported_at) " +
                    "ORDER BY day_num";
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);
                while (rs.next()) {
                    Map<String, Object> weeklyStat = new HashMap<>();
                    weeklyStat.put("dayName", rs.getString("day_name"));
                    weeklyStat.put("dayNum", rs.getInt("day_num"));
                    weeklyStat.put("count", rs.getInt("count"));
                    weeklyStats.add(weeklyStat);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get weekly stats: " + e.getMessage());
        }
        return weeklyStats;
    }

    /**
     * 獲取跨維度傳送統計
     */
    public static Map<String, Object> getCrossDimensionStats() {
        Map<String, Object> stats = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // 跨維度傳送次數
            String crossDimensionSql = "SELECT COUNT(*) as count FROM teleport_history WHERE from_world != to_world";
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(crossDimensionSql);
                if (rs.next()) {
                    stats.put("crossDimensionCount", rs.getInt("count"));
                }
            }

            // 同維度傳送次數
            String sameDimensionSql = "SELECT COUNT(*) as count FROM teleport_history WHERE from_world = to_world";
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(sameDimensionSql);
                if (rs.next()) {
                    stats.put("sameDimensionCount", rs.getInt("count"));
                }
            }

            // 最受歡迎的跨維度路線
            String popularRoutesSql = "SELECT from_world, to_world, COUNT(*) as count " +
                    "FROM teleport_history " +
                    "WHERE from_world != to_world " +
                    "GROUP BY from_world, to_world " +
                    "ORDER BY count DESC LIMIT 5";
            List<Map<String, Object>> popularRoutes = new ArrayList<>();
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(popularRoutesSql);
                while (rs.next()) {
                    Map<String, Object> route = new HashMap<>();
                    route.put("fromWorld", rs.getString("from_world"));
                    route.put("toWorld", rs.getString("to_world"));
                    route.put("count", rs.getInt("count"));
                    popularRoutes.add(route);
                }
            }
            stats.put("popularRoutes", popularRoutes);

        } catch (SQLException e) {
            logger.severe("Failed to get cross dimension stats: " + e.getMessage());
        }
        return stats;
    }

    /**
     * 獲取月度統計（最近12個月）
     */
    public static List<Map<String, Object>> getMonthlyStats(int months) {
        List<Map<String, Object>> monthlyStats = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT strftime('%Y-%m', teleported_at) as month, COUNT(*) as count " +
                    "FROM teleport_history " +
                    "WHERE DATE(teleported_at) >= DATE('now', '-' || ? || ' months') " +
                    "GROUP BY strftime('%Y-%m', teleported_at) " +
                    "ORDER BY month DESC";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, months);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    Map<String, Object> monthlyStat = new HashMap<>();
                    monthlyStat.put("month", rs.getString("month"));
                    monthlyStat.put("count", rs.getInt("count"));
                    monthlyStats.add(monthlyStat);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get monthly stats: " + e.getMessage());
        }
        return monthlyStats;
    }

    /**
     * 獲取玩家活躍度統計
     */
    public static Map<String, Object> getPlayerActivityStats() {
        Map<String, Object> stats = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // 今日活躍玩家
            String todayActivePlayersSql = "SELECT COUNT(DISTINCT player_uuid) as count FROM teleport_history WHERE DATE(teleported_at) = DATE('now')";
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(todayActivePlayersSql);
                if (rs.next()) {
                    stats.put("todayActivePlayers", rs.getInt("count"));
                }
            }

            // 本週活躍玩家
            String weekActivePlayersSql = "SELECT COUNT(DISTINCT player_uuid) as count FROM teleport_history WHERE DATE(teleported_at) >= DATE('now', '-7 days')";
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(weekActivePlayersSql);
                if (rs.next()) {
                    stats.put("weekActivePlayers", rs.getInt("count"));
                }
            }

            // 本月活躍玩家
            String monthActivePlayersSql = "SELECT COUNT(DISTINCT player_uuid) as count FROM teleport_history WHERE DATE(teleported_at) >= DATE('now', '-30 days')";
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(monthActivePlayersSql);
                if (rs.next()) {
                    stats.put("monthActivePlayers", rs.getInt("count"));
                }
            }

            // 平均每日傳送次數
            String avgDailyTeleportsSql = "SELECT AVG(daily_count) as avg_daily FROM (" +
                    "SELECT DATE(teleported_at) as date, COUNT(*) as daily_count " +
                    "FROM teleport_history " +
                    "WHERE DATE(teleported_at) >= DATE('now', '-30 days') " +
                    "GROUP BY DATE(teleported_at)" +
                    ")";
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(avgDailyTeleportsSql);
                if (rs.next()) {
                    stats.put("avgDailyTeleports", Math.round(rs.getDouble("avg_daily") * 100.0) / 100.0);
                }
            }

        } catch (SQLException e) {
            logger.severe("Failed to get player activity stats: " + e.getMessage());
        }
        return stats;
    }

    /**
     * 獲取維度分佈統計（基於傳送點所在維度，而非傳送歷史）
     */
    public static List<Map<String, Object>> getDimensionStats() {
        List<Map<String, Object>> dimensionStats = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // 從 warps 表統計各維度的傳送點數量，而不是從傳送歷史
            String sql = "SELECT world as dimension, COUNT(*) as count " +
                    "FROM warps " +
                    "GROUP BY world " +
                    "ORDER BY count DESC";
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(sql);
                while (rs.next()) {
                    Map<String, Object> dimensionStat = new HashMap<>();
                    dimensionStat.put("dimension", rs.getString("dimension"));
                    dimensionStat.put("count", rs.getInt("count"));
                    dimensionStats.add(dimensionStat);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get dimension stats: " + e.getMessage());
        }
        return dimensionStats;
    }

    /**
     * 獲取傳送點使用趨勢
     */
    public static List<Map<String, Object>> getWarpUsageTrend(String warpName, int days) {
        List<Map<String, Object>> trendData = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT DATE(teleported_at) as date, COUNT(*) as count " +
                    "FROM teleport_history " +
                    "WHERE warp_name = ? AND DATE(teleported_at) >= DATE('now', '-' || ? || ' days') " +
                    "GROUP BY DATE(teleported_at) " +
                    "ORDER BY date DESC";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, warpName);
                pstmt.setInt(2, days);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    Map<String, Object> trend = new HashMap<>();
                    trend.put("date", rs.getString("date"));
                    trend.put("count", rs.getInt("count"));
                    trendData.add(trend);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get warp usage trend: " + e.getMessage());
        }
        return trendData;
    }

    /**
     * 更新指定 UUID 的玩家名稱
     */
    public static void updatePlayerName(String playerUuid, String newPlayerName) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "UPDATE teleport_history SET player_name = ? WHERE player_uuid = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, newPlayerName);
                pstmt.setString(2, playerUuid);
                int updated = pstmt.executeUpdate();
                if (updated > 0) {
                    logger.info("Updated " + updated + " teleport history records for player: " + newPlayerName + " (UUID: " + playerUuid + ")");
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to update player name in teleport history: " + e.getMessage());
        }
    }

    public String getFormattedTeleportedAt() {
        try {
            JavaPlugin plugin = JavaPlugin.getPlugin(SignWarpX.class);
            String pattern = plugin.getConfig().getString("messages.date_time_format", "MM/dd/yyyy | hh:mm:ss a");
            
            LocalDateTime dateTime = LocalDateTime.parse(teleportedAt);
            
            // Check if pattern contains literal Chinese AM/PM text
            if (pattern.contains("上午下午")) {
                // Handle Chinese AM/PM format with literal text
                String basePattern = pattern.replace("上午下午", "");
                DateTimeFormatter baseFormatter = DateTimeFormatter.ofPattern(basePattern);
                String baseFormatted = dateTime.format(baseFormatter);
                
                // Determine if it's AM or PM
                int hour = dateTime.getHour();
                String ampm = (hour < 12) ? "上午" : "下午";
                
                return baseFormatted + ampm;
            } else if (pattern.contains("AM/PM")) {
                // Handle English AM/PM format with literal text
                String basePattern = pattern.replace("AM/PM", "");
                DateTimeFormatter baseFormatter = DateTimeFormatter.ofPattern(basePattern);
                String baseFormatted = dateTime.format(baseFormatter);
                
                // Determine if it's AM or PM
                int hour = dateTime.getHour();
                String ampm = (hour < 12) ? "AM" : "PM";
                
                return baseFormatted + ampm;
            } else {
                // Use standard Java formatting (supports 'a' for English AM/PM and other patterns)
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                return dateTime.format(formatter);
            }
        } catch (Exception e) {
            return teleportedAt;
        }
    }
}