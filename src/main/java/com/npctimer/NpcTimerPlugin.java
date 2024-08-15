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

import com.npctimer.PotentialKill;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

	@Inject
	private Gson gson;

	private NpcTimerPanel panel;
	private NavigationButton navButton;

	@Getter(AccessLevel.PACKAGE)
	private final List<NPC> targets = new ArrayList<>();

	private Map<String, NpcStats> npcStats = new HashMap<>();
	private Queue<PotentialKill> potentialKills = new LinkedList<>();
	private NPC currentTarget;
	private Instant combatStartTime;
	private int currentTargetHealthRatio = -1;
//	private Instant lastKillTime;
	private long lastKillTime;
	private long lastKillDuration;

	@Getter
	private boolean inCombat = false;

	private static final String CONFIG_GROUP = "NpcTimer";
	private static final String STATS_KEY = "npcStats";
	private static final Duration COMBAT_TIMEOUT = Duration.ofSeconds(10);
	private static final long KILL_CONFIRMATION_THRESHOLD = 5000;
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

		if (isTrackedNpc(name) && lastKillTime > 0)
		{
			// Confirm the kill and update stats
			System.out.println(name + ": " + lastKillTime);
			updateNpcStats(name, lastKillTime, true);
			lastKillTime = 0;
		}
	}

//	@Subscribe
//	public void onNpcLootReceived(final NpcLootReceived npcLootReceived)
//	{
//		final NPC npc = npcLootReceived.getNpc();
//		final String name = npc.getName();
//
//		if (isTrackedNpc(name))
//		{
//			long killTime;
//			boolean usePotentialKill = false;
//
//			PotentialKill kill = findPotentialKill(npc);
//			if (kill != null)
//			{
//				killTime = Duration.between(kill.startTime, Instant.now()).toMillis();
//				usePotentialKill = true;
//			}
//			else
//				killTime = 0;
//
//
//			// Only update stats if the kill time is reasonable (e.g., more than 3 seconds)
//			if (killTime > 3000)
//			{
//				System.out.println("NPC Tracked");
//				System.out.println("NPC Name: " + name + '\n' + "Kill Time: " + killTime);
//				updateNpcStats(name, killTime, true);
//			}
//			else
//			{
//				System.out.println("NPC Not Tracked");
//				System.out.println("NPC Name: " + name + '\n' + "Kill Time: " + killTime);
//				updateNpcStats(name, 0, false);
//			}
//			potentialKills.removeIf(pk -> pk.npc.getName().equals(name));
//		}
//	}

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

		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
		long currentKillTime = getCurrentKillTime();

		// If current kill time is 0, and we had a non-zero last kill time,
		// it means we've just finished a kill or switched targets
		if (currentKillTime == 0 && lastKillTime > 0)
		{
			// Store the last kill time for potential confirmation
			long potentialKillTime = lastKillTime;

			// Schedule a task to clear this potential kill if not confirmed
			scheduler.schedule(() -> {
				if (lastKillTime == potentialKillTime)
				{
					lastKillTime = 0;
				}
			}, KILL_CONFIRMATION_THRESHOLD, TimeUnit.MILLISECONDS);
		}

		lastKillTime = currentKillTime;
		System.out.println("lastKillTime: " + lastKillTime);
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

	private PotentialKill findPotentialKill(NPC npc)
	{
		PotentialKill oldestKill = null;
		for (PotentialKill kill : potentialKills)
		{
			if (kill.npc.getName().equals(npc.getName()))
				if (oldestKill == null || kill.startTime.isBefore(oldestKill.startTime))
					oldestKill = kill;
		}
		return oldestKill;
	}

	private void startCombat(NPC npc)
	{
		String npcName = npc.getName();
		if (npcName == null)
			return;

		Instant startTime = Instant.now();
		potentialKills.offer(new PotentialKill(npc, startTime));

		if (!inCombat || currentTarget == null || !npcName.equals(currentTarget.getName()))
		{
			currentTarget = npc;
			currentTargetHealthRatio = npc.getHealthRatio();
			combatStartTime = startTime;
			inCombat = true;
		}
		lastCombatTime = startTime;
		lastKillDuration = 0;  // Reset the last kill duration
	}

	private void updateCombatTime()
	{
		Instant now = Instant.now();
		if (combatStartTime != null)
			lastKillDuration = Duration.between(combatStartTime, now).toMillis();

//		lastKillTime = now;
		lastCombatTime = now;
	}

	private boolean isTrackedNpc(String npcName)
	{
		if (npcName == null)
			return false;

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
			npcStats = gson.fromJson(json, type);
		}
	}

	private void saveNpcStats()
	{
		String json = gson.toJson(npcStats);
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

	private long getRandomizedAverageTime(long averageTime)
	{
		// Create a range of +/- 3 seconds (3000 milliseconds)
		long minTime = Math.max(1000, averageTime - 3000); // Ensure it's at least 1 second
		long maxTime = averageTime + 3000;
		return minTime + (long) (Math.random() * (maxTime - minTime));
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

	private void updateNpcStats(String npcName, long killTime, boolean confirmKill)
	{
		NpcStats stats = npcStats.computeIfAbsent(npcName, k -> new NpcStats());

		if (confirmKill)
		{
			stats.killCount++;
			stats.totalKillTime += killTime;
			if (killTime < stats.personalBest || stats.personalBest == 0)
			{
				stats.personalBest = killTime;
			}
			saveNpcStats();
		}

		else
		{
			System.out.println("Kill not confirmed...");
		}
	}

//	private void updateNpcStats(String npcName, long killTime, boolean usePotentialKill)
//	{
//		NpcStats stats = npcStats.computeIfAbsent(npcName, k -> {
//			return new NpcStats();
//		});
//
//		stats.killCount++;
//
//		if (usePotentialKill && killTime > 3000)  // Only consider kill times over 3 seconds for PB
//		{
//			stats.totalKillTime += killTime;
//			if (killTime < stats.personalBest || stats.personalBest == 0)
//				stats.personalBest = killTime;
//		}
//		else
//		{
//			long averageTime = stats.killCount > 1 ? stats.totalKillTime / (stats.killCount - 1) : 30000;
//			long randomizedTime = getRandomizedAverageTime(averageTime);
//			stats.totalKillTime += randomizedTime;
//		}
//		saveNpcStats();
//	}

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
		potentialKills.clear();
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
