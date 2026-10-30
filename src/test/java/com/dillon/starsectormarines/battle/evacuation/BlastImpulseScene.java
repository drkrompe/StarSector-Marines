package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.StaggerService.Cause;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.CombatTelemetryService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.ArmorClass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A blast's shove ({@code mechs-nouns.md}, Blast impulse): real rounds' detonations, scripted onto a marine
 * squad, a light hull, a heavy hull and mechs, so what each weapon family's shove does can be read without a
 * battle around it. The bodies have deep health and no enemy: the scene is about the shove, not who wins.
 * <ul>
 *   <li>{@code srm-single}: one anti-armor SRM on the middle of a squad staggers the man it strikes and knocks
 *       no one down.</li>
 *   <li>{@code srm-salvo-at}, {@code srm-salvo-frag}: an SRM-15's three salvos of five on the same man; the
 *       anti-armor warhead knocks down the man it keeps striking, the fragmentation one the squad around him.</li>
 *   <li>{@code srm-salvo-suits}: the fragmentation salvo on power suits, which mostly keep their feet.</li>
 *   <li>{@code lrm-scout-car}, {@code lrm-tank}, {@code lrm-hound}, {@code lrm-bulwark}: an LRM-15's fifteen
 *       anti-armor missiles on a scout car (shoved), a tank and a heavy mech (unmoved), a light mech (a trace).</li>
 *   <li>{@code mortar}, {@code dc-5}: one heavy mortar round, and one DC-5 demolition shell, between a squad and a
 *       scout car.</li>
 *   <li>{@code wall}: a DC-5 shell drives a man into a wall, which he does not pass.</li>
 * </ul>
 */
public final class BlastImpulseScene implements BehaviorScene {

    static final int TICKS = 150;
    static final int FIRST = 15;
    private static final float DEEP = 50_000f;
    private static final float CX = 20.5f, CY = 10.5f;
    /** Squad around (CX, CY): the struck man in the middle, four about him. */
    private static final float[][] SQUAD = {{0f, 0f}, {-.75f, .3f}, {.7f, .35f}, {-.6f, -.55f}, {.55f, -.6f}};
    /** Where the rounds of a salvo land about their aim point: a tight, fixed pattern. */
    private static final float[][] SCATTER = {{0f, 0f}, {.25f, .1f}, {-.2f, .2f}, {.1f, -.25f}, {-.15f, -.1f}};

    static final List<String> LOOPS = List.of("srm-single", "srm-salvo-at", "srm-salvo-frag", "srm-salvo-suits",
            "lrm-scout-car", "lrm-tank", "lrm-hound", "lrm-bulwark", "mortar", "dc-5", "wall");

    @Override public String id() { return "blast-impulse"; }

