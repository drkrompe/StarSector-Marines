package com.dillon.starsectormarines.battle.ui.debug;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.StarsectorMarinesModPlugin;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

/** Writes the current battle's canonical commander/referee JSONL trace. */
@DebugOnly
public final class CommanderTraceDumper {

    private static final Logger LOG = Logger.getLogger(CommanderTraceDumper.class);
    private CommanderTraceDumper() { }

    public static String dump(BattleSimulation sim) {
        if (sim == null) return null;
        try {
            String path = StarsectorMarinesModPlugin.MOD_ID
                    + "/debug/commander_trace_tick_"
                    + sim.getSimTickIndex() + ".jsonl";
            Global.getSettings().writeTextFileToCommon(path,
                    sim.getCommandTraceJsonLines());
            LOG.info("CommanderTraceDumper: wrote trace to saves/common/" + path);
            return path;
        } catch (Exception ex) {
            LOG.warn("CommanderTraceDumper: dump failed", ex);
            return null;
        }
    }
}
