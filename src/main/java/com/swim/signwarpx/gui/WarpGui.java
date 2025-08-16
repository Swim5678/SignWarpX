package com.swim.signwarpx.gui;

import com.swim.signwarpx.SignWarpX;
import com.swim.signwarpx.Warp;
import com.swim.signwarpx.utils.WarpInviteUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WarpGui {

    private static final int ITEMS_PER_PAGE = 45; // 5 rows of items
    private static final ItemStack FILLER;

    static {
        FILLER = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = FILLER.getItemMeta();
        Objects.requireNonNull(fillerMeta).displayName(Component.text(" "));
        FILLER.setItemMeta(fillerMeta);
    }

    private static ItemStack createNextPageButton() {
        FileConfiguration config = SignWarpX.getInstance().getConfig();
        ItemStack nextPage = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = nextPage.getItemMeta();
        String nextPageText = config.getString("messages.gui_next_page", "Next Page");
        Objects.requireNonNull(nextMeta).displayName(Component.text(nextPageText).color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        nextPage.setItemMeta(nextMeta);
        return nextPage;
    }

    private static ItemStack createPreviousPageButton() {
        FileConfiguration config = SignWarpX.getInstance().getConfig();
        ItemStack previousPage = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = previousPage.getItemMeta();
        String previousPageText = config.getString("messages.gui_previous_page", "Previous Page");
        Objects.requireNonNull(prevMeta).displayName(Component.text(previousPageText).color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        previousPage.setItemMeta(prevMeta);
        return previousPage;
    }

    public static void openWarpGui(Player player, int page) {
        List<Warp> warps = Warp.getAll();
        int totalWarps = warps.size();
        FileConfiguration config = SignWarpX.getInstance().getConfig();
        String titlePrefix = config.getString("messages.gui_title_prefix", "Warps Admin");

        Component title = Component.text(titlePrefix + " - Page " + (page + 1)).color(NamedTextColor.DARK_BLUE);
        Inventory gui = Bukkit.createInventory(null, 54, title);

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, totalWarps);

        for (int i = start; i < end; i++) {
            Warp warp = warps.get(i);
            ItemStack warpItem = new ItemStack(Material.OAK_SIGN);
            ItemMeta warpMeta = warpItem.getItemMeta();
            Objects.requireNonNull(warpMeta).displayName(
                    Component.text(warp.getName())
                            .color(NamedTextColor.DARK_GREEN)
                            .decoration(TextDecoration.ITALIC, false)
            );
            List<Component> lore = getComponents(warp);
            warpMeta.lore(lore);
            warpItem.setItemMeta(warpMeta);
            gui.addItem(warpItem);
        }

        // Fill the bottom row with the filler item
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, FILLER);
        }

        // Add pagination buttons using the new methods
        gui.setItem(47, createPreviousPageButton());
        gui.setItem(51, createNextPageButton());

        player.openInventory(gui);
    }

    private static @NotNull List<Component> getComponents(Warp warp) {
        List<Component> lore = new ArrayList<>();
        FileConfiguration config = SignWarpX.getInstance().getConfig();

        // 使用配置文件中的世界标签
        lore.add(Component.text(config.getString("messages.gui_world_label", "World: ") + Objects.requireNonNull(warp.getLocation().getWorld()).getName())
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        // 将 XYZ 坐标显示在同一行，并四舍五入到整数位
        lore.add(Component.text(config.getString("messages.gui_coordinates_label", "coordinates: ") +
                        Math.round(warp.getLocation().getX()) + ", " + Math.round(warp.getLocation().getY()) + ", " + Math.round(warp.getLocation().getZ()))
                .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        // 使用配置文件中的创建时间标签
        lore.add(Component.text(config.getString("messages.gui_created_label", "Created: ") + warp.getFormattedCreatedAt())
                .color(NamedTextColor.DARK_GREEN)
                .decoration(TextDecoration.ITALIC, false));

        // 使用配置文件中的创建者标签
        lore.add(Component.text(config.getString("messages.gui_creator_label", "Creator: ") + warp.getCreator())
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));

        // 使用配置文件中的可见性标签和值
        String visibilityLabel = config.getString("messages.gui_visibility_label", "visibility: ");
        String visibilityValue = warp.isPrivate() ?
                config.getString("messages.gui_visibility_private", "私人") :
                config.getString("messages.gui_visibility_public", "公共");
        lore.add(Component.text(visibilityLabel + visibilityValue)
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        // 新增：显示被邀请的玩家
        if (warp.isPrivate()) {
            List<WarpInviteUtils> invites = warp.getInvitedPlayers();
            if (!invites.isEmpty()) {
                lore.add(Component.text(config.getString("messages.gui_invited_players_label", "Invited players:"))
                        .color(NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false));
                for (WarpInviteUtils invite : invites) {
                    lore.add(Component.text("- " + invite.invitedName())
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                }
            }
        }

        lore.add(Component.text(config.getString("messages.gui_click_to_teleport", "Click to teleport"))
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));

        return lore;
    }
}