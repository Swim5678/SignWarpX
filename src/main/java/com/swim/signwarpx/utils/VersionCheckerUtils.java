package com.swim.signwarpx.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.logging.Level;

public class VersionCheckerUtils {

    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2/project/eUdkHg5G/version";
    private static final String USER_AGENT = "SignWarpX/VersionChecker (Plugin by swim)";

    public static void checkVersion(JavaPlugin plugin, String currentVersion, @Nullable Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URI(MODRINTH_API_URL).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", USER_AGENT);

                if (connection.getResponseCode() != 200) {
                    plugin.getLogger().warning("Could not check for updates. (Response code: " + connection.getResponseCode() + ")");
                    return;
                }

                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder content = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                connection.disconnect();

                // Simple JSON parsing to avoid adding a library
                String response = content.toString();
                // The first version in the list is the latest one
                int versionKeyIndex = response.indexOf("\"version_number\"");
                if (versionKeyIndex == -1) {
                    plugin.getLogger().warning("Could not parse version info from Modrinth API response.");
                    return;
                }

                int startIndex = response.indexOf(":", versionKeyIndex) + 2; // Move to the start of the version string
                int endIndex = response.indexOf("\"", startIndex);
                String latestVersion = response.substring(startIndex, endIndex);

                if (!currentVersion.equalsIgnoreCase(latestVersion)) {
                    String updateMessage = "§6New version found! Current version: §e" + currentVersion + " §6Latest version: §a" + latestVersion;
                    String downloadMessage = "§bDownload link: §fhttps://modrinth.com/plugin/signwarpx/version/" + latestVersion;

                    plugin.getLogger().info("A new version of SignWarpX is available! Current version: " + currentVersion + " , Latest version: " + latestVersion);
                    plugin.getLogger().info("Download it from: https://modrinth.com/plugin/signwarpx/version/" + latestVersion);

                    if (player != null) {
                        player.sendMessage(updateMessage);
                        player.sendMessage(downloadMessage);
                    }
                } else {
                    plugin.getLogger().info("You are running the latest version of SignWarpX.");
                    if (player != null) {
                        player.sendMessage("§aYou are running the latest version of SignWarpX!");
                    }
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "An error occurred while checking for updates.", e);
            }
        });
    }

}