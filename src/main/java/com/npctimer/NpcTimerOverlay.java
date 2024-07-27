package com.npctimer;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

import javax.inject.Inject;
import java.awt.*;

public class NpcTimerOverlay extends OverlayPanel
{
    private NpcTimerPlugin plugin;
    private NpcTimerConfig config;
    private Client client;

    @Inject
    public NpcTimerOverlay(NpcTimerPlugin plugin, NpcTimerConfig config, Client client)
    {
        super(plugin);
        setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
        this.plugin = plugin;
        this.config = config;
        this.client = client;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.npcTimer())
        {
            return null;
        }

        panelComponent.getChildren().clear();

        String currentNpcName = plugin.getCurrentNpcName();

        if (currentNpcName != null)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(currentNpcName)
                    .leftColor(Color.YELLOW)
                    .build());

            if (config.showCurrentKillTime() && plugin.isInCombat())
            {
                long currentKillTime = plugin.getCurrentKillTime();
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Current Time:")
                        .right(formatTime(currentKillTime))
                        .build());
            }

            NpcTimerPlugin.NpcStats stats = plugin.getNpcStats(currentNpcName);
            if (stats != null)
            {
                if (config.showAverageKillTime() && stats.killCount > 0)
                {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Average Time:")
                            .right(formatTime(stats.totalKillTime / stats.killCount))
                            .build());
                }

                if (config.showTotalKills())
                {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Total Kills:")
                            .right(String.valueOf(stats.killCount))
                            .build());
                }

                if (config.showPersonalBest() && stats.personalBest != Long.MAX_VALUE)
                {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("Personal Best:")
                            .right(formatTime(stats.personalBest))
                            .build());
                }
            }
        }
        else
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("No NPC selected")
                    .build());
        }

        return super.render(graphics);
    }

    private String formatTime(long milliseconds)
    {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

}
