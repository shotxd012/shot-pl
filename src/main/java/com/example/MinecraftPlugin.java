package com.example;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;

public class MinecraftPlugin extends JavaPlugin implements Listener {
    
    @Override
    public void onEnable() {
        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        
        // Log that the plugin has been enabled
        getLogger().info("MinecraftPlugin has been enabled!");
    }
    
    @Override
    public void onDisable() {
        // Log that the plugin has been disabled
        getLogger().info("MinecraftPlugin has been disabled!");
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Send a welcome message when a player joins
        event.getPlayer().sendMessage("Welcome to the server!");
    }
} 