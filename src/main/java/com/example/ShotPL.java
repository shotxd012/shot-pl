package com.example;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.ChatColor;

public class ShotPL extends JavaPlugin implements Listener {
    
    @Override
    public void onEnable() {
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
        // Send a welcome message when a player joins
        event.getPlayer().sendMessage("§b§lShot-PL §7» §fWelcome to Shot-PL server!");
    }
} 