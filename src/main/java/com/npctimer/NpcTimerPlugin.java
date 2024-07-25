package com.npctimer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import javax.inject.Inject;
import javax.swing.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;

import java.lang.reflect.Type;
import java.util.*;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.events.NpcLootReceived;

import java.time.Duration;
import java.time.Instant;

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

	@Getter(AccessLevel.PACKAGE)
	private final List<NPC> targets = new ArrayList<>();

	private Map<String, NpcStats> npcStats = new HashMap<>();
	private NPC currentTarget;
	private Instant combatStartTime;
	private int currentTargetHealthRatio = -1;

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
	public void onNpcSpawned(NpcSpawned npcSpawned)
	{
		NPC npc = npcSpawned.getNpc();
		if (isTarget(npc))
		{
			targets.add(npc);
		}
	}

	@Subscribe
	public void onNpcLootReceived(final NpcLootReceived npcLootReceived)
	{
		final NPC npc = npcLootReceived.getNpc();
		final String name = npc.getName();

		if (isTrackedNpc(name))
		{
			confirmKill(npc);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		NPC npc = npcDespawned.getNpc();
		targets.remove(npc);
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
		if (target instanceof NPC && targets.contains((NPC) target))
		{
			NPC npc = (NPC) target;
			if (npc.equals(currentTarget))
			{
				currentTargetHealthRatio = npc.getHealthRatio();
				updateCombatTime();
				checkForKill(npc);
			}
		}
		else if (event.getActor() == client.getLocalPlayer() && client.getLocalPlayer().getInteracting() instanceof NPC)
		{
			NPC npc = (NPC) client.getLocalPlayer().getInteracting();
			if (targets.contains(npc) && npc.equals(currentTarget))
			{
				currentTargetHealthRatio = npc.getHealthRatio();
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
			currentTargetHealthRatio = npc.getHealthRatio();
			combatStartTime = Instant.now();
			inCombat = true;
		}
		updateCombatTime();
	}

	private void updateCombatTime()
	{
		lastCombatTime = Instant.now();
	}

	private void confirmKill(NPC npc)
	{
		String npcName = npc.getName();
		if (npcName == null) {
			return;
		}

		if (currentTarget != null && currentTarget.equals(npc))
		{
			NpcStats stats = npcStats.computeIfAbsent(npcName, k -> new NpcStats());
			long killTime = Duration.between(combatStartTime, Instant.now()).toMillis();
			stats.killCount++;
			stats.totalKillTime += killTime;
			stats.personalBest = Math.min(stats.personalBest, killTime);
			saveNpcStats();

			resetCombatState();
		}
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

	private boolean isTarget(NPC npc)
	{
		if (npc.getName() == null)
			return false;
		return isTrackedNpc(npc.getName());
	}

	private void loadNpcStats()
	{
		String json = configManager.getConfiguration(CONFIG_GROUP, STATS_KEY);
		if (json != null && !json.isEmpty())
		{
			Type type = new TypeToken<HashMap<String, NpcStats>>(){}.getType();
			npcStats = new Gson().fromJson(json, type);

			// Ensure all loaded stats have proper initial values
			for (NpcStats stats : npcStats.values())
			{
				if (stats.killCount == null) stats.killCount = 0;
				if (stats.totalKillTime == null) stats.totalKillTime = 0L;
				if (stats.personalBest == null) stats.personalBest = Long.MAX_VALUE;
			}
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
		if (player == null)
			return;

		NPC interacting = null;
		if (player.getInteracting() instanceof NPC)
		{
			interacting = (NPC) player.getInteracting();
		}

		if (interacting != null && targets.contains(interacting))
		{
			if (!inCombat || currentTarget == null || !currentTarget.equals(interacting))
			{
				resetCombatState();
				startCombat(interacting);
			}
			else
			{
				updateCombatTime();
				checkForKill(interacting);
			}
		}
		else if (inCombat)
		{
			checkCombatTimeout();
		}
	}

	private void checkForKill(NPC npc)
	{
		int newHealthRatio = npc.getHealthRatio();
		if (npc.equals(currentTarget) && newHealthRatio < currentTargetHealthRatio)
		{
			currentTargetHealthRatio = newHealthRatio;
		}
	}

	private void checkCombatTimeout()
	{
		if (lastCombatTime == null || Duration.between(lastCombatTime, Instant.now()).compareTo(COMBAT_TIMEOUT) > 0)
		{
			resetCombatState();
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
		currentTargetHealthRatio = -1;
		combatStartTime = null;
	}

	@Provides
	NpcTimerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NpcTimerConfig.class);
	}

	static class NpcStats
	{
		Integer killCount;
		Long totalKillTime;
		Long personalBest;

		NpcStats()
		{
			killCount = 0;
			totalKillTime = 0L;
			personalBest = Long.MAX_VALUE;
		}
	}
}
