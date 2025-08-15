package com.swim.signwarpx.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.swim.signwarpx.SignWarpX;
import com.swim.signwarpx.TeleportHistory;
import com.swim.signwarpx.Warp;
import com.swim.signwarpx.WarpInvite;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import spark.Spark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.*;

public class WebApiManager {
    private final SignWarpX plugin;
    private final Gson gson;
    private final WebSocketHandler webSocketHandler;
    private final int port;

    public WebApiManager(SignWarpX plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .create();
        this.webSocketHandler = new WebSocketHandler();
        this.port = plugin.getConfig().getInt("web.port");
    }

    private static @NotNull List<Map<String, Object>> getMaps(List<TeleportHistory> recentTeleports) {
        List<Map<String, Object>> recentTeleportDTOs = new ArrayList<>();
        for (TeleportHistory history : recentTeleports) {
            Map<String, Object> dto = new HashMap<>();
            dto.put("playerName", history.playerName());
            dto.put("warpName", history.warpName());
            dto.put("fromWorld", history.fromWorld());
            dto.put("toWorld", history.toWorld());
            dto.put("teleportedAt", history.getFormattedTeleportedAt());
            recentTeleportDTOs.add(dto);
        }
        return recentTeleportDTOs;
    }

    public void startWebServer() {
        System.setProperty("org.eclipse.jetty.LEVEL", "WARN");
        try {
            // 第一階段：設定基本配置（這些必須在任何路由之前）
            port(this.port);
            externalStaticFileLocation("plugins/SignWarpX/web");
            threadPool(8, 2, 30000);

            // 第二階段：設定 WebSocket（必須在 REST 路由之前）
            webSocket("/ws", webSocketHandler);

            // 第三階段：設定 CORS 和路由
            setupCors();
            setupApiRoutes();

            // 第四階段：設定異常處理和啟動
            setupExceptionHandlers();
            awaitInitialization();
            plugin.getLogger().info("Web Panel : http://localhost:" + this.port);
            plugin.getLogger().info("WebSocket : ws://localhost:" + this.port + "/ws");
            plugin.getLogger().info("API : http://localhost:" + this.port + "/api/");

        } catch (Exception e) {
            plugin.getLogger().severe(e.getMessage());
            for (StackTraceElement element : e.getStackTrace()) {
                plugin.getLogger().severe(element.toString());
            }
        }
    }

