package com.swim.signwarpx.gui;

import com.swim.signwarpx.SignWarpX;
import com.swim.signwarpx.Warp;
import com.swim.signwarpx.utils.EventListener.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class WarpGuiListener implements Listener {

    private final SignWarpX plugin;

    public WarpGuiListener(JavaPlugin plugin) {
        this.plugin = (SignWarpX) plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // 使用 Adventure API 取得標題並轉換為純文字進行比較
        Component titleComponent = event.getView().title();
        String title = PlainTextComponentSerializer.plainText().serialize(titleComponent);

        FileConfiguration config = plugin.getConfig();
        String titlePrefix = config.getString("messages.gui_title_prefix", "Warps Admin");

        if (title.startsWith(titlePrefix)) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            Player player = (Player) event.getWhoClicked();
            String[] titleParts = title.split(" ");
            int currentPage;
            try {
                currentPage = Integer.parseInt(titleParts[titleParts.length - 1]) - 1;
            } catch (NumberFormatException e) {
                MessageUtils.sendConfigMessage(player, config, "messages.gui_page_error");
                return;
            }

            if (clickedItem.getType() == Material.ARROW) {
                // 使用新的 Adventure API 方式取得顯示名稱
                Component displayNameComponent = Objects.requireNonNull(clickedItem.getItemMeta()).displayName();
                if (displayNameComponent != null) {
                    String displayName = PlainTextComponentSerializer.plainText().serialize(displayNameComponent);

                    String nextPageText = config.getString("messages.gui_next_page", "Next Page");
                    String previousPageText = config.getString("messages.gui_previous_page", "Previous Page");

                    if (displayName.equals(nextPageText)) {
                        int totalWarps = Warp.getAll().size();
                        int totalPages = (int) Math.ceil((double) totalWarps / 45);
                        if (currentPage + 1 < totalPages) {
                            WarpGui.openWarpGui(player, currentPage + 1);
                        }
                    } else if (displayName.equals(previousPageText)) {
                        if (currentPage > 0) {
                            WarpGui.openWarpGui(player, currentPage - 1);
                        }
                    }
                }
            } else if (clickedItem.getType() == Material.OAK_SIGN) {
                // 使用新的 Adventure API 方式處理傳送點名稱
                Component displayNameComponent = Objects.requireNonNull(clickedItem.getItemMeta()).displayName();
                if (displayNameComponent != null) {
                    String warpName = PlainTextComponentSerializer.plainText().serialize(displayNameComponent);
                    Warp warp = Warp.getByName(warpName);
                    if (warp != null) {
                        player.teleport(warp.getLocation());
                        // 使用配置文件中的消息
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("{warp-name}", warp.getName());
                        placeholders.put("{creator}", warp.getCreator());
                        MessageUtils.sendConfigMessage(player, config, "messages.gui_teleport_success", placeholders);
                        player.closeInventory();
                    } else {
                        // 使用配置文件中的錯誤消息
                        Map<String, String> placeholders = new HashMap<>();
                        placeholders.put("{warp-name}", warpName);
                        MessageUtils.sendConfigMessage(player, config, "messages.gui_warp_not_found", placeholders);
                    }
                }
            }
        }
    }
}