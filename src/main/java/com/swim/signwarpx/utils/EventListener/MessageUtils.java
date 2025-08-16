package com.swim.signwarpx.utils.EventListener;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * 訊息處理工具類
 * 統一處理配置訊息的發送和格式化
 */
public class MessageUtils {

    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    /**
     * 發送配置訊息給玩家
     *
     * @param player     目標玩家
     * @param config     配置檔案
     * @param messageKey 訊息鍵值
     */
    public static void sendConfigMessage(Player player, FileConfiguration config, String messageKey) {
        sendConfigMessage(player, config, messageKey, null);
    }

    /**
     * 發送配置訊息給玩家（支援佔位符替換）
     *
     * @param player       目標玩家
     * @param config       配置檔案
     * @param messageKey   訊息鍵值
     * @param placeholders 佔位符映射
     */
    public static void sendConfigMessage(Player player, FileConfiguration config, String messageKey, Map<String, String> placeholders) {
        String message = config.getString(messageKey);
        if (message != null) {
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    message = message.replace(entry.getKey(), entry.getValue());
                }
            }
            player.sendMessage(miniMessage.deserialize(message));
        }
    }

    /**
     * 獲取格式化後的配置訊息
     *
     * @param config       配置檔案
     * @param messageKey   訊息鍵值
     * @param placeholders 佔位符映射
     * @return 格式化後的訊息字串
     */
    public static String getFormattedMessage(FileConfiguration config, String messageKey, Map<String, String> placeholders) {
        String message = config.getString(messageKey);
        if (message != null && placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace(entry.getKey(), entry.getValue());
            }
        }
        return message;
    }
}