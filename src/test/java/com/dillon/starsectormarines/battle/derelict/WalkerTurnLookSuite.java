package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.MechLocomotion;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * A derelict walker turned 180 degrees where it stands, as manual control asks it to: its hips turned toward the far
 * heading each tick at the machine's own turn rate ({@link MechLocomotion#turnRateDegrees}), framed by the look preset at
 * eight moments on one contact sheet, for judging the turn's motion (owner: the legs must not tangle). One sheet a
 * walker size, and the frames themselves.
 */
public final class WalkerTurnLookSuite implements SnapshotSuite {

    static final int W = 1920;
    static final int H = 1080;
    static final int FRAMES = 8;
    /** Ticks between frames: the eight frames span the largest walker's turn. */
    static final int EVERY = Math.round(0.7f / BattleSimulation.TICK_DT);
    static final float CELLS = 10f;
    /** The unseen frames drawn between the sheet's, so the legs step every tick. */
    static final int TICK_W = 320;
    static final int TICK_H = 180;

    @Override public String id() { return "walker-turn"; }

    @Override public String label() { return "Derelict walkers turning 180 degrees in place"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            JSONObject fixture = new JSONObject(Files.readString(
                    context.projectRoot().resolve(DerelictWatchPicksSuite.FIXTURE), StandardCharsets.UTF_8));
            List<SnapshotArtifact> out = new ArrayList<>();
            for (DerelictWalkerFrame frame : new DerelictWalkerFrame[] {DerelictWalkerFrame.ROUNDS,
                    DerelictWalkerFrame.HALL}) {
                sheet(context, fixture, frame, out);
            }
            sheet(context, fixture, null, out);
            return out;
        }
    }

    /** One walker's turn; {@code frame} null for the heavy (any line frame). */
    private static void sheet(SnapshotContext context, JSONObject fixture, DerelictWalkerFrame frame,
                              List<SnapshotArtifact> out) throws Exception {
        BattleSimulation sim = HeadlessBattles.fixture(fixture);
        for (int i = 0; i < 30; i++) sim.advance(BattleSimulation.TICK_DT);
        DerelictSecurityDirector director = sim.derelictSecurity();
        long id = 0L;
        String name = frame == null ? "heavy" : frame.name().toLowerCase();
        for (DerelictSecurityDirector.Walker w : director.walkers()) {
            boolean match = frame == null ? w.frame() != DerelictWalkerFrame.ROUNDS && w.frame() != DerelictWalkerFrame.HALL
                    : w.frame() == frame;
            if (match && sim.squadMemberCount(w.squadId()) > 0) {
                id = sim.squadMemberAt(w.squadId(), 0);
                break;
            }
        }
        if (id == 0L) {
            System.out.printf("    [look/walker-turn] no %s walker%n", name);
            return;
        }
        EntityWorld world = sim.getEntityWorld();
        BattleComponents components = sim.getBattleComponents();
        float start = world.getFloat(id, components.MECH_LOCOMOTION, BattleComponents.MECH_LOCOMOTION_FACING_DEGREES);
        float target = start + 180f;
        float rate = world.getFloat(id, components.MECH_LOCOMOTION, BattleComponents.MECH_LOCOMOTION_TURN_RATE);
        float heading = start;
        float x = sim.world().renderX(id), y = sim.world().renderY(id);
        BufferedImage sheet = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        int tw = W / 4, th = H / 2;
        BattleReviewFrameRenderer tick = BattleReviewFrameRenderer.look(context.modRoot(), TICK_W, TICK_H, x, y, CELLS)
                .withoutRoofs();
        for (int f = 0; f < FRAMES; f++) {
            BufferedImage shot = BattleReviewFrameRenderer.look(context.modRoot(), W, H, x, y, CELLS).withoutRoofs()
                    .render(sim, "walker-turn-" + name + "-" + f);
            if (f == FRAMES - 1 || f == FRAMES / 2) out.add(new SnapshotArtifact(name + "/frame-" + f + ".png", shot));
            int col = f % 4, row = f / 4;
            // The middle of the frame, square-ish, scaled into its tile.
            int cw = H * tw / th;
            g.drawImage(shot, col * tw, row * th, col * tw + tw, row * th + th, (W - cw) / 2, 0, (W + cw) / 2, H,
                    null);
            float facing = heading;
            g.setColor(new Color(0, 0, 0, 170));
            g.fillRect(col * tw + 8, row * th + 8, 290, 34);
            g.setColor(Color.WHITE);
            g.drawString(String.format("t=%.1fs  asked %+.0f", f * EVERY * BattleSimulation.TICK_DT,
                    wrap(facing - start)), col * tw + 16, row * th + 33);
            for (int t = 0; t < EVERY; t++) {
                sim.advance(BattleSimulation.TICK_DT);
                // Manual control: the hips are asked straight round and turn at the machine's own rate; written
                // after the tick so the walker's own orders do not turn it back.
                float left = target - heading;
                heading += Math.max(-rate * BattleSimulation.TICK_DT, Math.min(rate * BattleSimulation.TICK_DT, left));
                float wrapped = wrap(heading);
                world.setFloat(id, components.MECH_LOCOMOTION, BattleComponents.MECH_LOCOMOTION_FACING_DEGREES, wrapped);
                world.setFloat(id, components.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_HIP_FACING_DEGREES,
                        wrapped);
                world.setFloat(id, components.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_FACING_DEGREES,
                        wrapped);
                // The drawn legs step only as the battle is drawn, as in the game every tick: an unseen small frame.
                if (t < EVERY - 1) tick.render(sim, "walker-turn-tick");
            }
        }
        g.setColor(Color.BLACK);
        g.fillRect(tw - 1, 0, 2, H);
        g.fillRect(2 * tw - 1, 0, 2, H);
        g.fillRect(3 * tw - 1, 0, 2, H);
        g.fillRect(0, th - 1, W, 2);
        g.dispose();
        out.add(new SnapshotArtifact(name + "/sheet.png", sheet));
    }

    private static float wrap(float d) {
        d %= 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }
}
