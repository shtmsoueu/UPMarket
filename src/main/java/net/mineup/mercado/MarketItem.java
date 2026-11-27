package net.mineup.mercado;

import org.bukkit.inventory.*;

import net.mineup.mercado.utils.geral.*;
import net.mineup.mercado.utils.itemhandler.*;

import java.text.*;
import java.util.UUID;

public class MarketItem
{
    private ItemStack item;
    private String uuid;
    private Category category;
    private String owner;
    private double price;
    
    public MarketItem(ItemStack item, Category category, String owner, double price, String uuid) {
        this.item = item;
        this.uuid = uuid;
        this.category = category;
        this.price = price;
        this.owner = owner;
    }
    
    public MarketItem(ItemStack item, Category category, String owner, double price) {
        this(item, category, owner, price, createUUID());
    }
    
    /**
     * Gera um identificador único para o anúncio.
     * O formato antigo (soma de char + typeId + data + contagem de anúncios) colidia
     * quando o jogador vendia um item e anunciava outro do mesmo tipo, o que fazia o
     * HashMap sobrescrever um anúncio e o DELETE apagar os dois.
     */
    public static String createUUID() {
        return UUID.randomUUID().toString();
    }
    
    @Override
    public String toString() {
        return "MarketItem [item=" + this.item + ", uuid=" + this.uuid + ", category=" + this.category + ", owner=" + this.owner + ", price=" + this.price + "]";
    }
    
    public ItemStack insertLore() {
        return new ItemBuilder(this.item).lore("", "&7Vendedor: " + Util.getColorPrefix(this.owner) + this.owner, "&7Custo: &e" + NumberFormat.getInstance().format(this.price) + " coins", "").build();
    }
    
    public ItemStack getItem() {
        return this.item;
    }
    
    public String getUUID() {
        return this.uuid;
    }
    
    public String getUUID(ItemStack item) {
        NBTItemStack packet = new NBTItemStack(item);
        return packet.getString("uuid");
    }
    
    public Category getCategory() {
        return this.category;
    }
    
    public String getOwner() {
        return this.owner;
    }
    
    public double getPrice() {
        return this.price;
    }
    
    public void setItem(ItemStack item) {
        this.item = item;
    }
    
    public void setUUID(String uuid) {
        this.uuid = uuid;
    }
    
    public void setCategory(Category category) {
        this.category = category;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    public void setPrice(double price) {
        this.price = price;
    }
}
