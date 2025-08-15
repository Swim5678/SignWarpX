package com.swim.signwarpx;

import com.swim.signwarpx.utils.EffectUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 處理傳送過程中的視覺和音效特效
 */
public class TeleportEffects {
    private final JavaPlugin plugin;
    private final FileConfiguration config;
    // 儲存玩家的進度條和充能任務
    private final ConcurrentHashMap<Player, BukkitTask> chargingTasks = new ConcurrentHashMap<>();
    // 儲存信標光束任務
    private final ConcurrentHashMap<Player, BukkitTask> beaconTasks = new ConcurrentHashMap<>();

    public TeleportEffects(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    /**
     * 開始傳送特效序列
     */
    public void startTeleportEffects(Player player, int delaySeconds) {
        if (!config.getBoolean("visual-effects-enabled", true)) {
            return;
        }

        Location playerLoc = player.getLocation();

        // 播放開始音效
        playStartSound(player);

        // 顯示出發地粒子特效
        showDepartureParticles(playerLoc);

        // 創建信標光束特效
        createBeaconBeam(player, delaySeconds);

        // 開始充能特效
        startChargingEffects(player, delaySeconds);
    }

    /**
     * 完成傳送特效
     */
    public void completeTeleportEffects(Player player, Location arrivalLocation) {
        // 停止充能特效
        stopChargingEffects(player);

        if (!config.getBoolean("visual-effects-enabled", true)) {
            return;
        }

        // 播放完成音效
        playCompleteSound(player);

        // 顯示到達地粒子特效
        showArrivalParticles(arrivalLocation);

        // 創建到達光束特效
        createArrivalBeam(arrivalLocation);
    }

    /**
     * 取消傳送特效
     */
    public void cancelTeleportEffects(Player player) {
        if (!config.getBoolean("visual-effects-enabled", true)) {
            // 即使特效被禁用，也要清空 Action Bar
            player.sendActionBar(Component.empty());
            return;
        }

        // 停止充能特效
        stopChargingEffects(player);

        // 停止信標光束特效
        BukkitTask beaconTask = beaconTasks.remove(player);
        if (beaconTask != null && !beaconTask.isCancelled()) {
            beaconTask.cancel();
        }

        // 播放取消音效
        playCancelSound(player);
    }

    /**
     * 播放開始音效
     */
    private void playStartSound(Player player) {
        Sound sound = EffectUtils.getSound("BLOCK_BEACON_ACTIVATE", plugin);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 0.6f, 1.2f);
        }
    }

    /**
     * 播放完成音效
     */
    private void playCompleteSound(Player player) {
        Sound sound = EffectUtils.getSound("ENTITY_ENDERMAN_TELEPORT", plugin);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 0.5f, 1.0f);
        }
    }

    /**
     * 播放取消音效
     */
    private void playCancelSound(Player player) {
        Sound sound = EffectUtils.getSound("BLOCK_BEACON_DEACTIVATE", plugin);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 0.6f, 1.0f);
        }
    }

    /**
     * 顯示出發地粒子特效
     */
    private void showDepartureParticles(Location location) {
        Particle particle = EffectUtils.getParticle("PORTAL", plugin);
        if (particle != null) {
            World world = location.getWorld();
            if (world != null) {
                world.spawnParticle(particle, location.add(0, 1, 0), 15, 1.5, 1.5, 1.5, 0.1);
            }
        }
    }

    /**
     * 顯示到達地粒子特效
     */
    private void showArrivalParticles(Location location) {
        Particle particle = EffectUtils.getParticle("ENCHANTMENT_TABLE", plugin);
        if (particle != null) {
            World world = location.getWorld();
            if (world != null) {
                // 創建一個優雅的粒子爆發效果
                final Particle finalParticle = particle;
                Bukkit.getScheduler().runTask(plugin, () -> world.spawnParticle(finalParticle, location.add(0, 1, 0), 20, 2.0, 2.0, 2.0, 0.1));

                // 延遲一點再來一次，創造層次感
                Bukkit.getScheduler().runTaskLater(plugin, () -> world.spawnParticle(finalParticle, location, 10, 1.0, 1.0, 1.0, 0.05), 5L);
            }
        }
    }

    /**
     * 開始充能特效
     */
    private void startChargingEffects(Player player, int delaySeconds) {
        // 充能音效設置
        Sound chargingSound = EffectUtils.getSound("BLOCK_BEACON_AMBIENT", plugin);

        Particle particle = EffectUtils.getParticle("END_ROD", plugin);

        if (particle != null) {
            final Particle finalParticle = particle;
            final Sound finalChargingSound = chargingSound;

            final BukkitTask[] taskRef = new BukkitTask[1];
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (ticks >= delaySeconds * 20) {
                        if (taskRef[0] != null) {
                            taskRef[0].cancel();
                        }
                        return;
                    }

                    Location loc = player.getLocation().add(0, 1, 0);
                    World world = loc.getWorld();
                    if (world != null) {
                        // 顯示充能粒子
                        world.spawnParticle(finalParticle, loc, 5, 0.8, 0.8, 0.8, 0.02);

                        // 播放充能音效
                        if (finalChargingSound != null && ticks % 20 == 0) {
                            player.playSound(loc, finalChargingSound, 0.3f, 1.5f);
                        }
                    }

                    ticks++;
                }
            }, 0L, 1L);

            taskRef[0] = task;

            chargingTasks.put(player, task);
        }
    }

    /**
     * 停止充能特效
     */
    private void stopChargingEffects(Player player) {
        // 停止粒子充能任務
        BukkitTask chargingTask = chargingTasks.remove(player);
        if (chargingTask != null && !chargingTask.isCancelled()) {
            chargingTask.cancel();
        }

    }

    /**
     * 玩家離線時清理
     */
    public void cleanupPlayer(Player player) {
        // 立即清空 Action Bar
        player.sendActionBar(Component.empty());

        // 停止所有任務
        BukkitTask chargingTask = chargingTasks.remove(player);
        if (chargingTask != null && !chargingTask.isCancelled()) {
            chargingTask.cancel();
        }

        BukkitTask beaconTask = beaconTasks.remove(player);
        if (beaconTask != null && !beaconTask.isCancelled()) {
            beaconTask.cancel();
        }
    }

    /**
     * 創建信標光束特效
     */
    private void createBeaconBeam(Player player, int delaySeconds) {
        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();
        if (world == null) return;

        // 創建向上的光束特效
        final BukkitTask[] beamTaskRef = new BukkitTask[1];
        BukkitTask beamTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= delaySeconds * 20) {
                    if (beamTaskRef[0] != null) {
                        beamTaskRef[0].cancel();
                    }
                    return;
                }

                Location currentLoc = player.getLocation();
                World currentWorld = currentLoc.getWorld();
                if (currentWorld != null) {
                    // 創建向上的光束
                    for (int y = 0; y < 20; y++) {
                        Location beamLoc = currentLoc.clone().add(0, y, 0);

                        // 主光束 - 使用白色粒子
                        currentWorld.spawnParticle(Particle.END_ROD, beamLoc, 1, 0.1, 0.1, 0.1, 0.01);

                        // 光束邊緣 - 使用發光粒子
                        if (y % 2 == 0) {
                            currentWorld.spawnParticle(Particle.GLOW, beamLoc, 2, 0.3, 0.1, 0.3, 0.02);
                        }

                        // 光束核心 - 使用閃爍粒子
                        if (y % 3 == 0) {
                            currentWorld.spawnParticle(Particle.FIREWORK, beamLoc, 1, 0.05, 0.05, 0.05, 0.01);
                        }
                    }
                }

                ticks += 2; // 每2tick執行一次，減少性能消耗
            }
        }, 0L, 2L);

        beamTaskRef[0] = beamTask;

        // 將光束任務加入信標任務管理
        beaconTasks.put(player, beamTask);
    }

    /**
     * 創建到達光束特效
     */
    private void createArrivalBeam(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        // 創建短暫的到達光束
        Bukkit.getScheduler().runTask(plugin, () -> {
            // 從天空向下的光束
            for (int y = 20; y >= 0; y--) {
                Location beamLoc = location.clone().add(0, y, 0);

                // 主光束
                world.spawnParticle(Particle.END_ROD, beamLoc, 2, 0.1, 0.1, 0.1, 0.02);

                // 光束效果
                if (y % 2 == 0) {
                    world.spawnParticle(Particle.GLOW, beamLoc, 3, 0.2, 0.1, 0.2, 0.03);
                }
            }

            // 地面爆發效果
            world.spawnParticle(Particle.FIREWORK, location.add(0, 1, 0), 10, 1.0, 0.5, 1.0, 0.1);
            world.spawnParticle(Particle.END_ROD, location, 15, 2.0, 1.0, 2.0, 0.05);
        });
    }
}