    private void setupCors() {
        // 處理 CORS 預檢請求
        options("/*", (request, response) -> {
            String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }

            String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }

            return "OK";
        });

        // 設定 CORS 標頭
        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
            response.header("Access-Control-Max-Age", "3600");
        });
    }

    private void setupApiRoutes() {
        // 健康檢查端點
        get("/api/health", (req, res) -> {
            res.type("application/json");
            Map<String, Object> health = new HashMap<>();
            health.put("status", "OK");
            health.put("timestamp", System.currentTimeMillis());
            health.put("connections", webSocketHandler.getConnectionCount());
            return gson.toJson(health);
        });

        // 獲取所有傳送點（分頁）
        get("/api/warps", (req, res) -> {
            res.type("application/json");

            try {
                int page = Integer.parseInt(req.queryParamOrDefault("page", "0"));
                int size = Integer.parseInt(req.queryParamOrDefault("size", "45"));

                // 確保分頁參數有效
                page = Math.max(0, page);
                size = Math.max(1, Math.min(100, size)); // 限制每頁最多100項

                List<Warp> allWarps = Warp.getAll();
                int total = allWarps.size();
                int totalPages = (int) Math.ceil((double) total / size);

                int start = page * size;
                int end = Math.min(start + size, total);

                List<WarpDTO> warpDTOs = new ArrayList<>();
                for (int i = start; i < end; i++) {
                    if (i < allWarps.size()) {
                        warpDTOs.add(convertToDTO(allWarps.get(i)));
                    }
                }

                Map<String, Object> response = new HashMap<>();
                response.put("warps", warpDTOs);
                response.put("currentPage", page);
                response.put("totalPages", totalPages);
                response.put("totalElements", total);
                response.put("pageSize", size);
                response.put("hasNext", page < totalPages - 1);
                response.put("hasPrevious", page > 0);

                return gson.toJson(response);

            } catch (Exception e) {
                res.status(500);
                return gson.toJson(Map.of("error", "載入傳送點失敗: " + e.getMessage()));
            }
        });

        // 獲取單一傳送點詳細資訊
        get("/api/warps/:name", (req, res) -> {
            res.type("application/json");

            try {
                String warpName = req.params(":name");
                Warp warp = Warp.getByName(warpName);

                if (warp == null) {
                    res.status(404);
                    return gson.toJson(Map.of("error", "傳送點不存在"));
                }

                return gson.toJson(convertToDTO(warp));

            } catch (Exception e) {
                res.status(500);
                return gson.toJson(Map.of("error", "獲取傳送點失敗: " + e.getMessage()));
            }
        });

        // 獲取傳送歷史統計
        get("/api/teleport-stats", (req, res) -> {
            res.type("application/json");

            try {

                // 基本統計
                Map<String, Object> totalStats = TeleportHistory.getTotalStats();
                Map<String, Object> stats = new HashMap<>(totalStats);

                // 最受歡迎的傳送點 (前10名)
                List<Map<String, Object>> popularWarps = TeleportHistory.getPopularWarps(10);
                stats.put("popularWarps", popularWarps);

                // 最活躍的玩家 (前10名)
                List<Map<String, Object>> activeUsers = TeleportHistory.getActiveUsers(10);
                stats.put("activeUsers", activeUsers);

                // 每日統計 (最近7天)
                List<Map<String, Object>> dailyStats = TeleportHistory.getDailyStats(7);
                stats.put("dailyStats", dailyStats);

                // 世界間傳送統計
                List<Map<String, Object>> worldStats = TeleportHistory.getWorldStats();
                stats.put("worldStats", worldStats);

                // 最近的傳送記錄 (最近10筆)
                List<TeleportHistory> recentTeleports = TeleportHistory.getRecentTeleports(10);
                List<Map<String, Object>> recentTeleportDTOs = getMaps(recentTeleports);
                stats.put("recentTeleports", recentTeleportDTOs);

                stats.put("lastUpdated", System.currentTimeMillis());

                return gson.toJson(stats);

            } catch (Exception e) {
                res.status(500);
                return gson.toJson(Map.of("error", "獲取傳送統計失敗: " + e.getMessage()));
            }
        });

        // 獲取增強版統計資訊
        get("/api/enhanced-stats", (req, res) -> {
            res.type("application/json");

            try {

                // 基本統計
                Map<String, Object> totalStats = TeleportHistory.getTotalStats();
                Map<String, Object> enhancedStats = new HashMap<>(totalStats);

                // 時段統計
                List<Map<String, Object>> hourlyStats = TeleportHistory.getHourlyStats();
                enhancedStats.put("hourlyStats", hourlyStats);

                // 週間統計
                List<Map<String, Object>> weeklyStats = TeleportHistory.getWeeklyStats();
                enhancedStats.put("weeklyStats", weeklyStats);

                // 跨維度統計
                Map<String, Object> crossDimensionStats = TeleportHistory.getCrossDimensionStats();
                enhancedStats.put("crossDimensionStats", crossDimensionStats);

                // 維度分佈統計已移至前端計算，不再需要後端查詢

                // 月度統計
                List<Map<String, Object>> monthlyStats = TeleportHistory.getMonthlyStats(12);
                enhancedStats.put("monthlyStats", monthlyStats);

                // 玩家活躍度統計
                Map<String, Object> playerActivityStats = TeleportHistory.getPlayerActivityStats();
                enhancedStats.put("playerActivityStats", playerActivityStats);

                // 熱門傳送點（更多數據）
                List<Map<String, Object>> popularWarps = TeleportHistory.getPopularWarps(20);
                enhancedStats.put("popularWarps", popularWarps);

                // 活躍玩家（更多數據）
                List<Map<String, Object>> activeUsers = TeleportHistory.getActiveUsers(20);
                enhancedStats.put("activeUsers", activeUsers);

                // 每日統計（更長時間）
                List<Map<String, Object>> dailyStats = TeleportHistory.getDailyStats(30);
                enhancedStats.put("dailyStats", dailyStats);

                // 最近傳送記錄（更多數據）
                List<TeleportHistory> recentTeleports = TeleportHistory.getRecentTeleports(50);
                List<Map<String, Object>> recentTeleportDTOs = getMaps(recentTeleports);
                enhancedStats.put("recentTeleports", recentTeleportDTOs);

                enhancedStats.put("lastUpdated", System.currentTimeMillis());

                return gson.toJson(enhancedStats);

            } catch (Exception e) {
                res.status(500);
                return gson.toJson(Map.of("error", "獲取增強統計失敗: " + e.getMessage()));
            }
        });

        // 獲取特定傳送點的使用趨勢
        get("/api/warp-trend/:warpName", (req, res) -> {
            res.type("application/json");

            try {
                String warpName = req.params(":warpName");
                int days = Integer.parseInt(req.queryParamOrDefault("days", "30"));

                List<Map<String, Object>> trendData = TeleportHistory.getWarpUsageTrend(warpName, days);

                Map<String, Object> response = new HashMap<>();
                response.put("warpName", warpName);
                response.put("days", days);
                response.put("trendData", trendData);
                response.put("lastUpdated", System.currentTimeMillis());

                return gson.toJson(response);

            } catch (Exception e) {
                res.status(500);
                return gson.toJson(Map.of("error", "獲取傳送點趨勢失敗: " + e.getMessage()));
            }
        });

        // 獲取玩家在線狀態
        get("/api/players/online-status", (req, res) -> {
            res.type("application/json");

            try {
                Map<String, Boolean> onlineStatus = new HashMap<>();

                // 獲取所有在線玩家
                plugin.getServer().getOnlinePlayers().forEach(player -> onlineStatus.put(player.getName(), true));

                return gson.toJson(Map.of("onlineStatus", onlineStatus));

            } catch (Exception e) {
                res.status(500);
                return gson.toJson(Map.of("error", "獲取玩家狀態失敗: " + e.getMessage()));
            }
        });

        // 獲取玩家傳送歷史
        get("/api/player-history/:uuid", (req, res) -> {
            res.type("application/json");

            try {
                String playerUuid = req.params(":uuid");
                int limit = Integer.parseInt(req.queryParamOrDefault("limit", "50"));
                limit = Math.max(1, Math.min(200, limit)); // 限制在1-200之間

                List<TeleportHistory> playerHistory = TeleportHistory.getPlayerHistory(playerUuid, limit);
                List<Map<String, Object>> historyDTOs = getMaps(playerHistory);

                Map<String, Object> response = new HashMap<>();
                response.put("playerUuid", playerUuid);
                response.put("history", historyDTOs);
                response.put("totalRecords", historyDTOs.size());

                return gson.toJson(response);

            } catch (Exception e) {
                res.status(500);
                return gson.toJson(Map.of("error", "獲取玩家歷史失敗: " + e.getMessage()));
            }
        });

        // 獲取伺服器統計資訊
        get("/api/stats", (req, res) -> {
            res.type("application/json");

            try {
                List<Warp> allWarps = Warp.getAll();
                long publicWarps = allWarps.stream().filter(w -> !w.isPrivate()).count();
                long privateWarps = allWarps.stream().filter(Warp::isPrivate).count();

                // 統計各世界的傳送點數量
                Map<String, Long> worldStats = new HashMap<>();
                allWarps.forEach(warp -> {
                    String world = warp.getLocation().getWorld().getName();
                    worldStats.put(world, worldStats.getOrDefault(world, 0L) + 1);
                });

                Map<String, Object> stats = new HashMap<>();
                stats.put("totalWarps", allWarps.size());
                stats.put("publicWarps", publicWarps);
                stats.put("privateWarps", privateWarps);
                stats.put("worldStats", worldStats);
                stats.put("connections", webSocketHandler.getConnectionCount());
                stats.put("lastUpdated", System.currentTimeMillis());
                String host = req.headers("Host");
                String scheme = req.scheme();
                String apiUrl = scheme + "://" + host + "/api";
                String wsUrl = (scheme.equals("https") ? "wss" : "ws") + "://" + host + "/ws";
                stats.put("apiUrl", apiUrl);
                stats.put("wsUrl", wsUrl);
                return gson.toJson(stats);

            } catch (Exception e) {
                res.status(500);
                return gson.toJson(Map.of("error", "獲取統計資料失敗: " + e.getMessage()));
            }
        });

        // 獲取插件配置
        get("/api/config", (req, res) -> {
            res.type("application/json");

            try {
                Map<String, Object> config = new HashMap<>();

                // 網頁服務設定
                Map<String, Object> webConfig = new HashMap<>();
                webConfig.put("enabled", plugin.getConfig().getBoolean("web.enabled", false));
                webConfig.put("port", plugin.getConfig().getInt("web.port", 8080));
                config.put("web", webConfig);

                // 傳送設定
                Map<String, Object> teleportConfig = new HashMap<>();
                teleportConfig.put("useItem", plugin.getConfig().getString("use-item", "ENDER_PEARL"));
                teleportConfig.put("useCost", plugin.getConfig().getInt("use-cost", 1));
                teleportConfig.put("teleportDelay", plugin.getConfig().getInt("teleport-delay", 5));
                teleportConfig.put("teleportUseCooldown", plugin.getConfig().getInt("teleport-use-cooldown", 10));
                teleportConfig.put("maxLeashDepth", plugin.getConfig().getInt("max-leash-depth", 5));
                config.put("teleport", teleportConfig);

                // 跨維度傳送設定
                Map<String, Object> crossDimensionConfig = new HashMap<>();
                crossDimensionConfig.put("enabled", plugin.getConfig().getBoolean("cross-dimension-teleport.enabled", true));
                crossDimensionConfig.put("opBypass", plugin.getConfig().getBoolean("cross-dimension-teleport.op-bypass", true));
                config.put("crossDimensionTeleport", crossDimensionConfig);

                // 群組設定
                Map<String, Object> warpGroupsConfig = new HashMap<>();
                warpGroupsConfig.put("enabled", plugin.getConfig().getBoolean("warp-groups.enabled", true));
                warpGroupsConfig.put("maxGroupsPerPlayer", plugin.getConfig().getInt("warp-groups.max-groups-per-player", 5));
                warpGroupsConfig.put("maxWarpsPerGroup", plugin.getConfig().getInt("warp-groups.max-warps-per-group", 10));
                warpGroupsConfig.put("maxMembersPerGroup", plugin.getConfig().getInt("warp-groups.max-members-per-group", 20));
                warpGroupsConfig.put("allowNormalPlayers", plugin.getConfig().getBoolean("warp-groups.allow-normal-players", true));
                config.put("warpGroups", warpGroupsConfig);

                // 告示牌設定
                Map<String, Object> signConfig = new HashMap<>();
                signConfig.put("createWptItem", plugin.getConfig().getString("create-wpt-item", "DIAMOND"));
                signConfig.put("createWptItemCost", plugin.getConfig().getInt("create-wpt-item-cost", 1));
                signConfig.put("maxWarpsPerPlayer", plugin.getConfig().getInt("max-warps-per-player", 10));
                signConfig.put("opUnlimitedWarps", plugin.getConfig().getBoolean("op-unlimited-warps", true));
                signConfig.put("defaultVisibility", plugin.getConfig().getBoolean("default-visibility", false));
                signConfig.put("showCreatorOnSign", plugin.getConfig().getBoolean("show-creator-on-sign", true));
                config.put("sign", signConfig);

                // 世界顯示名稱設定
                Map<String, Object> worldDisplayNames = new HashMap<>();
                worldDisplayNames.put("world", plugin.getConfig().getString("world-display-names.world", "Overworld"));
                worldDisplayNames.put("world_nether", plugin.getConfig().getString("world-display-names.world_nether", "Nether"));
                worldDisplayNames.put("world_the_end", plugin.getConfig().getString("world-display-names.world_the_end", "The End"));
                config.put("worldDisplayNames", worldDisplayNames);

                return gson.toJson(config);

            } catch (Exception e) {
                res.status(500);
                return gson.toJson(Map.of("error", "載入配置失敗: " + e.getMessage()));
            }
        });

        // 儲存插件配置
        post("/api/config", (req, res) -> {
            res.type("application/json");

            try {
                // 解析請求的 JSON 配置
                @SuppressWarnings("unchecked")
                Map<String, Object> newConfig = gson.fromJson(req.body(), Map.class);

                // 更新網頁服務設定
                if (newConfig.containsKey("web")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> webConfig = (Map<String, Object>) newConfig.get("web");
                    if (webConfig.containsKey("enabled")) {
                        plugin.getConfig().set("web.enabled", webConfig.get("enabled"));
                    }
                    if (webConfig.containsKey("port")) {
                        plugin.getConfig().set("web.port", ((Double) webConfig.get("port")).intValue());
                    }
                }

                // 更新傳送設定
                if (newConfig.containsKey("teleport")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> teleportConfig = (Map<String, Object>) newConfig.get("teleport");
                    if (teleportConfig.containsKey("useItem")) {
                        plugin.getConfig().set("use-item", teleportConfig.get("useItem"));
                    }
                    if (teleportConfig.containsKey("useCost")) {
                        plugin.getConfig().set("use-cost", ((Double) teleportConfig.get("useCost")).intValue());
                    }
                    if (teleportConfig.containsKey("teleportDelay")) {
                        plugin.getConfig().set("teleport-delay", ((Double) teleportConfig.get("teleportDelay")).intValue());
                    }
                    if (teleportConfig.containsKey("teleportUseCooldown")) {
                        plugin.getConfig().set("teleport-use-cooldown", ((Double) teleportConfig.get("teleportUseCooldown")).intValue());
                    }
                    if (teleportConfig.containsKey("maxLeashDepth")) {
                        plugin.getConfig().set("max-leash-depth", ((Double) teleportConfig.get("maxLeashDepth")).intValue());
                    }
                }

                // 更新跨維度傳送設定
                if (newConfig.containsKey("crossDimensionTeleport")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> crossDimensionConfig = (Map<String, Object>) newConfig.get("crossDimensionTeleport");
                    if (crossDimensionConfig.containsKey("enabled")) {
                        plugin.getConfig().set("cross-dimension-teleport.enabled", crossDimensionConfig.get("enabled"));
                    }
                    if (crossDimensionConfig.containsKey("opBypass")) {
                        plugin.getConfig().set("cross-dimension-teleport.op-bypass", crossDimensionConfig.get("opBypass"));
                    }
                }

                // 更新群組設定
                if (newConfig.containsKey("warpGroups")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> warpGroupsConfig = (Map<String, Object>) newConfig.get("warpGroups");
                    if (warpGroupsConfig.containsKey("enabled")) {
                        plugin.getConfig().set("warp-groups.enabled", warpGroupsConfig.get("enabled"));
                    }
                    if (warpGroupsConfig.containsKey("maxGroupsPerPlayer")) {
                        plugin.getConfig().set("warp-groups.max-groups-per-player", ((Double) warpGroupsConfig.get("maxGroupsPerPlayer")).intValue());
                    }
                    if (warpGroupsConfig.containsKey("maxWarpsPerGroup")) {
                        plugin.getConfig().set("warp-groups.max-warps-per-group", ((Double) warpGroupsConfig.get("maxWarpsPerGroup")).intValue());
                    }
                    if (warpGroupsConfig.containsKey("maxMembersPerGroup")) {
                        plugin.getConfig().set("warp-groups.max-members-per-group", ((Double) warpGroupsConfig.get("maxMembersPerGroup")).intValue());
                    }
                    if (warpGroupsConfig.containsKey("allowNormalPlayers")) {
                        plugin.getConfig().set("warp-groups.allow-normal-players", warpGroupsConfig.get("allowNormalPlayers"));
                    }
                }

                // 更新告示牌設定
                if (newConfig.containsKey("sign")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> signConfig = (Map<String, Object>) newConfig.get("sign");
                    if (signConfig.containsKey("createWptItem")) {
                        plugin.getConfig().set("create-wpt-item", signConfig.get("createWptItem"));
                    }
                    if (signConfig.containsKey("createWptItemCost")) {
                        plugin.getConfig().set("create-wpt-item-cost", ((Double) signConfig.get("createWptItemCost")).intValue());
                    }
                    if (signConfig.containsKey("maxWarpsPerPlayer")) {
                        plugin.getConfig().set("max-warps-per-player", ((Double) signConfig.get("maxWarpsPerPlayer")).intValue());
                    }
                    if (signConfig.containsKey("opUnlimitedWarps")) {
                        plugin.getConfig().set("op-unlimited-warps", signConfig.get("opUnlimitedWarps"));
                    }
                    if (signConfig.containsKey("defaultVisibility")) {
                        plugin.getConfig().set("default-visibility", signConfig.get("defaultVisibility"));
                    }
                    if (signConfig.containsKey("showCreatorOnSign")) {
                        plugin.getConfig().set("show-creator-on-sign", signConfig.get("showCreatorOnSign"));
                    }
                }

                // 更新世界顯示名稱設定
                if (newConfig.containsKey("worldDisplayNames")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> worldDisplayNames = (Map<String, Object>) newConfig.get("worldDisplayNames");
                    if (worldDisplayNames.containsKey("world")) {
                        plugin.getConfig().set("world-display-names.world", worldDisplayNames.get("world"));
                    }
                    if (worldDisplayNames.containsKey("world_nether")) {
                        plugin.getConfig().set("world-display-names.world_nether", worldDisplayNames.get("world_nether"));
                    }
                    if (worldDisplayNames.containsKey("world_the_end")) {
                        plugin.getConfig().set("world-display-names.world_the_end", worldDisplayNames.get("world_the_end"));
                    }
                }

                // 儲存配置檔案
                plugin.saveConfig();

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "配置已成功儲存");
                response.put("timestamp", System.currentTimeMillis());

                return gson.toJson(response);

            } catch (Exception e) {
                res.status(500);
                return gson.toJson(Map.of("error", "儲存配置失敗: " + e.getMessage()));
            }
        });

        // 獲取在線玩家列表
        get("/api/players/online", (req, res) -> {
            res.type("application/json");

            try {
                List<String> onlinePlayers = new ArrayList<>();
                plugin.getServer().getOnlinePlayers().forEach(player -> onlinePlayers.add(player.getName()));

                Map<String, Object> response = new HashMap<>();
                response.put("players", onlinePlayers);
                response.put("count", onlinePlayers.size());
                response.put("timestamp", System.currentTimeMillis());

                return gson.toJson(response);

            } catch (Exception e) {
                res.status(500);
                return gson.toJson(Map.of("error", "獲取在線玩家失敗: " + e.getMessage()));
            }
        });

        // 邀請玩家使用傳送錨點
        post("/api/warps/:warpName/invite", (req, res) -> {
            res.type("application/json");

            try {
                String warpName = req.params(":warpName");
                @SuppressWarnings("unchecked")
                Map<String, String> requestBody = gson.fromJson(req.body(), Map.class);
                String playerName = requestBody.get("player");

                if (playerName == null || playerName.trim().isEmpty()) {
                    res.status(400);
                    return gson.toJson(Map.of("error", "玩家名稱不能為空"));
                }

                Warp warp = Warp.getByName(warpName);
                if (warp == null) {
                    res.status(404);
                    return gson.toJson(Map.of("error", "傳送錨點不存在"));
                }

                if (!warp.isPrivate()) {
                    res.status(400);
                    return gson.toJson(Map.of("error", "只能邀請玩家使用私人傳送錨點"));
                }

                // 獲取玩家對象
                Player player = plugin.getServer().getPlayer(playerName);
                if (player == null) {
                    res.status(400);
                    return gson.toJson(Map.of("error", "玩家不在線或不存在"));
                }

                // 檢查玩家是否已經被邀請
                if (warp.isPlayerInvited(player.getUniqueId().toString())) {
                    res.status(400);
                    return gson.toJson(Map.of("error", "玩家已經被邀請使用此傳送錨點"));
                }

                // 邀請玩家
                warp.invitePlayer(player);
                // 廣播更新
                broadcastWarpUpdate("update", warp);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "已成功邀請 " + playerName + " 使用傳送錨點 " + warpName);
                response.put("warpName", warpName);
                response.put("playerName", playerName);
                response.put("timestamp", System.currentTimeMillis());

                return gson.toJson(response);

            } catch (Exception e) {
                res.status(500);
                return gson.toJson(Map.of("error", "邀請玩家時發生錯誤: " + e.getMessage()));
            }
        });

        // 移除玩家邀請
        post("/api/warps/:warpName/uninvite", (req, res) -> {
            res.type("application/json");

            try {
                String warpName = req.params(":warpName");
                @SuppressWarnings("unchecked")
                Map<String, String> requestBody = gson.fromJson(req.body(), Map.class);
                String playerName = requestBody.get("player");

                if (playerName == null || playerName.trim().isEmpty()) {
                    res.status(400);
                    return gson.toJson(Map.of("error", "玩家名稱不能為空"));
                }

                Warp warp = Warp.getByName(warpName);
                if (warp == null) {
                    res.status(404);
                    return gson.toJson(Map.of("error", "傳送錨點不存在"));
                }

                if (!warp.isPrivate()) {
                    res.status(400);
                    return gson.toJson(Map.of("error", "只能管理私人傳送錨點的邀請"));
                }

                // 獲取玩家 UUID（可能離線）
                String playerUuid = null;
                Player onlinePlayer = plugin.getServer().getPlayer(playerName);
                if (onlinePlayer != null) {
                    playerUuid = onlinePlayer.getUniqueId().toString();
                } else {
                    // 如果玩家離線，從邀請列表中查找 UUID
                    List<WarpInvite> invites = warp.getInvitedPlayers();
                    for (WarpInvite invite : invites) {
                        if (invite.invitedName().equals(playerName)) {
                            playerUuid = invite.invitedUuid();
                            break;
                        }
                    }
                }

                if (playerUuid == null) {
                    res.status(400);
                    return gson.toJson(Map.of("error", "找不到玩家信息"));
                }

                // 檢查玩家是否被邀請
                if (!warp.isPlayerInvited(playerUuid)) {
                    res.status(400);
                    return gson.toJson(Map.of("error", "玩家未被邀請使用此傳送錨點"));
                }

                // 移除邀請
                warp.removeInvite(playerUuid);
                // 廣播更新
                broadcastWarpUpdate("update", warp);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "已移除 " + playerName + " 對傳送錨點 " + warpName + " 的邀請");
                response.put("warpName", warpName);
                response.put("playerName", playerName);
                response.put("timestamp", System.currentTimeMillis());

                return gson.toJson(response);

            } catch (Exception e) {
                res.status(500);
                return gson.toJson(Map.of("error", "移除邀請時發生錯誤: " + e.getMessage()));
            }
        });

        // 獲取傳送錨點的邀請列表
        get("/api/warps/:warpName/invites", (req, res) -> {
            res.type("application/json");

            try {
                String warpName = req.params(":warpName");
                Warp warp = Warp.getByName(warpName);

                if (warp == null) {
                    res.status(404);
                    return gson.toJson(Map.of("error", "傳送錨點不存在"));
                }

                if (!warp.isPrivate()) {
                    res.status(400);
                    return gson.toJson(Map.of("error", "只有私人傳送錨點才有邀請列表"));
                }

                List<WarpInvite> invites = warp.getInvitedPlayers();
                List<String> invitedPlayerNames = invites.stream()
                        .map(WarpInvite::invitedName)
                        .toList();

                Map<String, Object> response = new HashMap<>();
                response.put("warpName", warpName);
                response.put("invitedPlayers", invitedPlayerNames);
                response.put("count", invitedPlayerNames.size());
                response.put("timestamp", System.currentTimeMillis());

                return gson.toJson(response);

            } catch (Exception e) {
                res.status(500);
                return gson.toJson(Map.of("error", "獲取邀請列表失敗: " + e.getMessage()));
            }
        });

        // 重新載入插件
        post("/api/reload", (req, res) -> {
            res.type("application/json");

            try {
                // 在主執行緒中執行重新載入
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        // 重新載入配置
                        plugin.reloadConfig();

                        // 更新事件監聽器的配置
                        if (plugin.getServer().getPluginManager().getPlugin("SignWarpX") != null) {
                            // 這裡可以添加其他需要重新載入的組件
                            plugin.getLogger().info("插件配置已重新載入");
                        }

                    } catch (Exception e) {
                        plugin.getLogger().severe("重新載入插件時發生錯誤: " + e.getMessage());
                    }
                });

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "插件重新載入指令已發送");
                response.put("timestamp", System.currentTimeMillis());

                return gson.toJson(response);

            } catch (Exception e) {
                res.status(500);
                return gson.toJson(Map.of("error", "重新載入插件失敗: " + e.getMessage()));
            }
        });
    }

    private void setupExceptionHandlers() {
        // 處理一般異常
        exception(Exception.class, (exception, request, response) -> {
            plugin.getLogger().warning("API 請求處理異常: " + exception.getMessage());
            response.status(500);
            response.type("application/json");
            response.body(gson.toJson(Map.of("error", "伺服器內部錯誤")));
        });

        // 處理 404
        notFound((req, res) -> {
            res.type("application/json");
            return gson.toJson(Map.of("error", "API 端點不存在"));
        });
    }

    private WarpDTO convertToDTO(Warp warp) {
        WarpDTO dto = new WarpDTO();
        dto.name = warp.getName();
        dto.creator = warp.getCreator();
        dto.creatorUuid = warp.getCreatorUuid();
        dto.world = warp.getLocation().getWorld().getName();
        dto.x = Math.round(warp.getLocation().getX());
        dto.y = Math.round(warp.getLocation().getY());
        dto.z = Math.round(warp.getLocation().getZ());
        dto.createdAt = warp.getFormattedCreatedAt();
        dto.isPrivate = warp.isPrivate();
        dto.visibility = warp.isPrivate() ? "私人" : "公共";

        // 獲取邀請玩家列表
        if (warp.isPrivate()) {
            List<WarpInvite> invites = warp.getInvitedPlayers();
            dto.invitedPlayers = invites.stream()
                    .map(WarpInvite::invitedName)
                    .toList();
        } else {
            dto.invitedPlayers = new ArrayList<>();
        }

        return dto;
    }

    // 廣播更新到所有連接的 WebSocket 客戶端
    public void broadcastWarpUpdate(String eventType, Warp warp) {
        try {
            Map<String, Object> update = new HashMap<>();
            update.put("type", eventType); // "create", "update", "delete"
            update.put("warp", convertToDTO(warp));
            update.put("timestamp", System.currentTimeMillis());

            String json = gson.toJson(update);
            webSocketHandler.broadcast(json);

        } catch (Exception e) {
            plugin.getLogger().warning("廣播傳送點更新失敗: " + e.getMessage());
        }
    }

    // 廣播統計資料更新
    public void broadcastStatsUpdate() {
        try {
            List<Warp> allWarps = Warp.getAll();
            long publicWarps = allWarps.stream().filter(w -> !w.isPrivate()).count();
            long privateWarps = allWarps.stream().filter(Warp::isPrivate).count();

            Map<String, Object> statsUpdate = new HashMap<>();
            statsUpdate.put("type", "stats_update");
            statsUpdate.put("totalWarps", allWarps.size());
            statsUpdate.put("publicWarps", publicWarps);
            statsUpdate.put("privateWarps", privateWarps);
            statsUpdate.put("timestamp", System.currentTimeMillis());

            String json = gson.toJson(statsUpdate);
            webSocketHandler.broadcast(json);

        } catch (Exception e) {
            plugin.getLogger().warning("廣播統計資料更新失敗: " + e.getMessage());
        }
    }

    public void stopWebServer() {
        try {
            // 關閉所有 WebSocket 連接
            webSocketHandler.closeAllConnections();

            // 停止 Spark 伺服器
            Spark.stop();

            // 等待伺服器完全停止
            Spark.awaitStop();
        } catch (Exception e) {
            plugin.getLogger().warning("停止 Web API 伺服器時發生錯誤: " + e.getMessage());
        }
    }

    public WebSocketHandler getWebSocketHandler() {
        return webSocketHandler;
    }
    
    // 獲取當前端口
    public int getCurrentPort() {
        return this.port;
    }

    // 資料傳輸物件
    public static class WarpDTO {
        public String name;
        public String creator;
        public String creatorUuid;
        public String world;
        public long x, y, z;
        public String createdAt;
        public boolean isPrivate;
        public String visibility;
        public List<String> invitedPlayers;
    }
}