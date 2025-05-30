package com.example.database;

import com.example.ShotPL;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;

public class DatabaseManager {
    private final ShotPL plugin;
    private Connection connection;

    public DatabaseManager(ShotPL plugin) {
        this.plugin = plugin;
        initializeDatabase();
    }

    private void initializeDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + "/player_data.db");
            
            // Create tables if they don't exist
            try (Statement stmt = connection.createStatement()) {
                // Player sessions table
                stmt.execute("CREATE TABLE IF NOT EXISTS player_sessions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid TEXT NOT NULL," +
                    "player_name TEXT NOT NULL," +
                    "login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "logout_time TIMESTAMP," +
                    "session_duration INTEGER DEFAULT 0" +
                    ")");

                // Player achievements table
                stmt.execute("CREATE TABLE IF NOT EXISTS player_achievements (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid TEXT NOT NULL," +
                    "achievement_id TEXT NOT NULL," +
                    "progress INTEGER DEFAULT 0," +
                    "completed BOOLEAN DEFAULT FALSE," +
                    "completed_at TIMESTAMP," +
                    "UNIQUE(player_uuid, achievement_id)" +
                    ")");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
        }
    }

    public void recordPlayerLogin(Player player) {
        String sql = "INSERT INTO player_sessions (player_uuid, player_name) VALUES (?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, player.getUniqueId().toString());
            pstmt.setString(2, player.getName());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to record player login: " + e.getMessage());
        }
    }

    public void recordPlayerLogout(Player player) {
        String sql = "UPDATE player_sessions SET logout_time = CURRENT_TIMESTAMP, " +
                    "session_duration = (strftime('%s', CURRENT_TIMESTAMP) - strftime('%s', login_time)) * 1000 " +
                    "WHERE player_uuid = ? AND logout_time IS NULL " +
                    "AND id = (SELECT id FROM player_sessions WHERE player_uuid = ? AND logout_time IS NULL ORDER BY login_time DESC LIMIT 1)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, player.getUniqueId().toString());
            pstmt.setString(2, player.getUniqueId().toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to record player logout: " + e.getMessage());
        }
    }

    public Map<String, Object> getPlayerStats(UUID playerUuid) {
        Map<String, Object> stats = new HashMap<>();
        
        // Get total playtime
        String playtimeSql = "SELECT SUM(session_duration) as total_playtime, " +
                           "MIN(login_time) as first_join " +
                           "FROM player_sessions " +
                           "WHERE player_uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(playtimeSql)) {
            pstmt.setString(1, playerUuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                stats.put("total_playtime", rs.getLong("total_playtime"));
                stats.put("first_join", rs.getString("first_join"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get player playtime: " + e.getMessage());
        }

        // Get recent sessions
        String sessionsSql = "SELECT login_time, logout_time, session_duration " +
                           "FROM player_sessions " +
                           "WHERE player_uuid = ? " +
                           "ORDER BY login_time DESC LIMIT 3";
        try (PreparedStatement pstmt = connection.prepareStatement(sessionsSql)) {
            pstmt.setString(1, playerUuid.toString());
            ResultSet rs = pstmt.executeQuery();
            List<Map<String, Object>> sessions = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> session = new HashMap<>();
                session.put("login_time", rs.getString("login_time"));
                session.put("logout_time", rs.getString("logout_time"));
                session.put("session_duration", rs.getLong("session_duration"));
                sessions.add(session);
            }
            stats.put("login_history", sessions);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get player sessions: " + e.getMessage());
        }

        // Get achievements
        String achievementsSql = "SELECT achievement_id, progress, completed, completed_at " +
                               "FROM player_achievements " +
                               "WHERE player_uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(achievementsSql)) {
            pstmt.setString(1, playerUuid.toString());
            ResultSet rs = pstmt.executeQuery();
            List<Map<String, Object>> achievements = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> achievement = new HashMap<>();
                achievement.put("id", rs.getString("achievement_id"));
                achievement.put("progress", rs.getInt("progress"));
                achievement.put("completed", rs.getBoolean("completed"));
                achievement.put("completed_at", rs.getString("completed_at"));
                achievements.add(achievement);
            }
            stats.put("achievements", achievements);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get player achievements: " + e.getMessage());
        }

        return stats;
    }

    public List<Map<String, Object>> getAllPlayersData() {
        List<Map<String, Object>> playersData = new ArrayList<>();
        
        String sql = "SELECT DISTINCT player_uuid, player_name, " +
                    "(SELECT SUM(session_duration) FROM player_sessions WHERE player_uuid = ps.player_uuid) as total_playtime, " +
                    "(SELECT MIN(login_time) FROM player_sessions WHERE player_uuid = ps.player_uuid) as first_join, " +
                    "(SELECT COUNT(*) FROM player_achievements WHERE player_uuid = ps.player_uuid AND completed = 1) as completed_achievements " +
                    "FROM player_sessions ps " +
                    "ORDER BY total_playtime DESC";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Map<String, Object> playerData = new HashMap<>();
                playerData.put("uuid", rs.getString("player_uuid"));
                playerData.put("name", rs.getString("player_name"));
                playerData.put("total_playtime", rs.getLong("total_playtime"));
                playerData.put("first_join", rs.getString("first_join"));
                playerData.put("completed_achievements", rs.getInt("completed_achievements"));
                
                // Get recent sessions
                String sessionsSql = "SELECT login_time, logout_time, session_duration " +
                                   "FROM player_sessions " +
                                   "WHERE player_uuid = ? " +
                                   "ORDER BY login_time DESC LIMIT 3";
                try (PreparedStatement pstmt = connection.prepareStatement(sessionsSql)) {
                    pstmt.setString(1, rs.getString("player_uuid"));
                    ResultSet sessionsRs = pstmt.executeQuery();
                    List<Map<String, Object>> sessions = new ArrayList<>();
                    while (sessionsRs.next()) {
                        Map<String, Object> session = new HashMap<>();
                        session.put("login_time", sessionsRs.getString("login_time"));
                        session.put("logout_time", sessionsRs.getString("logout_time"));
                        session.put("session_duration", sessionsRs.getLong("session_duration"));
                        sessions.add(session);
                    }
                    playerData.put("recent_sessions", sessions);
                }
                
                playersData.add(playerData);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get all players data: " + e.getMessage());
        }
        
        return playersData;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close database connection: " + e.getMessage());
        }
    }
} 