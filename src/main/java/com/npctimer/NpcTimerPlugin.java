package com.npctimer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.*;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;

@Slf4j
@PluginDescriptor(
	name = "NPC Combat Timer"
)
public class NpcTimerPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private NpcTimerConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private NpcTimerOverlay npcTimerOverlay;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	private NpcTimerPanel panel;
	private NavigationButton navButton;

	private Map<String, NpcStats> npcStats = new HashMap<>();
	private NPC currentTarget;
	private Instant combatStartTime;

	@Getter
	private boolean inCombat = false;

	private static final String CONFIG_GROUP = "NpcTimer";
	private static final String STATS_KEY = "npcStats";
	private static final Duration COMBAT_TIMEOUT = Duration.ofSeconds(10);
	private Instant lastCombatTime;

	@Override
	protected void startUp()
	{
		overlayManager.add(npcTimerOverlay);
		loadNpcStats();
		panel = new NpcTimerPanel(this, config);
		navButton = NavigationButton.builder()
				.tooltip("NPC Timer")
				.icon(ImageUtil.loadImageResource(getClass(), "/icon.png"))
				.priority(5)
				.panel(panel)
				.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		saveNpcStats();
		overlayManager.remove(npcTimerOverlay);
		clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		NPC npc = npcDespawned.getNpc();
		if (inCombat && npc.equals(currentTarget))
		{
			recordKill(npc.getName());
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (client.getLocalPlayer() == null)
			return;

		updateCombatState();
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor target = event.getActor();

		if (target instanceof NPC || (event.getActor() == client.getLocalPlayer() && client.getLocalPlayer().getInteracting() instanceof NPC))
		{
			NPC npc = (target instanceof NPC) ? (NPC) target : (NPC) client.getLocalPlayer().getInteracting();
			String npcName = npc.getName();

			if (npcName != null && isTrackedNpc(npcName))
			{
				if (!inCombat || currentTarget == null || !currentTarget.equals(npc))
				{
					startCombat(npc);
				}
				updateCombatTime();
				checkForKill(npc);
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals("NpcTimer"))
		{
			SwingUtilities.invokeLater(() -> {
				panel.update();
				loadNpcStats();
			});
		}
	}

	private void startCombat(NPC npc)
	{
		String npcName = npc.getName();
		if (npcName == null) {
			return; // Skip if the NPC name is null
		}

		if (!inCombat || currentTarget == null || !npcName.equals(currentTarget.getName()))
		{
			currentTarget = npc;
			combatStartTime = Instant.now();
			inCombat = true;
		}
		updateCombatTime();
	}

	private void updateCombatTime()
	{
		lastCombatTime = Instant.now();
	}

	private void recordKill(String npcName)
	{
		if (combatStartTime == null)
			return;

		NpcStats stats = npcStats.computeIfAbsent(npcName, k -> new NpcStats());
		long killTime = Duration.between(combatStartTime, Instant.now()).toMillis();
		stats.killCount++;
		stats.totalKillTime += killTime;
		stats.personalBest = Math.min(stats.personalBest, killTime);
		saveNpcStats();  // Save after each kill

		resetCombatState();
	}

	private boolean isTrackedNpc(String npcName)
	{
		if (npcName == null) {
			return false;
		}
		return Arrays.stream(config.npcsToTrack().split(","))
				.map(String::trim)
				.map(String::toLowerCase)
				.anyMatch(npcName.toLowerCase()::equals);
	}

	private void loadNpcStats()
	{
		String json = configManager.getConfiguration(CONFIG_GROUP, STATS_KEY);
		if (json != null && !json.isEmpty())
		{
			Type type = new TypeToken<HashMap<String, NpcStats>>(){}.getType();
			npcStats = new Gson().fromJson(json, type);
		}
	}

	private void saveNpcStats()
	{
		String json = new Gson().toJson(npcStats);
		configManager.setConfiguration(CONFIG_GROUP, STATS_KEY, json);
	}

	public long getCurrentKillTime()
	{
		return combatStartTime != null ? Duration.between(combatStartTime, Instant.now()).toMillis() : 0;
	}

	public String getCurrentNpcName()
	{
		return currentTarget != null ? currentTarget.getName() : null;
	}

	public NpcStats getNpcStats(String npcName)
	{
		return npcStats.get(npcName);
	}

	private void updateCombatState()
	{
		if (inCombat && currentTarget != null)
		{
			if (currentTarget.getHealthRatio() == 0 || currentTarget.isDead() || currentTarget.getHealthRatio() == 0)
				recordKill(currentTarget.getName());

			else if (Duration.between(lastCombatTime, Instant.now()).compareTo(COMBAT_TIMEOUT) > 0)
				resetCombatState();
		}
	}

	private void checkForKill(NPC npc)
	{
		if (npc.equals(currentTarget) && (npc.getHealthRatio() == 0 || npc.isDead() || npc.getHealthRatio() == 0))
		{
			recordKill(npc.getName());
		}
	}

	public void resetStatsForNpc(String npcName)
	{
		String normalizedName = npcName.trim().toLowerCase();
		for (Map.Entry<String, NpcStats> entry : new HashMap<>(npcStats).entrySet())
		{
			if (entry.getKey().toLowerCase().equals(normalizedName))
			{
				npcStats.remove(entry.getKey());
			}
		}
		saveNpcStats();
	}

	private void resetCombatState()
	{
		inCombat = false;
		currentTarget = null;
		combatStartTime = null;
	}

	@Provides
	NpcTimerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NpcTimerConfig.class);
	}

	static class NpcStats
	{
		int killCount = 0;
		long totalKillTime = 0;
		long personalBest = Long.MAX_VALUE;
	}
}
