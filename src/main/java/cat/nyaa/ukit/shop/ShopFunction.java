package cat.nyaa.ukit.shop;

import cat.nyaa.ukit.SpigotLoader;
import land.melon.lab.simplelanguageloader.utils.Pair;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.codehaus.plexus.util.ExceptionUtils;

import java.sql.*;
import java.util.UUID;

public class ShopFunction implements Listener {
    private final SpigotLoader pluginInstance;
    private Connection shopDB;

    public ShopFunction(SpigotLoader pluginInstance) {
        this.pluginInstance = pluginInstance;

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
        if(event.getSide() == Side.BACK) {
            return;
        }

        // is it WallSign?
        Block block = event.getBlock();
        BlockData blockData = block.getBlockData();
        if(!(blockData instanceof WallSign)) {
            return;
        }

        // is there a chest behind it?
        Directional directional = (Directional) blockData;
        BlockFace blockFace = directional.getFacing();
        Block behindBlock = block.getRelative(blockFace.getOppositeFace());
        if (!(behindBlock.getState() instanceof Chest)) {
            return;
        }
        Chest chest = (Chest) behindBlock;

        // get commodity item
        ItemStack commodity = getCommodity(chest);
        if(commodity == null) {
            return;
        }

        // get its content
        String shopType = event.getLine(0);
        String priceString = event.getLine(1);
        if((shopType == null) || (priceString == null)) {
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
        if(shopType.equals(pluginInstance.config.shopConfig.sellTriggerText)) {
            createShop(player, pluginInstance.config.shopConfig.sellDBText, price, commodity, chest, (Sign) block);
        } else if (shopType.equals(pluginInstance.config.shopConfig.buyTriggerText)) {
            createShop(player, pluginInstance.config.shopConfig.buyDBText , price, commodity, chest, (Sign) block);
        }
    }

    public class Shop {
        int shop_id = -1;
        String shop_type;
        double price;
        ItemStack commodity;
        UUID player_uuid;
        Location location;

        public Shop(String shop_type, double price, ItemStack commodity, UUID player_uuid, Location location) {
            this.shop_type = shop_type;
            this.price = price;
            this.commodity = commodity;
            this.player_uuid = player_uuid;
            this.location = location;
        }

        public boolean save(Player player) {
            String sql;
            String insert_sql = "INSERT INTO shop(shop_type, player_uuid, price, loc_x, loc_y, loc_z, world_name, commodity) VALUES(?, ?, ?, ?, ?, ?, ?, ?);";
            String update_sql = "UPDATE " + pluginInstance.config.shopConfig.tableName
                    + " SET shop_type = ?, player_uuid = ?, price = ?, loc_x = ?, loc_y = ?, loc_z = ?, world_name = ?, commodity = ? WHERE id = ?;";

            if(this.shop_id == -1) {
                sql = insert_sql;
            } else {
                sql = update_sql;
            }

            try (PreparedStatement pstmt = shopDB.prepareStatement(sql)) {
                pstmt.setString(1, this.shop_type);
                pstmt.setString(2, this.player_uuid.toString());
                pstmt.setDouble(3, this.price);
                pstmt.setInt(4, (int) this.location.getX());
                pstmt.setInt(5, (int) this.location.getY());
                pstmt.setInt(6, (int) this.location.getZ());
                pstmt.setString(7, this.location.getWorld().getName());
                pstmt.setBytes(8, this.commodity.serializeAsBytes());
                if(this.shop_id != -1) {
                    pstmt.setInt(9, this.shop_id);
                }

                pstmt.executeUpdate();
            } catch (SQLException e) {
                pluginInstance.getLogger().warning(ExceptionUtils.getStackTrace(e));
                return false;
            }
            return true;
        }

        public static Shop find(Player player) {
            // TODO
            return null;
        }
    }

    private void createShop(Player player, String shop_type, double price, ItemStack commodity, Chest chest, Sign sign) {
        Shop shop = new Shop(shop_type, price, commodity, player.getUniqueId(), chest.getLocation());
        if(shop.save(player)) {
            if(shop_type.equals(pluginInstance.config.shopConfig.sellDBText)) {
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

    private ItemStack getCommodity(Chest chest) {
        Inventory inventory = chest.getInventory();

        for (ItemStack item : inventory.getContents()) {
            if (item != null) {
                return item;
            }
        }
        return null;
    }

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
}
