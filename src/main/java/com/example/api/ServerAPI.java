package com.example.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import spark.Spark;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ServerAPI {
    private final ShotPL plugin;
    private final Gson gson;
    private long startTime;

    public ServerAPI(ShotPL plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        this.startTime = System.currentTimeMillis();
    }

    public void start() {
        int port = plugin.getConfig().getInt("api.port", 8080);
        boolean authEnabled = plugin.getConfig().getBoolean("api.auth.enabled", false);
        String authToken = plugin.getConfig().getString("api.auth.token", "");

        Spark.port(port);

        // Add authentication if enabled
        if (authEnabled) {
            Spark.before((request, response) -> {
                String token = request.headers("Authorization");
                if (token == null || !token.equals("Bearer " + authToken)) {
                    Spark.halt(401, "Unauthorized");
                }
            });
        }

        // Get server status
        Spark.get("/api/status", (request, response) -> {
            response.type("application/json");
            JsonObject status = new JsonObject();
            
            // Basic server info
            status.addProperty("server_name", Bukkit.getServerName());
            status.addProperty("version", Bukkit.getVersion());
            status.addProperty("online_players", Bukkit.getOnlinePlayers().size());
            status.addProperty("max_players", Bukkit.getMaxPlayers());
            status.addProperty("uptime", getUptime());
            
            // TPS (Ticks Per Second)
            double[] tps = Bukkit.getTPS();
            status.addProperty("tps_1m", String.format("%.2f", tps[0]));
            status.addProperty("tps_5m", String.format("%.2f", tps[1]));
            status.addProperty("tps_15m", String.format("%.2f", tps[2]));
            
            // Memory usage
            Runtime runtime = Runtime.getRuntime();
            status.addProperty("memory_used", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
            status.addProperty("memory_max", runtime.maxMemory() / 1024 / 1024);
            
            // Online players list
            JsonObject players = new JsonObject();
            for (Player player : Bukkit.getOnlinePlayers()) {
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
            
            return gson.toJson(status);
        });

        // Get specific player info
        Spark.get("/api/player/:uuid", (request, response) -> {
            response.type("application/json");
            String uuid = request.params(":uuid");
            Player player = Bukkit.getPlayer(UUID.fromString(uuid));
            
            if (player == null) {
                response.status(404);
                return gson.toJson(Map.of("error", "Player not found"));
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
            
            return gson.toJson(playerInfo);
        });

        plugin.getLogger().info("§aAPI server started on port " + port);
    }

    public void stop() {
        Spark.stop();
        plugin.getLogger().info("§cAPI server stopped");
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