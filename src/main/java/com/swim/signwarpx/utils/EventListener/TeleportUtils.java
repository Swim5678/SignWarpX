package com.swim.signwarpx.utils.EventListener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 傳送相關工具類
 * 統一處理傳送任務的管理和取消
 */
public class TeleportUtils {

    // 儲存傳送任務（排程）
    private static final ConcurrentHashMap<UUID, BukkitTask> teleportTasks = new ConcurrentHashMap<>();

    /**
     * 取消玩家的傳送任務
     *
     * @param player     玩家
     * @param plugin     插件實例
     * @param messageKey 取消訊息的鍵值
     */
    public static void cancelTeleportTask(Player player, JavaPlugin plugin, String messageKey) {
        UUID playerUUID = player.getUniqueId();
        BukkitTask teleportTask = teleportTasks.get(playerUUID);
        if (teleportTask != null && !teleportTask.isCancelled()) {
            teleportTask.cancel();
            teleportTasks.remove(playerUUID);

            // 返還物品
            ItemUtils.returnPendingItems(player, plugin.getConfig());

            // 發送取消訊息
            MessageUtils.sendConfigMessage(player, plugin.getConfig(), messageKey);
        }
    }

    /**
     * 添加傳送任務
     *
     * @param playerUUID 玩家UUID
     * @param task       傳送任務
     */
    public static void addTeleportTask(UUID playerUUID, BukkitTask task) {
        // 取消之前的任務
        BukkitTask previousTask = teleportTasks.get(playerUUID);
        if (previousTask != null && !previousTask.isCancelled()) {
            previousTask.cancel();
        }
        teleportTasks.put(playerUUID, task);
    }

    /**
     * 移除傳送任務
     *
     * @param playerUUID 玩家UUID
     */
    public static void removeTeleportTask(UUID playerUUID) {
        teleportTasks.remove(playerUUID);
    }

    /**
     * 檢查玩家是否有進行中的傳送任務
     *
     * @param playerUUID 玩家UUID
     * @return 是否有傳送任務
     */
    public static boolean hasTeleportTask(UUID playerUUID) {
        return teleportTasks.containsKey(playerUUID);
    }

    /**
     * 清理玩家的所有傳送相關數據
     *
     * @param player 玩家
     * @param plugin 插件實例
     */
    public static void cleanupPlayer(Player player, JavaPlugin plugin) {
        UUID playerUUID = player.getUniqueId();

        // 取消傳送任務
        BukkitTask task = teleportTasks.get(playerUUID);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        teleportTasks.remove(playerUUID);

        // 返還物品
        ItemUtils.returnPendingItems(player, plugin.getConfig());

        // 清理物品記錄
        ItemUtils.clearPendingItems(playerUUID);

        // 清理冷卻
        CooldownUtils.clearCooldown(playerUUID);
    }

    /**
     * 尋找安全的傳送位置
     * 優先尋找最安全且離目標最近的位置
     *
     * @param base 基礎位置
     * @return 安全的傳送位置
     */
    public static Location findSafeLocation(Location base) {
        World world = base.getWorld();
        if (world == null) {
            return base;
        }

        // 首先檢查目標位置本身是否安全
        Location safeLoc = checkLocationSafety(base);
        if (safeLoc != null) {
            return safeLoc;
        }

        // 使用螺旋搜索算法，從目標位置開始向外擴展
        return findSafeLocationSpiral(base);
    }

    /**
     * 檢查指定位置是否安全
     *
     * @param location 要檢查的位置
     * @return 如果安全返回調整後的位置，否則返回null
     */
    private static Location checkLocationSafety(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }

        int x = location.getBlockX();
        int z = location.getBlockZ();
        int startY = location.getBlockY();
        int maxY = world.getMaxHeight();
        int minY = world.getMinHeight();

        // 向上搜索安全位置（最多搜索10格）
        for (int y = startY; y < Math.min(maxY - 1, startY + 10); y++) {
            if (isSafePosition(world, x, y, z)) {
                return new Location(world, x + 0.5, y, z + 0.5, location.getYaw(), location.getPitch());
            }
        }

