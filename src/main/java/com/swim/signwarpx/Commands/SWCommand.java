package com.swim.signwarpx.Commands;

import com.swim.signwarpx.EventListener;
import com.swim.signwarpx.SignWarpX;
import com.swim.signwarpx.Warp;
import com.swim.signwarpx.WarpGroup;
import com.swim.signwarpx.gui.WarpGui;
import com.swim.signwarpx.utils.VersionCheckerUtils;
import com.swim.signwarpx.utils.WarpInviteUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("SameReturnValue")
public class SWCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final GroupCommand groupCommand;
    private final MiniMessage miniMessage;

    public SWCommand(JavaPlugin plugin) {
        this.plugin = plugin;
        this.groupCommand = new GroupCommand((SignWarpX) plugin);
        this.miniMessage = MiniMessage.miniMessage();
    }

    private static @NotNull String getString(List<Warp> playerWarps, int i, String warpListFormat, JavaPlugin plugin) {
        Warp warp = playerWarps.get(i);
        String visibility = warp.isPrivate() ?
                plugin.getConfig().getString("messages.visibility_private", "私人") :
                plugin.getConfig().getString("messages.visibility_public", "公共");
        return warpListFormat
                .replace("{index}", String.valueOf(i + 1))
                .replace("{warp-name}", warp.getName())
                .replace("{visibility}", visibility)
                .replace("{world}", Objects.requireNonNull(warp.getLocation().getWorld()).getName())
                .replace("{x}", String.valueOf((int) warp.getLocation().getX()))
                .replace("{y}", String.valueOf((int) warp.getLocation().getY()))
                .replace("{z}", String.valueOf((int) warp.getLocation().getZ()))
                .replace("{creator}", warp.getCreator())
                .replace("{created-at}", warp.getFormattedCreatedAt());
    }

    /**
     * 發送訊息給CommandSender，使用Adventure API
     */
    private void sendMessage(CommandSender sender, String message) {
        Component component = miniMessage.deserialize(message);
        sender.sendMessage(component);
    }

    /**
     * 從配置文件獲取訊息並發送
     */
    private void sendConfigMessage(CommandSender sender, String key, String defaultMessage) {
        String message = plugin.getConfig().getString(key, defaultMessage);
        sendMessage(sender, message);
    }

    /**
     * 從配置文件獲取訊息，替換占位符後發送
     */
    private void sendConfigMessage(CommandSender sender, String key, String defaultMessage, String... replacements) {
        String message = plugin.getConfig().getString(key, defaultMessage);
        for (int i = 0; i < replacements.length - 1; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        sendMessage(sender, message);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             String[] args) {
        if (args.length == 0) {
            return true;
        }

        // 新增群組指令處理
        if (args[0].equalsIgnoreCase("group")) {
            return groupCommand.handleGroupCommand(sender, args);
        }

        // 既有的指令處理...
        if (args[0].equalsIgnoreCase("gui")) {
            handleGuiCommand(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            handleReloadCommand(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("set")) {
            handleSetCommand(sender, args);
            return true;
        }

        // 新增的邀請系統指令
        if (args[0].equalsIgnoreCase("invite")) {
            handleInviteCommand(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("uninvite")) {
            handleUninviteCommand(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("list-invites")) {
            handleListInvitesCommand(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("list-own")) {
            handleMyWarpsCommand(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("history")) {
            handleHistoryCommand(sender, args);
            return true;
        }

        // 僅限 OP 傳送指定 Warp
        if (args[0].equalsIgnoreCase("tp")) {
            return handleWptCommand(sender, args);
        }

        // 版本檢查指令
        if (args[0].equalsIgnoreCase("version")) {
            handleVersionCommand(sender);
            return true;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> cmds = new ArrayList<>();
            if (sender.hasPermission("signwarp.admin")) cmds.add("gui");
            if (sender.hasPermission("signwarp.reload")) cmds.add("reload");
            cmds.add("set");
            cmds.add("list-own");
            cmds.add("history");
            if (sender.hasPermission("signwarp.invite")) {
                cmds.add("invite");
                cmds.add("uninvite");
                cmds.add("list-invites");
            }
            if (sender instanceof Player) {
                cmds.add("group");
            }
            if (sender instanceof Player p && p.isOp()) {
                cmds.add("tp");
            }
            cmds.add("version");
            for (String c : cmds) {
                if (c.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(c);
                }
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "group":
                    // 直接呼叫群組 tab 補全方法
                    return handleGroupTabCompletion(sender, args);
                case "set":
                    // 使用配置文件中的可見性文本進行tab補全
                    String privateText = plugin.getConfig().getString("messages.command_visibility_private", "私人");
                    String publicText = plugin.getConfig().getString("messages.command_visibility_public", "公共");
                    for (String v : new String[]{publicText, privateText}) {
                        if (v.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(v);
                        }
                    }
                    break;
                case "invite":
                case "uninvite":
                    Bukkit.getOnlinePlayers().forEach(pl -> {
                        if (pl.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(pl.getName());
                        }
                    });
                    break;
                case "list-invites":
                    if (sender instanceof Player pl) {
                        completions.addAll(getAccessibleWarps(pl, args[1]));
                    }
                    break;
                case "tp":
                    if (sender instanceof Player p && p.isOp()) {
                        Warp.getAll().stream()
                                .map(Warp::getName)
                                .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                                .forEach(completions::add);
                    }
                    break;
                case "list-own":
                    // 只有 OP 或管理員可以查看其他玩家的傳送點
                    if (sender instanceof Player p && (p.isOp() || p.hasPermission("signwarp.admin"))) {
                        // 補全線上玩家名稱
                        Bukkit.getOnlinePlayers().forEach(pl -> {
                            if (pl.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                                completions.add(pl.getName());
                            }
                        });

                        // 補全曾經創建過傳送點的玩家名稱
                        Warp.getAll().stream()
                                .map(Warp::getCreator)
                                .distinct()
                                .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                                .forEach(completions::add);
                    }
                    break;
                case "history":
                    // 補全傳送點名稱
                    Warp.getAll().stream()
                            .map(Warp::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .forEach(completions::add);
                    
                    // 只有 OP 可以查看其他玩家的歷史
                    if (sender instanceof Player p && p.isOp()) {
                        // 補全線上玩家名稱
                        Bukkit.getOnlinePlayers().forEach(pl -> {
                            if (pl.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                                completions.add(pl.getName());
                            }
                        });
                    }
                    break;
            }
        } else if (args.length >= 3) {
            if (args[0].equalsIgnoreCase("group")) {
                return handleGroupTabCompletion(sender, args);
            } else if (args[0].equalsIgnoreCase("set")
                    || args[0].equalsIgnoreCase("invite")
                    || args[0].equalsIgnoreCase("uninvite")) {
                if (sender instanceof Player pl && args.length == 3) {
                    completions.addAll(getAccessibleWarps(pl, args[2]));
                }
            }
        }

        return completions;
    }

    /**
     * 處理群組指令的 Tab 補全
     */
    private List<String> handleGroupTabCompletion(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player player)) {
            return completions;
        }

        // 檢查群組功能是否啟用
        if (!plugin.getConfig().getBoolean("warp-groups.enabled", true)) {
            return completions;
        }

        // 檢查玩家權限（與 GroupCommand 中的邏輯一致）
        if (!canPlayerUseGroups(player)) {
            return completions;
        }

        if (args.length == 2) {
            // 第一層子指令補全
            List<String> subCommands = Arrays.asList("create", "list", "info", "add", "remove", "invite", "uninvite", "delete");
            return subCommands.stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length < 3) {
            return completions;
        }

        String subCommand = args[1].toLowerCase();

        switch (subCommand) {
            case "add":
            case "remove":
            case "invite":
            case "uninvite":
            case "info":
            case "delete":
                if (args.length == 3) {
                    // 補全群組名稱 - 根據權限顯示不同的群組
                    List<WarpGroup> accessibleGroups;
                    if (player.isOp() || player.hasPermission("signwarp.group.admin")) {
                        // OP 和管理員可以看到所有群組
                        accessibleGroups = WarpGroup.getAllGroups();
                    } else {
                        // 普通玩家只能看到自己的群組
                        accessibleGroups = WarpGroup.getPlayerGroups(player.getUniqueId().toString());

                        // 加入玩家有權限存取的群組（是成員的群組）
                        List<WarpGroup> allGroups = WarpGroup.getAllGroups();
                        for (WarpGroup group : allGroups) {
                            if (WarpGroup.isPlayerInGroup(group.groupName(), player.getUniqueId().toString())
                                    && !accessibleGroups.contains(group)) {
                                accessibleGroups.add(group);
                            }
                        }
                    }

                    for (WarpGroup group : accessibleGroups) {
                        if (group.groupName().toLowerCase().startsWith(args[2].toLowerCase())) {
                            completions.add(group.groupName());
                        }
                    }
                } else if (args.length == 4) {
                    // 第四個參數的補全
                    String groupName = args[2];
                    WarpGroup group = WarpGroup.getByName(groupName);

                    if (group != null && hasGroupPermission(player, group, subCommand)) {
                        switch (subCommand) {
                            case "add":
                                // 補全可加入的傳送點（玩家的私人傳送點，且不在任何群組中）
                                List<Warp> playerWarps = Warp.getPlayerWarps(player.getUniqueId().toString());
                                List<String> groupWarps = group.getGroupWarps();

                                for (Warp warp : playerWarps) {
                                    if (warp.isPrivate() &&
                                            !isWarpInAnyGroup(warp.getName()) &&
                                            warp.getName().toLowerCase().startsWith(args[3].toLowerCase())) {
                                        completions.add(warp.getName());
                                    }
                                }
                                break;

                            case "remove":
                                // 補全群組中的傳送點
                                List<String> warpsInGroup = group.getGroupWarps();
                                for (String warpName : warpsInGroup) {
                                    if (warpName.toLowerCase().startsWith(args[3].toLowerCase())) {
                                        completions.add(warpName);
                                    }
                                }
                                break;

                            case "invite":
                                // 補全所有線上玩家（除了指令執行者）
                                Bukkit.getOnlinePlayers().forEach(pl -> {
                                    if (!pl.equals(player) &&
                                            pl.getName().toLowerCase().startsWith(args[3].toLowerCase())) {
                                        completions.add(pl.getName());
                                    }
                                });
                                break;
                            case "uninvite":
                                // 補全該群組的成員
                                List<WarpGroup.GroupMember> groupMembers = group.getGroupMembers();
                                for (WarpGroup.GroupMember member : groupMembers) {
                                    if (member.name().toLowerCase().startsWith(args[3].toLowerCase())) {
                                        completions.add(member.name());
                                    }
                                }
                                break;
                        }
                    }
                } else if (subCommand.equals("add")) {
                    // 多個傳送點的補全
                    String groupName = args[2];
                    WarpGroup group = WarpGroup.getByName(groupName);

                    if (group != null && hasGroupPermission(player, group, subCommand)) {
                        List<Warp> playerWarps = Warp.getPlayerWarps(player.getUniqueId().toString());
                        List<String> groupWarps = group.getGroupWarps();

                        // 排除已經在參數中的傳送點
                        List<String> alreadyAdded = new ArrayList<>(Arrays.asList(args).subList(3, args.length - 1));

                        for (Warp warp : playerWarps) {
                            if (warp.isPrivate() &&
                                    !groupWarps.contains(warp.getName()) &&
                                    !alreadyAdded.contains(warp.getName()) &&
                                    !isWarpInAnyGroup(warp.getName()) &&
                                    warp.getName().toLowerCase().startsWith(args[args.length - 1].toLowerCase())) {
                                completions.add(warp.getName());
                            }
                        }
                    }
                }
                break;
        }

        return completions;
    }

    /**
     * 檢查玩家是否有使用群組功能的權限（與 GroupCommand 中的邏輯一致）
     */
    private boolean canPlayerUseGroups(Player player) {
        // OP 總是可以使用
        if (player.isOp()) {
            return true;
        }

        // 檢查管理員權限
        if (player.hasPermission("signwarp.group.admin")) {
            return true;
        }

        // 檢查配置是否允許普通玩家使用群組功能
        boolean allowNormalPlayers = plugin.getConfig().getBoolean("warp-groups.allow-normal-players", true);
        if (!allowNormalPlayers) {
            return false;
        }

        // 檢查基本群組權限
        return player.hasPermission("signwarp.group.create") || player.hasPermission("signwarp.use");
    }

    /**
     * 檢查玩家是否有特定群組操作的權限
     */
    private boolean hasGroupPermission(Player player, WarpGroup group, String operation) {
        // OP 和管理員擁有所有權限
        if (player.isOp() || player.hasPermission("signwarp.group.admin")) {
            return true;
        }

        // 檢查是否為群組擁有者
        if (group.ownerUuid().equals(player.getUniqueId().toString())) {
            return true;
        }

        // 對於某些操作，群組成員也有權限
        if (operation.equalsIgnoreCase("info")) {
            return WarpGroup.isPlayerInGroup(group.groupName(), player.getUniqueId().toString());
        }
        return false; // 其他操作只有擁有者和管理員可以執行
    }

    /**
     * 檢查傳送點是否已在任何群組中
     */
    private boolean isWarpInAnyGroup(String warpName) {
        List<WarpGroup> allGroups = WarpGroup.getAllGroups();
        for (WarpGroup group : allGroups) {
            if (group.getGroupWarps().contains(warpName)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getAccessibleWarps(Player player, String prefix) {
        return Warp.getAll().stream()
                .filter(w -> w.getCreatorUuid().equals(player.getUniqueId().toString())
                        || player.hasPermission("signwarp.admin"))
                .map(Warp::getName)
                .filter(n -> n.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }

    private void handleGuiCommand(CommandSender sender) {
        if (sender instanceof Player player) {
            if (player.hasPermission("signwarp.admin")) {
                WarpGui.openWarpGui(player, 0);
            } else {
                sendConfigMessage(sender, "messages.not_permission",
                        "<red>您沒有權限使用此指令。");
            }
        } else {
            sendMessage(sender, "<red>此指令只能由玩家執行。");
        }
    }

    private void handleReloadCommand(CommandSender sender) {
        if (sender.hasPermission("signwarp.reload")) {
            plugin.reloadConfig();
            EventListener.updateConfig(plugin);

            // 處理 Web 伺服器配置變更
            if (plugin instanceof SignWarpX signWarpX) {
                handleWebServerConfigChange(signWarpX);
            }

            sendConfigMessage(sender, "messages.reload_success",
                    "<green>配置已重新載入");
        } else {
            sendConfigMessage(sender, "messages.not_permission",
                    "<red>您沒有權限使用此指令。");
        }
    }

    private void handleWebServerConfigChange(SignWarpX signWarpX) {
        boolean webEnabled = plugin.getConfig().getBoolean("web.enabled", false);
        int newPort = plugin.getConfig().getInt("web.port", 8080);

        if (signWarpX.getWebApiManager() != null) {
            // 當前有 Web 伺服器運行
            int currentPort = signWarpX.getWebApiManager().getCurrentPort();
            boolean wasEnabled = true;

            if (!webEnabled) {
                // Web 功能被禁用，停止伺服器
                try {
                    // 先廣播停用消息
                    String disableMessage = "{\"type\":\"config_reload\",\"webEnabled\":false,\"message\":\"Web interface disabled\"}";
                    signWarpX.getWebApiManager().getWebSocketHandler().broadcast(disableMessage);

                    // 停止 Web 伺服器
                    signWarpX.getWebApiManager().stopWebServer();
                    signWarpX.setWebApiManager(null);
                    plugin.getLogger().info("Web server has been disabled.");
                } catch (Exception e) {
                    plugin.getLogger().warning("An error occurred while disabling the web server: " + e.getMessage());
                }
            } else if (newPort != currentPort) {
                // 端口變更，重啟伺服器
                try {
                    // 先廣播端口變更消息
                    String portChangeMessage = String.format(
                            "{\"type\":\"config_reload\",\"webEnabled\":true,\"oldPort\":%d,\"newPort\":%d,\"message\":\"Port changed\"}",
                            currentPort, newPort
                    );
                    signWarpX.getWebApiManager().getWebSocketHandler().broadcast(portChangeMessage);

                    // 停止舊伺服器
                    signWarpX.getWebApiManager().stopWebServer();

                    // 啟動新伺服器
                    signWarpX.setWebApiManager(new com.swim.signwarpx.web.WebApiManager(signWarpX));
                    signWarpX.getWebApiManager().startWebServer();
                } catch (Exception e) {
                    plugin.getLogger().severe(e.getMessage());
                }
            } else {
                // 只是配置重載，廣播更新消息
                try {
                    String reloadMessage = "{\"type\":\"config_reload\",\"webEnabled\":true,\"message\":\"Configuration reloaded\"}";
                    signWarpX.getWebApiManager().getWebSocketHandler().broadcast(reloadMessage);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to broadcast configuration reload message: " + e.getMessage());
                }
            }
        } else if (webEnabled) {
            // 當前沒有 Web 伺服器，但配置啟用了，啟動伺服器
            try {
                signWarpX.setWebApiManager(new com.swim.signwarpx.web.WebApiManager(signWarpX));
                signWarpX.getWebApiManager().startWebServer();
                plugin.getLogger().info("Web server has been enabled, port: " + newPort);
            } catch (Exception e) {
                plugin.getLogger().severe("An error occurred while starting the web server: " + e.getMessage());
            }
        }
    }

    private void handleSetCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "<red>This command can only be executed by a player.");
            return;
        }
        if (args.length < 3) {
            sendConfigMessage(sender, "messages.set_visibility_usage",
                    "<red>用法: /wp set <公共|私人> <傳送點名稱>");
            return;
        }

        // 使用配置文件中的可见性文本进行比较
        String inputVisibility = args[1];
        String privateText = plugin.getConfig().getString("messages.command_visibility_private", "私人");
        String publicText = plugin.getConfig().getString("messages.command_visibility_public", "公共");

        boolean isPrivate;
        if (inputVisibility.equals(privateText)) {
            isPrivate = true;
        } else if (inputVisibility.equals(publicText)) {
            isPrivate = false;
        } else {
            sendConfigMessage(sender, "messages.invalid_visibility",
                    "<red>使用權限必須是 '公共' 或 '私人'");
            return;
        }

        String warpName = args[2];
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            sendMessage(player, "<red>找不到傳送點: " + warpName);
            return;
        }
        if (!canModifyWarp(player, warp)) {
            sendConfigMessage(sender, "messages.cant_modify_others_warp",
                    "<red>您只能更改自己創建的傳送點！");
            return;
        }

        Warp updated = new Warp(
                warp.getName(),
                warp.getLocation(),
                warp.getFormattedCreatedAt(),
                warp.getCreator(),
                warp.getCreatorUuid(),
                isPrivate
        );
        updated.save();
        sendConfigMessage(sender, "messages.warp_visibility_changed",
                "<green>傳送點 {warp-name} 的使用權限已更改為{visibility}。",
                "{warp-name}", warpName, "{visibility}", inputVisibility);
    }

    private void handleInviteCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "<red>此指令只能由玩家執行。");
            return;
        }
        if (!player.hasPermission("signwarp.invite")) {
            sendConfigMessage(sender, "messages.not_permission",
                    "<red>您沒有權限使用此指令。");
            return;
        }
        if (args.length < 3) {
            sendConfigMessage(sender, "messages.invite_usage",
                    "<red>用法: /wp invite <玩家> <傳送點名稱>");
            return;
        }
        String target = args[1], warpName = args[2];
        Player targetPlayer = Bukkit.getPlayer(target);
        if (targetPlayer == null) {
            sendConfigMessage(sender, "messages.player_not_found",
                    "<red>找不到玩家 '{player}' 或該玩家離線！",
                    "{player}", target);
            return;
        }
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            sendMessage(player, "<red>找不到傳送點: " + warpName);
            return;
        }
        if (!canModifyWarp(player, warp)) {
            sendConfigMessage(sender, "messages.not_your_warp",
                    "<red>這不是您的傳送點！");
            return;
        }
        if (warp.isPlayerInvited(targetPlayer.getUniqueId().toString())) {
            sendConfigMessage(sender, "messages.already_invited",
                    "<yellow>玩家 {player} 已經被邀請使用此傳送點。",
                    "{player}", targetPlayer.getName());
            return;
        }
        warp.invitePlayer(targetPlayer);
        sendConfigMessage(sender, "messages.invite_success",
                "<green>已邀請 {player} 使用傳送點 '{warp-name}'。",
                "{player}", targetPlayer.getName(), "{warp-name}", warpName);
        sendConfigMessage(targetPlayer, "messages.invite_received",
                "<green>{inviter} 邀請您使用傳送點 '{warp-name}'。",
                "{inviter}", player.getName(), "{warp-name}", warpName);
    }

    private void handleUninviteCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "<red>此指令只能由玩家執行。");
            return;
        }
        if (!player.hasPermission("signwarp.invite")) {
            sendConfigMessage(sender, "messages.not_permission",
                    "<red>您沒有權限！");
            return;
        }
        if (args.length < 3) {
            sendConfigMessage(sender, "messages.uninvite_usage",
                    "<red>用法: /wp uninvite <玩家> <傳送點名稱>");
            return;
        }
        String target = args[1], warpName = args[2];
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            sendMessage(player, "<red>找不到傳送點: " + warpName);
            return;
        }
        boolean isOwner = warp.getCreatorUuid().equals(player.getUniqueId().toString());
        boolean isAdmin = player.hasPermission("signwarp.admin");
        if (!isOwner && !isAdmin) {
            sendConfigMessage(sender, "messages.cant_modify_warp",
                    "<red>您無法修改此傳送點的邀請名單！");
            return;
        }
        Player tgt = Bukkit.getPlayer(target);
        if (tgt == null) {
            sendConfigMessage(sender, "messages.player_not_found",
                    "<red>找不到玩家 '{player}' 或該玩家離線！",
                    "{player}", target);
            return;
        }
        if (!warp.isPlayerInvited(tgt.getUniqueId().toString())) {
            sendConfigMessage(sender, "messages.not_invited",
                    "<red>玩家 {player} 未被邀請使用此傳送點。",
                    "{player}", tgt.getName());
            return;
        }
        warp.removeInvite(tgt.getUniqueId().toString());
        sendConfigMessage(sender, "messages.uninvite_success",
                "<green>已移除 {player} 使用傳送點 '{warp-name}' 的權限。",
                "{player}", tgt.getName(), "{warp-name}", warpName);
    }

    private void handleListInvitesCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "<red>此指令只能由玩家執行。");
            return;
        }
        if (args.length < 2) {
            sendMessage(player, "<red>用法: /wp list-invites <傳送點名稱>");
            return;
        }
        String warpName = args[1];
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            sendMessage(player, "<red>找不到傳送點: " + warpName);
            return;
        }
        boolean isOwner = warp.getCreatorUuid().equals(player.getUniqueId().toString());
        boolean isAdmin = player.hasPermission("signwarp.admin");

        // Consolidated permission check
        if (!isOwner && !isAdmin && !player.hasPermission("signwarp.invite.list-own")) {
            sendConfigMessage(sender, "messages.not_permission", "<red>您沒有權限！");
            return;
        }
        List<WarpInviteUtils> invites = warp.getInvitedPlayers();
        sendConfigMessage(sender, "messages.invite_list",
                "<green>=== 傳送點 '{warp-name}' 的邀請列表 ===",
                "{warp-name}", warpName);
        if (invites.isEmpty()) {
            sendConfigMessage(sender, "messages.no_invites",
                    "<gray>沒有玩家被邀請使用此傳送點。");
        } else {
            for (WarpInviteUtils wi : invites) {
                sendMessage(player, "<gray>- " + wi.invitedName());
            }
        }
    }

    /**
     * 新增方法：處理查看玩家自己擁有的傳送點指令
     * OP 可以使用 /signwarp list-own <玩家名稱> 來查看指定玩家的傳送點
     */
    private void handleMyWarpsCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "<red>此指令只能由玩家執行。");
            return;
        }

        String targetPlayerName;
        String targetPlayerUuid;
        boolean isViewingOthers = false;

        // 檢查是否有指定玩家參數
        if (args.length > 1) {
            // 檢查是否為 OP 或有管理員權限
            if (!player.isOp() && !player.hasPermission("signwarp.admin")) {
                sendConfigMessage(sender, "messages.not_permission_view_others",
                        "<red>您沒有權限查看其他玩家的傳送點！");
                return;
            }

            targetPlayerName = args[1];
            isViewingOthers = true;

            // 嘗試從線上玩家獲取 UUID
            Player targetPlayer = Bukkit.getPlayer(targetPlayerName);
            if (targetPlayer != null) {
                targetPlayerUuid = targetPlayer.getUniqueId().toString();
            } else {
                // 如果玩家不在線，嘗試從已有的傳送點資料中找到該玩家
                targetPlayerUuid = getPlayerUuidByName(targetPlayerName);
                if (targetPlayerUuid == null) {
                    sendConfigMessage(sender, "messages.player_not_found_or_no_warps",
                            "<red>找不到玩家 '{player}' 或該玩家沒有任何傳送點。",
                            "{player}", targetPlayerName);
                    return;
                }
            }
        } else {
            // 沒有指定玩家，查看自己的傳送點
            targetPlayerName = player.getName();
            targetPlayerUuid = player.getUniqueId().toString();
        }

        List<Warp> playerWarps = Warp.getPlayerWarps(targetPlayerUuid);

        // 取得配置中的訊息，如果沒有設定則使用預設值
        String headerMsg, noWarpsMsg, totalWarpsMsg;

        if (isViewingOthers) {
            headerMsg = Objects.requireNonNull(plugin.getConfig().getString("messages.my_warps_header_others"))
                    .replace("{player}", targetPlayerName);
            noWarpsMsg = Objects.requireNonNull(plugin.getConfig().getString("messages.no_warps_owned_others"))
                    .replace("{player}", targetPlayerName);
            totalWarpsMsg = Objects.requireNonNull(plugin.getConfig().getString("messages.total_warps_others"))
                    .replace("{player}", targetPlayerName);
        } else {
            headerMsg = plugin.getConfig().getString("messages.my_warps_header_self");
            noWarpsMsg = plugin.getConfig().getString("messages.no_warps_owned_self");
            totalWarpsMsg = plugin.getConfig().getString("messages.total_warps_self");
        }

        String warpListFormat = plugin.getConfig().getString("messages.warp_list_format",
                "<white>{index}. <aqua>{warp-name} <gray>({visibility}) - <yellow>{world} <gray>({x}, {y}, {z})");

        // 顯示標題
        sendMessage(player, headerMsg);

        if (playerWarps.isEmpty()) {
            // 沒有傳送點
            sendMessage(player, noWarpsMsg);
        } else {
            // 列出所有傳送點
            for (int i = 0; i < playerWarps.size(); i++) {
                String formattedMsg = getString(playerWarps, i, warpListFormat, plugin);
                sendMessage(player, formattedMsg);
            }

            // 顯示總數
            String totalMsg = null;
            if (totalWarpsMsg != null) {
                totalMsg = totalWarpsMsg.replace("{count}", String.valueOf(playerWarps.size()));
            }
            sendMessage(player, totalMsg);
        }
    }

    /**
     * 處理查看傳送歷史指令
     * /signwarp history - 查看自己的傳送歷史（最近10次）
     * /signwarp history <傳送點名稱> - 查看指定傳送點的使用歷史（最近10次）
     * /signwarp history <玩家名稱> - (僅OP) 查看指定玩家的傳送歷史（最近10次）
     */
    private void handleHistoryCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "<red>此指令只能由玩家執行。");
            return;
        }

        if (args.length == 1) {
            // 查看自己的傳送歷史
            handlePlayerHistory(player, player.getUniqueId().toString(), player.getName(), false);
        } else if (args.length == 2) {
            String target = args[1];
            
            // 檢查是否為傳送點名稱
            Warp warp = Warp.getByName(target);
            if (warp != null) {
                // 查看傳送點歷史
                handleWarpHistory(player, target);
                return;
            }
            
            // 檢查是否為玩家名稱 (僅OP可用)
            if (player.isOp()) {
                Player targetPlayer = Bukkit.getPlayer(target);
                
                if (targetPlayer != null) {
                    // 線上玩家
                    String onlinePlayerUuid = targetPlayer.getUniqueId().toString();
                    String onlinePlayerName = targetPlayer.getName();
                    handlePlayerHistory(player, onlinePlayerUuid, onlinePlayerName, true);
                } else {
                    // 先顯示載入訊息
                    String loadingMsg = plugin.getConfig().getString("messages.history_loading",
                            "<gray>正在載入傳送歷史...");
                    sendMessage(player, loadingMsg);
                    
                    // 異步查找玩家UUID
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        String offlinePlayerUuid = getPlayerUuidFromHistory(target);
                        
                        // 回到主線程處理結果
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (offlinePlayerUuid == null) {
                                sendConfigMessage(sender, "messages.player_not_found_in_history",
                                        "<red>找不到玩家 '{player}' 的傳送記錄。",
                                        "{player}", target);
                                return;
                            }
                            
                            // 找到UUID後繼續處理（這裡會再次異步查詢歷史）
                            handlePlayerHistory(player, offlinePlayerUuid, target, true);
                        });
                    });
                }
            } else {
                sendConfigMessage(sender, "messages.not_permission",
                        "<red>權限不足！");
            }
        } else {
            sendConfigMessage(sender, "messages.history_usage",
                    "<yellow>用法: /signwarp history [傳送點名稱|玩家名稱]");
        }
    }

    /**
     * 處理玩家傳送歷史顯示（異步查詢）
     */
    private void handlePlayerHistory(Player viewer, String targetPlayerUuid, String targetPlayerName, boolean isViewingOthers) {
        final int HISTORY_LIMIT = 10;
        
        // 先顯示載入訊息
        String loadingMsg = plugin.getConfig().getString("messages.history_loading",
                "<gray>正在載入傳送歷史...");
        sendMessage(viewer, loadingMsg);
        
        // 異步執行資料庫查詢
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<com.swim.signwarpx.TeleportHistory> history = com.swim.signwarpx.TeleportHistory.getPlayerHistory(targetPlayerUuid, HISTORY_LIMIT);
            
            // 回到主線程顯示結果
            Bukkit.getScheduler().runTask(plugin, () -> {
                String headerMsg, noHistoryMsg;
                
                if (isViewingOthers) {
                    headerMsg = plugin.getConfig().getString("messages.history_header_others",
                            "<green>=== {player} 的傳送歷史 ===").replace("{player}", targetPlayerName);
                    noHistoryMsg = plugin.getConfig().getString("messages.no_history_others",
                            "<gray>{player} 沒有傳送記錄。").replace("{player}", targetPlayerName);
                } else {
                    headerMsg = plugin.getConfig().getString("messages.history_header_self",
                            "<green>=== 我的傳送歷史 ===");
                    noHistoryMsg = plugin.getConfig().getString("messages.no_history_self",
                            "<gray>您沒有傳送記錄。");
                }
                
                sendMessage(viewer, headerMsg);
                
                if (history.isEmpty()) {
                    sendMessage(viewer, noHistoryMsg);
                } else {
                    String historyFormat = plugin.getConfig().getString("messages.history_format",
                            "<white>{index}. <aqua>{warp-name} <gray>({from-world} → {to-world}) <yellow>{time}");
                    
                    for (int i = 0; i < history.size(); i++) {
                        com.swim.signwarpx.TeleportHistory record = history.get(i);
                        String formatted = historyFormat
                                .replace("{index}", String.valueOf(i + 1))
                                .replace("{warp-name}", record.warpName())
                                .replace("{from-world}", getWorldDisplayName(record.fromWorld()))
                                .replace("{to-world}", getWorldDisplayName(record.toWorld()))
                                .replace("{time}", record.getFormattedTeleportedAt());
                        
                        sendMessage(viewer, formatted);
                    }
                    
                    String totalMsg = plugin.getConfig().getString("messages.history_total",
                            "<gray>顯示最近 {count} 次傳送記錄").replace("{count}", String.valueOf(history.size()));
                    sendMessage(viewer, totalMsg);
                }
            });
        });
    }

    /**
     * 處理傳送點歷史顯示（異步查詢）
     */
    private void handleWarpHistory(Player viewer, String warpName) {
        final int HISTORY_LIMIT = 10;
        
        // 先顯示載入訊息
        String loadingMsg = plugin.getConfig().getString("messages.history_loading",
                "<gray>正在載入傳送歷史...");
        sendMessage(viewer, loadingMsg);
        
        // 異步執行資料庫查詢
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<com.swim.signwarpx.TeleportHistory> history;
            
            // 檢查權限：OP 可以看到所有玩家的記錄，普通玩家只能看到自己的記錄
            if (viewer.isOp()) {
                history = com.swim.signwarpx.TeleportHistory.getWarpHistory(warpName, HISTORY_LIMIT);
            } else {
                history = com.swim.signwarpx.TeleportHistory.getWarpHistoryForPlayer(warpName, viewer.getUniqueId().toString(), HISTORY_LIMIT);
            }
            
            // 回到主線程顯示結果
            Bukkit.getScheduler().runTask(plugin, () -> {
                String headerMsg = plugin.getConfig().getString("messages.warp_history_header",
                        "<green>=== 傳送點 '{warp-name}' 的使用歷史 ===").replace("{warp-name}", warpName);
                
                sendMessage(viewer, headerMsg);
                
                if (history.isEmpty()) {
                    String noHistoryMsg = plugin.getConfig().getString("messages.no_warp_history",
                            "<gray>傳送點 '{warp-name}' 沒有使用記錄。").replace("{warp-name}", warpName);
                    sendMessage(viewer, noHistoryMsg);
                } else {
                    String historyFormat = plugin.getConfig().getString("messages.warp_history_format",
                            "<white>{index}. <aqua>{player-name} <gray>({from-world} → {to-world}) <yellow>{time}");
                    
                    for (int i = 0; i < history.size(); i++) {
                        com.swim.signwarpx.TeleportHistory record = history.get(i);
                        String formatted = historyFormat
                                .replace("{index}", String.valueOf(i + 1))
                                .replace("{player-name}", record.playerName())
                                .replace("{from-world}", getWorldDisplayName(record.fromWorld()))
                                .replace("{to-world}", getWorldDisplayName(record.toWorld()))
                                .replace("{time}", record.getFormattedTeleportedAt());
                        
                        sendMessage(viewer, formatted);
                    }
                    
                    String totalMsg = plugin.getConfig().getString("messages.warp_history_total",
                            "<gray>顯示最近 {count} 次使用記錄").replace("{count}", String.valueOf(history.size()));
                    sendMessage(viewer, totalMsg);
                }
            });
        });
    }

    /**
     * 從傳送歷史中查找玩家的UUID
     */
    private String getPlayerUuidFromHistory(String playerName) {
        List<com.swim.signwarpx.TeleportHistory> recentHistory = com.swim.signwarpx.TeleportHistory.getRecentTeleports(1000);
        for (com.swim.signwarpx.TeleportHistory record : recentHistory) {
            if (record.playerName().equalsIgnoreCase(playerName)) {
                return record.playerUuid();
            }
        }
        return null;
    }

    /**
     * 獲取世界顯示名稱
     */
    private String getWorldDisplayName(String worldName) {
        if (worldName == null) {
            return "Unknown World";
        }
        
        // 確保使用正確的配置來獲取世界顯示名稱
        String displayName = plugin.getConfig().getString("world-display-names." + worldName);
        if (displayName != null && !displayName.trim().isEmpty()) {
            return displayName;
        }
        
        // 如果配置中沒有對應的名稱，返回原始名稱
        return worldName;
    }

    /**
     * 輔助方法：根據玩家名稱查找 UUID
     * 從現有的傳送點資料中查找該玩家的 UUID
     */
    private String getPlayerUuidByName(String playerName) {
        List<Warp> allWarps = Warp.getAll();
        for (Warp warp : allWarps) {
            if (warp.getCreator().equalsIgnoreCase(playerName)) {
                return warp.getCreatorUuid();
            }
        }
        return null;
    }

    private boolean canModifyWarp(Player player, Warp warp) {
        return warp.getCreatorUuid().equals(player.getUniqueId().toString())
                || player.hasPermission("signwarp.admin");
    }

    /**
     * OP 專用：/signwarp tp <傳送點名稱>
     */
    private boolean handleWptCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "<red>This command can only be executed by players.");
            return true;
        }
        if (!player.isOp()) {
            sendConfigMessage(sender, "messages.tp_op_only",
                    "<red>只有伺服器 OP 可以使用此指令。");
            return true;
        }
        if (args.length < 2) {
            sendConfigMessage(sender, "messages.tp_usage",
                    "<yellow>用法: /signwarp tp <傳送點名稱>");
            return true;
        }
        String warpName = args[1];
        Warp warp = Warp.getByName(warpName);
        if (warp == null) {
            sendConfigMessage(sender, "messages.warp_not_found",
                    "<red>找不到傳送點: {warp-name}",
                    "{warp-name}", warpName);
            return true;
        }
        player.teleport(warp.getLocation());
        sendConfigMessage(sender, "messages.tp_success",
                "<green>已傳送到: {warp-name} (建立者: {creator})",
                "{warp-name}", warp.getName(), "{creator}", warp.getCreator());
        return true;
    }

    /**
     * 處理版本檢查指令
     */
    private void handleVersionCommand(CommandSender sender) {
        // 檢查權限：只有 OP 可以使用版本檢查指令
        if (sender instanceof Player player && !player.isOp()) {
            sendConfigMessage(sender, "messages.version_op_only",
                    "<red>Insufficient permissions");
            return;
        }

        if (plugin instanceof SignWarpX signWarpX) {
            // 獲取當前版本
            String currentVersion = signWarpX.getCurrentVersion();

            // 顯示當前版本
            sendMessage(sender, "<green>=== SignWarpX Version Info ===");
            sendMessage(sender, "<white>current Version: <aqua>" + currentVersion);
            sendMessage(sender, "<white>Project Page: <blue>https://modrinth.com/plugin/signwarpx");

            // 檢查更新（異步執行）
            sendMessage(sender, "<gray>Checking for updates...");

            if (sender instanceof Player player) {
                VersionCheckerUtils.checkVersion(signWarpX, currentVersion, player);
            } else {
                VersionCheckerUtils.checkVersion(signWarpX, currentVersion, null);
            }
        }
    }
}