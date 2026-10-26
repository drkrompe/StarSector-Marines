package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Saltwell Cross, The Dust Ledger's authored map ({@code dust-ledger-crossing.md}), through the look preset: the
 * whole map, then the apron, the market, the Harrow yard, the ridge, the compound, its east yard and pump house, and
 * both canal crossings, at site and play zoom, then a gun nest (its gun spawned), a pillbox and the feed junction.
 * Real ground, nothing drawn over it, except the last two frames: the whole map and the compound marked with the
 * mission's sites, side objectives, approaches and defences, for reading the mission at a glance. Every camera is placed from the recipe's own sites, so a run before a change and
 * one after frame the same ground. Opt-in. 1920x1080.
 */
public final class DustLedgerCrossingSnapshotSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    private static final long SEED = 6150;
    private static final float SITE_CELLS = 100f, CLOSE_CELLS = 36f;

    @Override public String id() { return "dust-ledger-crossing"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "Saltwell Cross: The Dust Ledger's authored map, whole and by site"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        TargetProfile asharu = new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.ARID, SettlementLink.ROAD);
        DustLedgerCrossing.Layout layout = DustLedgerCrossing.generate(SEED, asharu);
        BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W, FRAME_H, 0f, 0f, 1f);
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = BattleSetup.buildMap(layout.map(), List.of(), layout.map().defensePosts, SEED).sim()) {
            int w = DustLedgerCrossing.WIDTH, h = DustLedgerCrossing.HEIGHT;
            float play = BattleReviewFrameRenderer.PLAY_ZOOM_CELLS;
            frame(out, look, sim, "00-whole-map.png", w / 2f, h / 2f, Math.max(w, h * 16f / 9f));
            DustLedgerCrossing.Cell cross = DustLedgerCrossing.CROSSROADS;
            frame(out, look, sim, "01-market-site.png", cross.x() - 4, cross.y(), SITE_CELLS);
            frame(out, look, sim, "02-market-play.png", cross.x() - 6, cross.y() + 2, play);
            DustLedgerCrossing.Area apron = layout.landing();
            frame(out, look, sim, "03-apron-play.png", apron.centre().x() + 4, apron.centre().y(), play);
            DustLedgerCrossing.Area yard = layout.harrowYard();
            frame(out, look, sim, "04-harrow-yard-play.png", yard.centre().x(), yard.centre().y() + 4, play);
            DustLedgerCrossing.Cell rim = layout.ridgeOverwatch().get(layout.ridgeOverwatch().size() / 2);
            frame(out, look, sim, "05-ridge-overwatch-play.png", rim.x(), rim.y() - 10, play);
            DustLedgerCrossing.Cell ramp = DustLedgerCrossing.WEST_RAMP_FOOT;
            frame(out, look, sim, "06-ridge-west-ramp-close.png", ramp.x(), ramp.y() + 5, CLOSE_CELLS);
            DustLedgerCrossing.Area compound = layout.compound();
            frame(out, look, sim, "07-compound-site.png", compound.centre().x(), compound.centre().y(), SITE_CELLS);
            DustLedgerCrossing.Cell ledger = layout.ledgerTarget();
            look.withoutRoofs();
            frame(out, look, sim, "08-pump-house-close.png", ledger.x() - 10, ledger.y() + 6, CLOSE_CELLS);
            look.withRoofs();
            DustLedgerCrossing.Cell south = layout.canalCrossings().get(1);
            frame(out, look, sim, "09-south-crossing-play.png", south.x(), south.y() - 4, play);
            DustLedgerCrossing.Cell west = layout.canalCrossings().get(0);
            frame(out, look, sim, "10-west-crossing-play.png", west.x(), west.y() + 4, play);
            frame(out, look, sim, "11-compound-east-yard-play.png", ledger.x() + 8, ledger.y() + 4, play);
            DustLedgerCrossing.Cell gateNest = layout.nests().get(0).cell();
            frame(out, look, sim, "12-gate-nest-close.png", gateNest.x() - 2, gateNest.y() + 2, CLOSE_CELLS);
            var pillbox = layout.pillboxes().get(0).building();
            look.withoutRoofs();
            frame(out, look, sim, "13-gate-pillbox-close.png", (pillbox.left + pillbox.right) / 2f,
                    (pillbox.top + pillbox.bottom) / 2f, CLOSE_CELLS);
            look.withRoofs();
            DustLedgerCrossing.Area feed = layout.feedJunctionArea();
            frame(out, look, sim, "14-feed-junction-play.png", feed.centre().x() + 6, feed.centre().y() + 2, play);
            look.refocus(w / 2f + .5f, h / 2f + .5f, Math.max(w, h * 16f / 9f));
            out.add(new SnapshotArtifact("15-objectives-overview.png", look.renderMarked(sim, objectiveMarks(layout, false))));
            look.withoutRoofs();
            look.refocus(compound.centre().x() + .5f, compound.centre().y() + .5f, SITE_CELLS * .8f);
            out.add(new SnapshotArtifact("16-objectives-compound.png", look.renderMarked(sim, objectiveMarks(layout, true))));
            look.withRoofs();
        }
        return out;
    }

    /**
     * The mission read off the recipe's own sites: the apron and egress, the Raid's target and the broker, the three
     * side objectives, the alarm's exclusion ring, the three approaches, and the nests and pillboxes. {@code compoundOnly}
     * keeps the marks inside the walls, so a close frame carries no labels pinned to its edge for sites off it.
     */
    private static ReviewAnnotations objectiveMarks(DustLedgerCrossing.Layout layout, boolean compoundOnly) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        if (compoundOnly) {
            defences(marks, layout);
            ledgerAndBroker(marks, layout);
            return marks.build();
        }
        DustLedgerCrossing.Area ring = layout.exclusionRing();
        marks.box(ring.left(), ring.bottom(), ring.right(), ring.top(), "Exclusion ring: armed inside = alarm", ReviewStyle.NOTE);
        for (var approach : layout.approaches().entrySet()) {
            List<ReviewAnnotation.Point> points = new ArrayList<>();
            for (DustLedgerCrossing.Cell c : approach.getValue()) points.add(new ReviewAnnotation.Point(c.x() + .5f, c.y() + .5f));
            marks.polyline(points, "Approach: " + approach.getKey().name().toLowerCase().replace('_', ' '), ReviewStyle.ROUTE);
        }
        DustLedgerCrossing.Area apron = layout.landing();
        marks.box(apron.left(), apron.bottom(), apron.right(), apron.top(), "Landing apron / egress", ReviewStyle.LANDING);
        ledgerAndBroker(marks, layout);
        DustLedgerCrossing.Area pad = layout.skimmerPad();
        marks.box(pad.left(), pad.bottom(), pad.right(), pad.top(), "Broker's skimmer", ReviewStyle.OBJECTIVE);
        DustLedgerCrossing.Cell crate = layout.couplingCrate(), drop = layout.crateDrop();
        marks.arrow(crate.x() + .5f, crate.y() + .5f, drop.x() + .5f, drop.y() + .5f,
                "Side: carry the coupling to Harrow", ReviewStyle.ALLY);
        DustLedgerCrossing.Area yard = layout.harrowYard();
        marks.box(yard.left(), yard.bottom(), yard.right(), yard.top(), "Harrow yard (salvagers join)", ReviewStyle.ALLY);
        var well = layout.wellHouseArea();
        marks.box(well.left, well.bottom, well.right, well.top, "Side: the warden's nod", ReviewStyle.ALLY);
        DustLedgerCrossing.Area feed = layout.feedJunctionArea();
        marks.box(feed.left(), feed.bottom(), feed.right(), feed.top(), "Side: cut the feed (guns dark)", ReviewStyle.ALLY);
        var cantina = layout.cantina();
        marks.box(cantina.left, cantina.bottom, cantina.right, cantina.top, "Cantina: drinkers arm at alarm", ReviewStyle.DUG_IN);
        defences(marks, layout);
        return marks.build();
    }

    private static void ledgerAndBroker(ReviewAnnotations.Builder marks, DustLedgerCrossing.Layout layout) {
        DustLedgerCrossing.Area ledger = layout.ledgerRoom();
        marks.box(ledger.left(), ledger.bottom(), ledger.right(), ledger.top(), "1 Seize the ledger", ReviewStyle.KEEP);
        DustLedgerCrossing.Cell barrel = layout.burnBarrel();
        marks.label(barrel.x() + .5f, barrel.y() + .5f, "Burn barrel", ReviewStyle.KEEP);
        DustLedgerCrossing.Area office = layout.brokerOfficeRoom();
        marks.box(office.left(), office.bottom(), office.right(), office.top(), "Broker Teague (optional catch)", ReviewStyle.OBJECTIVE);
    }

    private static void defences(ReviewAnnotations.Builder marks, DustLedgerCrossing.Layout layout) {
        for (DustLedgerCrossing.Nest nest : layout.nests())
            marks.label(nest.cell().x() + .5f, nest.cell().y() + .5f, "Gun nest", ReviewStyle.FRONT);
        for (DustLedgerCrossing.Pillbox pillbox : layout.pillboxes()) {
            var b = pillbox.building();
            marks.box(b.left, b.bottom, b.right, b.top, "Pillbox", ReviewStyle.FRONT);
        }
    }

    private static void frame(List<SnapshotArtifact> out, BattleReviewFrameRenderer look, BattleSimulation sim,
                              String name, float x, float y, float cells) throws Exception {
        look.refocus(x + 0.5f, y + 0.5f, cells);
        out.add(new SnapshotArtifact(name, look.render(sim, "", ReviewAnnotations.NONE)));
    }
}
