package com.example.database;

import com.example.ShotPL;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class DatabaseManager {
    private final ShotPL plugin;
    private Connection connection;
    private final Map<UUID, Long> playerLoginTimes = new HashMap<>();

    public DatabaseManager(ShotPL plugin) {
        this.plugin = plugin;
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File dbFile = new File(dataFolder, "playerdata.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            // Create tables if they don't exist
            try (Statement stmt = connection.createStatement()) {
                // Players table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS players (
                        uuid TEXT PRIMARY KEY,
                        username TEXT NOT NULL,
                        first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        total_playtime INTEGER DEFAULT 0
                    )
                """);

                // Login history table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS login_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        logout_time TIMESTAMP,
                        session_duration INTEGER,
                        FOREIGN KEY (uuid) REFERENCES players(uuid)
                    )
                """);

                // Achievements table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS achievements (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        achievement_id TEXT NOT NULL,
                        achieved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        progress INTEGER DEFAULT 0,
                        completed BOOLEAN DEFAULT FALSE,
                        FOREIGN KEY (uuid) REFERENCES players(uuid)
                    )
                """);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database", e);
        }
    }

    public void recordPlayerLogin(Player player) {
        UUID uuid = player.getUniqueId();
        playerLoginTimes.put(uuid, System.currentTimeMillis());

        try {
            // Update or insert player record
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO players (uuid, username, last_seen) VALUES (?, ?, CURRENT_TIMESTAMP)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, player.getName());
                ps.executeUpdate();
            }

            // Record login
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO login_history (uuid, login_time) VALUES (?, CURRENT_TIMESTAMP)")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to record player login", e);
        }
    }

    public void recordPlayerLogout(Player player) {
        UUID uuid = player.getUniqueId();
        Long loginTime = playerLoginTimes.remove(uuid);
        if (loginTime == null) return;

        long sessionDuration = System.currentTimeMillis() - loginTime;

        try {
            // Update last login record
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE login_history SET logout_time = CURRENT_TIMESTAMP, session_duration = ? " +
                    "WHERE uuid = ? AND logout_time IS NULL ORDER BY login_time DESC LIMIT 1")) {
                ps.setLong(1, sessionDuration);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }

            // Update total playtime
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE players SET total_playtime = total_playtime + ? WHERE uuid = ?")) {
                ps.setLong(1, sessionDuration);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to record player logout", e);
        }
    }

    public Map<String, Object> getPlayerStats(UUID uuid) {
        Map<String, Object> stats = new HashMap<>();
        try {
            // Get basic player info
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT username, first_join, last_seen, total_playtime FROM players WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    stats.put("username", rs.getString("username"));
                    stats.put("first_join", rs.getTimestamp("first_join"));
                    stats.put("last_seen", rs.getTimestamp("last_seen"));
                    stats.put("total_playtime", rs.getLong("total_playtime"));
                }
            }

            // Get login history
            List<Map<String, Object>> loginHistory = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT login_time, logout_time, session_duration FROM login_history WHERE uuid = ? ORDER BY login_time DESC LIMIT 10")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, Object> login = new HashMap<>();
                    login.put("login_time", rs.getTimestamp("login_time"));
                    login.put("logout_time", rs.getTimestamp("logout_time"));
                    login.put("session_duration", rs.getLong("session_duration"));
                    loginHistory.add(login);
                }
            }
            stats.put("login_history", loginHistory);

            // Get achievements
            List<Map<String, Object>> achievements = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT achievement_id, achieved_at, progress, completed FROM achievements WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    Map<String, Object> achievement = new HashMap<>();
                    achievement.put("id", rs.getString("achievement_id"));
                    achievement.put("achieved_at", rs.getTimestamp("achieved_at"));
                    achievement.put("progress", rs.getInt("progress"));
                    achievement.put("completed", rs.getBoolean("completed"));
                    achievements.add(achievement);
                }
            }
            stats.put("achievements", achievements);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get player stats", e);
        }
        return stats;
    }

    public void awardAchievement(UUID uuid, String achievementId, int progress, boolean completed) {
        try {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO achievements (uuid, achievement_id, progress, completed) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, achievementId);
                ps.setInt(3, progress);
                ps.setBoolean(4, completed);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to award achievement", e);
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to close database connection", e);
        }
    }
} 