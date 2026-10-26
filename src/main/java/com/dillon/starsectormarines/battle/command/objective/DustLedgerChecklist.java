package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Cell;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Marker;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.State;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Area;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Sites;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.story.DustLedgerProgress;
import com.dillon.starsectormarines.i18n.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Dust Ledger's checklist rows beside the Raid's own (seize the ledger room, return to the apron): the lift-off
 * clock and exclusion ring, and the burning ledger. The broker and the quiet-phase jobs are side objectives, listed in
 * the Optional section by the toolkit.
 */
public final class DustLedgerChecklist {
    private DustLedgerChecklist() { }

    public static List<Item> items(DustLedgerSequence sequence, BattleView sim) {
        DustLedgerProgress p = sequence.progress();
        Sites sites = sequence.sites();
        return List.of(clock(p, sites), ledger(p, sites));
    }

    private static Item clock(DustLedgerProgress p, Sites sites) {
        boolean quiet = p.quiet();
        String phase = quiet ? Strings.format("dustLedgerHudClockQuiet", clockText(p.clockRemaining()))
                : Strings.named("dustLedgerHudAlarmCause", p.alarm().name());
        List<Marker> markers = new ArrayList<>();
        // The ring is drawn while it matters: during the quiet phase.
        if (quiet) {
            Area ring = sites.exclusionRing();
            var centre = ring.centre();
            markers.add(new Marker("dust-ledger-ring", Strings.get("dustLedgerHudRing"), centre.x(), centre.y(),
                    Strings.get("dustLedgerHudRingStatus"), State.ACTIVE, cells(ring.outline())));
        }
        return new Item("dust-ledger-1-clock", Strings.get("dustLedgerHudClockName"), quiet ? State.ACTIVE : State.COMPLETE,
                phase, Strings.get("dustLedgerHudClockSteps"),
                Strings.get(quiet ? "dustLedgerHudClockInstruction" : "dustLedgerHudAlarmInstruction"),
                quiet ? clockText(p.clockRemaining()) : "", quiet ? p.clockRemaining() / p.clockSeconds() : -1, "",
                markers, markers.isEmpty() ? "" : "dust-ledger-ring");
    }

    private static Item ledger(DustLedgerProgress p, Sites sites) {
        String phase;
        if (p.seized()) phase = Strings.format("dustLedgerHudLedgerSeized", percent(p.unburnedShare()),
                Strings.named("dustLedgerHudGrade", p.grade().name()));
        else if (p.quiet()) phase = Strings.get("dustLedgerHudLedgerWaiting");
        else if (p.burning()) phase = Strings.format("dustLedgerHudLedgerBurning", percent(p.burnedShare()));
        else phase = Strings.get("dustLedgerHudLedgerAshes");
        var barrel = sites.burnBarrel();
        var room = sites.ledgerRoom();
        Marker marker = new Marker("dust-ledger-barrel", Strings.get("dustLedgerHudBarrel"), barrel.x(), barrel.y(), phase,
                p.seized() ? State.COMPLETE : State.ACTIVE, cells(room.outline()));
        return new Item("dust-ledger-2-ledger", Strings.get("dustLedgerHudLedgerName"), p.seized() ? State.COMPLETE : State.ACTIVE,
                phase, Strings.get("dustLedgerHudLedgerSteps"), Strings.get("dustLedgerHudLedgerInstruction"),
                p.seized() ? percent(p.unburnedShare()) : percent(1f - p.burnedShare()),
                p.seized() ? p.unburnedShare() : 1f - p.burnedShare(), "", List.of(marker), marker.id());
    }

    private static List<Cell> cells(List<DustLedgerSequence.Cell> cells) {
        List<Cell> out = new ArrayList<>(cells.size());
        for (var c : cells) out.add(new Cell(c.x(), c.y()));
        return out;
    }

    static String clockText(float seconds) {
        int whole = (int) Math.ceil(seconds);
        return Strings.format("dustLedgerHudClockTime", whole / 60, String.format(Locale.ROOT, "%02d", whole % 60));
    }

    private static String percent(float share) { return Math.round(Math.max(0f, Math.min(1f, share)) * 100f) + "%"; }
}
