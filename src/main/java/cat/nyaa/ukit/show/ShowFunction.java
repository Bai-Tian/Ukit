package cat.nyaa.ukit.show;

import cat.nyaa.ukit.SpigotLoader;
import cat.nyaa.ukit.utils.SubCommandExecutor;
import cat.nyaa.ukit.utils.SubTabCompleter;
import land.melon.lab.simplelanguageloader.nms.ItemUtils;
import land.melon.lab.simplelanguageloader.utils.Pair;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ShowFunction implements SubCommandExecutor, SubTabCompleter {
    private final SpigotLoader pluginInstance;
    private final String SHOW_PERMISSION_NODE = "ukit.show";

    public ShowFunction(SpigotLoader pluginInstance) {
        this.pluginInstance = pluginInstance;
    }

    @Override
    public boolean invokeCommand(CommandSender commandSender, Command command, String label, String[] args) {
        if (commandSender instanceof ConsoleCommandSender) {
            commandSender.sendMessage(pluginInstance.language.commonLang.playerOnlyCommand.produce());
            return true;
        }
        var senderPlayer = (Player) commandSender;
        if (!senderPlayer.hasPermission(SHOW_PERMISSION_NODE)) {
            senderPlayer.sendMessage(pluginInstance.language.commonLang.permissionDenied.produce());
            return true;
        }
        var itemInHand = senderPlayer.getInventory().getItemInMainHand();
        // don't know how to use new Content API
        pluginInstance.getServer().spigot().broadcast(
                (itemInHand.getAmount() == 1 ? pluginInstance.language.showLang.showMessageSingle : pluginInstance.language.showLang.showMessageMultiple).produceWithBaseComponent(
                        Pair.of("player", senderPlayer.getName()),
                        Pair.of("item", ItemUtils.itemTextWithHover(itemInHand)),
                        Pair.of("amount", itemInHand.getAmount())
                )
        );
        return true;
    }

    @Override
    public String getHelp() {
        return null;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return null;
    }

    @Override
    public boolean checkPermission(CommandSender commandSender) {
        return commandSender.hasPermission(SHOW_PERMISSION_NODE);
    }
}
