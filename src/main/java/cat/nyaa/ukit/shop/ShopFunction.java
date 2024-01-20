package cat.nyaa.ukit.shop;

import cat.nyaa.ukit.SpigotLoader;
import land.melon.lab.simplelanguageloader.utils.Pair;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.sql.*;
import java.util.HashMap;
import java.util.UUID;

public class ShopFunction implements Listener {
    private final SpigotLoader pluginInstance;
    private final NamespacedKey shopIDKey;
    private final HashMap<Long, Shop> shopMap;

    private final Connection shopDB;

    public ShopFunction(SpigotLoader pluginInstance) {
        this.pluginInstance = pluginInstance;
        this.shopIDKey = new NamespacedKey(pluginInstance, "shop_id");
        this.shopMap = new HashMap<>();

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        try {
            shopDB = DriverManager.getConnection(pluginInstance.config.shopConfig.jdbcURL);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        try {
            DatabaseMetaData dbm = shopDB.getMetaData();
            ResultSet tables = dbm.getTables(null, null, pluginInstance.config.shopConfig.tableName, null);
            if (!tables.next()) {
                String sql = "CREATE TABLE " + pluginInstance.config.shopConfig.tableName + " (" +
                        "shop_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "shop_type TEXT NOT NULL, " +
                        "player_uuid TEXT NOT NULL, " +
                        "price DOUBLE NOT NULL, " +
                        "loc_x INTEGER NOT NULL, " +
                        "loc_y INTEGER NOT NULL, " +
                        "loc_z INTEGER NOT NULL, " +
                        "world_name TEXT NOT NULL, " +
                        "commodity BLOB NOT NULL);";
                try (Statement stmt = shopDB.createStatement()) {
                    stmt.execute(sql);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @EventHandler
    public void onSignChangeEvent(SignChangeEvent event) {
        // is the editing operation on the front?
        if (event.getSide() == Side.BACK) {
            return;
        }

        // is it WallSign?
        Block block = event.getBlock();
        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof WallSign)) {
            return;
        }

        // is there a chest behind it?
        Chest chest = getBehindChest(block);
        if (chest == null) {
            return;
        }

        // get commodity item
        ItemStack commodity = getCommodity(chest);
        if (commodity == null) {
            return;
        }

        // get its content
        String shopType = event.getLine(0);
        String priceString = event.getLine(1);
        if ((shopType == null) || (priceString == null)) {
            return;
        }

        // is the price a number?
        double price;
        try {
            price = Double.parseDouble(priceString);
        } catch (NumberFormatException e) {
            return;
        }

        Player player = event.getPlayer();
        // does the content trigger the creation of the shop?
        if (shopType.equals(pluginInstance.config.shopConfig.sellTriggerText)) {
            createShop(player, pluginInstance.config.shopConfig.sellDBText, price, commodity, chest, (Sign) block.getState());
        } else if (shopType.equals(pluginInstance.config.shopConfig.buyTriggerText)) {
            createShop(player, pluginInstance.config.shopConfig.buyDBText, price, commodity, chest, (Sign) block.getState());
        } else {
            return;
        }
        event.setCancelled(true);
    }

    // must be checked whether the Block is an instance of Sign before invoking this method
    private Chest getBehindChest(Block sign) {
        BlockData blockData = sign.getBlockData();
        Directional directional = (Directional) blockData;
        BlockFace blockFace = directional.getFacing();
        Block behindBlock = sign.getRelative(blockFace.getOppositeFace());
        if (!(behindBlock.getState() instanceof Chest)) {
            return null;
        }
        return (Chest) behindBlock.getState();
    }

    public Shop getShop(long shop_id) {
        // first find Shop from the shopMap
        if (shopMap.containsKey(shop_id)) {
            return shopMap.get(shop_id);
        }

        String sql = "SELECT * FROM shop WHERE shop_id = ?;";

        try (PreparedStatement pstmt = shopDB.prepareStatement(sql)) {
            pstmt.setLong(1, shop_id);

            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String shop_type = rs.getString("shop_type");
                double price = rs.getDouble("price");
                UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                Location location = new Location(
                        Bukkit.getWorld(rs.getString("world_name")),
                        rs.getInt("loc_x"),
                        rs.getInt("loc_y"),
                        rs.getInt("loc_z")
                );
                ItemStack commodity = ItemStack.deserializeBytes(rs.getBytes("commodity"));

                Shop shop = new Shop(shop_type, price, commodity, playerUUID, location, pluginInstance, shopDB);
                shop.shop_id = shop_id;

                shopMap.put(shop_id, shop);
                return shop;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    private void createShop(Player player, String shop_type, double price, ItemStack commodity, Chest chest, Sign sign) {
        Shop shop = new Shop(shop_type, price, commodity, player.getUniqueId(), sign.getLocation(), pluginInstance, shopDB);
        if (shop.save()) {
            chest.getPersistentDataContainer().set(shopIDKey, PersistentDataType.LONG, shop.shop_id);
            chest.update();
            sign.getPersistentDataContainer().set(shopIDKey, PersistentDataType.LONG, shop.shop_id);
            sign.setWaxed(true);
            sign.update();

            // set sign content TODO: l18n (some changes need to be done on the language dependency so just leave it here for now)
            // for some reason(which I don't know) the sign content updated on the tick which event cancelled will be ignored so we need to schedule it to the next tick
            // also for compability with folia, use RegionScheduler here
            // ref: https://docs.papermc.io/paper/dev/folia-support#region-scheduler
            // the behavior of scheduler will not be effected on plain paper server but it's the way that folia works
            pluginInstance.getServer().getRegionScheduler().execute(pluginInstance, sign.getLocation(), () -> {
                var side = sign.getSide(Side.FRONT);
                side.line(0, Component.text(player.getName()));
                side.line(1, Component.text(shop_type));
                side.line(2, Component.text("Price: " + price));
                side.line(3, Component.text(commodity.getType().toString()));
                sign.update();
            });

            if (shop_type.equals(pluginInstance.config.shopConfig.sellDBText)) {
                player.sendMessage(
                        pluginInstance.language.shopLang.successToCreateShopForSell.produce(
                                Pair.of("player", player.getName()),
                                Pair.of("price", Double.toString(price)),
                                Pair.of("item", commodity.getItemMeta().getDisplayName())
                        )
                );
            } else {
                player.sendMessage(
                        pluginInstance.language.shopLang.successToCreateShopForBuy.produce(
                                Pair.of("player", player.getName()),
                                Pair.of("price", Double.toString(price)),
                                Pair.of("item", commodity.getItemMeta().getDisplayName())
                        )
                );
            }
        } else {
            fail(player, sign, pluginInstance.language.shopLang.exception.produce());
        }
    }

    // find the first item in the chest
    private ItemStack getCommodity(Chest chest) {
        Inventory inventory = chest.getInventory();

        for (ItemStack item : inventory.getContents()) {
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    // clear all the lines of the sign if failed
    private void fail(Player player, Sign sign, String reason) {
        player.sendMessage(
                pluginInstance.language.shopLang.fail.produce(
                        Pair.of("reason", reason)
                )
        );
        sign.setLine(0, "");
        sign.setLine(1, "");
        sign.setLine(2, "");
        sign.setLine(3, "");
    }

    // this event handler helps players who click on a shop sign to open the chest located behind it
    @EventHandler
    public void onPlayerInteractEvent(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        BlockData blockData = block.getBlockData();

        if ((event.getAction() != Action.RIGHT_CLICK_BLOCK) || !(blockData instanceof WallSign)) {
            return;
        }

        Sign sign = (Sign) block.getState();
        if (!sign.getPersistentDataContainer().has(shopIDKey)) {
            return;
        }

        Long shop_id = sign.getPersistentDataContainer().get(shopIDKey, PersistentDataType.LONG);
        if (shop_id == null) {
            return;
        }

        Shop shop = getShop(shop_id);
        if (shop == null) {
            return;
        }

        Location sign_location = shop.location;
        Chest chest = getBehindChest(sign_location.getBlock());
        if (chest == null) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().openInventory(chest.getInventory());
    }

    // this event handler implements customer logic
    @EventHandler
    public void onInventoryClickEvent(InventoryClickEvent event) {
        Location location = event.getInventory().getLocation();
        if (location == null) {
            return;
        }

        BlockState blockState = location.getBlock().getState();

        if (blockState instanceof WallSign) {
            Chest behindChest = getBehindChest(location.getBlock());
            if (behindChest != null)
                blockState = behindChest;
        }

        if (!(blockState instanceof Chest chest)) {
            return;
        }

        if (!chest.getPersistentDataContainer().has(shopIDKey)) {
            return;
        }

        Long shop_id = chest.getPersistentDataContainer().get(shopIDKey, PersistentDataType.LONG);
        if (shop_id == null) {
            return;
        }

        Shop shop = getShop(shop_id);
        if (shop == null) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) {
            return;
        }

        int clickedItemAmount = clickedItem.getAmount();
        if (event.isLeftClick()) {
            if (buy(shop, 1)) {
                clickedItem.setAmount(clickedItemAmount - 1);
            }
        } else if (event.isRightClick()) {
            if (buy(shop, clickedItemAmount / 2)) {
                clickedItem.setAmount(clickedItemAmount - clickedItemAmount / 2);
            }
        } else if (event.isShiftClick()) {
            if (buy(shop, clickedItemAmount)) {
                event.getInventory().setItem(event.getSlot(), new ItemStack(Material.AIR));
            }
        }

        event.setCancelled(true);
    }

    private boolean buy(Shop shop, int amount) {
        Bukkit.broadcastMessage("测试：购买了" + amount + "个 " + shop.commodity.getType());
        // TODO
        return true;
    }
}
