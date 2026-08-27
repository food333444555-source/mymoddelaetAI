package ru.food333444555.mymoddelaetai.config;

import java.util.List;
import java.util.Map;

public class MobSettings {
    public boolean enabled = true;
    public boolean showHudButton = true;
    public boolean pinnedInSession = false; // stored in config if desired
    public boolean highlightThroughWalls = false;
    public String renderMode = "model"; // model | outline | box
    public String nameColor = "#FFFFFF";
    public String modelColor = "#FF0000";
    public float alpha = 0.6f;
    public boolean alertEnabled = true;
    public List<String> alertEvents; // e.g. spawn, setTarget, hurt
    public Map<String, String> stateColors; // state->hex

    public MobSettings() {}
}
