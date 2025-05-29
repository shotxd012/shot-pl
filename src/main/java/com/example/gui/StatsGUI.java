package com.example.gui;

import com.example.ShotPL;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.Statistic;

import java.util.*;

public class StatsGUI {
    private final ShotPL plugin;
    private static final int GUI_SIZE = 54;

    public StatsGUI(ShotPL plugin) {
        this.plugin = plugin;
    }

    public void openStatsGUI(Player viewer, Player target) {
        Map<String, Object> stats = plugin.getDatabaseManager().getPlayerStats(target.getUniqueId());
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, 
            ChatColor.DARK_PURPLE + "Stats: " + target.getName());

        // Player Head
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        headMeta.setOwningPlayer(target);
        headMeta.setDisplayName(ChatColor.GOLD + target.getName());
        List<String> headLore = new ArrayList<>();
        headLore.add(ChatColor.GRAY + "Click to refresh");
        headMeta.setLore(headLore);
        head.setItemMeta(headMeta);
        gui.setItem(4, head);

        // Basic Stats
        addStatItem(gui, 19, Material.HEART_OF_THE_SEA, "Health",
            String.format("%.1f/%.1f", target.getHealth(), target.getMaxHealth()));
        addStatItem(gui, 20, Material.COMPASS, "Location",
            String.format("%.1f, %.1f, %.1f", 
                target.getLocation().getX(),
                target.getLocation().getY(),
                target.getLocation().getZ()));
        addStatItem(gui, 21, Material.CLOCK, "Ping",
            target.getPing() + "ms");
        addStatItem(gui, 22, Material.BOOK, "GameMode",
            target.getGameMode().toString());
        addStatItem(gui, 23, Material.CLOCK, "Total Playtime",
            formatDuration((Long) stats.getOrDefault("total_playtime", 0L)));

        // Combat Stats
        addStatItem(gui, 28, Material.DIAMOND_SWORD, "Kills",
            String.valueOf(target.getStatistic(Statistic.PLAYER_KILLS)));
        addStatItem(gui, 29, Material.SKELETON_SKULL, "Deaths",
            String.valueOf(target.getStatistic(Statistic.DEATHS)));
        addStatItem(gui, 30, Material.SHIELD, "Damage Dealt",
            String.valueOf(target.getStatistic(Statistic.DAMAGE_DEALT)));
        addStatItem(gui, 31, Material.IRON_CHESTPLATE, "Damage Taken",
            String.valueOf(target.getStatistic(Statistic.DAMAGE_TAKEN)));

        // Movement Stats
        addStatItem(gui, 37, Material.ELYTRA, "Distance Traveled",
            String.format("%.1f blocks", 
                target.getStatistic(Statistic.WALK_ONE_CM) / 100.0 +
                target.getStatistic(Statistic.SPRINT_ONE_CM) / 100.0 +
                target.getStatistic(Statistic.SWIM_ONE_CM) / 100.0));
        addStatItem(gui, 38, Material.DIAMOND_BOOTS, "Jumps",
            String.valueOf(target.getStatistic(Statistic.JUMP)));
        addStatItem(gui, 39, Material.ENDER_PEARL, "Teleports",
            String.valueOf(target.getStatistic(Statistic.USE_ITEM)));
        addStatItem(gui, 40, Material.OAK_BOAT, "Boats Used",
            String.valueOf(target.getStatistic(Statistic.BOAT_ONE_CM) / 100));

        // Item Stats
        addStatItem(gui, 46, Material.DIAMOND_PICKAXE, "Blocks Broken",
            String.valueOf(target.getStatistic(Statistic.BREAK_ITEM)));
        addStatItem(gui, 47, Material.GRASS_BLOCK, "Blocks Placed",
            String.valueOf(target.getStatistic(Statistic.USE_ITEM)));
        addStatItem(gui, 48, Material.CHEST, "Items Crafted",
            String.valueOf(target.getStatistic(Statistic.CRAFT_ITEM)));
        addStatItem(gui, 49, Material.FURNACE, "Items Smelted",
            String.valueOf(target.getStatistic(Statistic.USE_ITEM)));

        // Achievements
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> achievements = (List<Map<String, Object>>) stats.get("achievements");
        if (achievements != null && !achievements.isEmpty()) {
            ItemStack achievementItem = new ItemStack(Material.GOLD_INGOT);
            ItemMeta meta = achievementItem.getItemMeta();
            meta.setDisplayName(ChatColor.GOLD + "Achievements");
            List<String> lore = new ArrayList<>();
            for (Map<String, Object> achievement : achievements) {
                String achievementId = achievement.get("id").toString();
                String progress = Boolean.TRUE.equals(achievement.get("completed")) ? 
                    ChatColor.GREEN + "Completed" : 
                    ChatColor.YELLOW + achievement.get("progress") + "%";
                lore.add(ChatColor.GRAY + "• " + achievementId + " - " + progress);
            }
            meta.setLore(lore);
            achievementItem.setItemMeta(meta);
            gui.setItem(25, achievementItem);
        }

        viewer.openInventory(gui);
    }

    private void addStatItem(Inventory gui, int slot, Material material, String name, String value) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + name);
        meta.setLore(Collections.singletonList(ChatColor.WHITE + value));
        item.setItemMeta(meta);
        gui.setItem(slot, item);
    }

    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
    }
} 