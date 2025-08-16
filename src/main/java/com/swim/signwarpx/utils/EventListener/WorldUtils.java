package com.swim.signwarpx.utils.EventListener;

import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * 世界相關工具類
 * 統一處理世界名稱顯示和跨次元傳送檢查
 */
public class WorldUtils {

    /**
     * 獲取世界的顯示名稱
     *
     * @param config    配置檔案
     * @param worldName 世界名稱
     * @return 顯示名稱
     */
    public static String getDisplayWorldName(FileConfiguration config, String worldName) {
        if (worldName == null) {
            return "未知世界";
        }

        // 從配置檔案讀取世界名稱對映
        String displayName = config.getString("world-display-names." + worldName);
        if (displayName != null) {
            return displayName;
        }

        // 如果配置檔案中沒有對應的世界名稱，使用預設邏輯
        if (worldName.equals("world") || worldName.endsWith("_overworld")) {
            return "主世界";
        } else if (worldName.equals("world_nether") || worldName.endsWith("_nether")) {
            return "地獄";
        } else if (worldName.equals("world_the_end") || worldName.endsWith("_the_end")) {
            return "終界";
        }

        return worldName;
    }

    /**
     * 檢查兩個世界是否不同
     *
     * @param world1 世界1
     * @param world2 世界2
     * @return 是否為不同世界
     */
    public static boolean isDifferentWorld(World world1, World world2) {
        if (world1 == null || world2 == null) {
            return true;
        }
        return !world1.equals(world2);
    }

    /**
     * 檢查是否允許跨次元傳送
     *
     * @param config      配置檔案
     * @param playerWorld 玩家當前世界
     * @param targetWorld 目標世界
     * @param isOp        玩家是否為OP
     * @return 是否允許跨次元傳送
     */
    public static boolean canCrossDimensionTeleport(FileConfiguration config, World playerWorld, World targetWorld, boolean isOp) {
        if (targetWorld == null) {
            return false;
        }

        if (!isDifferentWorld(playerWorld, targetWorld)) {
            return true;
        }

        boolean crossDimensionEnabled = config.getBoolean("cross-dimension-teleport.enabled", true);
        if (!crossDimensionEnabled) {
            boolean opBypass = config.getBoolean("cross-dimension-teleport.op-bypass", true);
            return opBypass && isOp;
        }

        return true;
    }

    /**
     * 獲取跨次元傳送被禁用時的錯誤訊息鍵值
     *
     * @return 訊息鍵值
     */
    public static String getCrossDimensionDisabledMessageKey() {
        return "messages.cross_dimension_disabled";
    }

    /**
     * 獲取世界未找到時的錯誤訊息鍵值
     *
     * @return 訊息鍵值
     */
    public static String getWorldNotFoundMessageKey() {
        return "messages.warp_world_not_found";
    }
}