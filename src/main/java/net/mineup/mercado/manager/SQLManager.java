package net.mineup.mercado.manager;

import net.mineup.mercado.*;
import net.mineup.mercado.database.*;
import net.mineup.mercado.utils.itemhandler.*;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class SQLManager
{
    private UPMarket plugin;
    
    public SQLManager(final UPMarket plugin) {
        this.plugin = plugin;
    }
    
    public void addItem(final MarketItem item) {
        try {
            if (this.plugin.getStorage().getConnection().isClosed()) {
                this.plugin.getStorage().connect();
            }
            final Database database = this.plugin.getStorage();
            final PreparedStatement stmt = database.prepareStatement("INSERT INTO km_items (" + Columns.CATEGORY.getColumn() + ", " + Columns.UUID.getColumn() + ", " + Columns.ITEM.getColumn() + ", " + Columns.PRICE.getColumn() + ", " + Columns.OWNER.getColumn() + ", " + Columns.TIME.getColumn() + ") VALUES (?, ?, ?, ?, ?, ?)");
            stmt.setString(1, item.getCategory().getName());
            stmt.setString(2, item.getUUID());
            stmt.setString(3, ItemHandler.toString(item.getItem()));
            stmt.setDouble(4, item.getPrice());
            stmt.setString(5, item.getOwner());
            stmt.setLong(6, item.getTime());
            stmt.execute();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * Insere o item em km_expired e remove o anúncio de km_items numa única transação,
     * para que um anúncio nunca fique ao mesmo tempo à venda e coletável (duplicação)
     * nem desapareça das duas tabelas (perda) se o servidor cair no meio da operação.
     */
    public boolean moveToExpired(final MarketItem item) {
        Connection connection = null;
        boolean previousAutoCommit = true;
        try {
            if (this.plugin.getStorage().getConnection().isClosed()) {
                this.plugin.getStorage().connect();
            }
            connection = this.plugin.getStorage().getConnection();
            previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO km_expired (" + Columns.UUID.getColumn() + ", " + Columns.ITEM.getColumn() + ", " + Columns.OWNER.getColumn() + ", " + Columns.TIME.getColumn() + ") VALUES (?, ?, ?, ?)");
                 PreparedStatement delete = connection.prepareStatement("DELETE FROM km_items WHERE " + Columns.UUID.getColumn() + "= ?")) {
                insert.setString(1, item.getUUID());
                insert.setString(2, ItemHandler.toString(item.getItem()));
                insert.setString(3, item.getOwner());
                insert.setLong(4, item.getTime());
                insert.executeUpdate();
                delete.setString(1, item.getUUID());
                delete.executeUpdate();
            }
            connection.commit();
            return true;
        }
        catch (Exception ex) {
            this.plugin.getLogger().log(Level.WARNING, "Falha ao expirar o anuncio " + item.getUUID() + "; nova tentativa na proxima verificacao.", ex);
            if (connection != null) {
                try {
                    connection.rollback();
                }
                catch (SQLException rollbackEx) {
                    this.plugin.getLogger().log(Level.WARNING, "Falha no rollback da expiracao.", rollbackEx);
                }
            }
            return false;
        }
        finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(previousAutoCommit);
                }
                catch (SQLException ignored) {
                }
            }
        }
    }
    
    /** A coluna "tempo" é REAL; lê como double para não depender da conversão do driver. */
    private static long readTime(final ResultSet rs) throws SQLException {
        return (long) rs.getDouble(Columns.TIME.getColumn());
    }
    
    public void deleteItem(final String uuid) {
        try {
            if (this.plugin.getStorage().getConnection().isClosed()) {
                this.plugin.getStorage().connect();
            }
            final Database database = this.plugin.getStorage();
            final PreparedStatement stmt = database.prepareStatement("DELETE FROM km_items WHERE " + Columns.UUID.getColumn() + "= ?");
            stmt.setString(1, uuid);
            stmt.executeUpdate();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public List<MarketItem> getEveryItems() {
        List<MarketItem> result = null;
        try {
            if (this.plugin.getStorage().getConnection().isClosed()) {
                this.plugin.getStorage().connect();
            }
            final Database database = this.plugin.getStorage();
            final PreparedStatement stmt = database.prepareStatement("SELECT * FROM km_items");
            result = new ArrayList<MarketItem>();
            final ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                final MarketItem item = new MarketItem(ItemHandler.fromString(rs.getString(Columns.ITEM.getColumn())), this.plugin.getCategoryManager().getCategory(rs.getString(Columns.CATEGORY.getColumn())), rs.getString(Columns.OWNER.getColumn()), rs.getDouble(Columns.PRICE.getColumn()), rs.getString(Columns.UUID.getColumn()));
                item.setTime(readTime(rs));
                result.add(item);
            }
            return result;
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return result;
        }
    }
    
    public int getAmountPlayerItems(final String player) {
        int i = 0;
        try {
            if (this.plugin.getStorage().getConnection().isClosed()) {
                this.plugin.getStorage().connect();
            }
            final Database database = this.plugin.getStorage();
            final PreparedStatement stmt = database.prepareStatement("SELECT " + Columns.ITEM.getColumn() + " FROM km_items WHERE " + Columns.OWNER.getColumn() + "= ?");
            stmt.setString(1, player);
            final ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ++i;
            }
            return i;
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return i;
        }
    }
    
    public void addExpired(final MarketItem item) {
        try {
            if (this.plugin.getStorage().getConnection().isClosed()) {
                this.plugin.getStorage().connect();
            }
            final Database database = this.plugin.getStorage();
            final PreparedStatement stmt = database.prepareStatement("INSERT INTO km_expired (" + Columns.UUID.getColumn() + ", " + Columns.ITEM.getColumn() + ", " + Columns.OWNER.getColumn() + ", " + Columns.TIME.getColumn() + ") VALUES (?, ?, ?, ?)");
            stmt.setString(1, item.getUUID());
            stmt.setString(2, ItemHandler.toString(item.getItem()));
            stmt.setString(3, item.getOwner());
            stmt.setLong(4, item.getTime());
            stmt.execute();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public void deletePersonal(final String uuid) {
        try {
            if (this.plugin.getStorage().getConnection().isClosed()) {
                this.plugin.getStorage().connect();
            }
            final Database database = this.plugin.getStorage();
            final PreparedStatement stmt = database.prepareStatement("DELETE FROM km_personal WHERE " + Columns.UUID.getColumn() + "= ?");
            stmt.setString(1, uuid);
            stmt.executeUpdate();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public List<MarketItem> getEveryExpired() {
        List<MarketItem> result = null;
        try {
            if (this.plugin.getStorage().getConnection().isClosed()) {
                this.plugin.getStorage().connect();
            }
            final Database database = this.plugin.getStorage();
            final PreparedStatement stmt = database.prepareStatement("SELECT * FROM km_expired");
            result = new ArrayList<MarketItem>();
            final ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                final MarketItem item = new MarketItem(ItemHandler.fromString(rs.getString(Columns.ITEM.getColumn())), null, rs.getString(Columns.OWNER.getColumn()), 0.0, rs.getString(Columns.UUID.getColumn()));
                item.setTime(readTime(rs));
                result.add(item);
            }
            return result;
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return result;
        }
    }
    
    public void deleteExpired(final String uuid) {
        try {
            if (this.plugin.getStorage().getConnection().isClosed()) {
                this.plugin.getStorage().connect();
            }
            final Database database = this.plugin.getStorage();
            final PreparedStatement stmt = database.prepareStatement("DELETE FROM km_expired WHERE " + Columns.UUID.getColumn() + "= ?");
            stmt.setString(1, uuid);
            stmt.executeUpdate();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public void addPersonal(final PersonalItem item) {
        try {
            if (this.plugin.getStorage().getConnection().isClosed()) {
                this.plugin.getStorage().connect();
            }
            final Database database = this.plugin.getStorage();
            final PreparedStatement stmt = database.prepareStatement("INSERT INTO km_personal (" + Columns.UUID.getColumn() + ", " + Columns.ITEM.getColumn() + ", " + Columns.PRICE.getColumn() + ", " + Columns.OWNER.getColumn() + ", " + Columns.BUYER.getColumn() + ") VALUES (?, ?, ?, ?, ?)");
            stmt.setString(1, item.getUUID());
            stmt.setString(2, ItemHandler.toString(item.getItem()));
            stmt.setDouble(3, item.getPrice());
            stmt.setString(4, item.getOwner());
            stmt.setString(5, item.getBuyer());
            stmt.execute();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public List<PersonalItem> getEveryPersonal() {
        List<PersonalItem> result = null;
        try {
            if (this.plugin.getStorage().getConnection().isClosed()) {
                this.plugin.getStorage().connect();
            }
            final Database database = this.plugin.getStorage();
            final PreparedStatement stmt = database.prepareStatement("SELECT * FROM km_personal");
            result = new ArrayList<PersonalItem>();
            final ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(new PersonalItem(ItemHandler.fromString(rs.getString(Columns.ITEM.getColumn())), rs.getString(Columns.OWNER.getColumn()), rs.getString(Columns.BUYER.getColumn()), rs.getDouble(Columns.PRICE.getColumn()), rs.getString(Columns.UUID.getColumn())));
            }
            return result;
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return result;
        }
    }
    
    public int getAmountPersonalItems(final String player) {
        int i = 0;
        try {
            if (this.plugin.getStorage().getConnection().isClosed()) {
                this.plugin.getStorage().connect();
            }
            final Database database = this.plugin.getStorage();
            final PreparedStatement stmt = database.prepareStatement("SELECT " + Columns.ITEM.getColumn() + " FROM km_personal WHERE " + Columns.BUYER.getColumn() + "= ?");
            stmt.setString(1, player);
            final ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ++i;
            }
            return i;
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return i;
        }
    }
}
