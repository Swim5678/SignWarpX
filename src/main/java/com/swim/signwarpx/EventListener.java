package com.swim.signwarpx;

import com.swim.signwarpx.utils.SignUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings({"BooleanMethodIsAlwaysInverted", "ConstantValue", "unused"})
public class EventListener implements Listener {
    private static FileConfiguration config;
    private final SignWarpX plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final TeleportEffects teleportEffects;

    // 儲存傳送任務（排程）
    private final ConcurrentHashMap<UUID, BukkitTask> teleportTasks = new ConcurrentHashMap<>();
    // 暫存扣除的物品數量（用於傳送取消時返還）
    private final ConcurrentHashMap<UUID, Integer> pendingItemCosts = new ConcurrentHashMap<>();
    // 傳送冷卻：記錄玩家下次可傳送的時間（毫秒）
    private final ConcurrentHashMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public EventListener(SignWarpX plugin) {
        this.plugin = plugin;
        config = plugin.getConfig();
        this.teleportEffects = new TeleportEffects(plugin);
    }

    // 更新配置檔的靜態方法
    public static void updateConfig(JavaPlugin plugin) {
        config = plugin.getConfig();
    }

    private void notifyWebClients(String eventType, Warp warp) {
        try {
            // 使用靜態方法獲取插件實例
            SignWarpX mainPlugin = SignWarpX.getInstance();
            if (mainPlugin != null && mainPlugin.getWebApiManager() != null) {
                mainPlugin.getWebApiManager().broadcastWarpUpdate(eventType, warp);
                mainPlugin.getWebApiManager().broadcastStatsUpdate();
            }
        } catch (Exception e) {
            plugin.getLogger().warning(e.getMessage());
        }
    }

    // ============= 共用方法區域 =============

    private boolean hasPermission(Player player, PermissionType type) {
        if (player.hasPermission(type.permission)) {
            return true;
        }

        if (type.messageKey != null) {
            sendConfigMessage(player, type.messageKey);
        }
        return false;
    }

    /**
     * 統一的配置訊息發送方法
     */
    private void sendConfigMessage(Player player, String messageKey) {
        sendConfigMessage(player, messageKey, null);
    }

