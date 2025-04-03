package arcane.luckpermsrefresh.plugin;

import fr.phoenixdevt.profiles.ProfileProvider;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Arrays;

public class RefreshProfilePerms extends JavaPlugin implements CommandExecutor {

    @Override
    public void onEnable() {
        if (this.getCommand("tempperm") != null) {
            this.getCommand("tempperm").setExecutor(this);
            this.getCommand("tempperm").setTabCompleter(new TempPermTabCompleter());
        }

        getLogger().info("RefreshProfilePerms loaded.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tempperm.use")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length < 3 || (!args[1].equalsIgnoreCase("add") && !args[1].equalsIgnoreCase("remove"))) {
            sender.sendMessage("§eUsage: /tempperm <player> <add|remove> <permission> [context...]");
            return true;
        }

        String action = args[1].toLowerCase();
        String permissionNode = args[2];
        String context = args.length > 3 ? Arrays.stream(args).skip(3).collect(Collectors.joining(" ")) : "";
        ProfileProvider provider = Bukkit.getServicesManager().getRegistration(ProfileProvider.class).getProvider();
        UUID uuid = provider.getPlayerData(Bukkit.getOfflinePlayer(args[0]).getUniqueId()).getCurrent().getUniqueId();

        Player target = Bukkit.getPlayer(uuid);

        // Execute LP command as console
        String lpCommand = "lp user " + uuid + " permission " + (action.equals("add") ? "set" : "unset") + " " + permissionNode;
        if (!context.isEmpty()) {
            lpCommand += " " + context;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), lpCommand);

        // Live temp via Bukkit attachment
        Bukkit.getScheduler().runTask(this, () -> {
            if (target != null && target.isOnline()) {
                PermissionAttachment attachment = target.addAttachment(this);
                attachment.setPermission(permissionNode, action.equals("add"));
                sender.sendMessage("§aPermission " + action + "ed live & via command for: " + target.getName());
            } else {
                sender.sendMessage("§ePermission " + action + "ed via command. Player is not online.");
            }
        });

        return true;
    }
}
