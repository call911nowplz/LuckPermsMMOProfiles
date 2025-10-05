package arcane.luckpermsrefresh.plugin;

import fr.phoenixdevt.profiles.*;
import fr.phoenixdevt.profiles.event.PlayerIdDispatchEvent;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RefreshProfilePerms extends JavaPlugin implements CommandExecutor, Listener {

    private LuckPerms luckPerms;
    private final Map<UUID, UUID> fakeToReal = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        // alias added in plugin.yml instead
        saveDefaultConfig();
        if (this.getCommand("mmoperm") != null) {
            this.getCommand("mmoperm").setExecutor(this);
            this.getCommand("mmoperm").setTabCompleter(new TempPermTabCompleter());
        }
        if (this.getCommand("listprofiles") != null) {
            this.getCommand("listprofiles").setExecutor(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        this.luckPerms = LuckPermsProvider.get();
        getLogger().info("LuckPermsMMOProfiles loaded.");
    }

    @Override
    public void onDisable() {
        // Clear the fakeToReal mapping to avoid memory leaks
        fakeToReal.clear();
        getLogger().info("LuckPermsMMOProfiles disabled.");
    }

    @EventHandler
    public void onPlayerIdDispatch(PlayerIdDispatchEvent event) {
        if (event.getFakeId() != null) {
            fakeToReal.put(event.getFakeId(), event.getInitialId());
            if (isDebug()) getLogger().info("[Dispatch] Mapped Fake UUID " + event.getFakeId() + " to Real UUID " + event.getInitialId());
        } else {
            getLogger().warning("[Dispatch] Missing Fake or Real UUID for player: " + event.getPlayer().getName());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        int delay = getConfig().getInt("syncDelay", 40);
        Bukkit.getScheduler().runTaskLater(this, () -> syncPlayer(player), delay);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Remove the player's mapping from fakeToReal to avoid memory leaks
        UUID fakeUUID = event.getPlayer().getUniqueId();
        fakeToReal.remove(fakeUUID);
        if (isDebug()) getLogger().info("[Quit] Removed mapping for fake UUID " + fakeUUID);
    }

    private void syncPlayer(Player player) {
        UUID fakeUUID = player.getUniqueId();
        UUID realUUID = fakeToReal.get(fakeUUID);
        getLogger().info("[Sync] Attempting sync for " + player.getName() + " (Fake UUID: " + fakeUUID + ")");
        if (realUUID == null) {
            getLogger().warning("[Sync] No real UUID found for " + fakeUUID);
            return;
        }
        getLogger().info("[Sync] Found real UUID for " + player.getName() + ": " + realUUID);

        UserManager userManager = luckPerms.getUserManager();
        GroupManager groupManager = luckPerms.getGroupManager();
        String serverName = luckPerms.getServerName();

        // Load both real and fake users
        CompletableFuture<User> realUserFuture = userManager.loadUser(realUUID);
        CompletableFuture<User> fakeUserFuture = userManager.loadUser(fakeUUID);
        CompletableFuture.allOf(realUserFuture, fakeUserFuture).thenAccept(ignored -> {
            User realUser = realUserFuture.join();
            User fakeUser = fakeUserFuture.join();

            attachGlobalPermissions(player, realUser, groupManager, serverName);
            syncFakeUserGroups(realUser, fakeUser, fakeUUID);
            syncFakeUserMeta(realUser, fakeUser, fakeUUID);
            attachProfilePermissions(player, fakeUser, groupManager, serverName);
        });
    }

    // Attaches global permissions and groups (from real user)
    private void attachGlobalPermissions(Player player, User realUser, GroupManager groupManager, String serverName) {
        Set<String> seenGroups = new HashSet<>();
        Collection<Node> globalNodes = unwrapPermissions(realUser.data().toCollection(), groupManager, serverName, seenGroups);
        Set<InheritanceNode> realUserGroups = realUser.getNodes().stream()
                .filter(n -> n instanceof InheritanceNode)
                .map(n -> (InheritanceNode) n)
                .collect(Collectors.toSet());

        List<String> perms = globalNodes.stream()
                .map(Node::getKey)
                .filter(key -> !key.startsWith("group."))
                .toList();
        List<String> groups = realUserGroups.stream()
                .map(InheritanceNode::getGroupName)
                .toList();

        if (isDebug()) {
            getLogger().info("[Sync] Global permissions for " + player.getName() + ": " + perms);
            getLogger().info("[Sync] Global groups for " + player.getName() + ": " + groups);
        }
        Bukkit.getScheduler().runTask(this, () -> {
            PermissionAttachment attachment = player.addAttachment(this);
            for (Node node : globalNodes) {
                attachment.setPermission(node.getKey(), node.getValue());
                if (isDebug()) getLogger().info("[Attach Perm] " + node.getKey() + " = " + node.getValue());
            }
            for (InheritanceNode group : realUserGroups) {
                attachment.setPermission(group.getKey(), true);
                if (isDebug()) getLogger().info("[Attach Group] " + group.getKey() + " = true");
            }
            player.recalculatePermissions();
            if (isDebug()) getLogger().info("[Sync] Global permissions & groups attached to " + player.getName());
        });
    }

    // Attaches profile-specific permissions from the fake user live to the player
    private void attachProfilePermissions(Player player, User fakeUser, GroupManager groupManager, String serverName) {
        Set<String> seenGroupsProfile = new HashSet<>();
        Collection<Node> profileNodes = unwrapPermissions(fakeUser.data().toCollection(), groupManager, serverName, seenGroupsProfile);
        List<String> profilePerms = profileNodes.stream()
                .map(Node::getKey)
                .filter(key -> !key.startsWith("group."))
                .toList();
        List<String> profileGroups = profileNodes.stream()
                .map(Node::getKey)
                .filter(key -> key.startsWith("group."))
                .toList();
        if (isDebug()) {
            getLogger().info("[Sync] Profile permissions for " + player.getName() + ": " + profilePerms);
            getLogger().info("[Sync] Profile groups for " + player.getName() + ": " + profileGroups);
        }
        Bukkit.getScheduler().runTask(this, () -> {
            PermissionAttachment profileAttachment = player.addAttachment(this);
            for (Node node : profileNodes) {
                profileAttachment.setPermission(node.getKey(), node.getValue());
                if (isDebug()) getLogger().info("[Attach Profile] " + node.getKey() + " = " + node.getValue());
            }
            if (isDebug()) getLogger().info("[Attach] Profile-specific permissions attached to " + player.getName());
        });
    }

    // Syncs groups persistently from the real user to the fake user
    private void syncFakeUserGroups(User realUser, User fakeUser, UUID fakeUUID) {
        Set<InheritanceNode> fakeGroups = fakeUser.getNodes().stream()
                .filter(n -> n instanceof InheritanceNode)
                .map(n -> (InheritanceNode) n)
                .collect(Collectors.toSet());
        Set<String> realGroupNames = realUser.getNodes().stream()
                .filter(n -> n instanceof InheritanceNode)
                .map(n -> ((InheritanceNode) n).getGroupName())
                .collect(Collectors.toSet());
        for (InheritanceNode fakeGroup : fakeGroups) {
            if (!realGroupNames.contains(fakeGroup.getGroupName())) {
                fakeUser.data().remove(fakeGroup);
                if (isDebug()) getLogger().info("[Group Sync] Removed group " + fakeGroup.getGroupName() + " from profile UUID " + fakeUUID);
            }
        }
        for (InheritanceNode realGroup : realUser.getNodes().stream()
                .filter(n -> n instanceof InheritanceNode)
                .map(n -> (InheritanceNode) n)
                .collect(Collectors.toSet())) {
            boolean alreadyPresent = fakeGroups.stream()
                    .anyMatch(n -> n.getGroupName().equalsIgnoreCase(realGroup.getGroupName()));
            if (!alreadyPresent) {
                InheritanceNode node = InheritanceNode.builder(realGroup.getGroupName()).build();
                fakeUser.data().add(node);
                if (isDebug()) getLogger().info("[Group Sync] Assigned group " + realGroup.getGroupName() + " to profile UUID " + fakeUUID);
            }
        }
        luckPerms.getUserManager().saveUser(fakeUser);
    }

    // This new method is supposed to fix the issue with prefixes
    // from groups not being removed if the main profile no longer has them
    private void syncFakeUserMeta(User realUser, User fakeUser, UUID fakeUUID) {
        Set<Node> realMetaNodes = realUser.getNodes().stream()
                .filter(n -> n.getKey().startsWith("prefix.") || n.getKey().startsWith("suffix."))
                .collect(Collectors.toSet());
        Set<Node> fakeMetaNodes = fakeUser.getNodes().stream()
                .filter(n -> n.getKey().startsWith("prefix.") || n.getKey().startsWith("suffix."))
                .collect(Collectors.toSet());

        for (Node fakeMeta : fakeMetaNodes) {
            if (!realMetaNodes.contains(fakeMeta)) {
                fakeUser.data().remove(fakeMeta);
                if (isDebug()) getLogger().info("[Meta Sync] Removed meta node " + fakeMeta.getKey() + " from fake profile " + fakeUUID);
            }
        }
        luckPerms.getUserManager().saveUser(fakeUser);
    }

    // Recursively unwraps permission nodes, filtering by server context and skipping prefix/suffix nodes
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
            // Skip prefix and suffix nodes
            if (node.getKey().startsWith("prefix.") || node.getKey().startsWith("suffix.")) {
                return Stream.empty();
            }
            ImmutableContextSet ctx = node.getContexts();
            Set<String> servers = ctx.getValues("server");
            if (!servers.isEmpty() && !servers.contains(serverName)) return Stream.empty();
            return Stream.of(node);
        }).collect(Collectors.toSet());
    }

    private boolean isDebug() {
        return getConfig().getBoolean("debug", true);
    }

    private void sendMessage(CommandSender sender, Component message) {
        if (sender instanceof Audience audience) {
            audience.sendMessage(message);
        } else {
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().serialize(message));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // TODO: Add offline players support
        if (command.getName().equalsIgnoreCase("listprofiles")) {
            if (args.length != 1) {
                sendMessage(sender, Component.text("Usage: /listprofiles <player>", NamedTextColor.YELLOW));
                return true;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
            ProfileProvider provider = Bukkit.getServicesManager().getRegistration(ProfileProvider.class).getProvider();
            ProfileList profileList;
            try {
                profileList = provider.getPlayerData(offline.getUniqueId());
            } catch (Exception ex) {
                sendMessage(sender, Component.text("Profile data is not loaded for this offline player.", NamedTextColor.RED));
                return true;
            }
            if (profileList == null) {
                sendMessage(sender, Component.text("No profiles found for this player.", NamedTextColor.RED));
                return true;
            }
            // If sender is not a player, output plain text
            // TODO: Add a message that says "Copied!" without making a new command
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
            Bukkit.dispatchCommand(sender, "lp user " + uuid + " info");
            return true;
        }
        if (action.equals("permissions")) {
            Bukkit.dispatchCommand(sender, "lp user " + uuid + " permission info");
            return true;
        }
        if (args.length < 3) {
            sendMessage(sender, Component.text("Usage: /mmoperm <player> <add|remove|check|permissions> [permission] [context...]", NamedTextColor.YELLOW));
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
}
