package com.hakerone.shadowauth.managers;

import com.hakerone.shadowauth.ShadowAuth;
import org.bukkit.Location;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DatabaseManager {

    private final ShadowAuth plugin;
    private HikariDataSource dataSource;
    private final Map<UUID, Long> authenticatedPlayers;
    private final Map<UUID, List<PotionEffect>> savedEffects;
    private final Map<UUID, Location> savedLocations;

    public DatabaseManager(ShadowAuth plugin) {
        this.plugin = plugin;
        this.authenticatedPlayers = new HashMap<>();
        this.savedEffects = new ConcurrentHashMap<>();
        this.savedLocations = new ConcurrentHashMap<>();
    }

    public void initialize() {
        try {
            setupDataSource();
            createTables();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupDataSource() throws Exception {
        String dbHost = plugin.getConfig().getString("settings.database.host", "");
        String dbPort = plugin.getConfig().getString("settings.database.port", "3306");
        String dbName = plugin.getConfig().getString("settings.database.name", "");
        String dbUsername = plugin.getConfig().getString("settings.database.username", "");
        String dbPassword = plugin.getConfig().getString("settings.database.password", "");

        HikariConfig config = new HikariConfig();

        if (!dbHost.isEmpty() && !dbName.isEmpty() && !dbUsername.isEmpty()) {
            config.setJdbcUrl("jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName + "?useSSL=false&allowPublicKeyRetrieval=true");
            config.setUsername(dbUsername);
            config.setPassword(dbPassword);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            plugin.getLogger().info("Using MySQL: " + dbName);
        } else {
            String dbPath = plugin.getDataFolder() + File.separator + "database.db";
            config.setJdbcUrl("jdbc:sqlite:" + dbPath);
            config.setDriverClassName("org.sqlite.JDBC");
            plugin.getLogger().info("Using SQLite: " + dbPath);
        }

        config.setMaximumPoolSize(plugin.getConfig().getInt("settings.database.pool.max_size", 10));
        config.setMinimumIdle(plugin.getConfig().getInt("settings.database.pool.min_idle", 2));
        this.dataSource = new HikariDataSource(config);
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database not available");
        }
        return dataSource.getConnection();
    }

    private void createTables() throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS accounts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT UNIQUE NOT NULL, " +
                "password_hash TEXT NOT NULL, " +
                "ip TEXT, " +
                "registered_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
            
            stmt.execute("CREATE TABLE IF NOT EXISTS sessions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "player_uuid TEXT UNIQUE NOT NULL, " +
                "username TEXT NOT NULL, " +
                "ip TEXT NOT NULL, " +
                "last_login DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "expires_at DATETIME)");
            
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_username ON accounts(username)");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_sessions_uuid ON sessions(player_uuid)");
        }
    }

    public boolean isRegistered(String username) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM accounts WHERE username = ?")) {
            stmt.setString(1, username.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error checking registration: " + e.getMessage());
            return false;
        }
    }

    public boolean register(String username, String passwordHash, String ip) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO accounts (username, password_hash, ip) VALUES (?, ?, ?)")) {
            stmt.setString(1, username.toLowerCase());
            stmt.setString(2, passwordHash);
            stmt.setString(3, ip);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Error registering: " + e.getMessage());
            return false;
        }
    }

    public String getPasswordHash(String username) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT password_hash FROM accounts WHERE username = ?")) {
            stmt.setString(1, username.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("password_hash") : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public void setAuthenticated(UUID uuid, boolean authenticated) {
        if (authenticated) {
            authenticatedPlayers.put(uuid, System.currentTimeMillis());
        } else {
            authenticatedPlayers.remove(uuid);
        }
    }

    public boolean isAuthenticated(UUID uuid) {
        return authenticatedPlayers.containsKey(uuid);
    }

    public boolean hasValidSession(UUID uuid) {
        Long time = authenticatedPlayers.get(uuid);
        if (time == null) return false;
        long hours = plugin.getSessionHours();
        return (System.currentTimeMillis() - time) < (hours * 60 * 60 * 1000);
    }

    public void createSession(UUID uuid, String username, String ip) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR REPLACE INTO sessions (player_uuid, username, ip, last_login, expires_at) VALUES (?, ?, ?, datetime('now'), datetime('now', '+' || ? || ' hours'))")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, username.toLowerCase());
            stmt.setString(3, ip);
            stmt.setInt(4, plugin.getSessionHours());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating session: " + e.getMessage());
        }
    }

    public boolean hasDatabaseSession(UUID uuid, String ip) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM sessions WHERE player_uuid = ? AND expires_at > datetime('now')")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                boolean hasSession = rs.next() && rs.getInt(1) > 0;
                return hasSession;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public void removeSession(UUID uuid) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM sessions WHERE player_uuid = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error removing session: " + e.getMessage());
        }
    }

    // Сохранение эффектов игрока
    public void saveEffects(UUID uuid, List<PotionEffect> effects) {
        savedEffects.put(uuid, new ArrayList<>(effects));
    }

    // Восстановление эффектов игрока
    public void restoreEffects(UUID uuid, org.bukkit.entity.Player player) {
        List<PotionEffect> effects = savedEffects.remove(uuid);
        if (effects != null) {
            for (PotionEffect effect : effects) {
                player.addPotionEffect(effect);
            }
        }
    }

    // Сохранение локации игрока
    public void saveLocation(UUID uuid, Location location) {
        savedLocations.put(uuid, location.clone());
    }

    // Восстановление локации игрока
    public void restoreLocation(UUID uuid, org.bukkit.entity.Player player) {
        Location location = savedLocations.remove(uuid);
        if (location != null) {
            player.teleport(location);
        }
    }

    // Очистка сохраненных данных
    public void clearSavedData(UUID uuid) {
        savedEffects.remove(uuid);
        savedLocations.remove(uuid);
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
