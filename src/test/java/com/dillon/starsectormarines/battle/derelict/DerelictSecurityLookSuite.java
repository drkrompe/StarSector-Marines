package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.mech.FactionMechLoadouts;
import com.dillon.starsectormarines.battle.mech.MechSpawnPlacement;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Derelict security's posts and nodes in their lineage's look (`derelict-security-network.md`, slice 9): the
 * extensive-grade fixture's first gun post folded, the same post unfolded, and its first zone's control node, one still
 * each through the look preset, close and with the roof lifted (a post or node may stand inside the site). The look
 * candidates are shown through {@code -Dsnapshot.artOverlay}; {@code -Dsnapshot.derelict.lineage=contingency} plays the
 * site as contingency security. A company Hound is stood a few cells beside each post and node, after the site has
 * settled and with no tick played after it, so every still shows the derelict art beside a shipped mech for scale and
 * style. Opt-in. No outcome is judged: the site plays a moment and is looked at.
 */
public final class DerelictSecurityLookSuite implements SnapshotSuite {

    static final String FIXTURE = "src/test/resources/battle-fixtures/ruins-derelict-extensive-v1.json";
    static final String LINEAGE_PROPERTY = "snapshot.derelict.lineage";
    static final String CONTINGENCY = "contingency";
    /** Ticks the site plays before its stills: its posts stood and folded, its zones dormant. */
    static final int SETTLE_TICKS = 30;
    static final float CLOSE_CELLS = 16f;
    static final float DRONE_ASIDE_CELLS = 3f;
    /** How far to the left of the post or node the scale Hound is looked for, in cells. */
    static final int HOUND_ASIDE_CELLS = 3;

    @Override public String id() { return "derelict-security-look"; }

    @Override public String label() { return "Derelict security's gun posts, folded and unfolded, and control nodes"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            JSONObject fixture = new JSONObject(Files.readString(context.projectRoot().resolve(FIXTURE),
                    StandardCharsets.UTF_8));
            if (CONTINGENCY.equals(System.getProperty(LINEAGE_PROPERTY))) occupant(fixture, "CONTINGENCY_SECURITY");
            BattleSimulation sim = HeadlessBattles.fixture(fixture);
            while (sim.getSimTickIndex() < SETTLE_TICKS && step(sim)) {
                // played to the site's settled posts, or to the end of a battle decided before them
            }
            DerelictSecurityDirector director = sim.derelictSecurity();
            List<SnapshotArtifact> out = new ArrayList<>();
            if (director == null) return out;
            System.out.println("    [look/derelict] look " + director.look() + ", " + director.posts().size()
                    + " posts, " + director.nodes().size() + " nodes");
            if (!director.posts().isEmpty()) {
                long post = director.posts().keySet().iterator().next();
                long hound = hound(sim, post);
                out.add(still(context, sim, post, hound, "derelict-post-folded"));
                // Unfolded: the zone's power on, as when it wakes. Rendered before the director's next pulse cuts it.
                for (long id : director.posts().keySet()) sim.turretState().restorePower(id);
                out.add(still(context, sim, post, hound, "derelict-post-unfolded"));
            }
            if (!director.nodes().isEmpty()) {
                // The zone's repair drone waits over its node; it is moved aside so the node is seen whole.
                for (long drone : director.repairDrones()) {
                    float x = sim.world().x(drone) + DRONE_ASIDE_CELLS;
                    float y = sim.world().y(drone);
                    AirBody body = sim.world().kinematics(drone);
                    if (body != null) body.teleport(x, y, 0f);
                    sim.world().setPos(drone, x, y);
                }
                long node = director.nodes().values().iterator().next();
                out.add(still(context, sim, node, hound(sim, node), "derelict-node"));
            }
            return out;
        }
    }

    /**
     * One look-preset still centred on a unit, close, the roof lifted. Prints where the unit falls in the image (the
     * camera keeps to the map, so a unit by its edge is off centre), and where the scale Hound {@code beside} falls
     * when there is one, for the picks cards' crops.
     */
    static SnapshotArtifact still(SnapshotContext context, BattleSimulation sim, long unit, long beside, String name)
            throws Exception {
        float x = sim.world().renderX(unit);
        float y = sim.world().renderY(unit);
        BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, x, y,
                CLOSE_CELLS).withoutRoofs();
        BattleCamera camera = renderer.cameraOf(sim);
        System.out.printf("    [look/derelict] %s at %d,%d%n", name, Math.round(camera.cellToScreenX(x)),
                Math.round(1080 - camera.cellToScreenY(y)));
        if (beside != 0L) System.out.printf("    [look/derelict] %s-hound at %d,%d%n", name,
                Math.round(camera.cellToScreenX(sim.world().renderX(beside))),
                Math.round(1080 - camera.cellToScreenY(sim.world().renderY(beside))));
        return new SnapshotArtifact(name + ".png", renderer.render(sim, name));
    }

    /**
     * A company Hound set down on standable ground a few cells to the left of {@code unit}, facing nothing in
     * particular, for scale and style beside the derelict art; 0 when no ground near it stands one.
     */
    static long hound(BattleSimulation sim, long unit) {
        int x = (int) Math.floor(sim.world().x(unit)) - HOUND_ASIDE_CELLS;
        int y = (int) Math.floor(sim.world().y(unit));
        MechSpawnPlacement.Point at = null;
        for (int r = 0; at == null && r <= 6; r++)
            for (int dy = -r; at == null && dy <= r; dy++) for (int dx = -r; at == null && dx <= r; dx++)
                if (Math.max(Math.abs(dx), Math.abs(dy)) == r)
                    at = MechSpawnPlacement.nearCell(sim.getGrid(), MechVariant.HOUND.radius, x + dx, y + dy,
                            (px, py) -> true, (px, py) -> true);
        if (at == null) {
            System.out.println("    [look/derelict] no standable ground for the scale Hound");
            return 0L;
        }
        int squad = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long mech = sim.spawn(new EntitySpec("Scale Hound", Faction.MARINE, UnitType.HEAVY_MECH, at.cellX(),
                at.cellY()).atPosition(at.x(), at.y()).mechVariant(MechVariant.HOUND).squad(squad));
        sim.world().attachMechLoadout(mech, FactionMechLoadouts.create(MechVariant.HOUND,
                MechVariant.HOUND.defaultRole, "independent"));
        Squad s = sim.getSquad(squad);
        s.leaderId = mech;
        s.originalSize = 1;
        return mech;
    }

    /** Sets every {@code occupant} in the fixture, at any depth. */
    static void occupant(Object node, String occupant) throws Exception {
        if (node instanceof JSONObject o) {
            for (Iterator<?> keys = o.keys(); keys.hasNext(); ) {
                String key = (String) keys.next();
                if (key.equals("occupant")) o.put(key, occupant);
                else occupant(o.opt(key), occupant);
            }
        } else if (node instanceof JSONArray a) {
            for (int i = 0; i < a.length(); i++) occupant(a.get(i), occupant);
        }
    }

    /** Advances one tick; false once the battle is decided, when the tick stops advancing. */
    private static boolean step(BattleSimulation sim) {
        var before = sim.getSimTickIndex();
        sim.advance(BattleSimulation.TICK_DT);
        return sim.getSimTickIndex() != before;
    }
}
