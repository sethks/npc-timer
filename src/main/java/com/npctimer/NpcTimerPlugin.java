package com.npctimer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.*;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;

import java.lang.reflect.Type;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.api.events.InteractingChanged;
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
	name = "NPC Timer"
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
	private NpcTimerConfig npcTimerConfig;

	@Inject
	private ScheduledExecutorService executorService;

	@Inject
	private ConfigManager configManager;


	private HashMap<String, NpcStats> npcStats = new HashMap<>();
	@Getter
    private boolean inCombat = false;
	private Actor lastCombatActor = null;
	private Instant lastCombatTime = null;
	private Instant currentKillStartTime;

	private static final Duration COMBAT_TIMEOUT = Duration.ofSeconds(10);
	private static final String CONFIG_GROUP = "Npc Timer";
	private static final String STATS_KEY = "npcStats";

	private boolean hasHitNpc = false;

	private String lastKilledNpcName = null;

	private int currentNpcId = -1;
	private int currentNpcHp = -1;
	private Instant lastDisengageTime = null;
	private static final Duration DISENGAGE_TIMEOUT = Duration.ofSeconds(5);

	static class NpcStats
	{
		int killCount = 0;
		long totalKillTime = 0;
		long personalBest = Long.MAX_VALUE;

		// Add a constructor for deserialization
		NpcStats() {}
	}

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(npcTimerOverlay);
		loadNpcStats();
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(npcTimerOverlay);
		saveNpcStats();
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!(event.getActor() instanceof NPC) || !inCombat)
		{
			return;
		}

		NPC npc = (NPC) event.getActor();
		if (npc.getId() == currentNpcId && !hasHitNpc)
		{
			hasHitNpc = true;
			currentKillStartTime = Instant.now();
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (event.getSource() != client.getLocalPlayer())
		{
			return;
		}

		Actor target = event.getTarget();
		if (target instanceof NPC)
		{
			NPC npc = (NPC) target;
			String npcName = npc.getName();
			if (isTrackedNpc(npcName))
			{
				int npcId = npc.getId();
				int npcHp = npc.getHealthRatio();

//				log.debug("Interacting with NPC: " + npcName + ", ID: " + npcId + ", HP: " + npcHp);

				if (npcId != currentNpcId || (npcHp != currentNpcHp && Duration.between(lastDisengageTime, Instant.now()).compareTo(DISENGAGE_TIMEOUT) > 0))
				{
//					log.debug("New NPC or re-engaged after timeout");
					inCombat = true;
					currentNpcId = npcId;
					currentNpcHp = npcHp;
					lastCombatActor = target;
					lastCombatTime = Instant.now();
					hasHitNpc = false;
					currentKillStartTime = null;
				}
				else
				{
//					log.debug("Same NPC, updating combat time");
					lastCombatTime = Instant.now();
				}
				lastDisengageTime = null;
			}
		}
		else
		{
//			log.debug("Disengaged from NPC");
			lastDisengageTime = Instant.now();
			checkCombatTimeout();
		}
	}


	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		if (npc.getId() == currentNpcId && npc.isDead())
		{
			recordKill(npc.getName());
			inCombat = false;
			currentNpcId = -1;
			currentNpcHp = -1;
			lastCombatActor = null;
			currentKillStartTime = null;
			hasHitNpc = false;
			saveNpcStats();
		}
	}

	private void checkCombatTimeout()
	{
		if (!inCombat) return;

		if (lastCombatTime != null && Duration.between(lastCombatTime, Instant.now()).compareTo(COMBAT_TIMEOUT) > 0)
		{
			inCombat = false;
			currentNpcId = -1;
			currentNpcHp = -1;
			lastCombatActor = null;
			lastCombatTime = null;
			currentKillStartTime = null;
			hasHitNpc = false;
		}
	}

	private void recordKill(String npcName)
	{
		NpcStats stats = npcStats.computeIfAbsent(npcName, k -> new NpcStats());
		long killTime = Duration.between(currentKillStartTime, Instant.now()).toMillis();
		stats.killCount++;
		stats.totalKillTime += killTime;
		stats.personalBest = Math.min(stats.personalBest, killTime);
		saveNpcStats();
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
		if (currentKillStartTime != null && hasHitNpc)
		{
			return Duration.between(currentKillStartTime, Instant.now()).toMillis();
		}
		else if (inCombat && lastCombatTime != null)
		{
			return Duration.between(lastCombatTime, Instant.now()).toMillis();
		}
		return 0;
	}

	public String getCurrentNpcName()
	{
		return inCombat ? (lastCombatActor != null ? lastCombatActor.getName() : null) : lastKilledNpcName;
	}

	public NpcStats getNpcStats(String npcName)
	{
		NpcStats stats = npcStats.get(npcName);
		return stats;
	}

	private boolean isTrackedNpc(String npcName)
	{
		String[] trackedNpcs = config.npcsToTrack().split(",");
		for (String trackedNpc : trackedNpcs)
		{
			if (trackedNpc.trim().equalsIgnoreCase(npcName))
			{
				return true;
			}
		}
		return false;
	}

	@Provides
	NpcTimerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NpcTimerConfig.class);
	}
}
