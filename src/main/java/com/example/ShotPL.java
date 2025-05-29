package com.example;

import com.example.api.ServerAPI;
import com.example.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ShotPL extends JavaPlugin implements Listener {
    private ServerAPI api;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialize database
        databaseManager = new DatabaseManager(this);

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Start API server if enabled
        if (getConfig().getBoolean("api.enabled", true)) {
            api = new ServerAPI(this);
            api.start();
        }

        // Log startup message
        getLogger().info("§a§lShot-PL §7» §fPlugin has been enabled!");
        getLogger().info("§a§lShot-PL §7» §fVersion: " + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        // Stop API server if running
        if (api != null) {
            api.stop();
        }

        // Close database connection
        if (databaseManager != null) {
            databaseManager.close();
        }

        // Log shutdown message
        getLogger().info("§c§lShot-PL §7» §fPlugin has been disabled!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Record player login
        databaseManager.recordPlayerLogin(player);

        // Send welcome message if enabled
        if (getConfig().getBoolean("welcome.enabled", true)) {
            String message = getConfig().getString("welcome.message", "§b§lShot-PL §7» §fWelcome {player} to the server!")
                    .replace("{player}", player.getName())
                    .replace("{server}", Bukkit.getServer().getName());
            player.sendMessage(message);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Record player logout
        databaseManager.recordPlayerLogout(event.getPlayer());
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
} 