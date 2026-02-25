package com.hakerone.shadowauth.listeners;

import com.hakerone.shadowauth.ShadowAuth;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;

public class PlayerListener implements Listener {

    private final ShadowAuth plugin;

    public PlayerListener(ShadowAuth plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        // Задача для проверки неавторизованных игроков
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (!plugin.getDatabaseManager().isAuthenticated(player.getUniqueId())) {
                    player.setGameMode(GameMode.ADVENTURE);
                    player.setInvulnerable(true);
                    player.setAllowFlight(false);
                    player.setFlying(false);
                    player.setHealth(player.getMaxHealth());
                    player.setFoodLevel(20);
                    player.setSaturation(20);
                    player.setExhaustion(0);
                }
            }
        }, 20L, 20L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String ip = player.getAddress().getAddress().getHostAddress();

        // Проверяем сессию в БД
        if (plugin.getDatabaseManager().hasDatabaseSession(player.getUniqueId(), ip)) {
            plugin.getDatabaseManager().setAuthenticated(player.getUniqueId(), true);
            player.sendMessage(plugin.getPrefix() + plugin.getMessage("login_success"));
            restorePlayer(player);
            // Восстанавливаем эффекты если были сохранены
            plugin.getDatabaseManager().restoreEffects(player.getUniqueId(), player);
            // Телепортация на спавн или место выхода
            teleportAfterAuth(player);
            return;
        }

        // Сессия истекла или IP не совпадает - нужно авторизоваться
        // Сбрасываем флаг авторизации в памяти
        plugin.getDatabaseManager().setAuthenticated(player.getUniqueId(), false);

        // Сохраняем эффекты игрока (заморозка)
        plugin.getDatabaseManager().saveEffects(player.getUniqueId(), new ArrayList<>(player.getActivePotionEffects()));
        // Сохраняем локацию выхода
        plugin.getDatabaseManager().saveLocation(player.getUniqueId(), player.getLocation());

        // Удаляем все эффекты
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Телепортация в комнату авторизации если включено
        if (plugin.getConfig().getBoolean("settings.auth_room.enabled", false)) {
            org.bukkit.World world = plugin.getServer().getWorld(
                plugin.getConfig().getString("settings.auth_room.world", "world"));
            if (world != null) {
                double x = plugin.getConfig().getDouble("settings.auth_room.x", 0);
                double y = plugin.getConfig().getDouble("settings.auth_room.y", 100);
                double z = plugin.getConfig().getDouble("settings.auth_room.z", 0);
                float yaw = (float) plugin.getConfig().getDouble("settings.auth_room.yaw", 0);
                float pitch = (float) plugin.getConfig().getDouble("settings.auth_room.pitch", 0);
                player.teleport(new org.bukkit.Location(world, x, y, z, yaw, pitch));
            }
        }

        // Игрок не авторизован - применяем ограничения
        player.setGameMode(GameMode.ADVENTURE);
        player.setInvulnerable(true);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setExhaustion(0);
        
        // Добавляем невидимость
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.INVISIBILITY,
            Integer.MAX_VALUE, 0, false, false));

        if (plugin.getDatabaseManager().isRegistered(player.getName())) {
            player.sendMessage(plugin.getPrefix() + plugin.colorize("&e&lВведите: &a/l <пароль>"));
        } else {
            player.sendMessage(plugin.getPrefix() + plugin.colorize("&e&lВведите: &a/reg <пароль>"));
        }
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getDatabaseManager().isAuthenticated(player.getUniqueId())) {
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                event.getFrom().getBlockZ() != event.getTo().getBlockZ() ||
                event.getFrom().getBlockY() != event.getTo().getBlockY()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getDatabaseManager().isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getDatabaseManager().isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getDatabaseManager().isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!plugin.getDatabaseManager().isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (!plugin.getDatabaseManager().isAuthenticated(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (!plugin.getDatabaseManager().isAuthenticated(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (!plugin.getDatabaseManager().isAuthenticated(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            if (!plugin.getDatabaseManager().isAuthenticated(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
        if (event.getEntity() instanceof Player player) {
            if (!plugin.getDatabaseManager().isAuthenticated(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (!plugin.getDatabaseManager().isAuthenticated(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String msg = event.getMessage().toLowerCase();

        if (!plugin.getDatabaseManager().isAuthenticated(player.getUniqueId())) {
            if (!msg.startsWith("/l ") && !msg.startsWith("/login ") &&
                !msg.startsWith("/reg ") && !msg.startsWith("/register ")) {
                event.setCancelled(true);
                player.sendMessage(plugin.getPrefix() + plugin.getMessage("need_auth"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!plugin.getDatabaseManager().isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDropItem(PlayerDropItemEvent event) {
        if (!plugin.getDatabaseManager().isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickupItem(PlayerAttemptPickupItemEvent event) {
        if (!plugin.getDatabaseManager().isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!plugin.getDatabaseManager().isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!plugin.getDatabaseManager().isAuthenticated(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        // Очищаем сохраненные данные при выходе
        plugin.getDatabaseManager().clearSavedData(event.getPlayer().getUniqueId());
    }

    private void restorePlayer(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
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
