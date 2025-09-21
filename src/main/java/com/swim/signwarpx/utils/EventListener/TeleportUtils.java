package com.swim.signwarpx.utils.EventListener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 傳送相關工具類
 * 功能：
 *  1. 傳送任務管理
 *  2. 在局部 5x5x5 立方體中選擇安全落點
 *  3. 方向加權：優先靠近並位於告示牌「正面」方向的安全點
 *  4. 找不到局部落點 → 螺旋搜尋 → 最終向上 fallback
 * 注意：不從 config 讀取任何搜尋/加權參數，全部為內建常數。
 */
public class TeleportUtils {

    /* ================= 任務管理結構 ================= */

    private static final ConcurrentHashMap<UUID, BukkitTask> teleportTasks = new ConcurrentHashMap<>();

    /* ================= 搜尋與方向加權常數 ================= */

    // 立方體半徑：2 → 邊長 5 (|dx|,|dy|,|dz| ≤ 2)
    private static final int LOCAL_CUBE_RADIUS = 2;

    // 最多收集候選點（性能保護上限）
    private static final int MAX_CANDIDATES = 20;

    // 螺旋搜尋最大半徑
    private static final int SPIRAL_MAX_RADIUS = 16;

    // 前方方向距離成本折扣最大值 (正對時距離成本 * (1 - FORWARD_BONUS))
    private static final double FORWARD_BONUS = 0.4;

    // 後方方向距離成本增幅最大值 (正後方距離成本 * (1 + BACK_PENALTY))
    private static final double BACK_PENALTY = 0.8;

    // 防止極端權重把成本壓得過低
    private static final double MIN_WEIGHT_CLAMP = 0.05;

    /* ================= 對外任務 API ================= */

    public static void cancelTeleportTask(Player player, JavaPlugin plugin, String messageKey) {
        UUID playerUUID = player.getUniqueId();
        BukkitTask teleportTask = teleportTasks.get(playerUUID);
        if (teleportTask != null && !teleportTask.isCancelled()) {
            teleportTask.cancel();
            teleportTasks.remove(playerUUID);

            // 返還物品
            ItemUtils.returnPendingItems(player, plugin.getConfig());
            // 訊息
            MessageUtils.sendConfigMessage(player, plugin.getConfig(), messageKey);
        }
    }

    public static void addTeleportTask(UUID playerUUID, BukkitTask task) {
        BukkitTask previousTask = teleportTasks.get(playerUUID);
        if (previousTask != null && !previousTask.isCancelled()) {
            previousTask.cancel();
        }
        teleportTasks.put(playerUUID, task);
    }

    public static void removeTeleportTask(UUID playerUUID) {
        teleportTasks.remove(playerUUID);
    }

    public static boolean hasTeleportTask(UUID playerUUID) {
        return teleportTasks.containsKey(playerUUID);
    }

    public static void cleanupPlayer(Player player, JavaPlugin plugin) {
        UUID playerUUID = player.getUniqueId();
        BukkitTask task = teleportTasks.get(playerUUID);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        teleportTasks.remove(playerUUID);

        ItemUtils.returnPendingItems(player, plugin.getConfig());
        ItemUtils.clearPendingItems(playerUUID);
        CooldownUtils.clearCooldown(playerUUID);
    }

    public static int getTeleportTaskCount() {
        return teleportTasks.size();
    }

    public static void cleanupCompletedTasks() {
        teleportTasks.entrySet().removeIf(entry -> {
            BukkitTask task = entry.getValue();
            return task.isCancelled() || !Bukkit.getScheduler().isQueued(task.getTaskId());
        });
    }

    /* ================= 對外主函式：尋找安全落點 ================= */

    /**
     * 統一入口：
     *  1. 局部 5x5x5 內收集候選並加權選擇
     *  2. 失敗 → 螺旋搜尋
     *  3. 再失敗 → 向上 fallback
     */
    public static Location findSafeLocation(Location base) {
        World world = base.getWorld();
        if (world == null) return base;

        Vector forward = deriveForwardVector(base);

        Location local = findNearestSafeInCube(base, forward);
        if (local != null) return local;

        Location spiral = findSafeLocationSpiral(base);
        if (spiral != null) return spiral;

        return findSafeLocationAbove(base);
    }

    /* ================= 局部候選搜尋 + 方向加權 ================= */

