package com.npctimer;

import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;

import javax.inject.Inject;
import java.awt.*;
import net.runelite.client.util.ColorUtil;

public class NpcTimerOverlay extends Overlay
{
    private NpcTimerPlugin plugin;
    private NpcTimerConfig config;
    private Client client;

    private PanelComponent panelComponent = new PanelComponent();

    @Inject
    public NpcTimerOverlay(NpcTimerPlugin plugin, NpcTimerConfig config, Client client)
    {
        super(plugin);
        setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
        setLayer(OverlayLayer.ABOVE_SCENE);
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

        return panelComponent.render(graphics);
    }

    private String formatTime(long milliseconds)
    {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

}
