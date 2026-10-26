package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
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
 * Real ground, nothing drawn over it; every camera is placed from the recipe's own sites, so a run before a change and
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
        }
        return out;
    }

    private static void frame(List<SnapshotArtifact> out, BattleReviewFrameRenderer look, BattleSimulation sim,
                              String name, float x, float y, float cells) throws Exception {
        look.refocus(x + 0.5f, y + 0.5f, cells);
        out.add(new SnapshotArtifact(name, look.render(sim, "", ReviewAnnotations.NONE)));
    }
}
