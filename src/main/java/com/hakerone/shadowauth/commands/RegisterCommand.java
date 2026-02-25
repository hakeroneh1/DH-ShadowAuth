package com.hakerone.shadowauth.commands;

import com.hakerone.shadowauth.ShadowAuth;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class RegisterCommand implements CommandExecutor {

    private final ShadowAuth plugin;

    public RegisterCommand(ShadowAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getPrefix() + plugin.colorize("&c&lТолько для игроков!"));
            return true;
        }

        if (plugin.getDatabaseManager().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessage("already_logged"));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessage("usage").replace("{command}", "register"));
            return true;
        }

        String password = args[0];
        if (password.length() < 4) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessage("password_short"));
            return true;
        }

        if (plugin.getDatabaseManager().isRegistered(player.getName())) {
            player.sendMessage(plugin.getPrefix() + plugin.getMessage("register_failed"));
            return true;
        }

        String hash = hashPassword(password);
        String ip = player.getAddress().getAddress().getHostAddress();

        if (plugin.getDatabaseManager().register(player.getName(), hash, ip)) {
            plugin.getDatabaseManager().setAuthenticated(player.getUniqueId(), true);
            plugin.getDatabaseManager().createSession(player.getUniqueId(), player.getName(), ip);
            player.sendMessage(plugin.getPrefix() + plugin.getMessage("register_success"));
            restorePlayer(player);
            // Восстанавливаем эффекты
            plugin.getDatabaseManager().restoreEffects(player.getUniqueId(), player);
            // Телепортация
            teleportAfterAuth(player);
            // Очищаем сохраненные данные
            plugin.getDatabaseManager().clearSavedData(player.getUniqueId());
        } else {
            player.sendMessage(plugin.getPrefix() + plugin.getMessage("register_failed"));
        }

        return true;
    }

    private void teleportAfterAuth(Player player) {
        int delay = plugin.getConfig().getInt("settings.teleport.delay", 5);
        
        if (plugin.getConfig().getBoolean("settings.spawn.enabled", false)) {
            // Телепортация на спавн
            org.bukkit.World world = plugin.getServer().getWorld(
                plugin.getConfig().getString("settings.spawn.world", "world"));
            if (world != null) {
                double x = plugin.getConfig().getDouble("settings.spawn.x", 0);
                double y = plugin.getConfig().getDouble("settings.spawn.y", 100);
                double z = plugin.getConfig().getDouble("settings.spawn.z", 0);
                float yaw = (float) plugin.getConfig().getDouble("settings.spawn.yaw", 0);
                float pitch = (float) plugin.getConfig().getDouble("settings.spawn.pitch", 0);
                
                if (delay > 0) {
                    player.sendMessage(plugin.getPrefix() + plugin.getMessage("teleporting"));
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> 
                        player.teleport(new org.bukkit.Location(world, x, y, z, yaw, pitch)), delay);
                } else {
                    player.teleport(new org.bukkit.Location(world, x, y, z, yaw, pitch));
                }
            }
        } else if (plugin.getConfig().getBoolean("settings.teleport.after_auth", true)) {
            // Телепортация на место выхода
            if (delay > 0) {
                player.sendMessage(plugin.getPrefix() + plugin.getMessage("teleporting"));
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> 
                    plugin.getDatabaseManager().restoreLocation(player.getUniqueId(), player), delay);
            } else {
                plugin.getDatabaseManager().restoreLocation(player.getUniqueId(), player);
            }
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private void restorePlayer(Player player) {
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.setInvulnerable(false);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setExhaustion(0);
        player.setRemainingAir(300);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY);
    }
}
