package ru.food333444555.mymoddelaetai.util;

public class ColorUtil {
    // Convert hex color like #RRGGBB or RRGGBB to float[4] RGBA (0..1)
    public static float[] hexToRGBA(String hex, float alphaFallback) {
        if (hex == null) hex = "#FFFFFF";
        hex = hex.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        try {
            int val = Integer.parseInt(hex, 16);
            int r = (val >> 16) & 0xFF;
            int g = (val >> 8) & 0xFF;
            int b = val & 0xFF;
            return new float[]{r / 255f, g / 255f, b / 255f, alphaFallback};
        } catch (NumberFormatException e) {
            return new float[]{1f,1f,1f,alphaFallback};
        }
    }
}
