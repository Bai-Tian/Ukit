package cat.nyaa.ukit.shop;

import cat.nyaa.ukit.SpigotLoader;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class Shop {
    long shop_id = -1;
    String shop_type;
    double price;
    ItemStack commodity;

    UUID player_uuid;
    Location location;

    SpigotLoader pluginInstance;

    Connection shopDB;

    public Shop(String shop_type, double price, ItemStack commodity, UUID player_uuid, Location location, SpigotLoader pluginInstance, Connection shopDB) {
        this.shop_type = shop_type;
        this.price = price;
        this.commodity = commodity;
        this.player_uuid = player_uuid;
        this.location = location;
        this.pluginInstance = pluginInstance;
        this.shopDB = shopDB;
    }

    // insert a new row if shop_id is -1
    public boolean save() {
        String sql = getSql();

        try (PreparedStatement pstmt = shopDB.prepareStatement(sql)) {
            pstmt.setString(1, shop_type);
            pstmt.setString(2, this.player_uuid.toString());
            pstmt.setDouble(3, this.price);
            pstmt.setInt(4, this.location.getBlockX());
            pstmt.setInt(5, this.location.getBlockY());
            pstmt.setInt(6, this.location.getBlockZ());
            pstmt.setString(7, this.location.getWorld().getName());
            pstmt.setBytes(8, this.commodity.serializeAsBytes());
            if (this.shop_id != -1) {
                pstmt.setLong(9, this.shop_id);
            }

            pstmt.executeUpdate();

            if (this.shop_id == -1) {
                ResultSet generatedKeys = pstmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    this.shop_id = generatedKeys.getLong(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @NotNull
    private String getSql() {
        String sql;
        String insert_sql = "INSERT INTO shop(shop_type, player_uuid, price, loc_x, loc_y, loc_z, world_name, commodity) VALUES(?, ?, ?, ?, ?, ?, ?, ?);";
        String update_sql = "UPDATE " + pluginInstance.config.shopConfig.tableName
                + " SET shop_type = ?, player_uuid = ?, price = ?, loc_x = ?, loc_y = ?, loc_z = ?, world_name = ?, commodity = ? WHERE id = ?;";

        if (this.shop_id == -1) {
            sql = insert_sql;
        } else {
            sql = update_sql;
        }
        return sql;
    }

    public long getShop_id() {
        return shop_id;
    }

    public String getShop_type() {
        return shop_type;
    }

    public double getPrice() {
        return price;
    }

    public ItemStack getCommodity() {
        return commodity;
    }

    public UUID getPlayer_uuid() {
        return player_uuid;
    }

    public Location getLocation() {
        return location;
    }
}