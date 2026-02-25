package com.hakerone.shadowauth;

import com.hakerone.shadowauth.commands.LoginCommand;
import com.hakerone.shadowauth.commands.LogoutCommand;
import com.hakerone.shadowauth.commands.RegisterCommand;
import com.hakerone.shadowauth.listeners.PlayerListener;
import com.hakerone.shadowauth.managers.CaptchaManager;
import com.hakerone.shadowauth.managers.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ShadowAuth extends JavaPlugin {

    private static ShadowAuth instance;
    private DatabaseManager databaseManager;
    private CaptchaManager captchaManager;

    @Override
    public void onEnable() {
        instance = this;
        
        saveDefaultConfig();
        
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
        
        captchaManager = new CaptchaManager(this);
        
        getCommand("register").setExecutor(new RegisterCommand(this));
        getCommand("login").setExecutor(new LoginCommand(this));
        getCommand("logout").setExecutor(new LogoutCommand(this));
        
        PlayerListener listener = new PlayerListener(this);
        listener.register();
        
        getLogger().info("ShadowAuth enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("ShadowAuth disabled!");
    }

    public static ShadowAuth getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public CaptchaManager getCaptchaManager() {
        return captchaManager;
    }

    public String getMessage(String key) {
        String message = getConfig().getString("settings.messages." + key, key);
        return colorize(message);
    }

    public String getPrefix() {
        return colorize(getConfig().getString("settings.messages.prefix", "&8[&6&lShadow&8&lAuth&8] "));
    }

    public String colorize(String message) {
        return message.replace("&", "§");
    }

    public int getSessionHours() {
        return getConfig().getInt("settings.session.max_age_hours", 15);
    }
}
