package com.example;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public class ShotPL extends JavaPlugin implements Listener {
    
    private FileConfiguration config;
    
    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        // Load config
        config = getConfig();
        
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        
        // Show stylish console message
        getLogger().info("§8§m----------------------------------------");
        getLogger().info("§b§lShot-PL §7is now running!");
        getLogger().info("§7Version: §f" + getDescription().getVersion());
        getLogger().info("§7Author: §f" + getDescription().getAuthors().get(0));
        getLogger().info("§8§m----------------------------------------");
    }
    
    @Override
    public void onDisable() {
        // Log that the plugin has been disabled
        getLogger().info("§8§m----------------------------------------");
        getLogger().info("§b§lShot-PL §7has been disabled!");
        getLogger().info("§8§m----------------------------------------");
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Check if welcome message is enabled
        if (config.getBoolean("welcome.enabled", true)) {
            // Get the welcome message from config
            String message = config.getString("welcome.message", "§b§lShot-PL §7» §fWelcome to Shot-PL server!");
            
            // Replace placeholders
            message = message.replace("{player}", event.getPlayer().getName())
                           .replace("{server}", getServer().getName());
            
            // Send the message
            event.getPlayer().sendMessage(message);
        }
    }
} 