package com.cosmiclogic.stashradar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** config/stashradar.json (LogicLib config: new fields are added automatically on update). */
public class StashRadarConfig {

    /** Search radius in blocks when the player doesn't pick one. */
    public int range = 16;
    /** Largest radius anyone may ask for. */
    public int maxRange = 32;
    /** Seconds between searches for one player (operators skip it). */
    public int cooldownSeconds = 5;
    /** Permission level needed for /find (0 = everyone, 2 = operators). */
    public int permissionLevel = 0;
    /**
     * Block ids that are searched. "shulker_boxes" means every shulker box color, "copper_chests" every copper
     * chest (Minecraft 1.21.9+). Modded containers work too.
     */
    public List<String> containers = new ArrayList<>(Arrays.asList(
            "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "shulker_boxes", "copper_chests"));
    /** Also look inside shulker box (and bundle) items stored in containers. */
    public boolean searchInsideItems = true;
    /** Also look at items shown in item frames and glow item frames. */
    public boolean searchItemFrames = true;
    /** Also tell the player how many they have in their own ender chest. */
    public boolean searchEnderChest = true;
    /** How long results are highlighted, and the outline color. */
    public int highlightSeconds = 12;
    public String highlightColor = "#3CFF7A";
    /** At most this many containers get outlines (the nearest ones). */
    public int maxHighlights = 16;
    /** Far-away results also get a glowing outline that shows through walls (only the searching player sees it). */
    public boolean glowThroughWalls = true;
    // ---- 1.2.0: the server's default look (players can choose their own with /find settings or the mod's screen)
    /** How results are outlined: "particles", "lines" (thin solid edges) or "off" (glow and chat only). */
    public String outlineStyle = "particles";
    /** dust (takes the color), end_rod, flame, soul_fire_flame, happy_villager, crit, glow, electric_spark. */
    public String particleType = "dust";
    /** Size of dust particles (0.3 - 4.0). */
    public double particleSize = 1.3;
    /** Blocks between particles along an edge (0.05 - 1.0; smaller is denser). */
    public double particleSpacing = 0.15;
    /** Color of the glow through walls (Minecraft shows the nearest of the 16 chat colors). */
    public String glowColor = "#FFFFFF";
    /** Color of the highlighted words in the chat answer. */
    public String chatColor = "#55FF55";
    /** Send the chat answer (turn off if players use the mod's HUD). */
    public boolean chatResults = true;
    /** Play a short sound when results come in (only the searching player hears it). */
    public boolean soundOnResults = false;
    /** Sound volume, 0 - 100. */
    public int soundVolume = 100;
    // ---- 1.2.0: the owner's limits
    /** How often outlines are redrawn, in milliseconds (250 - 5000). */
    public int updateMillis = 500;
    /** Most particles sent to one player per redraw, all outlines together. */
    public int maxParticlesPerUpdate = 480;
    /** Longest highlight a player may choose, in seconds. */
    public int maxHighlightSeconds = 60;
    /** Let players choose their own look (colors, particles, sounds). Off = everyone uses the settings above. */
    public boolean allowPlayerSettings = true;
    /** Adds the creator's server (Logic Survival) to the multiplayer list once, across all CosmicLogic mods. */
    public boolean addCreatorServer = true;
    /** Config layout version, used to upgrade older files. Don't change it. */
    public int configVersion = 0;
}
