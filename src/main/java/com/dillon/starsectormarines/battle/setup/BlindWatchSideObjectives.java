package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.objective.side.Ground;
import com.dillon.starsectormarines.battle.objective.side.SideField.Who;
import com.dillon.starsectormarines.battle.objective.side.SideObjective;
import com.dillon.starsectormarines.battle.objective.side.SideObjective.Discovery;
import com.dillon.starsectormarines.battle.objective.side.SideObjective.Effect;
import com.dillon.starsectormarines.battle.objective.side.SideObjective.Hook;
import com.dillon.starsectormarines.battle.objective.side.SideObjective.Return;
import com.dillon.starsectormarines.battle.objective.side.SideObjective.State;
import com.dillon.starsectormarines.battle.objective.side.SideObjective.Window;
import com.dillon.starsectormarines.battle.objective.side.SideTrigger;
import com.dillon.starsectormarines.battle.setup.BlindWatchSequence.Sites;
import com.dillon.starsectormarines.battle.story.BlindWatchProgress;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Blind Watch's three side objectives on the toolkit ({@code blind-watch-sounding.md}, Side objectives;
 * {@code mission-command-nouns.md}). The riggers are offered: free them at their module and walk them to the landing
 * ground before the dish field blows, and they give up the bunkers and the mortar carrier's track. The false schedule
 * is hidden until a squad sights or reaches the dish field's control hut: a squad leader holds the console while the
 * uplink stands and the warning is unsent, and Garnir takes a false sailing schedule instead of the warning. The cold
 * store is hidden until a squad sights the cryo pod racks by the lander pad: held while the dish field stands, its
 * manifest is logged, a record and nothing more (the owner's pending pick, built as recommended).
 *
 * <p>Pay is the owner's pick per job (pending); the numbers here are UNTUNED proposals.
 */
final class BlindWatchSideObjectives {
    static final String RIGGERS = "blind-watch-riggers", SCHEDULE = "blind-watch-schedule", COLD_STORE = "blind-watch-cold-store";
    /** Every job, in the order the briefing offers them, by their control-run names. */
    static final List<String> JOBS = List.of(RIGGERS, SCHEDULE, COLD_STORE);
    /** What the riggers' outfit pays for its crew walked out. UNTUNED: the story's proposal, pending the owner's pick. */
    static final int RIGGERS_FEE = 1_500;
    /** The false schedule pays nothing but its mark. UNTUNED: the story's proposal, pending the owner's pick. */
    static final int SCHEDULE_FEE = 0;
    /** How long a squad leader holds the console to send the false schedule. UNTUNED first pass. */
    static final float SCHEDULE_SECONDS = 15f;
    /** How far a company combatant spots the control hut's console from. UNTUNED first pass. */
    static final float CONSOLE_SIGHT_CELLS = 10f;
    /** How long a company combatant holds the pod racks, unbroken, to log the manifest. UNTUNED first pass. */
    static final float COLD_STORE_SECONDS = 20f;
    /** How far a company combatant spots the pod racks from. UNTUNED first pass. */
    static final float PODS_SIGHT_CELLS = 12f;
    static final String RIGGERS_MARK = "corvus.warion.riggers_freed", SCHEDULE_MARK = "corvus.garnir.false_schedule",
            COLD_STORE_MARK = "corvus.sable_wake.organ_manifest";
    private static final String LINE = "corvus.blind_watch.";

    private BlindWatchSideObjectives() { }

    /**
     * Free the riggers and walk them to the landing ground while the dish field stands: the post's bunkers and the
     * mortar carrier's track are revealed. Their death, all of them, ruins the job; one line tells either loss.
     *
     * @param reveal   the payoff, through the fog's standing sources
     * @param rigger   the rigger the escort follows, or 0 once none is on the field
     * @param escorted whether the riggers are freed and a company combatant is with them
     */
    static SideObjective riggers(Sites sites, BlindWatchProgress progress, Effect reveal, LongSupplier rigger,
                                 BooleanSupplier escorted) {
        return new SideObjective(RIGGERS, new Hook("blindWatchSideRiggers", "blindWatchSideOffererRiggers",
                Map.of(State.WORKING, LINE + "riggers_moving", State.DONE, LINE + "riggers_out", State.FAILED, LINE + "riggers_lost",
                        State.LAPSED, LINE + "riggers_lost")),
                new SideTrigger.Escort(rigger, escorted, DustLedgerSideObjectives.ground(sites.landingArea())),
                Window.phase(progress::dishesStanding), reveal, Return.done(RIGGERS_MARK, RIGGERS_FEE), null);
    }

    /**
     * Hold the dish field's console while the uplink stands and the warning is unsent: the warning can no longer
     * complete. Hidden until a company combatant sights or reaches the control hut.
     */
    static SideObjective schedule(Sites sites, BlindWatchProgress progress) {
        Ground console = DustLedgerSideObjectives.ground(sites.controlHut());
        return new SideObjective(SCHEDULE, new Hook("blindWatchSideSchedule", "blindWatchSideOffererCompany",
                Map.of(State.WORKING, LINE + "schedule_working", State.DONE, LINE + "schedule_sent", State.LAPSED, LINE + "schedule_lapsed")),
                new SideTrigger.Hold(Who.LEADER, console, SCHEDULE_SECONDS),
                Window.phase(() -> progress.uplinkStanding() && !progress.warning().terminal()),
                sim -> progress.falseSchedule(), Return.done(SCHEDULE_MARK, SCHEDULE_FEE), null)
                .hidden(Discovery.anyOf(Discovery.sight(Who.COMBATANT, console, CONSOLE_SIGHT_CELLS),
                        Discovery.reach(Who.COMBATANT, console)), LINE + "schedule_found");
    }

    /**
     * Hold the cryo pod racks while the dish field stands: the manifest is logged. No payoff in battle and no credits;
     * the mark is evidence for A Cold Account. Hidden until a company combatant sights or reaches the racks.
     */
    static SideObjective coldStore(Sites sites, BlindWatchProgress progress) {
        Ground pods = DustLedgerSideObjectives.ground(sites.podRacks());
        return new SideObjective(COLD_STORE, new Hook("blindWatchSideColdStore", "blindWatchSideOffererCompany",
                Map.of(State.WORKING, LINE + "cold_store_working", State.DONE, LINE + "cold_store_logged")),
                new SideTrigger.Hold(Who.COMBATANT, pods, COLD_STORE_SECONDS),
                Window.phase(progress::dishesStanding), Effect.NONE, Return.done(COLD_STORE_MARK), null)
                .hidden(Discovery.anyOf(Discovery.sight(Who.COMBATANT, pods, PODS_SIGHT_CELLS),
                        Discovery.reach(Who.COMBATANT, pods)), LINE + "cold_store_found");
    }
}
