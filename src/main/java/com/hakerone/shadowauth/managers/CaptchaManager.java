package com.hakerone.shadowauth.managers;

import com.hakerone.shadowauth.ShadowAuth;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CaptchaManager {

    private final ShadowAuth plugin;
    private final Map<UUID, CaptchaData> activeCaptchas;
    private final Random random;

    public CaptchaManager(ShadowAuth plugin) {
        this.plugin = plugin;
        this.activeCaptchas = new ConcurrentHashMap<>();
        this.random = new Random();
    }

    public enum CaptchaType {
        MATH,
        TEXT,
        BOTH
    }

    public static class CaptchaData {
        public final String question;
        public final String answer;
        public final long expiresAt;
        public int attempts;

        public CaptchaData(String question, String answer, int timeoutSeconds) {
            this.question = question;
            this.answer = answer;
            this.expiresAt = System.currentTimeMillis() + (timeoutSeconds * 1000L);
            this.attempts = 0;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    public CaptchaData generateCaptcha() {
        CaptchaType type = getCaptchaType();
        
        switch (type) {
            case MATH:
                return generateMathCaptcha();
            case TEXT:
                return generateTextCaptcha();
            case BOTH:
            default:
                return random.nextBoolean() ? generateMathCaptcha() : generateTextCaptcha();
        }
    }

    private CaptchaType getCaptchaType() {
        String typeStr = plugin.getConfig().getString("settings.captcha.type", "MATH").toUpperCase();
        try {
            return CaptchaType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return CaptchaType.MATH;
        }
    }

    private CaptchaData generateMathCaptcha() {
        int a = random.nextInt(20) + 1;
        int b = random.nextInt(20) + 1;
        int result = a + b;
        String question = plugin.colorize(plugin.getConfig().getString(
            "settings.captcha.messages.math", "&e&lРешите: {question}"))
            .replace("{question}", a + " + " + b + " = ?");
        return new CaptchaData(question, String.valueOf(result), getTimeout());
    }

    private CaptchaData generateTextCaptcha() {
        String[] codes = {"A7K9", "X3M2", "P5L8", "B4N6", "Q1W9", "Z8Y3", "R2T7", "M6K4"};
        String code = codes[random.nextInt(codes.length)];
        String question = plugin.colorize(plugin.getConfig().getString(
            "settings.captcha.messages.text", "&e&lВведите код: {code}"))
            .replace("{code}", code);
        return new CaptchaData(question, code, getTimeout());
    }

    private int getTimeout() {
        return plugin.getConfig().getInt("settings.captcha.timeout", 60);
    }

    public void showCaptcha(Player player) {
        CaptchaData captcha = generateCaptcha();
        activeCaptchas.put(player.getUniqueId(), captcha);
        player.sendMessage(captcha.question);
    }

    public boolean checkCaptcha(Player player, String input) {
        CaptchaData captcha = activeCaptchas.get(player.getUniqueId());
        
        if (captcha == null) {
            return true; // No captcha active
        }

        if (captcha.isExpired()) {
            activeCaptchas.remove(player.getUniqueId());
            player.sendMessage(plugin.getPrefix() + plugin.colorize(
                plugin.getConfig().getString("settings.captcha.messages.timeout", "&c&lВремя вышло!")));
            return false;
        }

        captcha.attempts++;
        
        if (input.equalsIgnoreCase(captcha.answer)) {
            activeCaptchas.remove(player.getUniqueId());
            player.sendMessage(plugin.getPrefix() + plugin.colorize(
                plugin.getConfig().getString("settings.captcha.messages.success", "&a&lКапча пройдена!")));
            return true;
        }

        int maxAttempts = plugin.getConfig().getInt("settings.captcha.max_attempts", 3);
        if (captcha.attempts >= maxAttempts) {
            activeCaptchas.remove(player.getUniqueId());
            player.sendMessage(plugin.getPrefix() + plugin.colorize(
                plugin.getConfig().getString("settings.captcha.messages.failed", "&c&lНеверно! Попробуйте снова.")));
            showCaptcha(player); // Show new captcha
            return false;
        }

        player.sendMessage(plugin.getPrefix() + plugin.colorize(
            plugin.getConfig().getString("settings.captcha.messages.failed", "&c&lНеверно! Попробуйте ещё раз.")));
        return false;
    }

    public boolean hasActiveCaptcha(Player player) {
        CaptchaData captcha = activeCaptchas.get(player.getUniqueId());
        return captcha != null && !captcha.isExpired();
    }

    public void removeCaptcha(Player player) {
        activeCaptchas.remove(player.getUniqueId());
    }

    public boolean shouldShowCaptcha(int failedAttempts) {
        if (!plugin.getConfig().getBoolean("settings.captcha.enabled", false)) {
            return false;
        }
        
        int afterAttempts = plugin.getConfig().getInt("settings.captcha.after_attempts", 0);
        return afterAttempts == 0 || failedAttempts >= afterAttempts;
    }
}
