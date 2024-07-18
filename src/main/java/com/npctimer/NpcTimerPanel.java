package com.npctimer;

import net.runelite.client.ui.PluginPanel;
import javax.swing.*;
import java.awt.*;

public class NpcTimerPanel extends PluginPanel
{
    private final NpcTimerPlugin plugin;
    private final NpcTimerConfig config;

    public NpcTimerPanel(NpcTimerPlugin plugin, NpcTimerConfig config)
    {
        this.plugin = plugin;
        this.config = config;

        setLayout(new BorderLayout());
        rebuild();
    }

    private void rebuild()
    {
        removeAll();

        JPanel npcPanel = new JPanel(new GridLayout(0, 1));
        for (String npcName : config.npcsToTrack().split(","))
        {
            npcName = npcName.trim();
            if (!npcName.isEmpty())
            {
                JButton resetButton = new JButton("Reset " + npcName);
                String finalNpcName = npcName;
                resetButton.addActionListener(e ->
                {
                    int confirm = JOptionPane.showConfirmDialog(this,
                            "Are you sure you want to reset stats for " + finalNpcName + "?",
                            "Confirm Reset",
                            JOptionPane.YES_NO_OPTION);
                    if (confirm == JOptionPane.YES_OPTION)
                    {
                        plugin.resetStatsForNpc(finalNpcName);
                        JOptionPane.showMessageDialog(this, "Stats for " + finalNpcName + " have been reset.");
                    }
                });
                npcPanel.add(resetButton);
            }
        }

        JScrollPane scrollPane = new JScrollPane(npcPanel);
        add(scrollPane, BorderLayout.CENTER);

        revalidate();
        repaint();
    }

    public void update()
    {
        SwingUtilities.invokeLater(this::rebuild);
    }
}
