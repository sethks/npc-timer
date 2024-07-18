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
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

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
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(npcTimerOverlay);
		saveNpcStats();
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (client.getLocalPlayer() == null)
			return;

		updateCombatState();
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (event.getSource() != client.getLocalPlayer())
			return;

		if (event.getTarget() instanceof NPC)
		{
			NPC npc = (NPC) event.getTarget();
			if (isTrackedNpc(npc.getName()))
			{
				startCombat(npc);
			}
		}
		else if (inCombat)
		{
			// Player interacted with something else, start timeout
			updateCombatTime();
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!(event.getActor() instanceof NPC))
			return;

		NPC npc = (NPC) event.getActor();
		if (isTrackedNpc(npc.getName()))
		{
			updateCombatTime();
			if (npc.getHealthRatio() == 0)
			{
				recordKill(npc.getName());
			}
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		if (npc == currentTarget && npc.isDead())
		{
			recordKill(npc.getName());
		}
	}

	private void startCombat(NPC npc)
	{
		if (currentTarget == null || !currentTarget.equals(npc))
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

	private void checkCombatTimeout()
	{
		if (lastCombatTime == null || Duration.between(lastCombatTime, Instant.now()).compareTo(COMBAT_TIMEOUT) > 0)
		{
			inCombat = false;
			currentTarget = null;
			combatStartTime = null;
		}
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
		saveNpcStats();

		inCombat = false;
		currentTarget = null;
		combatStartTime = null;
	}

	private boolean isTrackedNpc(String npcName)
	{
		return Arrays.stream(config.npcsToTrack().split(","))
				.map(String::trim)
				.anyMatch(npcName::equalsIgnoreCase);
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
		Player player = client.getLocalPlayer();
		Actor target = player.getInteracting();

		if (target instanceof NPC && isTrackedNpc(((NPC) target).getName()))
		{
			if (!inCombat || currentTarget == null || !currentTarget.equals(target))
			{
				startCombat((NPC) target);
			}
			else
			{
				updateCombatTime();
			}
		}
		else if (inCombat)
		{
			checkCombatTimeout();
		}
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
