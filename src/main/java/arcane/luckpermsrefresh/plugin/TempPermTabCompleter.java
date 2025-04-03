package arcane.luckpermsrefresh.plugin;

import org.bukkit.command.TabCompleter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TempPermTabCompleter implements TabCompleter {

    private static final List<String> ACTIONS = Arrays.asList("add", "remove");
    private static final List<String> COMMON_PERMS = Arrays.asList(
            "example.perm1", "example.perm2", "mmocore.skill.use", "myplugin.command.use"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 2) {
            return ACTIONS;
        }
        if (args.length == 3) {
            return COMMON_PERMS;
        }
        return Collections.emptyList();
    }
}
