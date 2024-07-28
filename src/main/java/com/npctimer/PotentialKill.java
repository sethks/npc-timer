package com.npctimer;

import net.runelite.api.NPC;
import java.time.Instant;

public class PotentialKill
{
    NPC npc;
    Instant startTime;

    PotentialKill(NPC npc, Instant startTime)
    {
        this.npc = npc;
        this.startTime = startTime;
    }
}
