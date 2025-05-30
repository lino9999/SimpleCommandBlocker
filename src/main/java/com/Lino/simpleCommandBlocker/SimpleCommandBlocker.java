package com.Lino.simpleCommandBlocker;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class SimpleCommandBlocker extends JavaPlugin implements Listener, CommandExecutor {

    private List<String> processedBlockedCommands;
    private List<Pattern> blockedCommandPatterns;
    private List<String> excludedPlayers;
    private Map<String, Integer> blockedCommandAttempts;
    private boolean enableLogs;

    private String msgBlocked;
    private String msgAdminAlert;
    private String msgScbHeader;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfiguration();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("scb").setExecutor(this);
        getLogger().info("SimpleCommandBlocker activated!");
    }

    private void loadConfiguration() {
        processedBlockedCommands = new ArrayList<>();
        blockedCommandPatterns = new ArrayList<>();
        blockedCommandAttempts = new HashMap<>();

        for (String cmd : getConfig().getStringList("blocked-commands")) {
            String processed = cmd.trim().replaceFirst("^/", "");
            processedBlockedCommands.add(processed);
            Pattern pattern = Pattern.compile(processed, Pattern.CASE_INSENSITIVE);
            blockedCommandPatterns.add(pattern);
            blockedCommandAttempts.put(processed, 0);
        }

        excludedPlayers = getConfig().getStringList("excluded-players");
        enableLogs = getConfig().getBoolean("enable-logs", true);

        msgBlocked = ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.blocked", "&cThis command is blocked!"));
        msgAdminAlert = ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.adminAlert", "&e[SimpleCommandBlocker] The player {player} attempted to use a blocked command: /{command}"));
        msgScbHeader = ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.scbHeader", "&6[SimpleCommandBlocker] Blocked Commands:"));
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (excludedPlayers.contains(player.getName())) {
            return;
        }

        String message = event.getMessage();
        if (message.startsWith("/")) {
            message = message.substring(1);
        }
        String lowerMessage = message.toLowerCase();

        for (int i = 0; i < blockedCommandPatterns.size(); i++) {
            Pattern pattern = blockedCommandPatterns.get(i);
            if (pattern.matcher(lowerMessage).lookingAt()) {
                String patternStr = processedBlockedCommands.get(i);
                blockedCommandAttempts.put(patternStr, blockedCommandAttempts.get(patternStr) + 1);

                event.setCancelled(true);
                player.sendMessage(msgBlocked);

                if (enableLogs) { // Controlla se i log sono attivati
                    logIllegalCommand(player, event.getMessage());
                }

                String alert = msgAdminAlert.replace("{player}", player.getName())
                        .replace("{command}", event.getMessage().replaceFirst("^/", ""));
                for (Player admin : Bukkit.getOnlinePlayers()) {
                    if (admin.isOp() || admin.hasPermission("simplecommandblocker.notify")) {
                        admin.sendMessage(alert);
                    }
                }
                return;
            }
        }
    }

    private void logIllegalCommand(Player player, String command) {
        if (!enableLogs) return; // Se i log sono disattivati, esce subito

        File logsFolder = new File(getDataFolder(), "logs");
        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
        }
        File logFile = new File(logsFolder, player.getName() + ".log");
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write("[" + new Date().toString() + "] " + command + "\n");
        } catch (IOException e) {
            getLogger().severe("Unable to write log for player " + player.getName());
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be executed in-game.");
            return true;
        }

        Player player = (Player) sender;
        if (!(player.isOp() || player.hasPermission("simplecommandblocker.admin"))) {
            player.sendMessage(ChatColor.RED + "You do not have permission to execute this command.");
            return true;
        }

        player.sendMessage(msgScbHeader);
        for (String patternStr : processedBlockedCommands) {
            int count = blockedCommandAttempts.getOrDefault(patternStr, 0);
            player.sendMessage(ChatColor.YELLOW + "- /" + patternStr + " | Failed Attempts: " + count);
        }
        return true;
    }
}