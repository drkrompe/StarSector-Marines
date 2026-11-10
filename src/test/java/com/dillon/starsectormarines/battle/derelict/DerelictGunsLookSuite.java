package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.appearance.FacingSystem;
import com.dillon.starsectormarines.battle.appearance.LayeredMechAppearance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.DamageDollCanvas;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;
import org.json.JSONObject;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The derelict walkers' guns on their hardpoints (`mechs-nouns.md`, mount; {@code MechBodyHardpoints}): the vast
 * ruin's MG-25 rounds walker, MG-40 and MG-70 hall frame stood side by side on open floor with the roof lifted, each
 * heading up the frame and aiming off to its left so its guns turn on their mounts, then each close, aiming the other
 * way and firing (its flashes at its barrels' tips). Look preset; still frames, no ticks after staging. Then the
 * damage doll of every walker frame, read from its own loadout, each port and rack drawing the weapon it holds. Opt-in.
 * No outcome is judged.
 */
public final class DerelictGunsLookSuite implements SnapshotSuite {

    static final int SETTLE_TICKS = 120;
    static final float LINEUP_CELLS = 16f;
    /** The aims: off the heading to the left for the lineup, to the right for the close shots, degrees. */
    static final float LINEUP_AIM = 35f;
    static final float CLOSE_AIM = -50f;
    /** Open floor the lineup needs, cells either side of its centre. */
    static final int HALF_W = 7;
    static final int HALF_H = 4;

    @Override public String id() { return "derelict-guns-look"; }

    @Override public String label() { return "The derelict walkers' guns on their hardpoints: lineup and close"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            JSONObject fixture = new JSONObject(Files.readString(
                    context.projectRoot().resolve(DerelictHeaviesLookSuite.VAST_FIXTURE), StandardCharsets.UTF_8));
            BattleSimulation sim = settled(fixture);
            Map<MechVariant, Long> walkers = walkers(sim);
            System.out.println("    [look/derelict-guns] walkers " + walkers.keySet());
            if (walkers.isEmpty()) return out;
            long first = walkers.values().iterator().next();
            int[] floor = openFloor(sim.getGrid(), sim.world().cellX(first), sim.world().cellY(first));
            if (floor == null) return out;
            float cx = floor[0] + 0.5f;
            float cy = floor[1] + 0.5f;
            float[] offsets = {-4.5f, 0f, 4.5f};
            MechVariant[] order = {MechVariant.SECURITY_ROUNDS, MechVariant.SECURITY_WALKER,
                    MechVariant.SECURITY_HALL};
            for (int i = 0; i < order.length; i++) {
                Long id = walkers.get(order[i]);
                if (id != null) stand(sim, id, cx + offsets[i], cy, 0f, LINEUP_AIM, 0);
            }
            out.add(new SnapshotArtifact("derelict-guns-lineup.png", BattleReviewFrameRenderer.look(
                    context.modRoot(), 1920, 1080, cx, cy, LINEUP_CELLS).withoutRoofs()
                    .render(sim, "derelict-guns-lineup")));
            int firing = LayeredMechAppearance.FLAG_CHAINGUN_FLASH | LayeredMechAppearance.FLAG_SRM_FLASH;
            for (int i = 0; i < order.length; i++) {
                Long id = walkers.get(order[i]);
                if (id == null) continue;
                float x = cx + offsets[i];
                stand(sim, id, x, cy, 0f, CLOSE_AIM, firing);
                String name = "derelict-guns-" + order[i].id;
                out.add(new SnapshotArtifact(name + ".png", BattleReviewFrameRenderer.look(context.modRoot(), 1920,
                        1080, x, cy, 3.2f * order[i].renderScale).withoutRoofs().render(sim, name)));
                stand(sim, id, x, cy, 0f, LINEUP_AIM, 0);
            }
            out.add(new SnapshotArtifact("derelict-guns-dolls.png", dolls(context)));
        }
        return out;
    }

    /** Every walker frame's doll, intact, at three and a half times its HUD size, captioned with its fit. */
    private static BufferedImage dolls(SnapshotContext context) {
        SvgAsset.Source previous = SvgAsset.setSource(path -> Files.readString(context.modRoot().resolve(path)));
        try {
            HeadlessUiRenderer renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
            int w = Math.round(DamageDollCanvas.SURFACE_WIDTH * 3.2f);
            int h = Math.round(DamageDollCanvas.SURFACE_HEIGHT * 3.2f);
            BufferedImage image = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setColor(new Color(18, 25, 30));
                g.fillRect(0, 0, 1920, 1080);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
                DerelictWalkerFrame[] frames = DerelictWalkerFrame.values();
                int perRow = 3;
                for (int i = 0; i < frames.length; i++) {
                    MechLoadoutComponent loadout = frames[i].createLoadout();
                    DamageDollCanvas.View view = new DamageDollCanvas.View();
                    view.read(loadout);
                    BufferedImage tile = renderer.renderCanvas(DamageDollCanvas.bare(view), w, h);
                    int row = i / perRow;
                    int inRow = Math.min(perRow, frames.length - row * perRow);
                    int x = (1920 - inRow * w - (inRow - 1) * 40) / 2 + (i % perRow) * (w + 40);
                    int y = 150 + row * (h + 140);
                    g.drawImage(tile, x, y, null);
                    g.setColor(new Color(0xC5, 0xD6, 0xCB));
                    g.drawString(frames[i].designation(), x, y - 112);
                    g.setColor(new Color(0x8F, 0xA3, 0x98));
                    List<String> fit = fit(loadout);
                    for (int line = 0; line < fit.size(); line++) g.drawString(fit.get(line), x, y - 88 + line * 22);
                }
            } finally {
                g.dispose();
            }
            return image;
        } finally {
            SvgAsset.setSource(previous);
        }
    }

    /** A loadout's guns, a line a mount. */
    private static List<String> fit(MechLoadoutComponent loadout) {
        List<String> out = new ArrayList<>();
        for (MechMountSlot slot : MechMountSlot.values()) {
            MechWeaponMount mount = loadout.mount(slot);
            if (mount != null) out.add(slot.name().toLowerCase().replace('_', ' ') + ": " + mount.component.displayName());
        }
        return out;
    }

    private static BattleSimulation settled(JSONObject fixture) throws Exception {
        BattleSimulation sim = HeadlessBattles.fixture(fixture);
        while (sim.getSimTickIndex() < SETTLE_TICKS) {
            int before = sim.getSimTickIndex();
            sim.advance(BattleSimulation.TICK_DT);
            if (sim.getSimTickIndex() == before) break;
        }
        return sim;
    }

    /** One standing walker of each derelict chassis the site fields. */
    private static Map<MechVariant, Long> walkers(BattleSimulation sim) {
        Map<MechVariant, Long> out = new EnumMap<>(MechVariant.class);
        DerelictSecurityDirector director = sim.derelictSecurity();
        if (director == null) return out;
        for (DerelictSecurityDirector.Walker w : director.walkers()) {
            if (sim.squadMemberCount(w.squadId()) == 0) continue;
            long id = sim.squadMemberAt(w.squadId(), 0);
            if (!sim.world().hasMechLoadout(id)) continue;
            out.putIfAbsent(sim.world().mechLoadout(id).variant, id);
        }
        return out;
    }

    /** The walker stood at (x, y) heading {@code heading}, its fire control on {@code aim} off it, {@code flags} lit. */
    private static void stand(BattleSimulation sim, long id, float x, float y, float heading, float aim, int flags) {
        sim.world().setPos(id, x, y);
        EntityWorld world = sim.getEntityWorld();
        BattleComponents components = sim.getBattleComponents();
        FacingSystem.faceStanding(world, components, id, heading);
        sim.world().mechLoadout(id).torsoFacingDegrees = heading + aim;
        world.setFloat(id, components.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_FACING_DEGREES,
                heading + aim);
        int base = world.getInt(id, components.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_FLAGS)
                & ~(LayeredMechAppearance.FLAG_CHAINGUN_FLASH | LayeredMechAppearance.FLAG_SRM_FLASH
                | LayeredMechAppearance.FLAG_MOVING);
        world.setInt(id, components.MECH_LAYERED_ANIMATION, BattleComponents.MECH_LAYERED_FLAGS, base | flags);
    }

    /** The open cell nearest (cx, cy) with {@link #HALF_W} by {@link #HALF_H} walkable cells round it; null if none. */
    private static int[] openFloor(NavigationGrid grid, int cx, int cy) {
        int[] best = null;
        long bestD = Long.MAX_VALUE;
        for (int y = HALF_H; y < grid.getHeight() - HALF_H; y++) {
            for (int x = HALF_W; x < grid.getWidth() - HALF_W; x++) {
                long d = (long) (x - cx) * (x - cx) + (long) (y - cy) * (y - cy);
                if (d >= bestD || !open(grid, x, y)) continue;
                bestD = d;
                best = new int[] {x, y};
            }
        }
        return best;
    }

    private static boolean open(NavigationGrid grid, int x, int y) {
        for (int dy = -HALF_H; dy <= HALF_H; dy++)
            for (int dx = -HALF_W; dx <= HALF_W; dx++) if (!grid.isWalkable(x + dx, y + dy)) return false;
        return true;
    }
}
