package com.swim.signwarpx.utils.EventListener;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品處理工具類
 * 統一處理物品檢查、扣除和返還
 */
public class ItemUtils {

    // 暫存扣除的物品數量（用於傳送取消時返還）
    private static final ConcurrentHashMap<UUID, Integer> pendingItemCosts = new ConcurrentHashMap<>();

    /**
     * 檢查並扣除創建傳送點所需的物品
     *
     * @param player 玩家
     * @param config 配置檔案
     * @return 是否成功扣除物品
     */
    public static boolean checkAndConsumeCreateItem(Player player, FileConfiguration config) {
        String itemName = config.getString("create-wpt-item", "none");
        if ("none".equalsIgnoreCase(itemName)) {
            return true; // 不需要物品
        }

        Material material = Material.getMaterial(itemName.toUpperCase());
        if (material == null) {
            return false;
        }

        int cost = config.getInt("create-wpt-item-cost", 1);
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType() != material) {
            Map<String, String> placeholders = Map.of(
                    "{use-item}", itemName,
                    "{use-cost}", String.valueOf(cost)
            );
            MessageUtils.sendConfigMessage(player, config, "messages.not_enough_item", placeholders);
            return false;
        }

        if (itemInHand.getAmount() < cost) {
            Map<String, String> placeholders = Map.of(
                    "{use-item}", itemName,
                    "{use-cost}", String.valueOf(cost)
            );
            MessageUtils.sendConfigMessage(player, config, "messages.not_enough_item", placeholders);
            return false;
        }

        // 扣除物品
        int remaining = itemInHand.getAmount() - cost;
        if (remaining <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            itemInHand.setAmount(remaining);
            player.getInventory().setItemInMainHand(itemInHand);
        }

        // 記錄扣除數量（用於傳送取消時返還）
        pendingItemCosts.put(player.getUniqueId(), cost);
        return true;
    }

    /**
     * 檢查並扣除使用傳送所需的物品
     *
     * @param player   玩家
     * @param config   配置檔案
     * @param handItem 手持物品
     * @return 是否成功扣除物品
     */
    public static boolean checkAndConsumeUseItem(Player player, FileConfiguration config, ItemStack handItem) {
        String useItem = config.getString("use-item", "none");
        if ("none".equalsIgnoreCase(useItem)) {
            return true; // 不需要物品
        }

        Material requiredMaterial = Material.getMaterial(useItem.toUpperCase());
        if (requiredMaterial == null) {
            return false;
        }

        if (handItem == null || handItem.getType() != requiredMaterial) {
            Map<String, String> placeholders = Map.of("{use-item}", useItem);
            MessageUtils.sendConfigMessage(player, config, "messages.invalid_item", placeholders);
            return false;
        }

        int useCost = config.getInt("use-cost", 0);
        if (handItem.getAmount() < useCost) {
            Map<String, String> placeholders = Map.of(
                    "{use-cost}", String.valueOf(useCost),
                    "{use-item}", useItem
            );
            MessageUtils.sendConfigMessage(player, config, "messages.not_enough_item", placeholders);
            return false;
        }

        // 扣除物品
        int remaining = handItem.getAmount() - useCost;
        if (remaining <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            handItem.setAmount(remaining);
            player.getInventory().setItemInMainHand(handItem);
        }

        pendingItemCosts.put(player.getUniqueId(), useCost);
        return true;
    }

    /**
     * 返還暫存的物品
     *
     * @param player 玩家
     * @param config 配置檔案
     */
    public static void returnPendingItems(Player player, FileConfiguration config) {
        UUID playerUUID = player.getUniqueId();
        if (pendingItemCosts.containsKey(playerUUID)) {
            String useItem = config.getString("use-item", "none");
            if (!"none".equalsIgnoreCase(useItem)) {
                Material material = Material.getMaterial(useItem.toUpperCase());
                if (material != null) {
                    int cost = pendingItemCosts.get(playerUUID);
                    player.getInventory().addItem(new ItemStack(material, cost));
                }
            }
            pendingItemCosts.remove(playerUUID);
        }
    }

    /**
     * 清除玩家的暫存物品記錄
     *
     * @param playerUUID 玩家UUID
     */
    public static void clearPendingItems(UUID playerUUID) {
        pendingItemCosts.remove(playerUUID);
    }

    /**
     * 檢查玩家是否有暫存的物品扣除記錄
     *
     * @param playerUUID 玩家UUID
     * @return 是否有暫存記錄
     */
    public static boolean hasPendingItems(UUID playerUUID) {
        return pendingItemCosts.containsKey(playerUUID);
    }
}