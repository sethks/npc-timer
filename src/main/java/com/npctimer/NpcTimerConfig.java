package com.npctimer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("Npc Timer")
public interface NpcTimerConfig extends Config
{
	@ConfigItem(
			keyName = "npcTimer",
			name = "Enable NPC Timer",
			description = "Enables the NPC Timer overlay",
			position = 0
	)
	default boolean npcTimer() { return true; }

	@ConfigItem(
			keyName = "showCurrentKillTime",
			name = "Show Current Kill Time",
			description = "Displays the current kill time",
			position = 1
	)
	default boolean showCurrentKillTime() { return true; }

	@ConfigItem(
			keyName = "showAverageKillTime",
			name = "Show Average Kill Time",
			description = "Displays the average kill time",
			position = 2
	)
	default boolean showAverageKillTime() { return true; }

	@ConfigItem(
			keyName = "showTotalKills",
			name = "Show Total Kills",
			description = "Displays the total number of kills",
			position = 3
	)
	default boolean showTotalKills() { return true; }

	@ConfigItem(
			keyName = "showPersonalBest",
			name = "Show Personal Best",
			description = "Displays the personal best kill time",
			position = 4
	)
	default boolean showPersonalBest() { return true; }

//	@ConfigItem(
//			keyName = "removeOnDeath",
//			name = "Remove overlay on NPC death",
//			description = "If enabled, removes the overlay when the NPC dies",
//			position = 5
//	)
//	default boolean removeOnDeath() { return false; }

	@ConfigItem(
			keyName = "npcsToTrack",
			name = "NPCs to Track",
			description = "Comma-separated list of NPC names to track. (not case sensitive)",
			position = 6
	)
	default String npcsToTrack() { return ""; }
}
