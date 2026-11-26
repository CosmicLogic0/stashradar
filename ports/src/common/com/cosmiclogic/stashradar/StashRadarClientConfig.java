package com.cosmiclogic.stashradar;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * config/stashradar-client.json: settings that only live on the player's own game (1.2.0). Checked by
 * {@link StashRadarSettings#CLIENT}; the player's highlight look is sent to each server that has Stash Radar.
 */
public class StashRadarClientConfig {
    /** A small results panel on screen while a search is highlighted. */
    public boolean hudEnabled = false;
    /** top_left, top_right, bottom_left or bottom_right. */
    public String hudCorner = "top_left";
    public int hudOffsetX = 4;
    public int hudOffsetY = 4;
    /** 0.5 - 3.0 */
    public double hudScale = 1.0;
    /** Background see-through level: 0 = clear, 100 = solid. */
    public int hudOpacity = 50;
    public boolean hudBackground = true;
    /** One short line instead of several. */
    public boolean hudCompact = false;
    public boolean hudShowItem = true;
    public boolean hudShowCount = true;
    public boolean hudShowNearest = true;
    public boolean hudShowTime = true;
    public String hudTextColor = "#FFFFFF";
    public String hudAccentColor = "#3CFF7A";
    public String hudBackgroundColor = "#000000";
    /** Keep a list of recent searches in the search screen. */
    public boolean rememberSearches = true;
    /**
     * My highlight look (keys from StashRadarSettings.LOOK, e.g. "highlightColor": "#FF8800"). Empty = the server's
     * defaults. Sent to servers that have Stash Radar 1.2.0+ when joining and when saved.
     */
    public Map<String, String> look = new LinkedHashMap<>();
}
