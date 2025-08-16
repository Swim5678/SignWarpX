package com.swim.signwarpx.utils.EventListener;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 冷卻時間管理工具類
 * 統一處理玩家冷卻時間的檢查和設置
 */
public class CooldownUtils {

    // 傳送冷卻：記錄玩家下次可傳送的時間（毫秒）
    private static final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    /**
     * 檢查玩家是否在冷卻中
     *
     * @param player 玩家
     * @param config 配置檔案
     * @return 是否可以執行操作（不在冷卻中）
     */
    public static boolean checkCooldown(Player player, FileConfiguration config) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (cooldowns.containsKey(playerId)) {
            long cooldownEnd = cooldowns.get(playerId);
            if (now < cooldownEnd) {
                long remainingSeconds = (cooldownEnd - now + 999) / 1000;
                Map<String, String> placeholders = Map.of("{cooldown}", String.valueOf(remainingSeconds));
                MessageUtils.sendConfigMessage(player, config, "messages.cooldown", placeholders);
                return false;
            }
        }
        return true;
    }

    /**
     * 設置玩家的冷卻時間
     *
     * @param player      玩家
     * @param config      配置檔案
     * @param cooldownKey 配置檔案中的冷卻時間鍵值
     */
    public static void setCooldown(Player player, FileConfiguration config, String cooldownKey) {
        int cooldownSeconds = config.getInt(cooldownKey, 5);
        setCooldown(player.getUniqueId(), cooldownSeconds);
    }

    /**
     * 設置玩家的冷卻時間
     *
     * @param playerUUID      玩家UUID
     * @param cooldownSeconds 冷卻秒數
     */
    public static void setCooldown(UUID playerUUID, int cooldownSeconds) {
        cooldowns.put(playerUUID, System.currentTimeMillis() + cooldownSeconds * 1000L);
    }

    /**
     * 清除玩家的冷卻時間
     *
     * @param playerUUID 玩家UUID
     */
    public static void clearCooldown(UUID playerUUID) {
        cooldowns.remove(playerUUID);
    }

    /**
     * 獲取玩家剩餘的冷卻時間（秒）
     *
     * @param playerUUID 玩家UUID
     * @return 剩餘冷卻時間，如果沒有冷卻則返回0
     */
    public static long getRemainingCooldown(UUID playerUUID) {
        if (!cooldowns.containsKey(playerUUID)) {
            return 0;
        }

        long cooldownEnd = cooldowns.get(playerUUID);
        long now = System.currentTimeMillis();

        if (now >= cooldownEnd) {
            cooldowns.remove(playerUUID);
            return 0;
        }

        return (cooldownEnd - now + 999) / 1000;
    }

    /**
     * 清理過期的冷卻記錄
     * 建議定期調用此方法來清理記憶體
     */
    public static void cleanupExpiredCooldowns() {
        long now = System.currentTimeMillis();
        cooldowns.values().removeIf(cooldownEnd -> cooldownEnd <= now);
    }

    /**
     * 獲取當前冷卻記錄數量（用於監控）
     *
     * @return 冷卻記錄數量
     */
    public static int getCooldownCount() {
        return cooldowns.size();
    }
}