    @Override public String label() {
        return "Blast impulse: missile, shell and mortar blasts shove men and light hulls, never heavy ones or through walls";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : LOOPS) reports.add(playOne(loop, frames, 3));
        return reports;
    }

    /** One round landing at a tick: its weapon, where, and the body its contact charge strikes (0 for none). */
    record Round(int tick, String weapon, float x, float y, long contact) {}

    /** The world of one loop, built and scripted, for this scene and its look. */
    static final class Setup {
        final SceneWorld world;
        final BattleSimulation sim;
        final List<Long> squad = new ArrayList<>();
        long hull, mech, struck;
        final List<Round> rounds = new ArrayList<>();

        Setup(String loop) throws Exception {
            boolean wall = loop.equals("wall");
            NavigationGrid grid = new NavigationGrid(40, 20);
            CellTopology topology = new CellTopology(40, 20);
            for (int y = 0; y < 20; y++) for (int x = 0; x < 40; x++) if (!wall || x != 22) grid.setWalkableFloor(x, y);
            SceneBuilder builder = SceneBuilder.over(grid, topology).seed(20261030L).missionCompletion(false);
            boolean squadLoop = loop.startsWith("srm") || loop.equals("mortar") || loop.equals("dc-5");
            ArmorClass armor = loop.equals("srm-salvo-suits") ? ArmorClass.POWER_SUIT : ArmorClass.STANDARD;
            if (squadLoop) {
                for (int i = 0; i < SQUAD.length; i++) {
                    builder.unit("m" + i, Faction.MARINE, UnitType.MARINE, (int) CX, (int) CY,
                            spec -> spec.health(DEEP).armorClass(armor));
                }
            }
            if (wall) builder.unit("m0", Faction.MARINE, UnitType.MARINE, 21, (int) CY, spec -> spec.health(DEEP));
            if (loop.equals("lrm-hound") || loop.equals("lrm-bulwark")) {
                builder.squad("mech").faction(Faction.MARINE).size(1).at((int) CX, (int) CY)
                        .mech(loop.equals("lrm-hound") ? MechVariant.HOUND : MechVariant.BULWARK, MechRole.ASSAULT)
                        .stationary().health(DEEP).done();
            }
            // Eyes on the ground, out of every blast, so the frames draw what happens.
            builder.unit("observer", Faction.MARINE, UnitType.MARINE, 8, 4, spec -> spec.health(DEEP));
            world = builder.build();
            sim = world.sim();
            if (squadLoop) {
                for (int i = 0; i < SQUAD.length; i++) {
                    long id = world.unit("m" + i);
                    sim.world().setPos(id, CX + SQUAD[i][0], CY + SQUAD[i][1]);
                    squad.add(id);
                }
                struck = squad.get(0);
            }
            if (wall) {
                struck = world.unit("m0");
                sim.world().setPos(struck, 21.45f, CY);
                squad.add(struck);
            }
            if (loop.startsWith("lrm-") || loop.equals("mortar") || loop.equals("dc-5")) {
                VehicleType type = loop.equals("lrm-tank") ? VehicleType.BREAKTHROUGH_TANK : VehicleType.SCOUT_CAR;
                if (!loop.equals("lrm-hound") && !loop.equals("lrm-bulwark")) {
                    float hx = loop.startsWith("lrm-") ? CX : CX + 2.6f;
                    hull = sim.convoy().spawn(type, Faction.MARINE, VehicleMission.deployed(hx, CY));
                    sim.convoy().body(hull).teleport(hx, CY, 0f);
                }
            }
            if (loop.equals("lrm-hound") || loop.equals("lrm-bulwark")) mech = world.members("mech")[0];
            script(loop);
        }

        private void script(String loop) {
            switch (loop) {
                case "srm-single" -> rounds.add(new Round(FIRST, "weapon.mech-srm", CX, CY, struck));
                case "srm-salvo-at" -> salvo(15, "weapon.mech-srm", CX, CY, struck, 3, 18);
                case "srm-salvo-frag", "srm-salvo-suits" -> salvo(15, "weapon.mech-srm-frag", CX, CY, 0L, 3, 18);
                case "lrm-scout-car", "lrm-tank" -> salvo(15, "weapon.mech-lrm", CX, CY, hull, 3, 18);
                case "lrm-hound", "lrm-bulwark" -> salvo(15, "weapon.mech-lrm", CX, CY, mech, 3, 18);
                case "mortar" -> rounds.add(new Round(FIRST, "weapon.vehicle-mortar", CX + 1.1f, CY, 0L));
                case "dc-5" -> rounds.add(new Round(FIRST, "weapon.mech-dc-5", CX + 1.1f, CY, 0L));
                case "wall" -> rounds.add(new Round(FIRST, "weapon.mech-dc-5", 20.4f, CY, 0L));
                default -> throw new IllegalStateException(loop);
            }
        }

        /** {@code count} rounds, salvos of five three ticks apart, salvos {@code gap} ticks apart. */
        private void salvo(int count, String weapon, float x, float y, long contact, int spacing, int gap) {
            for (int i = 0; i < count; i++) {
                float[] off = SCATTER[i % SCATTER.length];
                int tick = FIRST + (i / 5) * gap + (i % 5) * spacing;
                // A rack's first missile of each salvo strikes its man; the rest burst about him.
                rounds.add(new Round(tick, weapon, x + off[0], y + off[1], i % 5 == 0 ? contact : 0L));
            }
        }

        /** Lands the rounds due at {@code tick}. */
        void fire(int tick) {
            for (Round r : rounds) {
                if (r.tick() != tick) continue;
                WeaponDef w = WeaponRegistry.require(r.weapon());
                long contact = r.contact() != 0L && sim.world().isAlive(r.contact()) && w.contactDamage > 0f
                        ? r.contact() : 0L;
                sim.detonateNow(new PendingDetonation(CombatTelemetryService.NO_ATTACKER, r.x(), r.y(), 0f,
                        w.aoeRadius, w.damage, w.penetration, w.wallDamage, Faction.DEFENDER, false,
                        w.wallDamageRadius, true, false, contact, contact != 0L ? w.contactDamage : 0f,
                        contact != 0L ? w.contactPenetration : 0f, false,
                        contact != 0L ? new long[] {contact} : new long[0]).withBlast(w.blast()));
            }
        }
    }

    private SceneReport playOne(String loop, FrameSink frames, int every) throws Exception {
        Setup setup = new Setup(loop);
        try (BattleSimulation sim = setup.sim) {
            Trace trace = new Trace(setup);
            SceneRun.of(setup.world).observe(trace)
                    .frames(frames, loop, every, tick -> loop + " / blast impulse / tick " + tick)
                    .run(TICKS);
            return report(loop, setup, trace);
        }
    }

    private SceneReport report(String loop, Setup setup, Trace trace) {
        List<Verdict> verdicts = new ArrayList<>();
        int tumbled = trace.tumbled.size(), staggered = trace.staggered.size();
        verdicts.add(Verdict.of("slides-not-jumps", trace.maxStep <= .25f,
                "largest move of a body in one tick " + trace.maxStep + " cells"));
        switch (loop) {
            case "srm-single" -> verdicts.add(Verdict.of("staggers-not-tumbles",
                    trace.staggered.contains(setup.struck) && tumbled == 0,
                    "struck man staggered " + trace.staggered.contains(setup.struck) + ", " + tumbled + " down"));
            case "srm-salvo-at" -> verdicts.add(Verdict.of("knocks-its-man-down",
                    trace.tumbled.contains(setup.struck),
                    tumbled + " of " + setup.squad.size() + " down, struck man " + trace.tumbled.contains(setup.struck)));
            case "srm-salvo-frag" -> verdicts.add(Verdict.of("knocks-the-squad-down", tumbled >= 3,
                    tumbled + " of " + setup.squad.size() + " down"));
            case "srm-salvo-suits" -> verdicts.add(Verdict.of("suits-mostly-stand", tumbled <= 1,
                    tumbled + " of " + setup.squad.size() + " down, " + staggered + " staggered"));
            case "lrm-scout-car" -> verdicts.add(Verdict.of("shoves-a-light-hull",
                    trace.hullShift >= .1f && trace.hullShift <= 2f,
                    "scout car shifted " + trace.hullShift + " cells, turned " + trace.hullTurn + " degrees"));
            case "lrm-tank" -> verdicts.add(Verdict.of("tank-unmoved", trace.hullShift < .01f,
                    "tank shifted " + trace.hullShift + " cells"));
            case "lrm-bulwark" -> verdicts.add(Verdict.of("heavy-mech-unmoved", trace.mechShift < .01f,
                    "Bulwark shifted " + trace.mechShift + " cells"));
            case "lrm-hound" -> verdicts.add(Verdict.of("light-mech-a-trace", trace.mechShift < .1f,
                    "Hound shifted " + trace.mechShift + " cells"));
            case "mortar", "dc-5" -> {
                verdicts.add(Verdict.of("shoves-men-and-hull", staggered + tumbled >= 2 && trace.hullShift > .02f,
                        tumbled + " down, " + staggered + " staggered, scout car shifted " + trace.hullShift));
                if (loop.equals("dc-5")) {
                    verdicts.add(Verdict.of("demolition-shell-knocks-down", tumbled >= 1, tumbled + " down"));
                }
            }
            case "wall" -> verdicts.add(Verdict.of("never-through-a-wall", trace.maxX + .25f <= 22f + 1e-3f,
                    "furthest east " + trace.maxX + " (wall face at 22)"));
            default -> throw new IllegalStateException(loop);
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("rounds", setup.rounds.size());
        metrics.put("tumbled", tumbled);
        metrics.put("staggered", staggered);
        metrics.put("meanBodyShift", trace.meanShift(setup));
        metrics.put("maxBodyShift", trace.maxShift);
        metrics.put("hullShift", trace.hullShift);
        metrics.put("hullTurnDegrees", trace.hullTurn);
        metrics.put("mechShift", trace.mechShift);
        metrics.put("maxStep", trace.maxStep);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    /** Fires the script and measures what moved, tick by tick. */
    static final class Trace implements TickObserver {
        private final Setup setup;
        final List<Long> tumbled = new ArrayList<>(), staggered = new ArrayList<>();
        private final Map<Long, float[]> start = new LinkedHashMap<>(), last = new LinkedHashMap<>();
        private final Map<Long, Float> shift = new LinkedHashMap<>();
        float maxStep, maxShift, hullShift, hullTurn, mechShift, maxX = Float.NEGATIVE_INFINITY;
        private float hullX = Float.NaN, hullY, hullFacing;

        Trace(Setup setup) {
            this.setup = setup;
            BattleSimulation sim = setup.sim;
            for (long id : setup.squad) start.put(id, new float[]{sim.world().x(id), sim.world().y(id)});
            if (setup.mech != 0L) start.put(setup.mech, new float[]{sim.world().x(setup.mech), sim.world().y(setup.mech)});
            if (setup.hull != 0L) {
                hullX = sim.convoy().body(setup.hull).x;
                hullY = sim.convoy().body(setup.hull).y;
                hullFacing = sim.convoy().body(setup.hull).facingDegrees;
            }
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            for (Map.Entry<Long, float[]> e : start.entrySet()) {
                long id = e.getKey();
                if (!sim.world().isAlive(id)) continue;
                float x = sim.world().x(id), y = sim.world().y(id);
                float[] prev = last.get(id);
                if (prev != null) maxStep = Math.max(maxStep, (float) Math.hypot(x - prev[0], y - prev[1]));
                last.put(id, new float[]{x, y});
                float s = (float) Math.hypot(x - e.getValue()[0], y - e.getValue()[1]);
                if (id == setup.mech) mechShift = Math.max(mechShift, s);
                else {
                    shift.merge(id, s, Math::max);
                    maxShift = Math.max(maxShift, s);
                    maxX = Math.max(maxX, x);
                }
                if (sim.stagger().isStaggered(id)) {
                    Cause cause = sim.stagger().cause(id);
                    if (cause == Cause.KNOCKED_DOWN && !tumbled.contains(id)) tumbled.add(id);
                    if (cause == Cause.STAGGERED && !staggered.contains(id)) staggered.add(id);
                }
            }
            if (setup.hull != 0L && sim.world().isAlive(setup.hull)) {
                var body = sim.convoy().body(setup.hull);
                hullShift = Math.max(hullShift, (float) Math.hypot(body.x - hullX, body.y - hullY));
                hullTurn = Math.max(hullTurn, Math.abs(((body.facingDegrees - hullFacing) % 360f + 540f) % 360f - 180f));
            }
            setup.fire(tick);
        }

        float meanShift(Setup setup) {
            if (setup.squad.isEmpty()) return 0f;
            float sum = 0f;
            for (long id : setup.squad) sum += shift.getOrDefault(id, 0f);
            return sum / setup.squad.size();
        }
    }
}
