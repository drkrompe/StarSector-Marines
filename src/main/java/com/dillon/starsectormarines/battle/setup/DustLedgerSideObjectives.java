package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Marker;
import com.dillon.starsectormarines.battle.objective.side.Ground;
import com.dillon.starsectormarines.battle.objective.side.SideObjective;
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
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The Dust Ledger's one side objective on the toolkit ({@code dust-ledger-crossing.md}, Bonus objective;
 * {@code mission-command-nouns.md}): take the broker's surrender after the alarm and bring him to the freight apron
 * alive before his skimmer leaves. It pays Wayline's bounty.
 */
final class DustLedgerSideObjectives {
    static final String BROKER = "dust-ledger-broker";
    /** What Wayline pays for the broker delivered alive (owner, 2026-10-26). */
    static final int BROKER_BOUNTY = 3_000;
    private static final String LINE = "corvus.dust_ledger.";

    private DustLedgerSideObjectives() { }

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
}
