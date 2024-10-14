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

	@ConfigItem(
			keyName = "npcsToTrack",
			name = "NPCs to Track",
			description = "Comma-separated list of NPC names to track (not case-sensitive)"
	)
	default String npcsToTrack() { return ""; }

	@ConfigItem(
			keyName = "npcsToTrack",
			name = "NPCs to Track",
			description = "Comma-separated list of NPC names to track"
	)
	void setNpcsToTrack(String npcsToTrack);
}
