package com.swim.signwarpx.utils.EventListener;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * 傳送點權限檢查工具類
 * 統一處理傳送點相關的權限檢查
 */
public class WarpPermissionUtils {

    /**
     * 檢查玩家是否可以使用指定的傳送點
     *
     * @param player 玩家
     * @param config 配置檔案
     * @param warp   傳送點對象
     * @return 是否可以使用
     */
    public static boolean canUseWarp(Player player, FileConfiguration config, Object warp) {
        // 管理員可以使用所有傳送點
        if (PermissionUtils.hasPermissionSilent(player, PermissionUtils.PermissionType.ADMIN)) {
            return true;
        }

        // 這裡需要根據您的 Warp 類別來調整
        // 假設 Warp 類別有以下方法：
        // - getCreatorUuid(): String
        // - isPrivate(): boolean
        // - canUseWarp(String playerUuid): boolean

        try {
            // 使用反射來調用 Warp 的方法，以保持通用性
            Class<?> warpClass = warp.getClass();

            // 創建者可以使用自己的傳送點
            String creatorUuid = (String) warpClass.getMethod("getCreatorUuid").invoke(warp);
            if (player.getUniqueId().toString().equals(creatorUuid)) {
                return true;
            }

            // 如果是公共傳送點，任何人都可以使用
            boolean isPrivate = (Boolean) warpClass.getMethod("isPrivate").invoke(warp);
            if (!isPrivate) {
                return true;
            }

            // 使用 Warp 類別的完整權限檢查方法（包含群組成員檢查）
            boolean canUse = (Boolean) warpClass.getMethod("canUseWarp", String.class)
                    .invoke(warp, player.getUniqueId().toString());
            if (canUse) {
                return true;
            }

        } catch (Exception e) {
            // 如果反射失敗，記錄錯誤並拒絕訪問
            System.err.println("Error checking warp permissions: " + e.getMessage());
            return false;
        }

        // 發送私人傳送點錯誤訊息
        MessageUtils.sendConfigMessage(player, config, "messages.private_warp");
        return false;
    }

    /**
     * 檢查玩家是否可以創建新的傳送點
     *
     * @param player 玩家
     * @param config 配置檔案
     * @return 是否可以創建
     */
    public static boolean canCreateWarp(Player player, FileConfiguration config) {
        int maxWarps = config.getInt("max-warps-per-player", 10);
        if (maxWarps == -1) {
            return true;
        }

        boolean opUnlimited = config.getBoolean("op-unlimited-warps", true);
        if (opUnlimited && player.isOp()) {
            return true;
        }

        // 這裡需要根據您的 Warp 類別來調整
        // 假設有靜態方法 Warp.getPlayerWarpCount(String playerUuid)
        try {
            Class<?> warpClass = Class.forName("com.swim.signwarpx.Warp");
            int currentWarps = (Integer) warpClass.getMethod("getPlayerWarpCount", String.class)
                    .invoke(null, player.getUniqueId().toString());
            return currentWarps < maxWarps;
        } catch (Exception e) {
            System.err.println("Error checking player warp count: " + e.getMessage());
            return false;
        }
    }
}