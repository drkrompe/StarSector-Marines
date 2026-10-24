package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.allied.EscortPace;
import com.dillon.starsectormarines.battle.command.allied.SupplyDelivery;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.setup.CordonSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An escorted ammunition truck as the owner would see it ({@code cordon-allied-command.md}, rule 3: escorted means
 * alongside): a pooled town Cordon played unrecorded, one redoubt's guns emptied as beat L2 empties them, and the
 * battle played on until the truck the line asks for has its escort alongside and sets out; then recorded through the
 * look preset with the camera following the pair up the street to the redoubt, at play zoom and closer.
 *
 * <p>{@code -Dsnapshot.cordonEscort.density=TOWN}, {@code .seed=14}, {@code .redoubt=2}, {@code .drainAt=7200} (the
 * tick the guns run dry; late enough that the shed has built a hull the sectors can spare) and {@code .ticks=900}
 * choose the battle and the length. Opt-in: it plays minutes of a battle before it draws.
 */
public final class CordonEscortLookSuite implements SnapshotSuite {

    /** The latest tick the escorted truck is waited for. */
    static final int BY = 20000;

    @Override public String id() { return "cordon-escort-look"; }

    @Override public String label() { return "Cordon: an ammunition truck and its escort driving together up the street"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        CordonPlan.Density density = CordonPlan.Density.valueOf(
                System.getProperty("snapshot.cordonEscort.density", "TOWN").trim().toUpperCase(Locale.ROOT));
        long seed = Long.parseLong(System.getProperty("snapshot.cordonEscort.seed", "14").trim());
        int redoubt = Integer.parseInt(System.getProperty("snapshot.cordonEscort.redoubt", "2").trim());
        int drainAt = Integer.parseInt(System.getProperty("snapshot.cordonEscort.drainAt", "7200").trim());
        int ticks = Integer.parseInt(System.getProperty("snapshot.cordonEscort.ticks", "900").trim());
        LookReview.Shot shot = new LookReview.Shot("escorted-truck", LookGround.verdant(), 40, 30, placement -> {
            BattleSimulation sim = CordonSetup.create(CordonSetup.map(CordonVariant.LINE, density, SurfacePalette.VERDANT,
                    seed), CordonVariant.LINE, List.of(), CordonSequence.Timetable.STANDARD);
            CordonSequence cordon = sim.getCordon();
            CordonRedoubt dry = cordon.redoubts().get(redoubt - 1);
            long[] pair = null;
            while (pair == null && sim.getSimTickIndex() < BY) {
                sim.advance(BattleSimulation.TICK_DT);
                if (sim.getSimTickIndex() == drainAt) cordon.line().stock(dry).drain(dry.guns());
                if (sim.getSimTickIndex() > drainAt) pair = setOut(sim, cordon);
            }
            if (pair == null) throw new IllegalStateException("no escorted truck set out by tick " + BY);
            placement.subject("truck", pair[0]);
            placement.subject("escort", pair[1]);
            System.out.printf("    [look/escorted-truck] %s seed %d: truck %d and escort %d set out at tick %d%n",
                    density, seed, pair[0], pair[1], sim.getSimTickIndex());
            return sim;
        }, placement -> List.of(
                LookReview.View.follow("play", placement, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS, "truck", "escort"),
                LookReview.View.follow("close", placement, 32f, "truck", "escort")), ticks, 2, 3);
        return LookReview.record(context.modRoot(), shot);
    }

    /** The truck that has just set out with its escort alongside, and the escort; {@code null} until one has. */
    private static long[] setOut(BattleSimulation sim, CordonSequence cordon) {
        for (Map.Entry<SupplyDelivery, CordonRedoubt> e : cordon.line().deliveries().entrySet()) {
            SupplyDelivery delivery = e.getKey();
            EscortPace pace = delivery.pace();
            if (pace == null || !pace.setOut() || pace.last() != EscortPace.Pace.GO) continue;
            long escort = cordon.armour().escortOf(delivery.truck());
            if (escort != 0L && cordon.armour().escorted(delivery.truck())) return new long[]{delivery.truck(), escort};
        }
        return null;
    }
}
