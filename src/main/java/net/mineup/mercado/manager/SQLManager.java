package net.mineup.mercado.manager;

import org.bukkit.scheduler.*;

import net.mineup.mercado.*;
import net.mineup.mercado.database.*;
import net.mineup.mercado.utils.itemhandler.*;

import java.sql.*;
import org.bukkit.plugin.*;
import java.util.*;

public class SQLManager
{
    private UPMarket plugin;
    
    public SQLManager(final UPMarket plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Corrige anúncios que ficaram com o mesmo ID por causa do gerador antigo.
     * Todas as linhas de um ID repetido são apagadas e reinseridas, cada uma com um
     * UUID novo e os demais campos intactos, dentro de uma transação.
     * Assim os itens que estavam "escondidos" no cache voltam a aparecer.
     */
    public void fixDuplicateIds(final String table) {
        final String uuidColumn = Columns.UUID.getColumn();
        try {
            if (this.plugin.getStorage().getConnection().isClosed()) {
                this.plugin.getStorage().connect();
            }
            final Connection conn = this.plugin.getStorage().getConnection();
            final Map<String, List<Map<String, Object>>> byId = new LinkedHashMap<String, List<Map<String, Object>>>();
            final List<String> columns = new ArrayList<String>();
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM " + table)) {
                final ResultSetMetaData meta = rs.getMetaData();
                for (int i = 1; i <= meta.getColumnCount(); ++i) {
                    columns.add(meta.getColumnName(i));
                }
                while (rs.next()) {
                    final Map<String, Object> row = new LinkedHashMap<String, Object>();
                    for (final String column : columns) {
                        row.put(column, rs.getObject(column));
                    }
                    final String id = String.valueOf(row.get(uuidColumn));
                    List<Map<String, Object>> rows = byId.get(id);
                    if (rows == null) {
                        rows = new ArrayList<Map<String, Object>>();
                        byId.put(id, rows);
                    }
                    rows.add(row);
                }
            }
            int fixed = 0;
            final StringBuilder insert = new StringBuilder("INSERT INTO " + table + " (");
            final StringBuilder values = new StringBuilder(") VALUES (");
            for (int i = 0; i < columns.size(); ++i) {
                insert.append(i == 0 ? "" : ", ").append(columns.get(i));
                values.append(i == 0 ? "?" : ", ?");
            }
            final String insertSql = insert.append(values).append(")").toString();
            final boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement delete = conn.prepareStatement("DELETE FROM " + table + " WHERE " + uuidColumn + " = ?");
                 PreparedStatement ins = conn.prepareStatement(insertSql)) {
                for (final Map.Entry<String, List<Map<String, Object>>> entry : byId.entrySet()) {
                    if (entry.getValue().size() < 2) {
                        continue;
                    }
                    delete.setString(1, entry.getKey());
                    delete.executeUpdate();
                    for (final Map<String, Object> row : entry.getValue()) {
                        row.put(uuidColumn, MarketItem.createUUID());
                        for (int i = 0; i < columns.size(); ++i) {
                            ins.setObject(i + 1, row.get(columns.get(i)));
                        }
                        ins.executeUpdate();
                        ++fixed;
                    }
                }
                conn.commit();
            }
            catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
            finally {
                conn.setAutoCommit(autoCommit);
            }
            if (fixed > 0) {
                this.plugin.getLogger().warning(fixed + " anuncio(s) com ID repetido em " + table + " receberam um ID novo.");
            }
        }
        catch (Exception ex) {
            this.plugin.getLogger().severe("Falha ao corrigir IDs repetidos em " + table + ": " + ex.getMessage());
            ex.printStackTrace();
        }
    }
    
    /**
     * Anúncios ativos passam para km_expired com o mesmo ID. Com o gerador antigo, um
     * anúncio ativo podia ter o mesmo ID de um item já expirado, e a passagem falharia
     * no índice único. Dá um ID novo aos anúncios ativos nessa situação.
     * Deve rodar depois de fixDuplicateIds("km_items").
     */
    public void fixIdsShared(final String table, final String otherTable) {
        final String uuidColumn = Columns.UUID.getColumn();
        try {
            if (this.plugin.getStorage().getConnection().isClosed()) {
                this.plugin.getStorage().connect();
            }
            final Connection conn = this.plugin.getStorage().getConnection();
            final List<String> shared = new ArrayList<String>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT " + uuidColumn + " FROM " + table + " WHERE " + uuidColumn + " IN (SELECT " + uuidColumn + " FROM " + otherTable + ")")) {
                while (rs.next()) {
                    shared.add(rs.getString(1));
                }
            }
            if (shared.isEmpty()) {
                return;
            }
            try (PreparedStatement update = conn.prepareStatement("UPDATE " + table + " SET " + uuidColumn + " = ? WHERE " + uuidColumn + " = ?")) {
                for (final String id : shared) {
                    update.setString(1, MarketItem.createUUID());
                    update.setString(2, id);
                    update.executeUpdate();
                }
            }
            this.plugin.getLogger().warning(shared.size() + " anuncio(s) em " + table + " tinham o mesmo ID de " + otherTable + " e receberam um ID novo.");
        }
        catch (Exception ex) {
            this.plugin.getLogger().severe("Falha ao corrigir IDs compartilhados entre " + table + " e " + otherTable + ": " + ex.getMessage());
        }
    }
    
    /**
     * Garante um índice único na coluna de ID em tabelas antigas, criadas sem PRIMARY KEY.
     */
    public void ensureUniqueId(final String table) {
        final String uuidColumn = Columns.UUID.getColumn();
        try {
            if (this.plugin.getStorage().getConnection().isClosed()) {
                this.plugin.getStorage().connect();
            }
            final Connection conn = this.plugin.getStorage().getConnection();
            try (ResultSet rs = conn.getMetaData().getIndexInfo(null, null, table, true, false)) {
                while (rs.next()) {
                    if (uuidColumn.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                        return;
                    }
                }
            }
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("CREATE UNIQUE INDEX idx_" + table + "_" + uuidColumn + " ON " + table + " (" + uuidColumn + ")");
            }
        }
        catch (Exception ex) {
            this.plugin.getLogger().severe("Falha ao criar indice unico em " + table + ": " + ex.getMessage());
        }
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
            stmt.setLong(6, System.currentTimeMillis());
            stmt.execute();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    public void refreshItem(final String player) {
        if (player == null) {
            return;
        }
        new BukkitRunnable() {
            public void run() {
                try {
                    if (SQLManager.this.plugin.getStorage().getConnection().isClosed()) {
                        SQLManager.this.plugin.getStorage().connect();
                    }
                    final Database database = SQLManager.this.plugin.getStorage();
                    final PreparedStatement stmt = database.prepareStatement("SELECT * FROM km_items WHERE " + Columns.OWNER.getColumn() + "= ?");
                    stmt.setString(1, player);
                    final ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        final long expire = rs.getLong(Columns.TIME.getColumn());
                        final long after = SQLManager.this.plugin.getConfig().getInt("Timers.Tempo_Loja") * 60 * 60 * 1000;
                        final long atual = System.currentTimeMillis();
                        if (expire + after <= atual) {
                            SQLManager.this.plugin.getItensManager().addExpired(new MarketItem(ItemHandler.fromString(rs.getString(Columns.ITEM.getColumn())), null, rs.getString(Columns.OWNER.getColumn()), 0.0, rs.getString(Columns.UUID.getColumn())));
                            SQLManager.this.plugin.getItensManager().removeItem(rs.getString(Columns.UUID.getColumn()));
                        }
                    }
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }.runTask((Plugin)UPMarket.getInstance());
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
                result.add(new MarketItem(ItemHandler.fromString(rs.getString(Columns.ITEM.getColumn())), this.plugin.getCategoryManager().getCategory(rs.getString(Columns.CATEGORY.getColumn())), rs.getString(Columns.OWNER.getColumn()), rs.getDouble(Columns.PRICE.getColumn()), rs.getString(Columns.UUID.getColumn())));
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
            stmt.setLong(4, System.currentTimeMillis());
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
    
    public void refreshExpired(final String player) {
        if (player == null) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (SQLManager.this.plugin.getStorage().getConnection().isClosed()) {
                        SQLManager.this.plugin.getStorage().connect();
                    }
                    final Database database = SQLManager.this.plugin.getStorage();
                    final PreparedStatement stmt = database.prepareStatement("SELECT * FROM km_expired WHERE " + Columns.OWNER.getColumn() + "= ?");
                    stmt.setString(1, player);
                    final ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        final long expire = rs.getLong(Columns.TIME.getColumn());
                        final long after = SQLManager.this.plugin.getConfig().getInt("Timers.Tempo_Expirado") * 60 * 60 * 1000;
                        final long atual = System.currentTimeMillis();
                        if (expire + after <= atual) {
                            SQLManager.this.plugin.getItensManager().removeExpired(rs.getString(Columns.UUID.getColumn()));
                        }
                    }
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
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
                result.add(new MarketItem(ItemHandler.fromString(rs.getString(Columns.ITEM.getColumn())), null, rs.getString(Columns.OWNER.getColumn()), 0.0, rs.getString(Columns.UUID.getColumn())));
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
