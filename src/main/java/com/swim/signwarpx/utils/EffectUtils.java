package com.swim.signwarpx.utils;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 特效工具類，處理API兼容性問題
 */
public class EffectUtils {

    /**
     * 安全地獲取Sound，使用新的Registry API
     */
    public static Sound getSound(String soundName, JavaPlugin plugin) {
        if (soundName == null || soundName.isEmpty()) {
            return getDefaultSound();
        }

        try {
            // 使用新的Registry API
            if (hasRegistryAPI()) {
                // 嘗試多種格式的key
                String[] keyFormats = {
                        soundName.toLowerCase(),
                        convertSoundName(soundName)
                };

                for (String keyFormat : keyFormats) {
                    try {
                        NamespacedKey key = NamespacedKey.minecraft(keyFormat);
                        Sound sound = Registry.SOUNDS.get(key);
                        if (sound != null) {
                            return sound;
                        }
                    } catch (Exception ignored) {
                        // 繼續嘗試下一個格式
                    }
                }
            }

            plugin.getLogger().warning("Sound not found in registry: " + soundName + ", using default sound");
            return getDefaultSound();

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get sound: " + soundName + " - " + e.getMessage());
            return getDefaultSound();
        }
    }

    /**
     * 安全地獲取Particle，使用新的Registry API
     */
    public static Particle getParticle(String particleName, JavaPlugin plugin) {
        if (particleName == null || particleName.isEmpty()) {
            return getDefaultParticle();
        }

        try {
            // 使用新的Registry API
            if (hasRegistryAPI()) {
                // 嘗試多種格式的key
                String[] keyFormats = {
                        particleName.toLowerCase(),
                };

                for (String keyFormat : keyFormats) {
                    try {
                        NamespacedKey key = NamespacedKey.minecraft(keyFormat);
                        Particle particle = Registry.PARTICLE_TYPE.get(key);
                        if (particle != null) {
                            return particle;
                        }
                    } catch (Exception ignored) {
                        // 繼續嘗試下一個格式
                    }
                }
            }

            plugin.getLogger().warning("Particle not found in registry: " + particleName + ", using default particle");
            return getDefaultParticle();

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get particle: " + particleName + " - " + e.getMessage());
            return getDefaultParticle();
        }
    }

    /**
     * 檢查是否有Registry API可用
     */
    private static boolean hasRegistryAPI() {
        try {
            Class.forName("org.bukkit.Registry");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }


    /**
     * 獲取默認音效
     */
    private static Sound getDefaultSound() {
        try {
            if (hasRegistryAPI()) {
                Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft("entity.experience_orb.pickup"));
                if (sound != null) return sound;
            }
        } catch (Exception ignored) {
        }

        // 如果Registry API失敗，返回null（靜音）
        return null;
    }

    /**
     * 獲取默認粒子
     */
    private static Particle getDefaultParticle() {
        try {
            if (hasRegistryAPI()) {
                Particle particle = Registry.PARTICLE_TYPE.get(NamespacedKey.minecraft("crit"));
                if (particle != null) return particle;
            }
        } catch (Exception ignored) {
        }

        // 如果Registry API失敗，返回null（無粒子）
        return null;
    }

    /**
     * 轉換音效名稱格式
     */
    private static String convertSoundName(String soundName) {
        // 將常見的音效名稱轉換為正確的registry key格式
        String converted = soundName.toLowerCase();
        // 將剩餘的下劃線轉換為點
        converted = converted.replace("_", ".");

        return converted;
    }
}