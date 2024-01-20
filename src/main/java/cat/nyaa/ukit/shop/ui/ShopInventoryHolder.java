package cat.nyaa.ukit.shop.ui;

import cat.nyaa.ukit.shop.Shop;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class ShopInventoryHolder implements InventoryHolder {
    private final Inventory anvilInventory;
    private final Shop shop;

    public ShopInventoryHolder(Shop shop) {
        this.shop = shop;
        //TODO: i18n
        anvilInventory = Bukkit.getServer().createInventory(this, InventoryType.ANVIL, "How much would you want to buy?");
        anvilInventory.setItem(0, shop.getCommodity().asOne());
    }

    @Override
    public @NotNull Inventory getInventory() {
        return anvilInventory;
    }
}