        // 向下搜索安全位置（最多搜索5格）
        for (int y = startY - 1; y > Math.max(minY, startY - 5); y--) {
            if (isSafePosition(world, x, y, z)) {
                return new Location(world, x + 0.5, y, z + 0.5, location.getYaw(), location.getPitch());
            }
        }

        return null;
    }

    /**
     * 使用螺旋搜索算法尋找安全位置
     *
     * @param base 基礎位置
     * @return 最近的安全位置
     */
    private static Location findSafeLocationSpiral(Location base) {
        World world = base.getWorld();
        int centerX = base.getBlockX();
        int centerZ = base.getBlockZ();
        int baseY = base.getBlockY();

        // 螺旋搜索，最大搜索半徑為16格
        int maxRadius = 16;

        for (int radius = 1; radius <= maxRadius; radius++) {
            // 搜索當前半徑的所有位置
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // 只檢查邊界上的點，避免重複檢查內部已檢查過的點
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    int x = centerX + dx;
                    int z = centerZ + dz;

                    // 檢查這個位置是否安全
                    Location safeLoc = checkLocationSafety(new Location(world, x, baseY, z, base.getYaw(), base.getPitch()));
                    if (safeLoc != null) {
                        return safeLoc;
                    }
                }
            }
        }

        // 如果螺旋搜索失敗，嘗試在原位置上方尋找
        return findSafeLocationAbove(base);
    }

    /**
     * 在指定位置上方尋找安全位置
     *
     * @param base 基礎位置
     * @return 安全位置
     */
    private static Location findSafeLocationAbove(Location base) {
        World world = base.getWorld();
        int x = base.getBlockX();
        int z = base.getBlockZ();
        int maxY = world.getMaxHeight();

        // 從基礎位置向上搜索，最多搜索到世界最高處
        for (int y = base.getBlockY(); y < maxY - 1; y++) {
            if (isSafePosition(world, x, y, z)) {
                return new Location(world, x + 0.5, y, z + 0.5, base.getYaw(), base.getPitch());
            }
        }

        // 如果還是找不到，返回世界最高處的安全位置
        return new Location(world, x + 0.5, maxY - 2, z + 0.5, base.getYaw(), base.getPitch());
    }

    /**
     * 檢查指定座標是否為安全位置
     *
     * @param world 世界
     * @param x     X座標
     * @param y     Y座標
     * @param z     Z座標
     * @return 是否安全
     */
    private static boolean isSafePosition(World world, int x, int y, int z) {
        try {
            // 檢查腳部位置（玩家站立的位置）
            Location feetLoc = new Location(world, x, y, z);
            // 檢查頭部位置（玩家頭部的位置）
            Location headLoc = new Location(world, x, y + 1, z);
            // 檢查腳下位置（玩家站立的支撐方塊）
            Location belowLoc = new Location(world, x, y - 1, z);

            // 基本安全檢查：腳部和頭部必須是空氣，腳下必須是實體方塊
            boolean feetSafe = world.getBlockAt(feetLoc).getType().isAir();
            boolean headSafe = world.getBlockAt(headLoc).getType().isAir();
            boolean groundSafe = !world.getBlockAt(belowLoc).getType().isAir();

            return feetSafe && headSafe && groundSafe;

        } catch (Exception e) {
            // 如果檢查過程中出現異常，認為不安全
            return false;
        }
    }

    /**
     * 獲取當前傳送任務數量（用於監控）
     *
     * @return 傳送任務數量
     */
    public static int getTeleportTaskCount() {
        return teleportTasks.size();
    }

    /**
     * 清理所有已完成或取消的傳送任務
     */
    public static void cleanupCompletedTasks() {
        teleportTasks.entrySet().removeIf(entry -> {
            BukkitTask task = entry.getValue();
            return task.isCancelled() || !Bukkit.getScheduler().isQueued(task.getTaskId());
        });
    }
}