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
    /** Block ids that are searched. "shulker_boxes" means every shulker box color. Modded containers work too. */
    public List<String> containers = new ArrayList<>(Arrays.asList(
            "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "shulker_boxes"));
    /** Also look inside shulker box (and bundle) items stored in containers. */
    public boolean searchInsideItems = true;
    /** How long results are highlighted, and the outline color. */
    public int highlightSeconds = 12;
    public String highlightColor = "#3CFF7A";
    /** At most this many containers get outlines (the nearest ones). */
    public int maxHighlights = 16;
    /** Adds the creator's server (Logic Survival) to the multiplayer list once, across all CosmicLogic mods. */
    public boolean addCreatorServer = true;
}
