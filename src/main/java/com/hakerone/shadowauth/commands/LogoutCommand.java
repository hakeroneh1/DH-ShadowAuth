package com.hakerone.shadowauth.commands;

import com.hakerone.shadowauth.ShadowAuth;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LogoutCommand implements CommandExecutor {

    private final ShadowAuth plugin;

    public LogoutCommand(ShadowAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только для игроков!");
            return true;
        }

        if (!plugin.getDatabaseManager().isAuthenticated(player.getUniqueId())) {
            player.sendMessage(plugin.getMessage("need_auth"));
            return true;
        }

        // Сбрасываем сессию в памяти и удаляем из БД
        plugin.getDatabaseManager().setAuthenticated(player.getUniqueId(), false);
        plugin.getDatabaseManager().removeSession(player.getUniqueId());
        // Очищаем сохраненные данные
        plugin.getDatabaseManager().clearSavedData(player.getUniqueId());
        
        // Кикаем игрока с сообщением
        player.kickPlayer(plugin.getPrefix() + plugin.colorize("&e&lВы вышли из аккаунта. Пожалуйста, войдите снова."));

        return true;
    }
}
