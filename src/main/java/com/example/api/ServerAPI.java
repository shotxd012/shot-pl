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

public class ServerAPI {
    private final ShotPL plugin;
    private final Gson gson;
    private long startTime;
    private long lastTickTime;
    private double[] tpsHistory;
    private int tpsIndex;
    private HttpServer server;

    public ServerAPI(ShotPL plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        this.startTime = System.currentTimeMillis();
        this.lastTickTime = startTime;
        this.tpsHistory = new double[3];
        this.tpsIndex = 0;
        
        // Start TPS monitoring task
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateTPS, 20L, 20L);
    }

    private void updateTPS() {
        long currentTime = System.currentTimeMillis();
        long timeSpent = currentTime - lastTickTime;
        double tps = 1000.0 / timeSpent;
        
        tpsHistory[tpsIndex] = tps;
        tpsIndex = (tpsIndex + 1) % tpsHistory.length;
        
        lastTickTime = currentTime;
    }

    public void start() {
        try {
            int port = plugin.getConfig().getInt("api.port", 8080);
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newCachedThreadPool());

            // Status endpoint
            server.createContext("/api/status", exchange -> {
                if (!isAuthorized(exchange)) {
                    sendResponse(exchange, 401, "Unauthorized");
                    return;
                }

                JsonObject status = new JsonObject();
                Server bukkitServer = Bukkit.getServer();
                
                // Basic server info
                status.addProperty("server_name", bukkitServer.getName());
                status.addProperty("version", bukkitServer.getVersion());
                status.addProperty("online_players", bukkitServer.getOnlinePlayers().size());
                status.addProperty("max_players", bukkitServer.getMaxPlayers());
                status.addProperty("uptime", getUptime());
                
                // TPS
                status.addProperty("tps_current", String.format("%.2f", tpsHistory[tpsIndex]));
                status.addProperty("tps_average", String.format("%.2f", 
                    (tpsHistory[0] + tpsHistory[1] + tpsHistory[2]) / 3.0));
                
                // Memory usage
                Runtime runtime = Runtime.getRuntime();
                status.addProperty("memory_used", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
                status.addProperty("memory_max", runtime.maxMemory() / 1024 / 1024);
                
                // Online players list
                JsonObject players = new JsonObject();
                for (Player player : bukkitServer.getOnlinePlayers()) {
                    JsonObject playerInfo = new JsonObject();
                    playerInfo.addProperty("name", player.getName());
                    playerInfo.addProperty("uuid", player.getUniqueId().toString());
                    playerInfo.addProperty("health", player.getHealth());
                    playerInfo.addProperty("ping", player.getPing());
                    playerInfo.addProperty("gamemode", player.getGameMode().toString());
                    playerInfo.addProperty("location", String.format("%.2f, %.2f, %.2f", 
                        player.getLocation().getX(),
                        player.getLocation().getY(),
                        player.getLocation().getZ()));
                    players.add(player.getUniqueId().toString(), playerInfo);
                }
                status.add("players", players);
                
                sendResponse(exchange, 200, gson.toJson(status));
            });

            // Player info endpoint
            server.createContext("/api/player/", exchange -> {
                if (!isAuthorized(exchange)) {
                    sendResponse(exchange, 401, "Unauthorized");
                    return;
                }

                String path = exchange.getRequestURI().getPath();
                String uuid = path.substring("/api/player/".length());
                
                try {
                    Player player = Bukkit.getPlayer(UUID.fromString(uuid));
                    if (player == null) {
                        sendResponse(exchange, 404, gson.toJson(Map.of("error", "Player not found")));
                        return;
                    }
                    
                    JsonObject playerInfo = new JsonObject();
                    playerInfo.addProperty("name", player.getName());
                    playerInfo.addProperty("uuid", player.getUniqueId().toString());
                    playerInfo.addProperty("health", player.getHealth());
                    playerInfo.addProperty("ping", player.getPing());
                    playerInfo.addProperty("gamemode", player.getGameMode().toString());
                    playerInfo.addProperty("location", String.format("%.2f, %.2f, %.2f", 
                        player.getLocation().getX(),
                        player.getLocation().getY(),
                        player.getLocation().getZ()));
                    
                    sendResponse(exchange, 200, gson.toJson(playerInfo));
                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 400, gson.toJson(Map.of("error", "Invalid UUID format")));
                }
            });

            server.start();
            plugin.getLogger().info("§aAPI server started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start API server: " + e.getMessage());
        }
    }

    private boolean isAuthorized(HttpExchange exchange) {
        boolean authEnabled = plugin.getConfig().getBoolean("api.auth.enabled", false);
        if (!authEnabled) return true;

        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        String token = plugin.getConfig().getString("api.auth.token", "");
        return authHeader != null && authHeader.equals("Bearer " + token);
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