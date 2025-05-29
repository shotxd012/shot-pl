package com.example;

import com.example.api.ServerAPI;
import com.example.commands.LeaderboardCommand;
import com.example.commands.StatsCommand;
import com.example.database.DatabaseManager;
import com.example.gui.StatsGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ShotPL extends JavaPlugin implements Listener {
    private ServerAPI api;
    private DatabaseManager databaseManager;
    private StatsGUI statsGUI;
    private long startTime;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();

        // Initialize start time
        startTime = System.currentTimeMillis();

        // Initialize database
        databaseManager = new DatabaseManager(this);

        // Initialize API server
        api = new ServerAPI(this);
        api.start();

        // Initialize GUI
        statsGUI = new StatsGUI(this);

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Register commands
        getCommand("stats").setExecutor(new StatsCommand(this));
        getCommand("leaderboard").setExecutor(new LeaderboardCommand(this));

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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().startsWith("Stats: ")) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null && event.getCurrentItem().getType().name().contains("HEAD")) {
                // Refresh stats when clicking the player head
                statsGUI.openStatsGUI((Player) event.getWhoClicked(), 
                    Bukkit.getPlayer(event.getView().getTitle().substring(7)));
            }
        }
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public StatsGUI getStatsGUI() {
        return statsGUI;
    }

    public long getStartTime() {
        return startTime;
    }
} 