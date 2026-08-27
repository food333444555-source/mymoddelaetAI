package com.df.mobvisualizer;

import java.util.Locale;

public final class MobColors {
    private MobColors() {}

    public static int forEntity(String type, int id, int maxId, MobOverlayConfig config,
                                 boolean hurt, boolean returned, boolean alert) {
        if (alert && config.alertEnabled) {
            return 0xFFFFD34E;
        }
        if (hurt && config.hurtEnabled) {
            return config.hurtColor;
        }
        if (returned && config.returnedEnabled) {
            return config.hurtStarColor;
        }
        Integer custom = customColor(type, config);
        if (custom != null) return custom;
        return forId(id, maxId, config);
    }

    public static int forId(int id, int maxId, MobOverlayConfig config) {
        Integer ruleColor = ruleColor(id, maxId, config.idColorRules, false);
        if (ruleColor != null) return ruleColor;
        ruleColor = ruleColor(id, maxId, config.percentColorRules, true);
        if (ruleColor != null) return ruleColor;
        if (id < config.purpleIdLimit) return config.purpleColor;
        if (maxId <= 0) return config.orangeColor;
        double percent = id * 100.0 / maxId;
        if (percent < config.darkRedPercent) return config.darkRedColor;
        if (percent < config.redPercent) return config.redColor;
        if (percent < config.darkOrangePercent) return config.darkOrangeColor;
        if (percent < config.orangePercent) return config.orangeColor;
        return 0xFF9E9E9E;
    }

    private static Integer ruleColor(int id, int maxId, String rules, boolean percentageOnly) {
        if (rules == null || rules.isBlank()) return null;
        double percent = maxId <= 0 ? 100.0 : id * 100.0 / maxId;
        int validRules = 0;
        for (String raw : rules.split(";")) {
            if (validRules >= 10) break;
            String[] pair = raw.trim().split("=", 2);
            if (pair.length != 2) continue;
            String condition = pair[0].trim().toLowerCase(Locale.ROOT).replace(" ", "");
            try {
                boolean percentRule = condition.startsWith("percent");
                if (percentageOnly != percentRule) continue;
                if (!percentRule && !condition.startsWith("id")) continue;
                double value = percentRule ? percent : id;
                String expression = percentRule ? condition.substring("percent".length())
                        : condition.substring("id".length());
                boolean matches;
                if (expression.startsWith("<=")) matches = value <= Double.parseDouble(expression.substring(2));
                else if (expression.startsWith(">=")) matches = value >= Double.parseDouble(expression.substring(2));
                else if (expression.startsWith("<")) matches = value < Double.parseDouble(expression.substring(1));
                else if (expression.startsWith(">")) matches = value > Double.parseDouble(expression.substring(1));
                else if (expression.startsWith("=")) matches = value == Double.parseDouble(expression.substring(1));
                else continue;
                if (!matches) continue;
                String hex = pair[1].trim().replace("#", "").replace("0x", "").replace("0X", "");
                validRules++;
                return parseColor(hex);
            } catch (NumberFormatException ignored) {}
            validRules++;
        }
        return null;
    }

    public static Integer customColor(String type, MobOverlayConfig config) {
        if (config.customMobColors != null && !config.customMobColors.isBlank()) {
            String normalizedType = type.toLowerCase(Locale.ROOT);
            for (String entry : config.customMobColors.split(",")) {
                String[] pair = entry.trim().split("=", 2);
                if (pair.length != 2 || !normalizedType.equals(pair[0].trim().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                try {
                    String value = pair[1].trim().replace("#", "").replace("0x", "").replace("0X", "");
                    return parseColor(value);
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    public static Integer chunkColor(int id, int maxId, MobOverlayConfig config) {
        Integer color = ruleColor(id, maxId, config.idColorRules, false);
        if (color != null) return color;
        color = ruleColor(id, maxId, config.percentColorRules, true);
        if (color != null) return color;
        return id < config.purpleIdLimit ? config.purpleColor : null;
    }

    private static int parseColor(String value) {
        if (value.length() != 6 && value.length() != 8) throw new NumberFormatException("color length");
        long parsed = Long.parseLong(value, 16);
        return value.length() == 6 ? (int) (0xFF000000L | parsed) : (int) parsed;
    }
}
