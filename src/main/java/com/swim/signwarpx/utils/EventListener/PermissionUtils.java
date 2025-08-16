package com.swim.signwarpx.utils.EventListener;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/**
 * 權限檢查工具類
 * 統一處理權限檢查和相關訊息發送
 */
public class PermissionUtils {

    /**
     * 檢查玩家是否擁有指定權限
     *
     * @param player 玩家
     * @param config 配置檔案
     * @param type   權限類型
     * @return 是否擁有權限
     */
    public static boolean hasPermission(Player player, FileConfiguration config, PermissionType type) {
        if (player.hasPermission(type.getPermission())) {
            return true;
        }

        if (type.getMessageKey() != null) {
            MessageUtils.sendConfigMessage(player, config, type.getMessageKey());
        }
        return false;
    }

    /**
     * 檢查玩家是否擁有指定權限（不發送訊息）
     *
     * @param player 玩家
     * @param type   權限類型
     * @return 是否擁有權限
     */
    public static boolean hasPermissionSilent(Player player, PermissionType type) {
        return player.hasPermission(type.getPermission());
    }

    /**
     * 權限類型枚舉
     */
    public enum PermissionType {
        CREATE("signwarp.create", "messages.create_permission"),
        USE("signwarp.use", "messages.use_permission"),
        DESTROY("signwarp.destroy", "messages.destroy_permission"),
        ADMIN("signwarp.admin", null);

        private final String permission;
        private final String messageKey;

        PermissionType(String permission, String messageKey) {
            this.permission = permission;
            this.messageKey = messageKey;
        }

        public String getPermission() {
            return permission;
        }

        public String getMessageKey() {
            return messageKey;
        }
    }
}