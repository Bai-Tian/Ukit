package cat.nyaa.ukit.elytra;

import cat.nyaa.ukit.SpigotLoader;
import cat.nyaa.ukit.utils.SubCommandExecutor;
import cat.nyaa.ukit.utils.SubTabCompleter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class ElytraFunction implements Listener, SubCommandExecutor, SubTabCompleter {
    private final SpigotLoader pluginInstance;
    private final Material holdingItemType;
    private final NamespacedKey speedometerKey;
    private final String ELYTRA_SPEEDOMETER_PERMISSION_NODE = "ukit.elytra.speedometer";
    private final List<String> subCommands = List.of("elytra");

    public ElytraFunction(SpigotLoader pluginInstance) {
        this.pluginInstance = pluginInstance;
        holdingItemType = Material.valueOf(pluginInstance.config.elytraConfig.holdingItem);
        speedometerKey = new NamespacedKey(pluginInstance, "speedometer");
    }

    @EventHandler
    public void onPlayerGliding(PlayerMoveEvent event) {
        // skip if speed is high or player is not gliding
        if (!event.getPlayer().isGliding())
            return;

        if (event.getPlayer().getInventory().getItemInMainHand().getType() != holdingItemType)
            return;

        // generate a speed indication bar ("||" for each 0.1 speed, gold for below trigger speed
        if(event.getPlayer().getPersistentDataContainer().get(speedometerKey, PersistentDataType.BOOLEAN)) {
            var speed = event.getPlayer().getVelocity().length();
            var speedBar = Component.text();
            for (var i = 0; i < 25; i++) {
                speedBar.append(Component.text("||").color(speed * 10 < i ? TextColor.color(NamedTextColor.GRAY) : TextColor.color(i < pluginInstance.config.elytraConfig.triggerSpeed * 10 ? 0xe89f64 : 0x96db81)));
            }
            speedBar.append(Component.text(" " + String.format("%.2f", speed * 20 * 0.06) + " km/min").color(TextColor.color(NamedTextColor.WHITE)));
            event.getPlayer().sendActionBar(speedBar);
        }

        // skip if speed exceed 0.8
        if (event.getPlayer().getVelocity().length() > pluginInstance.config.elytraConfig.triggerSpeed)
            return;

        // select firework in pack
        var fireworkSlot = selectFireworkSlot(event.getPlayer().getInventory());
        if (fireworkSlot == -1) return;
        var fireworkItem = subtractOneItem(event.getPlayer().getInventory(), fireworkSlot);
        if (fireworkItem == null) return;

        event.getPlayer().fireworkBoost(fireworkItem);
    }

    private int selectFireworkSlot(PlayerInventory inventory) {
        for (var i = 0; i < inventory.getSize(); i++) {
            var item = inventory.getItem(i);
            if (item == null) continue;
            if (item.getType() == Material.FIREWORK_ROCKET) {
                return i;
            }
        }
        return -1;
    }

    private ItemStack subtractOneItem(PlayerInventory inventory, int slot) {
        var item = inventory.getItem(slot);
        if (item == null)
            return null;
        if (item.getAmount() == 1) {
            inventory.setItem(slot, new ItemStack(Material.AIR));
        } else {
            item.setAmount(item.getAmount() - 1);
        }
        return item.asOne();
    }

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent event) {
        PersistentDataContainer persistentDataContainer = event.getPlayer().getPersistentDataContainer();
        if(!persistentDataContainer.has(speedometerKey)) {
            persistentDataContainer.set(speedometerKey, PersistentDataType.BOOLEAN, true);
        }
    }

    @Override
    public boolean invokeCommand(CommandSender commandSender, Command command, String label, String[] args) {
        if (!(commandSender instanceof Player player)) {
            commandSender.sendMessage(pluginInstance.language.commonLang.playerOnlyCommand.produce());
        } else if (!player.hasPermission(ELYTRA_SPEEDOMETER_PERMISSION_NODE)) {
            player.sendMessage(pluginInstance.language.commonLang.permissionDenied.produce());
        } else if (args.length > 0) {
            if(args[0].equalsIgnoreCase("speedometer")) {
                if (args.length > 1) {
                    switch (args[1].toLowerCase()) {
                        case "enable" -> enablePlayer(player);
                        case "disable" -> disablePlayer(player);
                        case "toggle" -> togglePlayer(player);
                        default -> {
                            return false;
                        }
                    }
                } else {
                    togglePlayer(player);
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    private void enablePlayer(Player player) {
        PersistentDataContainer persistentDataContainer = player.getPersistentDataContainer();
        persistentDataContainer.set(speedometerKey, PersistentDataType.BOOLEAN, true);
        player.sendMessage(pluginInstance.language.elytraLang.speedometerEnabled.produce());
    }

    private void disablePlayer(Player player) {
        PersistentDataContainer persistentDataContainer = player.getPersistentDataContainer();
        persistentDataContainer.set(speedometerKey, PersistentDataType.BOOLEAN, false);
        player.sendMessage(pluginInstance.language.elytraLang.speedometerDisabled.produce());
    }

    private boolean isPlayerEnabled(Player player) {
        return player.getPersistentDataContainer().get(speedometerKey, PersistentDataType.BOOLEAN);
    }

    private void togglePlayer(Player player) {
        if (isPlayerEnabled(player)) {
            disablePlayer(player);
        } else {
            enablePlayer(player);
        }
    }

    @Override
    public String getHelp() {
        return pluginInstance.language.elytraLang.help.produce();
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // 不会写呜呜
        return null;
    }

    @Override
    public boolean checkPermission(CommandSender commandSender) {
        return commandSender.hasPermission(ELYTRA_SPEEDOMETER_PERMISSION_NODE);
    }
}