    private static Location findNearestSafeInCube(Location base,
                                                  Vector forward) {

        World world = base.getWorld();
        if (world == null) return null;

        int bx = base.getBlockX();
        int by = base.getBlockY();
        int bz = base.getBlockZ();

        List<Location> candidates = new ArrayList<>(Math.min(TeleportUtils.MAX_CANDIDATES, 64));

        for (int layer = 0; layer <= TeleportUtils.LOCAL_CUBE_RADIUS; layer++) {
            for (int dx = -layer; dx <= layer; dx++) {
                for (int dy = -layer; dy <= layer; dy++) {
                    for (int dz = -layer; dz <= layer; dz++) {
                        int cheb = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
                        if (cheb != layer) continue;

                        int x = bx + dx;
                        int y = by + dy;
                        int z = bz + dz;

                        if (isSafePosition(world, x, y, z)) {
                            candidates.add(new Location(world, x + 0.5, y, z + 0.5));
                            if (candidates.size() >= TeleportUtils.MAX_CANDIDATES) {
                                return chooseWithDirectionalWeight(base, candidates, forward);
                            }
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) return null;
        return chooseWithDirectionalWeight(base, candidates, forward);
    }

    /**
     * 方向加權成本：
     * cost = distSq * weight
     *  dot = 水平單位向量與 forward 單位向量內積
     *  前方(dot >= 0): weight = 1 - dot * FORWARD_BONUS
     *  後方(dot < 0):  weight = 1 + (-dot) * BACK_PENALTY
     */
    private static Location chooseWithDirectionalWeight(Location base,
                                                        List<Location> candidates,
                                                        Vector forward) {
        if (forward == null || forward.lengthSquared() == 0) {
            return chooseByDistance(base, candidates);
        }

        Vector fwdNorm = forward.clone().normalize();
        Vector baseVec = base.toVector();

        Location best = null;
        double bestCost = Double.MAX_VALUE;
        // tie-breaker 指標（優先避免左右偏移）
        double bestLateralDist = Double.MAX_VALUE;
        double bestAbsDy = Double.MAX_VALUE;
        double bestForwardDist = -Double.MAX_VALUE;
        final double EPS = 1e-9;

        for (Location c : candidates) {
            double dx = c.getX() - base.getX();
            double dy = c.getY() - base.getY();
            double dz = c.getZ() - base.getZ();
            double distSq = dx * dx + dy * dy + dz * dz;

            Vector horiz = c.toVector().subtract(baseVec);
            horiz.setY(0);

            double weight;
            double dot; // 用於 tie-breaker
            double horizLen = horiz.length();
            if (horizLen == 0) {
                weight = 1 - FORWARD_BONUS; // 同一格：視為正前最優惠
                dot = 1.0;
            } else {
                dot = horiz.clone().normalize().dot(fwdNorm); // [-1,1]
                if (dot >= 0) {
                    weight = 1 - dot * FORWARD_BONUS;
                } else {
                    weight = 1 + (-dot) * BACK_PENALTY;
                }
            }
            if (weight < MIN_WEIGHT_CLAMP) weight = MIN_WEIGHT_CLAMP;

            double cost = distSq * weight;

            // 計算左右偏移（lateralDist 越小越正中）
            double forwardDist = Math.max(dot, 0) * horizLen; // 正前投影距離（負向不計）
            double lateralDist = Math.sqrt(Math.max(horizLen * horizLen - forwardDist * forwardDist, 0));
            double absDy = Math.abs(dy);

            boolean better;
            // 1) 盡可能不偏左/右：先比較 lateralDist
            if (lateralDist + EPS < bestLateralDist) {
                better = true;
            } else if (Math.abs(lateralDist - bestLateralDist) <= EPS) {
                // 2) lateral 相等時，選擇成本較低者（距離×方向權重）
                if (cost + EPS < bestCost) {
                    better = true;
                } else if (Math.abs(cost - bestCost) <= EPS) {
                    // 3) 再比較高度差較小者
                    if (absDy + EPS < bestAbsDy) {
                        better = true;
                    } else if (Math.abs(absDy - bestAbsDy) <= EPS) {
                        // 4) 最後偏向更靠正前
                        better = forwardDist > bestForwardDist + EPS;
                    } else {
                        better = false;
                    }
                } else {
                    better = false;
                }
            } else {
                better = false;
            }

            if (better) {
                best = c;
                bestLateralDist = lateralDist;
                bestCost = cost;
                bestAbsDy = absDy;
                bestForwardDist = forwardDist;
            }
        }
        return best;
    }

    /**
     * 純距離選最近（給 forward 無法推導時）
     */
    private static Location chooseByDistance(Location base, List<Location> list) {
        double bx = base.getX();
        double by = base.getY();
        double bz = base.getZ();
        Location best = null;
        double bestDist = Double.MAX_VALUE;
        double bestAbsDy = Double.MAX_VALUE;
        double bestManhattan = Double.MAX_VALUE;
        double bestAxisImbalance = Double.MAX_VALUE;
        final double EPS = 1e-9;

        for (Location loc : list) {
            double dx = loc.getX() - bx;
            double dy = loc.getY() - by;
            double dz = loc.getZ() - bz;
            double dist = dx * dx + dy * dy + dz * dz;

            double absDy = Math.abs(dy);
            double manhattan = Math.abs(dx) + Math.abs(dz);
            double axisImbalance = Math.abs(Math.abs(dx) - Math.abs(dz));

            boolean better;
            if (dist + EPS < bestDist) {
                better = true;
            } else if (Math.abs(dist - bestDist) <= EPS) {
                // 距離幾乎相等：優先較小垂直差、再較小水平曼哈頓距離、再較小軸失衡
                if (absDy + EPS < bestAbsDy) {
                    better = true;
                } else if (Math.abs(absDy - bestAbsDy) <= EPS) {
                    if (manhattan + EPS < bestManhattan) {
                        better = true;
                    } else if (Math.abs(manhattan - bestManhattan) <= EPS) {
                        better = axisImbalance + EPS < bestAxisImbalance;
                    } else {
                        better = false;
                    }
                } else {
                    better = false;
                }
            } else {
                better = false;
            }

            if (better) {
                bestDist = dist;
                best = loc;
                bestAbsDy = absDy;
                bestManhattan = manhattan;
                bestAxisImbalance = axisImbalance;
            }
        }
        return best;
    }

    /* ================= 告示牌朝向推導 ================= */

    /**
     * 推導告示牌「正面」方向向量：
     *  1. 牆上告示牌：WallSign.getFacing() 為玩家面向告示牌閱讀時的方向，
     *     這正是我們要的正面方向（有字的那一面）
     *  2. 站立告示牌：優先使用方塊的 rotation → 轉換為 yaw → 方向向量
     *  3. 若 block 不是告示牌或例外 → 回傳 null
     */
    private static Vector deriveForwardVector(Location base) {
        try {
            Block block = base.getBlock();
            Material type = block.getType();

            // 牆上告示牌
            if (Tag.WALL_SIGNS.isTagged(type)) {
                BlockData data = block.getBlockData();
                if (data instanceof WallSign wallSign) {
                    Vector dir = wallSign.getFacing().getDirection();
                    if (dir.lengthSquared() > 0) return dir.normalize();
                }
            }

            // 站立告示牌：使用方塊 rotation，不使用 yaw
            if (Tag.STANDING_SIGNS.isTagged(type)) {
                BlockData data = block.getBlockData();
                if (data instanceof org.bukkit.block.data.type.Sign standing) {
                    Vector dir = standing.getRotation().getDirection();
                    dir.setY(0);
                    if (dir.lengthSquared() > 0) return dir.normalize();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /* ================= 螺旋與向上 Fallback ================= */

    private static Location findSafeLocationSpiral(Location base) {
        World world = base.getWorld();
        if (world == null) return null;

        int centerX = base.getBlockX();
        int centerZ = base.getBlockZ();
        int baseY = base.getBlockY();

        for (int radius = 1; radius <= SPIRAL_MAX_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    Location safe = checkColumnSmallVertical(new Location(world, x, baseY, z));
                    if (safe != null) return safe;
                }
            }
        }
        return null;
    }

    private static Location checkColumnSmallVertical(Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        int x = location.getBlockX();
        int z = location.getBlockZ();
        int startY = location.getBlockY();
        int maxY = world.getMaxHeight();
        int minY = world.getMinHeight();

        // 向上最多 10
        for (int y = startY; y < Math.min(maxY - 1, startY + 10); y++) {
            if (isSafePosition(world, x, y, z)) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        // 向下最多 5
        for (int y = startY - 1; y > Math.max(minY, startY - 5); y--) {
            if (isSafePosition(world, x, y, z)) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return null;
    }

    private static Location findSafeLocationAbove(Location base) {
        World world = base.getWorld();
        if (world == null) return base;
        int x = base.getBlockX();
        int z = base.getBlockZ();
        int maxY = world.getMaxHeight();

        for (int y = base.getBlockY(); y < maxY - 1; y++) {
            if (isSafePosition(world, x, y, z)) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return new Location(world, x + 0.5, maxY - 2, z + 0.5);
    }

    /* ================= 安全性判定 ================= */

    /**
     * 腳與頭必須是空氣（或可通過），腳下必須是實體方塊（非空氣）
     * 可依需求擴充：排除危險方塊（火、岩漿等）
     */
    private static boolean isSafePosition(World world, int x, int y, int z) {
        try {
            // 邊界保護：避免頂層或底層越界
            if (y < world.getMinHeight() + 1 || y > world.getMaxHeight() - 2) return false;

            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            Block below = world.getBlockAt(x, y - 1, z);

            if (!feet.getType().isAir()) return false;
            if (!head.getType().isAir()) return false;
            if (below.getType().isAir()) return false;

            // 可選：排除危險方塊（示例，可自行擴充清單）
            Material unsafeBelow = below.getType();
            return unsafeBelow != Material.LAVA && unsafeBelow != Material.FIRE && unsafeBelow != Material.CACTUS;
        } catch (Exception e) {
            return false;
        }
    }
}