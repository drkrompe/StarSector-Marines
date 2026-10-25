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
import com.dillon.starsectormarines.battle.story.DustLedgerProgress.Side;
import com.dillon.starsectormarines.i18n.Strings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Dust Ledger's checklist rows beside the Raid's own (seize the ledger room, return to the apron): the lift-off
 * clock and exclusion ring, the burning ledger, the broker, and the two quiet-phase side objectives.
 */
public final class DustLedgerChecklist {
    private DustLedgerChecklist() { }

    public static List<Item> items(DustLedgerSequence sequence, BattleView sim) {
        DustLedgerProgress p = sequence.progress();
        Sites sites = sequence.sites();
        return List.of(clock(p, sites), ledger(p, sites), broker(p, sites, sequence, sim),
                coupling(p, sites, sequence), warden(p, sites));
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

    private static Item broker(DustLedgerProgress p, Sites sites, DustLedgerSequence sequence, BattleView sim) {
        var office = sites.brokerOffice();
        long id = sequence.broker();
        boolean present = id != 0L && sim.resolveUnit(id) != 0L && sim.world().hasPosition(id);
        int x = present ? sim.world().cellX(id) : office.x(), y = present ? sim.world().cellY(id) : office.y();
        State state = switch (p.broker()) {
            case DELIVERED -> State.COMPLETE;
            case ESCAPED, DEAD -> State.FAILED;
            default -> State.ACTIVE;
        };
        String phase = Strings.named("dustLedgerHudBrokerState", p.broker().name());
        List<Marker> markers = new ArrayList<>();
        if (state == State.ACTIVE) markers.add(new Marker("dust-ledger-broker", Strings.get("dustLedgerHudBrokerName"), x, y, phase, state));
        var pad = sites.skimmerPad();
        markers.add(new Marker("dust-ledger-pad", Strings.get("dustLedgerHudSkimmerPad"), pad.x(), pad.y(),
                Strings.get("dustLedgerHudSkimmerPadStatus"), state));
        return new Item("dust-ledger-3-broker", Strings.get("dustLedgerHudBrokerItem"), state, phase,
                Strings.get("dustLedgerHudBrokerSteps"), phase, phase, -1, "", markers, markers.get(0).id());
    }

    private static Item coupling(DustLedgerProgress p, Sites sites, DustLedgerSequence sequence) {
        State state = side(p.coupling());
        String phase = p.coupling() == Side.PAID ? Strings.get("dustLedgerHudCouplingPaid")
                : p.coupling() == Side.LAPSED ? Strings.get("dustLedgerHudSideLapsed")
                : sequence.crateCarried() ? Strings.get("dustLedgerHudCouplingCarried")
                : Strings.get("dustLedgerHudCouplingLying");
        var yard = sites.harrowYard().centre();
        List<Marker> markers = new ArrayList<>();
        if (state == State.ACTIVE) markers.add(new Marker("dust-ledger-crate", Strings.get("dustLedgerHudCrate"),
                sequence.crateX(), sequence.crateY(), phase, state));
        markers.add(new Marker("dust-ledger-harrow", Strings.get("dustLedgerHudHarrowYard"), yard.x(), yard.y(),
                Strings.get("dustLedgerHudHarrowYardStatus"), state, cells(sites.harrowYard().outline())));
        return new Item("dust-ledger-4-coupling", Strings.get("dustLedgerHudCouplingName"), state, phase,
                Strings.get("dustLedgerHudCouplingSteps"), phase, phase, -1, "", markers, markers.get(0).id());
    }

    private static Item warden(DustLedgerProgress p, Sites sites) {
        State state = side(p.warden());
        boolean waiting = p.warden() == Side.OPEN && p.nodProgress() > 0f;
        String phase = p.warden() == Side.PAID ? Strings.get("dustLedgerHudWardenPaid")
                : p.warden() == Side.LAPSED ? Strings.get("dustLedgerHudSideLapsed")
                : waiting ? Strings.get("dustLedgerHudWardenWaiting") : Strings.get("dustLedgerHudWardenOpen");
        var well = sites.wellHouse().centre();
        List<Marker> markers = new ArrayList<>();
        markers.add(new Marker("dust-ledger-well", Strings.get("dustLedgerHudWellHouse"), well.x(), well.y(), phase, state,
                cells(sites.wellHouse().outline())));
        if (p.spottersUp()) for (int i = 0; i < sites.spotterPosts().size(); i++) {
            var post = sites.spotterPosts().get(i);
            markers.add(new Marker("dust-ledger-spotter-" + i, Strings.get("dustLedgerHudSpotter"), post.x(), post.y(),
                    Strings.get("dustLedgerHudSpotterStatus"), State.COMPLETE));
        }
        return new Item("dust-ledger-5-warden", Strings.get("dustLedgerHudWardenName"), state, phase,
                Strings.get("dustLedgerHudWardenSteps"), phase,
                waiting ? MissionObjectiveFamilies.timed(Strings.get("dustLedgerHudWardenTimer"), p.nodProgress(), DustLedgerProgress.NOD_SECONDS) : phase,
                waiting ? p.nodProgress() / DustLedgerProgress.NOD_SECONDS : -1, "", markers, "dust-ledger-well");
    }

    private static State side(Side side) {
        return side == Side.PAID ? State.COMPLETE : side == Side.LAPSED ? State.FAILED : State.ACTIVE;
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
