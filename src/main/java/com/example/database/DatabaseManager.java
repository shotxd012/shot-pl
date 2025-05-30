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

                // Player statistics table
                stmt.execute("CREATE TABLE IF NOT EXISTS player_statistics (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid TEXT NOT NULL," +
                    "blocks_broken INTEGER DEFAULT 0," +
                    "blocks_placed INTEGER DEFAULT 0," +
                    "deaths INTEGER DEFAULT 0," +
                    "kills INTEGER DEFAULT 0," +
                    "distance_walked INTEGER DEFAULT 0," +
                    "distance_sprinted INTEGER DEFAULT 0," +
                    "distance_swum INTEGER DEFAULT 0," +
                    "distance_flown INTEGER DEFAULT 0," +
                    "jumps INTEGER DEFAULT 0," +
                    "food_eaten INTEGER DEFAULT 0," +
                    "damage_taken INTEGER DEFAULT 0," +
                    "damage_dealt INTEGER DEFAULT 0," +
                    "fish_caught INTEGER DEFAULT 0," +
                    "items_crafted INTEGER DEFAULT 0," +
                    "mobs_killed INTEGER DEFAULT 0," +
                    "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "UNIQUE(player_uuid)" +
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
        
        // Update player statistics when they logout
        updatePlayerStatistics(player);
    }

    public void updatePlayerStatistics(Player player) {
        String sql = "INSERT OR REPLACE INTO player_statistics (" +
                    "player_uuid, blocks_broken, blocks_placed, deaths, kills, " +
                    "distance_walked, distance_sprinted, distance_swum, distance_flown, " +
                    "jumps, food_eaten, damage_taken, damage_dealt, fish_caught, " +
                    "items_crafted, mobs_killed, last_updated) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, player.getUniqueId().toString());
            pstmt.setInt(2, player.getStatistic(org.bukkit.Statistic.MINE_BLOCK));
            pstmt.setInt(3, player.getStatistic(org.bukkit.Statistic.USE_ITEM));
            pstmt.setInt(4, player.getStatistic(org.bukkit.Statistic.DEATHS));
            pstmt.setInt(5, player.getStatistic(org.bukkit.Statistic.PLAYER_KILLS));
            pstmt.setInt(6, player.getStatistic(org.bukkit.Statistic.WALK_ONE_CM));
            pstmt.setInt(7, player.getStatistic(org.bukkit.Statistic.SPRINT_ONE_CM));
            pstmt.setInt(8, player.getStatistic(org.bukkit.Statistic.SWIM_ONE_CM));
            pstmt.setInt(9, player.getStatistic(org.bukkit.Statistic.FLY_ONE_CM));
            pstmt.setInt(10, player.getStatistic(org.bukkit.Statistic.JUMP));
            pstmt.setInt(11, player.getStatistic(org.bukkit.Statistic.ANIMALS_BRED));
            pstmt.setInt(12, player.getStatistic(org.bukkit.Statistic.DAMAGE_TAKEN));
            pstmt.setInt(13, player.getStatistic(org.bukkit.Statistic.DAMAGE_DEALT));
            pstmt.setInt(14, player.getStatistic(org.bukkit.Statistic.FISH_CAUGHT));
            pstmt.setInt(15, player.getStatistic(org.bukkit.Statistic.DROP));
            pstmt.setInt(16, player.getStatistic(org.bukkit.Statistic.MOB_KILLS));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update player statistics: " + e.getMessage());
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

        // Get player statistics
        String statisticsSql = "SELECT * FROM player_statistics WHERE player_uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(statisticsSql)) {
            pstmt.setString(1, playerUuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Map<String, Object> statistics = new HashMap<>();
                statistics.put("blocks_broken", rs.getInt("blocks_broken"));
                statistics.put("blocks_placed", rs.getInt("blocks_placed"));
                statistics.put("deaths", rs.getInt("deaths"));
                statistics.put("kills", rs.getInt("kills"));
                statistics.put("distance_walked", rs.getInt("distance_walked"));
                statistics.put("distance_sprinted", rs.getInt("distance_sprinted"));
                statistics.put("distance_swum", rs.getInt("distance_swum"));
                statistics.put("distance_flown", rs.getInt("distance_flown"));
                statistics.put("jumps", rs.getInt("jumps"));
                statistics.put("food_eaten", rs.getInt("food_eaten"));
                statistics.put("damage_taken", rs.getInt("damage_taken"));
                statistics.put("damage_dealt", rs.getInt("damage_dealt"));
                statistics.put("fish_caught", rs.getInt("fish_caught"));
                statistics.put("items_crafted", rs.getInt("items_crafted"));
                statistics.put("mobs_killed", rs.getInt("mobs_killed"));
                statistics.put("total_distance", rs.getInt("distance_walked") + rs.getInt("distance_sprinted") + rs.getInt("distance_swum") + rs.getInt("distance_flown"));
                statistics.put("last_updated", rs.getString("last_updated"));
                stats.put("statistics", statistics);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get player statistics: " + e.getMessage());
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