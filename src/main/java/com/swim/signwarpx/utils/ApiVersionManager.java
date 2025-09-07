package com.swim.signwarpx.utils;

import org.bukkit.Bukkit;
import com.swim.signwarpx.SignWarpX;

/**
 * Manages API version compatibility between 1.21 and 1.21.6
 * 讓1.21.6以上(含)使用1.21.6api，1.21-1.21.5使用1.21api
 */
public class ApiVersionManager {
    private static ApiVersionManager instance;
    private final ApiVersion detectedVersion;
    
    public enum ApiVersion {
        API_1_21("1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5"),  // 1.21-1.21.5 use 1.21 API
        API_1_21_6("1.21.6");            // 1.21.6 and above use 1.21.6 API
        
        private final String[] supportedVersions;
        
        ApiVersion(String... supportedVersions) {
            this.supportedVersions = supportedVersions;
        }
        
        public String[] getSupportedVersions() {
            return supportedVersions;
        }
    }
    
    private ApiVersionManager() {
        this.detectedVersion = detectApiVersion();
        SignWarpX plugin = SignWarpX.getInstance();
        plugin.getLogger().info("Detected server version: " + getServerVersion() + 
            ", using API version: " + detectedVersion.name());
    }
    
    public static ApiVersionManager getInstance() {
        if (instance == null) {
            instance = new ApiVersionManager();
        }
        return instance;
    }
    
    private ApiVersion detectApiVersion() {
        SignWarpX plugin = SignWarpX.getInstance();
        String serverVersion = getServerVersion();
        
        // Extract version numbers for comparison
        if (serverVersion.contains("1.21.6") || isVersionHigherThan("1.21.6", serverVersion)) {
            return ApiVersion.API_1_21_6;
        } else if (serverVersion.contains("1.21") && !serverVersion.contains("1.21.6")) {
            // This covers 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5
            return ApiVersion.API_1_21;
        }
        
        // Default to 1.21 API for unknown versions
        plugin.getLogger().warning("Unknown server version: " + serverVersion +
            ", defaulting to 1.21 API");
        return ApiVersion.API_1_21;
    }
    
    private String getServerVersion() {
        return Bukkit.getServer().getVersion();
    }
    
    private boolean isVersionHigherThan(String baseVersion, String serverVersionString) {
        try {
            // Extract version number from server version string
            String versionPart = serverVersionString;
            if (versionPart.contains("MC: ")) {
                versionPart = versionPart.substring(versionPart.indexOf("MC: ") + 4);
                if (versionPart.contains(")")) {
                    versionPart = versionPart.substring(0, versionPart.indexOf(")"));
                }
            }
            
            String[] baseParts = baseVersion.split("\\.");
            String[] serverParts = versionPart.split("\\.");
            
            int maxLength = Math.max(baseParts.length, serverParts.length);
            
            for (int i = 0; i < maxLength; i++) {
                int basePart = i < baseParts.length ? Integer.parseInt(baseParts[i]) : 0;
                int serverPart = i < serverParts.length ? Integer.parseInt(serverParts[i]) : 0;
                
                if (serverPart > basePart) {
                    return true;
                } else if (serverPart < basePart) {
                    return false;
                }
            }
            
            return false; // Versions are equal
        } catch (Exception e) {
            SignWarpX.getInstance().getLogger().warning("Failed to parse version: " + e.getMessage());
            return false;
        }
    }
    
    public ApiVersion getApiVersion() {
        return detectedVersion;
    }
    
    public boolean isUsing1216Api() {
        return detectedVersion == ApiVersion.API_1_21_6;
    }
    
    public boolean isUsing121Api() {
        return detectedVersion == ApiVersion.API_1_21;
    }
}