    private void sendConfigMessage(Player player, String messageKey, Map<String, String> placeholders) {
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
     * 統一的物品返還方法
     */
    private void returnPendingItems(Player player) {
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
     * 統一的傳送任務取消方法
     */
    private void cancelTeleportTask(Player player, String messageKey) {
        UUID playerUUID = player.getUniqueId();
        BukkitTask teleportTask = teleportTasks.get(playerUUID);
        if (teleportTask != null && !teleportTask.isCancelled()) {
            teleportTask.cancel();
            teleportTasks.remove(playerUUID);
            returnPendingItems(player);

            // 取消傳送特效（會立即清空 Action Bar）
            teleportEffects.cancelTeleportEffects(player);

            sendConfigMessage(player, messageKey);
        }
    }

    /**
     * 統一的傳送權限檢查方法
     */
    private boolean canUseWarp(Player player, Warp warp) {
        // 管理員可以使用所有傳送點
        if (hasPermission(player, PermissionType.ADMIN)) {
            return true;
        }

        // 創建者可以使用自己的傳送點
        if (player.getUniqueId().toString().equals(warp.getCreatorUuid())) {
            return true;
        }

        // 如果是公共傳送點，任何人都可以使用
        if (!warp.isPrivate()) {
            return true;
        }

        // 使用 Warp 類別的完整權限檢查方法（包含群組成員檢查）
        if (warp.canUseWarp(player.getUniqueId().toString())) {
            return true;
        }

        // 發送私人傳送點錯誤訊息
        sendConfigMessage(player, "messages.private_warp");
        return false;
    }

    /**
     * 統一的 SignData 獲取方法
     */
    private SignData getSignData(Block block) {
        Sign signBlock = SignUtils.getSignFromBlock(block);
        if (signBlock == null) {
            return null;
        }

        Component[] lines = new Component[4];
        for (int i = 0; i < 4; i++) {
            lines[i] = signBlock.getSide(Side.FRONT).line(i);
        }
        return new SignData(lines);
    }

    /**
     * 統一的WPT物品檢查與扣除方法
     */
    private boolean checkAndConsumeItem(Player player) {
        String itemName = config.getString("create-wpt-item", "none");
        if ("none".equalsIgnoreCase(itemName)) {
            return true; // 不需要物品
        }

        Material material = Material.getMaterial(itemName.toUpperCase());

        int cost = config.getInt("create-wpt-item-cost", 1);
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType() != material) {
            Map<String, String> placeholders = Map.of(
                    "{use-item}", itemName,
                    "{use-cost}", String.valueOf(cost)
            );
            sendConfigMessage(player, "messages.not_enough_item", placeholders);
            return false;
        }

        if (itemInHand.getAmount() < cost) {
            Map<String, String> placeholders = Map.of(
                    "{use-item}", itemName,
                    "{use-cost}", String.valueOf(cost)
            );
            sendConfigMessage(player, "messages.not_enough_item", placeholders);
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

        // 記錄扣除數量（用於傳送取消時返還）- 修正此處
        pendingItemCosts.put(player.getUniqueId(), cost);

        return true;
    }

    /**
     * 統一的冷卻檢查方法
     */
    private boolean checkCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (cooldowns.containsKey(playerId)) {
            long cooldownEnd = cooldowns.get(playerId);
            if (now < cooldownEnd) {
                long remainingSeconds = (cooldownEnd - now + 999) / 1000;
                Map<String, String> placeholders = Map.of("{cooldown}", String.valueOf(remainingSeconds));
                sendConfigMessage(player, "messages.cooldown", placeholders);
                return false;
            }
        }
        return true;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        startCooldownCleanupTask();
    }

    private void startCooldownCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            cooldowns.values().removeIf(cooldownEnd -> cooldownEnd <= now);
        }, 6000L, 6000L);
    }

    private boolean canCreateWarp(Player player) {
        int maxWarps = config.getInt("max-warps-per-player", 10);
        if (maxWarps == -1) {
            return true;
        }

        boolean opUnlimited = config.getBoolean("op-unlimited-warps", true);
        if (opUnlimited && player.isOp()) {
            return true;
        }

        int currentWarps = Warp.getPlayerWarpCount(player.getUniqueId().toString());
        return currentWarps < maxWarps;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        Component[] lines = new Component[4];
        for (int i = 0; i < 4; i++) {
            lines[i] = event.line(i);
        }
        SignData signData = new SignData(lines);

        if (!signData.isWarpSign()) {
            return;
        }

        Player player = event.getPlayer();
        Block signBlock = event.getBlock();

        // 檢查依附方塊
        Material supportType = getSupportBlockType(signBlock);
        if (isGravityAffected(supportType)) {
            Component msg = Component.text("無法建立在沙子、礫石等重力方塊上！", NamedTextColor.RED);
            player.sendMessage(msg);
            event.setCancelled(true);
            return;
        }

        // 統一權限檢查
        if (!hasPermission(player, PermissionType.CREATE)) {
            event.setCancelled(true);
            return;
        }

        // 傳送點名稱有效性檢查
        if (!signData.isValidWarpName()) {
            sendConfigMessage(player, "messages.no_warp_name");
            event.setCancelled(true);
            return;
        }

        Warp existingWarp = Warp.getByName(signData.warpName);

        if (signData.isWarp()) {
            handleWarpSignCreation(event, player, signData, existingWarp);
        } else if (signData.isWarpTarget()) {
            handleWarpTargetCreation(event, player, signData, existingWarp);
        }
    }

    private Material getSupportBlockType(Block signBlock) {
        if (signBlock.getBlockData() instanceof WallSign wallSign) {
            Block attachedBlock = signBlock.getRelative(wallSign.getFacing().getOppositeFace());
            return attachedBlock.getType();
        } else {
            Block attachedBlock = signBlock.getRelative(BlockFace.DOWN);
            return attachedBlock.getType();
        }
    }

    /**
     * 處理 Warp 告示牌建立時的世界資訊設置
     */
    private void handleWarpSignCreation(SignChangeEvent event, Player player, SignData signData, Warp existingWarp) {
        // 檢查目標 WarpTarget 是否存在
        if (existingWarp == null) {
            sendConfigMessage(player, "messages.warp_not_found",
                    Map.of("{warp-name}", signData.warpName));
            event.setCancelled(true);
            return;
        }
        event.line(0, Component.text(SignData.HEADER_WARP).color(NamedTextColor.BLUE));
        // 檢查是否啟用世界資訊顯示
        boolean showWorldInfo = config.getBoolean("sign-world-info.enabled", true);
        if (showWorldInfo) {
            // 獲取目標世界名稱
            String worldName = existingWarp.getLocation().getWorld().getName();
            String displayWorldName = getDisplayWorldName(worldName);

            // 獲取顯示格式和顏色
            String format = config.getString("sign-world-info.format", "世界: {world-name}");
            String colorName = config.getString("sign-world-info.color", "GRAY");

            // 替換佔位符
            String worldInfo = format.replace("{world-name}", displayWorldName);

            // 設定顏色
            NamedTextColor color;
            try {
                color = NamedTextColor.NAMES.value(colorName.toUpperCase());
            } catch (IllegalArgumentException e) {
                color = NamedTextColor.GRAY; // 預設顏色
            }

            // 設置告示牌第三行為世界資訊
            event.line(2, Component.text(worldInfo).color(color));
        }

        sendConfigMessage(player, "messages.warp_created");
    }

    private void handleWarpTargetCreation(SignChangeEvent event, Player player, SignData signData, Warp existingWarp) {
        if (existingWarp != null) {
            sendConfigMessage(player, "messages.warp_name_taken");
            event.setCancelled(true);
            return;
        }

        if (!canCreateWarp(player)) {
            int maxWarps = config.getInt("max-warps-per-player", 10);
            int currentWarps = Warp.getPlayerWarpCount(player.getUniqueId().toString());
            Map<String, String> placeholders = Map.of(
                    "{current}", String.valueOf(currentWarps),
                    "{max}", String.valueOf(maxWarps)
            );
            sendConfigMessage(player, "messages.warp_limit_reached", placeholders);
            event.setCancelled(true);
            return;
        }

        // 檢查並扣除物品
        if (!checkAndConsumeItem(player
        )) {
            event.setCancelled(true);
            return;
        }

        // 建立傳送目標
        createWarpTarget(event, player, signData);
    }

    private void createWarpTarget(SignChangeEvent event, Player player, SignData signData) {
        String currentDateTime = LocalDateTime.now().toString();
        boolean defaultVisibility = config.getBoolean("default-visibility", false);
        Warp warp = new Warp(signData.warpName, event.getBlock().getLocation(), currentDateTime,
                player.getName(), player.getUniqueId().toString(), defaultVisibility);
        warp.save();
        event.line(0, Component.text(SignData.HEADER_TARGET).color(NamedTextColor.BLUE));
        boolean showCreatorOnSign = config.getBoolean("show-creator-on-sign", true);
        if (showCreatorOnSign) {
            String creatorDisplayFormat = config.getString("messages.creator-display-format", "<gray>建立者: <white>{creator}");
            String formattedCreatorInfo = creatorDisplayFormat.replace("{creator}", player.getName());
            event.line(2, miniMessage.deserialize(formattedCreatorInfo));
        }

        sendConfigMessage(player, "messages.target_sign_created");
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material blockType = block.getType();

        if (!Tag.ALL_SIGNS.isTagged(blockType)) {
            if (hasBlockWarpSign(block)) {
                event.setCancelled(true);
            }
            return;
        }

        SignData signData = getSignData(block);
        if (signData == null || !signData.isWarpTarget() || !signData.isValidWarpName()) {
            return;
        }

        Player player = event.getPlayer();
        Warp warp = Warp.getByName(signData.warpName);
        if (warp == null) {
            return;
        }

        // 權限檢查
        boolean hasPermission = hasPermission(player, PermissionType.DESTROY) ||
                player.getUniqueId().toString().equals(warp.getCreatorUuid());

        if (!hasPermission) {
            sendConfigMessage(player, "messages.destroy_permission");
            event.setCancelled(true);
            return;
        }
        // 執行破壞
        warp.remove();
        sendConfigMessage(player, "messages.warp_destroyed");

    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        SignData signData = getSignData(block);
        if (signData == null) {
            return;
        }

        // 自動鎖定標識牌
        Sign signBlock = SignUtils.getSignFromBlock(block);
        if (signData.isWarpSign() && signBlock != null && !signBlock.isWaxed()) {
            signBlock.setWaxed(true);
            signBlock.update();
        }

        if (!signData.isWarp() || !signData.isValidWarpName()) {
            return;
        }

        Player player = event.getPlayer();
        handleTeleportRequest(player, signData.warpName, event.getItem());
    }

    private void handleTeleportRequest(Player player, String warpName, ItemStack handItem) {
        UUID playerId = player.getUniqueId();
        Warp warp = Warp.getByName(warpName);

        // 檢查傳送點權限
        if (warp != null && !canUseWarp(player, warp)) {
            return;
        }

        // 檢查冷卻
        if (!checkCooldown(player)) {
            return;
        }

        // 檢查使用權限
        if (!hasPermission(player, PermissionType.USE)) {
            return;
        }

        // 檢查是否已在傳送中
        if (teleportTasks.containsKey(playerId)) {
            return;
        }

        // 檢查船隻限制
        if (player.getVehicle() instanceof Boat) {
            sendConfigMessage(player, "messages.cannot_teleport_on_boat");
            return;
        }

        // 檢查並扣除使用物品
        String useItem = config.getString("use-item", "none");
        if (!"none".equalsIgnoreCase(useItem)) {
            Material requiredMaterial = Material.getMaterial(useItem.toUpperCase());

            if (handItem == null || handItem.getType() != requiredMaterial) {
                Map<String, String> placeholders = Map.of("{use-item}", useItem);
                sendConfigMessage(player, "messages.invalid_item", placeholders);
                return;
            }

            int useCost = config.getInt("use-cost", 0);
            if (handItem.getAmount() < useCost) {
                Map<String, String> placeholders = Map.of(
                        "{use-cost}", String.valueOf(useCost),
                        "{use-item}", useItem
                );
                sendConfigMessage(player, "messages.not_enough_item", placeholders);
                return;
            }

            // 扣除物品
            int remaining = handItem.getAmount() - useCost;
            if (remaining <= 0) {
                player.getInventory().setItemInMainHand(null);
            } else {
                handItem.setAmount(remaining);
                player.getInventory().setItemInMainHand(handItem);
            }
            pendingItemCosts.put(playerId, useCost);
        }

        teleportPlayer(player, warpName);
    }

    private void teleportPlayer(Player player, String warpName) {
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            returnPendingItems(player);
            sendConfigMessage(player, "messages.warp_not_found");
            return;
        }

        // 再次檢查權限（防止競態條件）
        if (warp.isPrivate() && !canUseWarp(player, warp)) {
            returnPendingItems(player);
            return;
        }

        // 檢查跨次元傳送限制
        if (!canCrossDimensionTeleport(player, warp)) {
            return;
        }

        int teleportDelay = config.getInt("teleport-delay", 5);
        Map<String, String> placeholders = Map.of(
                "{warp-name}", warp.getName(),
                "{time}", String.valueOf(teleportDelay)
        );
        sendConfigMessage(player, "messages.teleport", placeholders);

        // 開始視覺特效
        teleportEffects.startTeleportEffects(player, teleportDelay);

        scheduleTeleportTask(player, warp, teleportDelay);
    }

    private void scheduleTeleportTask(Player player, Warp warp, int delay) {
        UUID playerUUID = player.getUniqueId();

        // 取消之前的傳送任務
        BukkitTask previousTask = teleportTasks.get(playerUUID);
        if (previousTask != null) {
            previousTask.cancel();
        }

        // 準備傳送相關實體
        Collection<Entity> leashedEntities = collectLeashedEntities(player);
        Entity playerVehicle = player.getVehicle();
        Boat nearestBoat = findNearestBoatWithPassengers(player, leashedEntities); // 傳入韁繩實體列表

        // 排程傳送任務
        BukkitTask teleportTask = Bukkit.getScheduler().runTaskLater(plugin, () -> executeTeleport(player, warp, playerVehicle, nearestBoat, leashedEntities), delay * 20L);

        teleportTasks.put(playerUUID, teleportTask);
    }

    /**
     * 優化版本的遞迴牽引收集方法，增加深度限制和警告訊息
     */
    private Collection<Entity> collectLeashedEntities(Player player) {
        Set<Entity> allLeashedEntities = new HashSet<>();
        Set<Entity> visited = new HashSet<>();

        // 從配置檔讀取最大牽引深度，預設為5層
        int maxLeashDepth = config.getInt("max-leash-depth", 5);
        // 從配置檔讀取是否啟用遞迴牽引，預設啟用
        boolean enableRecursiveLeash = config.getBoolean("enable-recursive-leash", true);

        if (enableRecursiveLeash) {
            // 使用 AtomicInteger 來追蹤實際達到的最大深度
            AtomicInteger maxReachedDepth = new AtomicInteger(0);

            // 從玩家開始遞迴收集所有牽引的實體
            collectLeashedEntitiesRecursive(player, allLeashedEntities, visited, 0, maxLeashDepth, maxReachedDepth);

            // 檢查玩家的坐騎是否也在牽引實體
            Entity playerVehicle = player.getVehicle();
            if (playerVehicle instanceof LivingEntity vehicleEntity) {
                collectLeashedEntitiesRecursive(vehicleEntity, allLeashedEntities, visited, 0, maxLeashDepth, maxReachedDepth);
            }

            // 檢查是否超過最大深度並發出警告
            if (maxReachedDepth.get() >= maxLeashDepth) {
                sendLeashDepthWarning(player, maxLeashDepth, allLeashedEntities.size());
            }
        } else {
            // 如果停用遞迴功能，使用原本的邏輯
            collectLeashedEntitiesOriginal(player, allLeashedEntities);
        }

        return allLeashedEntities;
    }

    /**
     * 帶深度限制和深度追蹤的遞迴牽引收集方法
     */
    private void collectLeashedEntitiesRecursive(Entity holder, Set<Entity> collectedEntities,
                                                 Set<Entity> visited, int currentDepth, int maxDepth,
                                                 AtomicInteger maxReachedDepth) {
        // 更新實際達到的最大深度
        maxReachedDepth.set(Math.max(maxReachedDepth.get(), currentDepth));

        // 檢查深度限制
        if (currentDepth >= maxDepth) {
            return;
        }

        // 防止重複處理同一個實體
        if (visited.contains(holder)) {
            return;
        }
        visited.add(holder);

        // 搜尋範圍內所有被此實體牽引的生物
        for (Entity entity : holder.getNearbyEntities(14, 14, 14)) {
            if (entity instanceof LivingEntity livingEntity) {
                // 檢查是否被當前holder牽引
                if (livingEntity.isLeashed() && livingEntity.getLeashHolder().equals(holder)) {
                    // 避免重複添加
                    if (!collectedEntities.contains(livingEntity)) {
                        collectedEntities.add(livingEntity);

                        // 遞迴檢查這個被牽引的實體是否也在牽引其他實體
                        collectLeashedEntitiesRecursive(livingEntity, collectedEntities, visited,
                                currentDepth + 1, maxDepth, maxReachedDepth);
                    }
                }
            }
        }
    }

    /**
     * 發送牽引深度警告訊息給玩家
     */
    private void sendLeashDepthWarning(Player player, int maxDepth, int totalEntities) {
        // 檢查是否啟用警告訊息
        boolean enableDepthWarning = config.getBoolean("enable-leash-depth-warning", true);
        if (!enableDepthWarning) {
            return;
        }

        Map<String, String> placeholders = Map.of(
                "{max-depth}", String.valueOf(maxDepth),
                "{total-entities}", String.valueOf(totalEntities)
        );

        sendConfigMessage(player, "messages.leash_depth_warning", placeholders);
    }

    /**
     * 原本的邏輯（作為後備方案）
     */
    private void collectLeashedEntitiesOriginal(Player player, Set<Entity> collectedEntities) {
        // 1. 收集玩家直接牽引的實體
        for (Entity entity : player.getNearbyEntities(14, 14, 14)) {
            if (entity instanceof LivingEntity livingEntity) {
                if (livingEntity.isLeashed() && livingEntity.getLeashHolder().equals(player)) {
                    collectedEntities.add(livingEntity);
                }
            }
        }

        // 2. 檢查玩家的坐騎是否也在牽引實體
        Entity playerVehicle = player.getVehicle();
        if (playerVehicle instanceof LivingEntity vehicleEntity) {
            for (Entity entity : playerVehicle.getNearbyEntities(14, 14, 14)) {
                if (entity instanceof LivingEntity livingEntity) {
                    if (livingEntity.isLeashed() && livingEntity.getLeashHolder().equals(vehicleEntity)) {
                        collectedEntities.add(livingEntity);
                    }
                }
            }
        }
    }

    private Boat findNearestBoatWithPassengers(Player player, Collection<Entity> leashedEntities) {
        Boat nearestBoat = null;
        double minDistance = Double.MAX_VALUE;
        Location playerLoc = player.getLocation();

        for (Entity entity : player.getWorld().getNearbyEntities(playerLoc, 5, 5, 5)) {
            if (entity instanceof Boat boat) {
                boolean shouldTeleportBoat = false;

                // 原有邏輯：檢查是否有非玩家乘客
                boolean hasNonPlayerPassenger = boat.getPassengers().stream()
                        .anyMatch(passenger -> !(passenger instanceof Player));

                if (hasNonPlayerPassenger) {
                    shouldTeleportBoat = true;
                }

                // 新增邏輯：檢查船隻是否被韁繩拴住
                // 檢查船隻本身是否在牽引實體列表中（雖然船隻不是LivingEntity，但可能有特殊情況）
                if (leashedEntities.contains(boat)) {
                    shouldTeleportBoat = true;
                }

                // 檢查船隻的乘客是否被韁繩拴住
                for (Entity passenger : boat.getPassengers()) {
                    if (leashedEntities.contains(passenger)) {
                        shouldTeleportBoat = true;
                        break;
                    }
                }

                // 檢查韁繩實體是否在船隻附近（可能正在拖拽船隻）
                for (Entity leashedEntity : leashedEntities) {
                    if (leashedEntity.getLocation().distance(boat.getLocation()) <= 3.0) {
                        shouldTeleportBoat = true;
                        break;
                    }
                }

                if (shouldTeleportBoat) {
                    double distance = boat.getLocation().distance(playerLoc);
                    if (distance < minDistance) {
                        minDistance = distance;
                        nearestBoat = boat;
                    }
                }
            }
        }
        return nearestBoat;
    }

    private Location findSafeLocation(Location base) {
        World world = base.getWorld();
        int x = base.getBlockX();
        int z = base.getBlockZ();
        int startY = base.getBlockY();
        int maxY = world.getMaxHeight();

        // 從目標位置向上尋找空氣方塊，腳下必須是實體方塊
        for (int y = startY; y < maxY - 1; y++) {
            Location loc = new Location(world, x + 0.5, y, z + 0.5);
            Location feet = new Location(world, x, y, z);
            Location head = new Location(world, x, y + 1, z);

            if (world.getBlockAt(feet).getType().isAir() && world.getBlockAt(head).getType().isAir()) {
                Location below = new Location(world, x, y - 1, z);
                if (!world.getBlockAt(below).getType().isAir()) {
                    return loc;
                }
            }
        }
        // 如果找不到安全空間，返回原始位置
        return base;
    }

    private void executeTeleport(Player player, Warp warp, Entity playerVehicle,
                                 Boat nearestBoat, Collection<Entity> leashedEntities) {
        Location targetLocation = findSafeLocation(warp.getLocation());
        UUID playerUUID = player.getUniqueId();

        // 在傳送前記錄來源世界名稱，避免傳送後獲取錯誤的世界
        String fromWorldName = player.getWorld().getName();
        String toWorldName = targetLocation.getWorld() != null ? targetLocation.getWorld().getName() : "unknown";

        // 檢查是否啟用牽繩保存功能（新增配置選項）
        boolean preserveLeashConnections = config.getBoolean("preserve-leash-connections", true);
        Map<Entity, Entity> leashConnections = new HashMap<>();

        if (preserveLeashConnections) {
            // 記錄並暫時移除牽繩連接
            for (Entity entity : leashedEntities) {
                if (entity instanceof LivingEntity livingEntity && livingEntity.isLeashed()) {
                    Entity leashHolder = livingEntity.getLeashHolder();
                    if (leashHolder != null) {
                        // 記錄牽繩關係
                        leashConnections.put(entity, leashHolder);

                        // 暫時移除牽繩連接（避免掉落牽繩物品）
                        livingEntity.setLeashHolder(null);
                    }
                }
            }

            // 清理可能掉落的牽繩物品
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Entity nearbyEntity : player.getNearbyEntities(8, 8, 8)) {
                    if (nearbyEntity instanceof org.bukkit.entity.Item item) {
                        if (item.getItemStack().getType() == Material.LEAD) {
                            item.remove();
                        }
                    }
                }
            });
        }

        // 先傳送牽引的實體
        leashedEntities.forEach(entity -> entity.teleport(targetLocation));

        // 傳送玩家
        player.teleport(targetLocation);

        // 處理載具傳送
        if (playerVehicle != null && !playerVehicle.isDead()) {
            playerVehicle.teleport(targetLocation);
            player.teleport(targetLocation);
            // 延遲 5 tick 再讓玩家上載具，確保載具已到達
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!playerVehicle.getPassengers().contains(player)) {
                    playerVehicle.addPassenger(player);
                }
            }, 5L);
        }

        // 處理船隻傳送
        if (nearestBoat != null) {
            teleportBoatWithPassengers(nearestBoat, targetLocation);
        }

        // 恢復牽繩連接（延遲執行確保實體完全傳送完成）
        if (preserveLeashConnections && !leashConnections.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Map.Entry<Entity, Entity> entry : leashConnections.entrySet()) {
                    Entity leashedEntity = entry.getKey();
                    Entity leashHolder = entry.getValue();

                    // 檢查實體是否仍然有效且在同一世界
                    if (leashedEntity instanceof LivingEntity livingEntity &&
                            !leashedEntity.isDead() &&
                            leashHolder != null &&
                            !leashHolder.isDead() &&
                            leashedEntity.getWorld().equals(leashHolder.getWorld())) {

                        // 檢查距離是否在合理範圍內（10格以內）
                        double distance = leashedEntity.getLocation().distance(leashHolder.getLocation());
                        if (distance <= 10.0) {
                            try {
                                livingEntity.setLeashHolder(leashHolder);
                            } catch (Exception e) {
                                // 如果恢復失敗，記錄警告但不中斷傳送流程
                                plugin.getLogger().warning("[SignWarp] 無法恢復牽繩連接: " + e.getMessage());
                            }
                        } else {
                            // 距離太遠，發送警告訊息給玩家
                            if (leashHolder instanceof Player playerHolder) {
                                sendConfigMessage(playerHolder, "messages.leash_restore_failed_distance");
                            }
                        }
                    }
                }
            }, 3L); // 延遲 3 tick 確保傳送完全完成
        }

        // 完成傳送特效
        teleportEffects.completeTeleportEffects(player, targetLocation);

        // 記錄傳送歷史（使用傳送前記錄的世界名稱）
        TeleportHistory.recordTeleport(player.getName(), player.getUniqueId().toString(),
                warp.getName(), fromWorldName, toWorldName);

        // 完成傳送
        finalizeTeleport(player, warp);
    }

    private void teleportBoatWithPassengers(Boat boat, Location targetLocation) {
        List<Entity> nonPlayerPassengers = boat.getPassengers().stream()
                .filter(passenger -> !(passenger instanceof Player))
                .toList();

        // 解除乘客關係
        nonPlayerPassengers.forEach(boat::removePassenger);

        // 傳送船隻和乘客
        boat.teleport(targetLocation);
        nonPlayerPassengers.forEach(passenger -> passenger.teleport(targetLocation));

        // 恢復乘客關係
        nonPlayerPassengers.forEach(boat::addPassenger);
    }

    private void finalizeTeleport(Player player, Warp warp) {
        UUID playerUUID = player.getUniqueId();

        Map<String, String> placeholders = Map.of("{warp-name}", warp.getName());
        sendConfigMessage(player, "messages.teleport-success", placeholders);

        // 清除扣除物品記錄
        pendingItemCosts.remove(playerUUID);

        // 設定冷卻
        int useCooldown = config.getInt("teleport-use-cooldown", 5);
        cooldowns.put(playerUUID, System.currentTimeMillis() + useCooldown * 1000L);

        // 清除傳送任務記錄
        teleportTasks.remove(playerUUID);
    }

    private boolean canCrossDimensionTeleport(Player player, Warp warp) {
        World playerWorld = player.getWorld();
        World warpWorld = warp.getLocation().getWorld();

        if (warpWorld == null) {
            sendConfigMessage(player, "messages.warp_world_not_found");
            returnPendingItems(player);
            return false;
        }

        if (!isDifferentWorld(playerWorld, warpWorld)) {
            return true;
        }

        boolean crossDimensionEnabled = config.getBoolean("cross-dimension-teleport.enabled", true);
        if (!crossDimensionEnabled) {
            boolean opBypass = config.getBoolean("cross-dimension-teleport.op-bypass", true);
            if (opBypass && player.isOp()) {
                return true;
            }

            String targetWorldName = getDisplayWorldName(warpWorld.getName());
            Map<String, String> placeholders = Map.of("{target-world}", targetWorldName);
            sendConfigMessage(player, "messages.cross_dimension_disabled", placeholders);
            returnPendingItems(player);
            return false;
        }

        return true;
    }

    private String getDisplayWorldName(String worldName) {
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

    private boolean isDifferentWorld(World world1, World world2) {
        if (world1 == null || world2 == null) {
            return true;
        }
        return !world1.equals(world2);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (teleportTasks.containsKey(playerUUID)) {
            Location from = event.getFrom();
            Location to = event.getTo();

            if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
                cancelTeleportTask(player, "messages.teleport-cancelled");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Player player = event.getPlayer();

        if (teleportTasks.containsKey(playerId)) {
            BukkitTask task = teleportTasks.get(playerId);
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
            teleportTasks.remove(playerId);
            returnPendingItems(player);
        }

        pendingItemCosts.remove(playerId);
        cooldowns.remove(playerId);

        // 清理特效
        teleportEffects.cleanupPlayer(player);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        cancelTeleportTask(player, "messages.teleport-death-cancelled");
    }

    @EventHandler
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        if (hasBlockWarpSign(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    // ============= 方塊保護相關事件 =============

    @EventHandler
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        if (hasBlockWarpSign(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        if (hasBlockWarpSign(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (hasBlockWarpSign(event.blockList())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if (hasBlockWarpSign(event.blockList())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (isGravityAffected(block.getType())) {
            if (hasBlockWarpSign(block)) {
                event.setCancelled(true);
                return;
            }

            Block above = block.getRelative(BlockFace.UP);
            Sign signAbove = SignUtils.getSignFromBlock(above);
            if (signAbove != null && isWarpSign(signAbove)) {
                event.setCancelled(true);
            }
        }
    }

    private boolean isGravityAffected(Material type) {
        return type == Material.SAND || type == Material.GRAVEL ||
                type == Material.ANVIL || type == Material.RED_SAND
                || type == Material.TNT || type == Material.WHITE_CONCRETE_POWDER
                || type == Material.ORANGE_CONCRETE_POWDER || type == Material.MAGENTA_CONCRETE_POWDER
                || type == Material.LIGHT_BLUE_CONCRETE_POWDER || type == Material.YELLOW_CONCRETE_POWDER
                || type == Material.LIME_CONCRETE_POWDER || type == Material.PINK_CONCRETE_POWDER
                || type == Material.GRAY_CONCRETE_POWDER || type == Material.LIGHT_GRAY_CONCRETE_POWDER
                || type == Material.CYAN_CONCRETE_POWDER || type == Material.PURPLE_CONCRETE_POWDER
                || type == Material.BLUE_CONCRETE_POWDER || type == Material.BROWN_CONCRETE_POWDER
                || type == Material.GREEN_CONCRETE_POWDER || type == Material.BLACK_CONCRETE_POWDER
                || type == Material.RED_CONCRETE_POWDER;
    }

    // ============= 輔助方法 =============

    private boolean hasBlockWarpSign(Block block) {
        return SignUtils.hasBlockSign(block, this::isWarpSign);
    }

    private boolean hasBlockWarpSign(List<Block> blocks) {
        return SignUtils.hasBlockSign(blocks, this::isWarpSign);
    }

    private boolean isWarpSign(Sign signBlock) {
        Component[] lines = new Component[4];
        for (int i = 0; i < 4; i++) {
            lines[i] = signBlock.getSide(Side.FRONT).line(i);
        }
        SignData signData = new SignData(lines);
        return signData.isWarpSign();
    }

    /**
     * 統一的權限檢查方法
     */
    private enum PermissionType {
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
    }
}