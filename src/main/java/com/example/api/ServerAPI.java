package com.example.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.example.ShotPL;
import org.bukkit.Server;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ServerAPI {
    private final ShotPL plugin;
    private final Gson gson;
    private long startTime;
    private final AtomicInteger tickCount;
    private final double[] tpsHistory;
    private int tpsIndex;
    private HttpServer server;
    private long lastTickTime;

    public ServerAPI(ShotPL plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        this.startTime = System.currentTimeMillis();
        this.lastTickTime = startTime;
        this.tickCount = new AtomicInteger(0);
        this.tpsHistory = new double[3];
        this.tpsIndex = 0;
        
        // Start TPS monitoring task
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateTPS, 20L, 20L);
    }

    private void updateTPS() {
        long currentTime = System.currentTimeMillis();
        long timeSpent = currentTime - lastTickTime;
        int ticks = tickCount.getAndSet(0);
        
        // Calculate TPS based on actual ticks
        double tps = (ticks * 1000.0) / timeSpent;
        
        // Ensure TPS is within reasonable bounds (0-20)
        tps = Math.min(20.0, Math.max(0.0, tps));
        
        tpsHistory[tpsIndex] = tps;
        tpsIndex = (tpsIndex + 1) % tpsHistory.length;
        
        lastTickTime = currentTime;
    }

    public void start() {
        try {
            int port = plugin.getConfig().getInt("api.port", 8080);
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(10));

            // Status endpoint
            server.createContext("/api/status", exchange -> {
                if (!checkAuth(exchange)) {
                    sendResponse(exchange, 401, "Unauthorized");
                    return;
                }

                if (!"GET".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "Method not allowed");
                    return;
                }

                Map<String, Object> response = new HashMap<>();
                response.put("server_name", Bukkit.getServer().getName());
                response.put("version", Bukkit.getServer().getVersion());
                response.put("online_players", Bukkit.getOnlinePlayers().size());
                response.put("max_players", Bukkit.getMaxPlayers());
                response.put("uptime", System.currentTimeMillis() - plugin.getStartTime());
                response.put("current_tps", tpsHistory[0]);
                response.put("average_tps", (tpsHistory[0] + tpsHistory[1] + tpsHistory[2]) / 3);
                
                // Memory usage
                Runtime runtime = Runtime.getRuntime();
                response.put("memory_used", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
                response.put("memory_max", runtime.maxMemory() / 1024 / 1024);

                // Online players
                List<Map<String, Object>> players = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Map<String, Object> playerInfo = new HashMap<>();
                    playerInfo.put("name", player.getName());
                    playerInfo.put("uuid", player.getUniqueId().toString());
                    playerInfo.put("health", player.getHealth());
                    playerInfo.put("game_mode", player.getGameMode().toString());
                    playerInfo.put("ping", player.getPing());
                    players.add(playerInfo);
                }
                response.put("players", players);

                sendResponse(exchange, 200, gson.toJson(response));
            });

            // Player info endpoint
            server.createContext("/api/player/", exchange -> {
                if (!checkAuth(exchange)) {
                    sendResponse(exchange, 401, "Unauthorized");
                    return;
                }

                if (!"GET".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "Method not allowed");
                    return;
                }

                String path = exchange.getRequestURI().getPath();
                String playerName = path.substring("/api/player/".length());
                Player player = Bukkit.getPlayer(playerName);

                if (player == null) {
                    sendResponse(exchange, 404, "Player not found");
                    return;
                }

                Map<String, Object> response = new HashMap<>();
                response.put("name", player.getName());
                response.put("uuid", player.getUniqueId().toString());
                response.put("health", player.getHealth());
                response.put("max_health", player.getMaxHealth());
                response.put("game_mode", player.getGameMode().toString());
                response.put("ping", player.getPing());
                response.put("location", Arrays.asList(
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ()
                ));

                // Add player stats from database
                response.putAll(plugin.getDatabaseManager().getPlayerStats(player.getUniqueId()));

                sendResponse(exchange, 200, gson.toJson(response));
            });

            // All players data endpoint
            server.createContext("/api/players", exchange -> {
                if (!checkAuth(exchange)) {
                    sendResponse(exchange, 401, "Unauthorized");
                    return;
                }

                if (!"GET".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "Method not allowed");
                    return;
                }

                List<Map<String, Object>> playersData = plugin.getDatabaseManager().getAllPlayersData();
                
                // Enhance player data with online status and additional information
                for (Map<String, Object> playerData : playersData) {
                    String uuid = (String) playerData.get("uuid");
                    Player onlinePlayer = Bukkit.getPlayer(UUID.fromString(uuid));
                    
                    // Add online status
                    playerData.put("is_online", onlinePlayer != null);
                    
                    // Add additional information for online players
                    if (onlinePlayer != null) {
                        playerData.put("health", onlinePlayer.getHealth());
                        playerData.put("max_health", onlinePlayer.getMaxHealth());
                        playerData.put("game_mode", onlinePlayer.getGameMode().toString());
                        playerData.put("ping", onlinePlayer.getPing());
                        playerData.put("location", Arrays.asList(
                            onlinePlayer.getLocation().getX(),
                            onlinePlayer.getLocation().getY(),
                            onlinePlayer.getLocation().getZ()
                        ));
                        playerData.put("world", onlinePlayer.getWorld().getName());
                        playerData.put("level", onlinePlayer.getLevel());
                        playerData.put("exp", onlinePlayer.getExp());
                        playerData.put("food_level", onlinePlayer.getFoodLevel());
                        playerData.put("last_played", onlinePlayer.getLastPlayed());
                    }
                }
                
                sendResponse(exchange, 200, gson.toJson(playersData));
            });

            // Player stats endpoint
            server.createContext("/api/player/stats/", exchange -> {
                if (!checkAuth(exchange)) {
                    sendResponse(exchange, 401, "Unauthorized");
                    return;
                }

                String path = exchange.getRequestURI().getPath();
                String uuid = path.substring("/api/player/stats/".length());
                
                try {
                    Map<String, Object> stats = plugin.getDatabaseManager().getPlayerStats(UUID.fromString(uuid));
                    if (stats.isEmpty()) {
                        sendResponse(exchange, 404, gson.toJson(Map.of("error", "Player not found")));
                        return;
                    }
                    
                    sendResponse(exchange, 200, gson.toJson(stats));
                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 400, gson.toJson(Map.of("error", "Invalid UUID format")));
                }
            });

            // Player achievements endpoint
            server.createContext("/api/player/achievements/", exchange -> {
                if (!checkAuth(exchange)) {
                    sendResponse(exchange, 401, "Unauthorized");
                    return;
                }

                String path = exchange.getRequestURI().getPath();
                String uuid = path.substring("/api/player/achievements/".length());
                
                try {
                    Map<String, Object> stats = plugin.getDatabaseManager().getPlayerStats(UUID.fromString(uuid));
                    if (stats.isEmpty()) {
                        sendResponse(exchange, 404, gson.toJson(Map.of("error", "Player not found")));
                        return;
                    }
                    
                    JsonObject response = new JsonObject();
                    response.add("achievements", gson.toJsonTree(stats.get("achievements")));
                    sendResponse(exchange, 200, gson.toJson(response));
                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 400, gson.toJson(Map.of("error", "Invalid UUID format")));
                }
            });

            // Start tick counter
            Bukkit.getScheduler().runTaskTimer(plugin, () -> tickCount.incrementAndGet(), 1L, 1L);

            server.start();
            plugin.getLogger().info("§aAPI server started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start API server: " + e.getMessage());
        }
    }

    private boolean checkAuth(HttpExchange exchange) {
        String apiKey = plugin.getConfig().getString("api.key");
        if (apiKey == null || apiKey.isEmpty()) {
            return true;
        }

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        return authHeader != null && authHeader.equals("Bearer " + apiKey);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            plugin.getLogger().info("§cAPI server stopped");
        }
    }

    private String getUptime() {
        long uptime = System.currentTimeMillis() - startTime;
        long seconds = uptime / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        return String.format("%dd %dh %dm %ds", 
            days, hours % 24, minutes % 60, seconds % 60);
    }
} 