package com.swim.signwarpx.utils;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.swim.signwarpx.SignWarpX;

import java.lang.reflect.Method;

/**
 * Compatibility layer for API differences between 1.21 and 1.21.6
 * 處理1.21和1.21.6 API之間的差異
 */
public class ApiCompatibilityLayer {
    private static ApiCompatibilityLayer instance;
    private final ApiVersionManager versionManager;
    
    private ApiCompatibilityLayer() {
        this.versionManager = ApiVersionManager.getInstance();
    }
    
    public static ApiCompatibilityLayer getInstance() {
        if (instance == null) {
            instance = new ApiCompatibilityLayer();
        }
        return instance;
    }
    
    /**
     * 給予玩家物品，處理不同API版本之間的差異
     * Give item to player, handling differences between API versions
     */
    public boolean giveItem(Player player, ItemStack item) {
        try {
            if (versionManager.isUsing1216Api()) {
                // 1.21.6 API might have newer methods
                return giveItemModern(player, item);
            } else {
                // 1.21-1.21.5 use legacy approach
                return giveItemLegacy(player, item);
            }
        } catch (Exception e) {
            SignWarpX.getInstance().getLogger().warning("Failed to give item to player: " + e.getMessage());
            // Fallback to basic approach
            player.getInventory().addItem(item);
            return true;
        }
    }
    
    private boolean giveItemModern(Player player, ItemStack item) {
        try {
            // Try to use newer API methods if available
            Method giveMethod = player.getClass().getMethod("giveItem", ItemStack.class);
            Object result = giveMethod.invoke(player, item);
            return result instanceof Boolean ? (Boolean) result : true;
        } catch (NoSuchMethodException e) {
            // Method doesn't exist, fall back to legacy
            return giveItemLegacy(player, item);
        } catch (Exception e) {
            SignWarpX.getInstance().getLogger().warning("Modern giveItem failed: " + e.getMessage());
            return giveItemLegacy(player, item);
        }
    }
    
    private boolean giveItemLegacy(Player player, ItemStack item) {
        player.getInventory().addItem(item);
        return true;
    }
    
    /**
     * 發送動作條訊息，處理不同API版本之間的差異
     * Send action bar message, handling differences between API versions
     */
    public void sendActionBar(Player player, String message) {
        try {
            if (versionManager.isUsing1216Api()) {
                sendActionBarModern(player, message);
            } else {
                sendActionBarLegacy(player, message);
            }
        } catch (Exception e) {
            SignWarpX.getInstance().getLogger().warning("Failed to send action bar: " + e.getMessage());
            // Fallback to regular message
            player.sendMessage(message);
        }
    }
    
    private void sendActionBarModern(Player player, String message) {
        try {
            // Try newer Component-based approach
            net.kyori.adventure.text.Component component = net.kyori.adventure.text.Component.text(message);
            player.sendActionBar(component);
        } catch (Exception e) {
            // Fall back to legacy if modern approach fails
            sendActionBarLegacy(player, message);
        }
    }
    
    private void sendActionBarLegacy(Player player, String message) {
        // Use reflection for legacy support if needed
        player.sendActionBar(message);
    }
    
    /**
     * 檢查特定API功能是否可用
     * Check if specific API features are available
     */
    public boolean hasModernFeatures() {
        return versionManager.isUsing1216Api();
    }
    
    /**
     * 獲取版本特定的配置設定
     * Get version-specific configuration settings
     */
    public String getVersionSpecificConfig(String key1216, String key121) {
        if (versionManager.isUsing1216Api()) {
            return key1216;
        } else {
            return key121;
        }
    }
}