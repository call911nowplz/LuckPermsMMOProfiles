package arcane.luckpermsrefresh.plugin;

import fr.phoenixdevt.profiles.ProfileList;
import fr.phoenixdevt.profiles.ProfileProvider;
import fr.phoenixdevt.profiles.PlayerProfile;
import fr.phoenixdevt.profiles.event.PlayerIdDispatchEvent;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RefreshProfilePerms extends JavaPlugin implements CommandExecutor, Listener {

    private LuckPerms luckPerms;

    @Override
    public void onEnable() {
        if (this.getCommand("mmoperm") != null) {
            this.getCommand("mmoperm").setExecutor(this);
            this.getCommand("mmoperm").setTabCompleter(new TempPermTabCompleter());
        }

        if (this.getCommand("listprofiles") != null) {
            this.getCommand("listprofiles").setExecutor(this);
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        this.luckPerms = LuckPermsProvider.get();

        getLogger().info("RefreshProfilePerms loaded.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("listprofiles")) {
            if (args.length != 1) {
                sendMessage(sender, Component.text("Usage: /listprofiles <player>", NamedTextColor.YELLOW));
                return true;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
            ProfileProvider provider = Bukkit.getServicesManager().getRegistration(ProfileProvider.class).getProvider();
            ProfileList profileList = provider.getPlayerData(offline.getUniqueId());
            if (profileList == null) {
                sendMessage(sender, Component.text("No profiles found for this player.", NamedTextColor.RED));
                return true;
            }
            // If sender is not a player, output plain text
            if (!(sender instanceof Player)) {
                sender.sendMessage("§e" + offline.getName() + " Profiles UUIDs:");
                int index = 1;
                for (Object obj : profileList.getProfiles()) {
                    if (obj instanceof PlayerProfile profile) {
                        sender.sendMessage("§7- Profile " + index + ": §f" + profile.getUniqueId());
                        index++;
                    }
                }
                return true;
            }
            Player commandPlayer = (Player) sender;
            sendMessage(commandPlayer, Component.text(offline.getName() + " Profiles UUIDs: (Click to copy UUID)", NamedTextColor.YELLOW));
            int index = 1;
            for (Object obj : profileList.getProfiles()) {
                if (obj instanceof PlayerProfile profile) {
                    Component line = Component.text("- Profile " + index + ": ", NamedTextColor.GRAY)
                            .append(Component.text(profile.getUniqueId().toString(), NamedTextColor.WHITE)
                                    .hoverEvent(HoverEvent.showText(Component.text("Click to copy UUID", NamedTextColor.GRAY)))
                                    .clickEvent(ClickEvent.copyToClipboard(profile.getUniqueId().toString())));
                    sendMessage(commandPlayer, line);
                    index++;
                }
            }
            return true;
        }
        if (!sender.hasPermission("mmoperm.use")) {
            sendMessage(sender, Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sendMessage(sender, Component.text("Usage: /mmoperm <player|UUID> <add|remove|check|permissions> [permission] [context...]", NamedTextColor.YELLOW));
            return true;
        }

        String playerName = args[0];
        String action = args[1].toLowerCase();

        UUID uuid;
        try {
            uuid = UUID.fromString(playerName);
        } catch (IllegalArgumentException e) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
            ProfileProvider provider = Bukkit.getServicesManager().getRegistration(ProfileProvider.class).getProvider();
            ProfileList profileList = provider.getPlayerData(offline.getUniqueId());
            uuid = profileList.getCurrent().getUniqueId();
        }

        if (action.equals("check")) {
            String cmd = "lp user " + uuid + " info";
            Bukkit.dispatchCommand(sender, cmd);
            return true;
        }

        if (action.equals("permissions")) {
            String cmd = "lp user " + uuid + " permission info";
            Bukkit.dispatchCommand(sender, cmd);
            return true;
        }

        if (args.length < 3) {
            sendMessage(sender, Component.text("Usage: /mmoperm <player> <add|remove> <permission> [context...]", NamedTextColor.YELLOW));
            return true;
        }

        String permissionNode = args[2];
        String context = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "";
        Player target = Bukkit.getPlayer(uuid);

        if (action.equals("remove")) {
            User user = luckPerms.getUserManager().getUser(uuid);
            boolean hasGlobal = user != null && user.getNodes().stream()
                    .anyMatch(n -> n.getKey().equalsIgnoreCase(permissionNode) && n.getContexts().isEmpty());
            if (!hasGlobal && context.isEmpty()) {
                sendMessage(sender, Component.text("That permission only exists with context. Use the correct context to remove it.", NamedTextColor.RED));
                return true;
            }
        }

        String lpCommand = "lp user " + uuid + " permission " + (action.equals("add") ? "set" : "unset") + " " + permissionNode;
        if (!context.isEmpty()) {
            lpCommand += " " + context;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), lpCommand);

        Bukkit.getScheduler().runTask(this, () -> {
            if (target != null && target.isOnline()) {
                PermissionAttachment attachment = target.addAttachment(this);
                attachment.setPermission(permissionNode, action.equals("add"));
                sendMessage(sender, Component.text("Permission " + action + "ed live & via command for: " + target.getName(), NamedTextColor.GREEN));
            } else {
                sendMessage(sender, Component.text("Permission " + action + "ed via command. Player is not online.", NamedTextColor.YELLOW));
            }
        });

        return true;
    }

    @EventHandler
    public void onProfileDispatch(PlayerIdDispatchEvent event) {
        UUID fakeId = event.getFakeId();
        if (fakeId == null) return;

        UserManager userManager = luckPerms.getUserManager();
        GroupManager groupManager = luckPerms.getGroupManager();
        String serverName = luckPerms.getServerName();

        Player player = event.getPlayer();

        userManager.loadUser(fakeId).thenAccept(user -> {
            Set<String> seenGroups = new HashSet<>();
            Collection<Node> allNodes = unwrapPermissions(user.data().toCollection(), groupManager, serverName, seenGroups);
            Bukkit.getScheduler().runTask(this, () -> {
                for (Node node : allNodes) {
                    player.addAttachment(this, node.getKey(), node.getValue());
                }
            });
        });
    }

    private Collection<Node> unwrapPermissions(Collection<Node> nodes, GroupManager groupManager, String serverName, Set<String> seenGroups) {
        return nodes.stream().flatMap(node -> {
            if (node.getKey().startsWith("group.")) {
                String[] split = node.getKey().split("\\.");
                if (split.length > 1) {
                    String groupName = split[1].toLowerCase();
                    if (seenGroups.contains(groupName)) return Stream.empty();
                    seenGroups.add(groupName);
                    Group group = groupManager.getGroup(groupName);
                    if (group != null) {
                        return unwrapPermissions(group.data().toCollection(), groupManager, serverName, seenGroups).stream();
                    }
                }
            }
            ImmutableContextSet ctx = node.getContexts();
            Set<String> servers = ctx.getValues("server");
            if (!servers.isEmpty() && !servers.contains(serverName)) return Stream.empty();
            return Stream.of(node);
        }).collect(Collectors.toSet());
    }

    // Helper method to send a Component message to any CommandSender
    private void sendMessage(CommandSender sender, Component message) {
        if (sender instanceof Audience audience) {
            audience.sendMessage(message);
        } else {
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().serialize(message));
        }
    }
}
