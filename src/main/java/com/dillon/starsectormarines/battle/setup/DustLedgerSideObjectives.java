package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Marker;
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
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Area;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Cell;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Sites;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Broker;
import com.dillon.starsectormarines.i18n.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The Dust Ledger's four side objectives on the toolkit ({@code dust-ledger-crossing.md}, Side objectives;
 * {@code mission-command-nouns.md}): the salvagers' coupling, the warden's nod and cutting the feed, open until the
 * alarm, and the broker, open until he is gone. Their payoffs (the salvage team, the cantina held and the spotters, the
 * dark nests) are the sequence's to carry out from each job's state. The feed is hidden until a squad finds the tap; the
 * coupling and the broker also pay credits.
 */
final class DustLedgerSideObjectives {
    static final String COUPLING = "dust-ledger-coupling", WARDEN = "dust-ledger-warden", FEED = "dust-ledger-feed",
            BROKER = "dust-ledger-broker";
    /** The quiet-phase jobs, in the order the briefing offers them, by their control-run names. */
    static final List<String> QUIET_JOBS = List.of(COUPLING, WARDEN, FEED);
    /** How long a squad leader stays at the well house for the warden's nod. UNTUNED first pass. */
    static final float NOD_SECONDS = 8f;
    /** How long a company combatant works the feed junction, unbroken, to cut the nests' power. UNTUNED first pass. */
    static final float FEED_SECONDS = 10f;
    /** How far a company combatant spots the tap's junction box from. UNTUNED first pass. */
    static final float FEED_SIGHT_CELLS = 12f;
    /** Wayline's delivery fee for the coupling, beside a 22,000-credit Low contract (owner, 2026-10-26). */
    static final int COUPLING_FEE = 1_500;
    /** What Wayline pays for the broker delivered alive (owner, 2026-10-26). */
    static final int BROKER_BOUNTY = 3_000;
    private static final String LINE = "corvus.dust_ledger.";

    private DustLedgerSideObjectives() { }

    /** Carry the drive coupling from the apron to the Harrow yard before the alarm: the salvage team joins it. */
    static SideObjective coupling(Sites sites, DustLedgerProgress progress) {
        return new SideObjective(COUPLING, new Hook("dustLedgerSideCoupling", "dustLedgerSideOffererHarrow",
                Map.of(State.WORKING, LINE + "harrow_crate", State.DONE, LINE + "harrow_delivered")),
                new SideTrigger.Carry(Who.COMBATANT, cell(sites.crate()), DustLedgerSequence.CRATE_REACH_CELLS,
                        ground(sites.harrowYard())),
                Window.phase(progress::quiet), Effect.NONE, Return.done("corvus.harrow.coupling_delivered", COUPLING_FEE), null);
    }

    /** A squad leader waits at the well house before the alarm: the watch holds the cantina and spots the yard. */
    static SideObjective warden(Sites sites, DustLedgerProgress progress, BooleanSupplier spottersUp) {
        return new SideObjective(WARDEN, new Hook("dustLedgerSideWarden", "dustLedgerSideOffererWarden",
                Map.of(State.WORKING, LINE + "warden_approach", State.DONE, LINE + "warden_nod")),
                new SideTrigger.Hold(Who.LEADER, ground(sites.wellHouse()), NOD_SECONDS),
                Window.phase(progress::quiet), Effect.NONE, Return.done("corvus.saltwell.warden_nod"),
                () -> spottersUp.getAsBoolean() ? markers("dust-ledger-spotter-", "dustLedgerHudSpotter",
                        "dustLedgerHudSpotterStatus", sites.spotterPosts(), MissionObjectiveSnapshot.State.COMPLETE) : List.of());
    }

    /**
     * Work the power tap's junction box, unbroken, before the alarm: the gun nests are dead when it comes. Hidden: the
     * briefing knows only that the nests have power from somewhere, until a squad sights or reaches the box.
     */
    static SideObjective feed(Sites sites, DustLedgerProgress progress, Effect cutNests, Supplier<List<Cell>> nests,
                              BooleanSupplier nestsPowered) {
        Ground junction = ground(sites.feedJunction());
        return new SideObjective(FEED, new Hook("dustLedgerSideFeed", "dustLedgerSideOffererCompany",
                Map.of(State.WORKING, LINE + "feed_approach", State.DONE, LINE + "feed_cut", State.LAPSED, LINE + "feed_lapsed")),
                new SideTrigger.Hold(Who.COMBATANT, junction, FEED_SECONDS),
                Window.phase(progress::quiet), cutNests, Return.done("corvus.sable_wake.feed_cut"),
                // The nests are marked while they can still fire on the company.
                () -> nestsPowered.getAsBoolean() ? markers("dust-ledger-nest-", "dustLedgerHudNest", "dustLedgerHudNestStatus",
                        nests.get(), MissionObjectiveSnapshot.State.ACTIVE) : List.of())
                .hidden(Discovery.anyOf(Discovery.sight(Who.COMBATANT, junction, FEED_SIGHT_CELLS),
                        Discovery.reach(Who.COMBATANT, junction)), LINE + "feed_found");
    }

    /** Take the broker's surrender and bring him to the freight apron alive; his skimmer leaving closes the job. */
    static SideObjective broker(Sites sites, DustLedgerProgress progress, LongSupplier broker, Supplier<SideObjective> self) {
        Cell pad = sites.skimmerPad();
        return new SideObjective(BROKER, new Hook("dustLedgerSideBroker", "dustLedgerSideOffererCompany",
                Map.of(State.WORKING, LINE + "broker_surrendered", State.DONE, LINE + "broker_apron", State.FAILED, LINE + "broker_lost")),
                new SideTrigger.Escort(broker, () -> progress.broker() == Broker.HELD, ground(sites.apron())),
                Window.phase(() -> progress.broker() != Broker.ESCAPED), Effect.NONE, Return.done("corvus.teague.delivered", BROKER_BOUNTY),
                () -> List.of(new Marker("dust-ledger-pad", Strings.get("dustLedgerHudSkimmerPad"), pad.x(), pad.y(),
                        Strings.get("dustLedgerHudSkimmerPadStatus"), self.get().state().picture())));
    }

    static Ground ground(Area area) {
        List<MissionObjectiveSnapshot.Cell> cells = new ArrayList<>(area.cells().size());
        for (Cell c : area.cells()) cells.add(new MissionObjectiveSnapshot.Cell(c.x(), c.y()));
        return Ground.of(cells);
    }

    private static MissionObjectiveSnapshot.Cell cell(Cell c) { return new MissionObjectiveSnapshot.Cell(c.x(), c.y()); }

    private static List<Marker> markers(String idPrefix, String nameKey, String statusKey, List<Cell> cells,
                                        MissionObjectiveSnapshot.State state) {
        List<Marker> out = new ArrayList<>(cells.size());
        for (int i = 0; i < cells.size(); i++)
            out.add(new Marker(idPrefix + i, Strings.get(nameKey), cells.get(i).x(), cells.get(i).y(), Strings.get(statusKey), state));
        return out;
    }
}
