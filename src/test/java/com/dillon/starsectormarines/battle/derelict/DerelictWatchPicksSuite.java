package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.drone.Flock;
import com.dillon.starsectormarines.battle.drone.FlockDuct;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Picture cards for the derelict watch's four picks (`derelict-watch.md`), on the vast derelict ruin through the look
 * preset at 1920x1080: rounds and service ducts, the facility's wake, the walker sizes and repair. Each scene is the
 * fixture played only until the network stands, then staged (a duct sent, a stage raised, walkers set side by side,
 * a walker felled for its wreck) and stepped until the staged thing shows. Opt-in. No outcome is judged.
 */
public final class DerelictWatchPicksSuite implements SnapshotSuite {

    static final String FIXTURE = DerelictHeaviesLookSuite.VAST_FIXTURE;
    static final int SETTLE_TICKS = 120;
    static final int W = 1920;
    static final int H = 1080;
    static final float WIDE = 34f;
    static final float CLOSE = 22f;

    @Override public String id() { return "derelict-watch-picks"; }

    @Override public String label() { return "Derelict watch picks: movement, waking, walker sizes, repair"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            JSONObject fixture = new JSONObject(Files.readString(context.projectRoot().resolve(FIXTURE),
                    StandardCharsets.UTF_8));
            List<SnapshotArtifact> out = new ArrayList<>();
            movement(context, fixture, out);
            waking(context, fixture, out);
            sizes(context, fixture, out);
            repair(context, fixture, out);
            doors(context, fixture, out);
            ram(context, fixture, out);
            return out;
        }
    }

    // ---- movement ----

    private static void movement(SnapshotContext context, JSONObject fixture, List<SnapshotArtifact> out)
            throws Exception {
        BattleSimulation sim = settled(fixture, SETTLE_TICKS);
        DerelictSecurityDirector director = sim.derelictSecurity();
        log("bulkheads laid %d", director.bulkheads().size());
        // The rounds walker on its round: played on a while it walks.
        for (DerelictSecurityDirector.Walker w : director.walkers()) {
            if (w.frame() != DerelictWalkerFrame.ROUNDS) continue;
            PatrolRound round = director.roundOf(w.squadId());
            log("rounds lance %d zone %d: %d places, first (%d, %d)", w.squadId(), w.zoneId(),
                    round == null ? 0 : round.places().size(), w.postX(), w.postY());
        }
        int roundsSquad = -1;
        for (DerelictSecurityDirector.Walker w : director.walkers()) {
            if (w.frame() == DerelictWalkerFrame.ROUNDS && roundsSquad < 0) roundsSquad = w.squadId();
        }
        for (int i = 0; i < 6 && roundsSquad >= 0; i++) {
            play(sim, 150);
            if (sim.squadMemberCount(roundsSquad) <= 0) break;
            long r = sim.squadMemberAt(roundsSquad, 0);
            log("t=%d rounds walker at (%.1f, %.1f), order %s", sim.getSimTickIndex(), sim.world().x(r),
                    sim.world().y(r), director.orderOf(roundsSquad));
        }
        long rounds = first(sim, director, DerelictWalkerFrame.ROUNDS);
        BufferedImage roundShot = null;
        if (rounds != 0L) {
            log("rounds walker at (%.1f, %.1f)", sim.world().x(rounds), sim.world().y(rounds));
            roundShot = look(context, sim, sim.world().renderX(rounds), sim.world().renderY(rounds), CLOSE, true,
                    "movement-rounds");
        }
        // A flock through the ducts: sent from its home vent up in a court's light well.
        BattleSimulation ducted = settled(fixture, SETTLE_TICKS);
        DerelictSecurityDirector network = ducted.derelictSecurity();
        ServiceDucts.Vent court = null;
        for (ServiceDucts.Vent vent : network.ducts().vents()) if (vent.id() >= network.network().zones().size()) court = vent;
        Flock flock = null;
        int zoneOfCourt = court == null ? -1 : court.zoneId();
        if (court != null) {
            // The court's zone wakes, so its flock is up and out of its cradle before it goes into the ducts.
            network.network().stir(zoneOfCourt, ducted.getSimTickIndex());
            play(ducted, 150);
            for (Flock f : ducted.flocks().flocks()) {
                if (f.hatch() == null && f.size() > 0 && (flock == null || f.size() > flock.size())) flock = f;
            }
        }
        BufferedImage ductShot = null;
        if (court != null && flock != null) {
            // In at the open-sky vent nearest the drones: a flock never flies under the roof to a vent there.
            long lead = flock.memberIds().get(0);
            ServiceDucts.Vent entry = null;
            for (ServiceDucts.Vent vent : network.ducts().vents()) {
                if (vent == court) continue;
                if (entry == null || Math.hypot(vent.cellX() - ducted.world().x(lead), vent.cellY() - ducted.world().y(lead))
                        < Math.hypot(entry.cellX() - ducted.world().x(lead), entry.cellY() - ducted.world().y(lead))) {
                    entry = vent;
                }
            }
            boolean went = ducted.flocks().sendThroughDuct(flock, entry.cellX() + 0.5f, entry.cellY() + 0.5f,
                    court.cellX() + 0.5f, court.cellY() + 0.5f, 3f);
            log("duct: flock %d of %d drones sent %s from (%.1f, %.1f)", flock.id(), flock.size(), went,
                    flock.anchorX(), flock.anchorY());
            for (int t = 0; t < 1200 && flock.inDuct(); t++) {
                step(ducted);
                if (t % 60 == 0 && flock.duct() != null) {
                    log("duct t=%d phase %s inside %d outside %d", t, flock.duct().phase(), flock.duct().inside(),
                            flock.size());
                    for (long m : flock.memberIds()) {
                        log("  member at (%.1f, %.1f) render (%.1f, %.1f)", ducted.world().x(m), ducted.world().y(m),
                                ducted.world().renderX(m), ducted.world().renderY(m));
                        break;
                    }
                }
                if (flock.duct() != null && flock.duct().phase() == FlockDuct.Phase.OUT) {
                    play(ducted, 6);
                    break;
                }
            }
            log("duct ended: inDuct %s duct %s surfaced %d, flock still listed %s", flock.inDuct(),
                    flock.duct() == null ? "none" : flock.duct().phase(), flock.duct() == null ? -1 : flock.duct().surfaced(),
                    ducted.flocks().flocks().contains(flock));
            play(ducted, 40);
            log("duct flock up at the court (%d, %d), %d drones", court.cellX(), court.cellY(), flock.size());
            ductShot = look(context, ducted, court.cellX() + 0.5f, court.cellY() + 0.5f, WIDE, false, "movement-duct");
        } else {
            log("no court vent on this site; no duct shot");
        }
        BufferedImage today = null;
        long heavy = first(sim, director, DerelictWalkerFrame.LINE);
        if (heavy != 0L) {
            today = look(context, settled(fixture, SETTLE_TICKS), sim.world().renderX(heavy),
                    sim.world().renderY(heavy), CLOSE, true, "movement-today");
        }
        add(out, "movement/a-rounds-and-ducts.png", pair(roundShot, ductShot));
        add(out, "movement/b-rounds-only.png", roundShot);
        add(out, "movement/c-ducts-only.png", ductShot);
        add(out, "movement/today-posts.png", today);
    }

    // ---- waking ----

    private static void waking(SnapshotContext context, JSONObject fixture, List<SnapshotArtifact> out)
            throws Exception {
        BattleSimulation quiet = settled(fixture, SETTLE_TICKS);
        DerelictSecurityDirector director = quiet.derelictSecurity();
        int zones = director.network().zones().size();
        int outer = zones - 1;
        int inner = Math.max(0, zones - 2);
        float[] outerAt = at(quiet, director.reserveBays().get(outer));
        float[] innerAt = at(quiet, director.reserveBays().get(inner));
        BufferedImage outerQuiet = outerAt == null ? null
                : look(context, quiet, outerAt[0], outerAt[1], WIDE, false, "waking-quiet");
        BufferedImage innerQuiet = innerAt == null ? null
                : look(context, quiet, innerAt[0], innerAt[1], WIDE, false, "waking-inner-quiet");

        BattleSimulation roused = settled(fixture, SETTLE_TICKS);
        roused.derelictSecurity().wake().advance(FacilityWake.Stage.ROUSED);
        play(roused, 240);
        log("roused: %d flocks up", roused.flocks().flocks().size());
        BufferedImage outerRoused = outerAt == null ? null
                : look(context, roused, outerAt[0], outerAt[1], WIDE, false, "waking-roused");
        add(out, "waking/a-stir-roused.png", outerRoused);
        add(out, "waking/b-depth.png", pair(outerRoused, innerQuiet));
        add(out, "waking/c-breach-only.png", outerQuiet);
        add(out, "waking/today-quiet.png", outerQuiet);
    }

    // ---- doors: a bulkhead sealed and opened, a dead door forced by a hall frame ----

    private static void doors(SnapshotContext context, JSONObject fixture, List<SnapshotArtifact> out)
            throws Exception {
        BattleSimulation sim = settled(fixture, SETTLE_TICKS);
        DerelictSecurityDirector director = sim.derelictSecurity();
        if (!director.bulkheads().isEmpty()) {
            RuinsDelve.Area span = director.bulkheads().get(0).door().span();
            float x = span.centreX() + 1f, y = span.centreY() + 0.5f;
            log("bulkhead at (%d, %d) %s", span.left(), span.bottom(),
                    sim.getRuinsWorks().doorStates().stanceOf(director.bulkheads().get(0).door()));
            BufferedImage sealed = look(context, sim, x, y, CLOSE, true, "doors-bulkhead-sealed");
            director.wake().advance(FacilityWake.Stage.HUNTING);
            play(sim, 60);
            log("bulkhead opened %s, now %s", director.bulkheadsOpened(),
                    sim.getRuinsWorks().doorStates().stanceOf(director.bulkheads().get(0).door()));
            BufferedImage open = look(context, sim, x, y, CLOSE, true, "doors-bulkhead-open");
            add(out, "doors/bulkhead-sealed.png", sealed);
            add(out, "doors/bulkhead-open.png", open);
        }
        BattleSimulation rammed = settled(fixture, SETTLE_TICKS);
        DerelictSecurityDirector network = rammed.derelictSecurity();
        RuinsDelve.LeafDoor door = null;
        for (DerelictSecurityDirector.Walker w : network.walkers()) {
            if (w.frame() != DerelictWalkerFrame.HALL) continue;
            RuinsDelve.LeafDoor dead = DerelictSecurity.deadDoorOf(rammed.getRuinsWorks().delve(), w.postX(), w.postY());
            if (dead == null) continue;
            door = dead;
            network.network().stir(w.zoneId(), rammed.getSimTickIndex());
            break;
        }
        boolean standIn = door == null;
        if (standIn) {
            // No hall frame is shut in on this site: the first dead hall door is forced by the director's own calls.
            for (RuinsDelve.LeafDoor d : rammed.getRuinsWorks().doorStates().doors()) {
                if (d.stance() == RuinsDelve.DoorStance.DEAD
                        || (door == null && !d.cargo() && d.length() >= 3 && d.stance() == RuinsDelve.DoorStance.ANSWERS)) {
                    door = d;
                    if (d.stance() == RuinsDelve.DoorStance.DEAD) break;
                }
            }
            if (door == null) {
                Map<Object, Integer> n = new TreeMap<>();
                for (RuinsDelve.LeafDoor d : rammed.getRuinsWorks().doorStates().doors()) {
                    n.merge(d.stance() + (d.cargo() ? "/cargo" : "") + "/" + d.length(), 1, Integer::sum);
                }
                log("no dead door on this site: %s", n);
                return;
            }
            log("no hall frame with a dead door on this site; forcing the %s door at (%d, %d) by the director's calls",
                    door.stance(), door.span().left(), door.span().bottom());
        }
        float x = door.span().centreX() + 0.5f, y = door.span().centreY() + 0.5f;
        BufferedImage before = look(context, rammed, x, y, CLOSE, true, "doors-dead");
        if (standIn) {
            RuinsDelve.Area span = door.span();
            if (door.stance() == RuinsDelve.DoorStance.DEAD) {
                rammed.mapEditor().openPassage(span.left(), span.bottom(), span.centreX(), span.centreY());
                rammed.mapEditor().openPassage(span.right(), span.top(), span.centreX(), span.centreY());
            }
            rammed.getRuinsWorks().doorStates().force(door);
        }
        for (int t = 0; t < 3000 && !standIn && network.forcedDoors().isEmpty(); t += 60) play(rammed, 60);
        log("forced %d doors by t=%d, stance %s", network.forcedDoors().size(), rammed.getSimTickIndex(),
                rammed.getRuinsWorks().doorStates().stanceOf(door));
        play(rammed, 60);
        add(out, "doors/dead-before.png", before);
        add(out, "doors/forced.png", look(context, rammed, x, y, CLOSE, true, "doors-forced"));
    }

    // ---- a hall frame ramming a dead door, on a seed whose site has one ----

    /** Seeds tried, from the fixture's own, for a hall-bound frame with a dead door on its hall's face. */
    static final int RAM_SEEDS = 8;

    private static void ram(SnapshotContext context, JSONObject fixture, List<SnapshotArtifact> out)
            throws Exception {
        long seed0 = fixture.getLong("seed");
        for (int k = 0; k < RAM_SEEDS; k++) {
            JSONObject f = new JSONObject(fixture.toString());
            f.put("seed", seed0 + k);
            BattleSimulation sim = settled(f, 30);
            DerelictSecurityDirector director = sim.derelictSecurity();
            int halls = 0, bound = 0, dead = 0, withDoor = 0;
            for (RuinsDelve.LeafDoor d : sim.getRuinsWorks().doorStates().doors()) {
                if (d.stance() == RuinsDelve.DoorStance.DEAD) dead++;
            }
            for (DerelictSecurityDirector.Walker w : director.walkers()) {
                if (w.frame() != DerelictWalkerFrame.HALL) continue;
                halls++;
                if (director.hallBound(w.squadId())) bound++;
                if (DerelictSecurity.deadDoorOf(sim.getRuinsWorks().delve(), w.postX(), w.postY()) != null) withDoor++;
            }
            log("ram: seed %d: %d hall frames, %d bound, %d with a dead door on their hall, %d dead doors",
                    seed0 + k, halls, bound, withDoor, dead);
            for (DerelictSecurityDirector.Walker w : director.walkers()) {
                RuinsDelve.LeafDoor door = director.deadDoorOf(w.squadId());
                if (w.frame() != DerelictWalkerFrame.HALL || door == null || !director.hallBound(w.squadId())) continue;
                log("ram: seed %d, lance %d, dead door at (%d, %d)", seed0 + k, w.squadId(), door.span().left(),
                        door.span().bottom());
                float x = door.span().centreX() + 0.5f, y = door.span().centreY() + 0.5f;
                add(out, "ram/before.png", look(context, sim, x, y, CLOSE, true, "ram-before"));
                director.network().stir(w.zoneId(), sim.getSimTickIndex());
                boolean mid = false;
                for (int t = 0; t < 6000 && director.forcedDoors().isEmpty(); t++) {
                    if (!step(sim)) break;
                    if (!mid && director.ramProgress(w.squadId()) >= 0.5f) {
                        mid = true;
                        log("ram: half way at t=%d", sim.getSimTickIndex());
                        add(out, "ram/mid.png", look(context, sim, x, y, CLOSE, true, "ram-mid"));
                    }
                }
                log("ram: forced %d by t=%d, stance %s, still bound %s", director.forcedDoors().size(),
                        sim.getSimTickIndex(), sim.getRuinsWorks().doorStates().stanceOf(door),
                        director.hallBound(w.squadId()));
                play(sim, 120);
                add(out, "ram/after.png", look(context, sim, x, y, CLOSE, true, "ram-after"));
                return;
            }
        }
        log("ram: no seed of %d has a hall-bound frame with a dead door", RAM_SEEDS);
    }

    // ---- walker sizes ----

    private static void sizes(SnapshotContext context, JSONObject fixture, List<SnapshotArtifact> out)
            throws Exception {
        for (String option : new String[] {"a-medium-and-large", "b-large-only", "c-medium-only", "today-heavy"}) {
            BattleSimulation sim = settled(fixture, SETTLE_TICKS);
            DerelictSecurityDirector director = sim.derelictSecurity();
            RuinsDelve delve = sim.getRuinsWorks() == null ? null : sim.getRuinsWorks().delve();
            List<int[]> halls = DerelictSecurity.hallPosts(delve, 1);
            if (halls.isEmpty()) {
                log("no plant hall on this site; no size shots");
                return;
            }
            int[] hall = halls.get(0);
            long large = first(sim, director, DerelictWalkerFrame.HALL);
            long heavy = first(sim, director, DerelictWalkerFrame.LINE);
            long medium = first(sim, director, DerelictWalkerFrame.ROUNDS);
            boolean showLarge = option.startsWith("a") || option.startsWith("b");
            boolean showMedium = option.startsWith("a") || option.startsWith("c");
            place(sim, heavy, hall[0] + 0.5f, hall[1] + 0.5f);
            if (showLarge) place(sim, large, hall[0] - 4.5f, hall[1] + 0.5f);
            else take(sim, large);
            if (showMedium) place(sim, medium, hall[0] + 3.5f, hall[1] + 0.5f);
            else take(sim, medium);
            step(sim);
            place(sim, heavy, hall[0] + 0.5f, hall[1] + 0.5f);
            if (showLarge) place(sim, large, hall[0] - 4.5f, hall[1] + 0.5f);
            if (showMedium) place(sim, medium, hall[0] + 3.5f, hall[1] + 0.5f);
            add(out, "sizes/" + option + ".png", look(context, sim, hall[0] + 0.5f, hall[1] + 0.5f, CLOSE, true,
                    "sizes-" + option));
        }
    }

    // ---- repair ----

    private static void repair(SnapshotContext context, JSONObject fixture, List<SnapshotArtifact> out)
            throws Exception {
        for (String option : new String[] {"a-bay-and-raise", "b-bay-only", "c-raise-no-bay"}) {
            BattleSimulation sim = settled(fixture, SETTLE_TICKS);
            DerelictSecurityDirector director = sim.derelictSecurity();
            DerelictSecurityDirector.Walker felled = null;
            for (DerelictSecurityDirector.Walker walker : director.walkers()) {
                if (walker.frame() == DerelictWalkerFrame.LINE && sim.squadMemberCount(walker.squadId()) > 0) {
                    felled = walker;
                    break;
                }
            }
            if (felled == null) return;
            long node = director.nodes().get(felled.zoneId());
            Long bay = director.serviceBays().get(felled.zoneId());
            if (option.startsWith("c") && bay != null) take(sim, bay);
            float fx = 0f;
            float fy = 0f;
            if (!option.startsWith("b")) {
                long walker = sim.squadMemberAt(felled.squadId(), 0);
                // Felled beside its node, where the zone's drone works; held there for a pulse so the network
                // knows where it stood.
                for (int t = 0; t < 20; t++) {
                    place(sim, walker, sim.world().x(node) + 5f, sim.world().y(node));
                    step(sim);
                }
                fx = sim.world().x(walker);
                fy = sim.world().y(walker);
                sim.applyDamage(walker, 1e6f, 1e3f);
                Wreck wreck = null;
                for (int t = 0; t < 2400; t++) {
                    step(sim);
                    for (Wreck w : director.wrecks()) if (w.squadId == felled.squadId()) wreck = w;
                    if (wreck != null && wreck.progress() > 0.35f) break;
                }
                log("%s: wreck %s at %.2f raised", option, wreck == null ? "none" : wreck.state(),
                        wreck == null ? 0f : wreck.progress());
            } else {
                play(sim, 60);
            }
            float cx = option.startsWith("b") ? sim.world().renderX(node) : (sim.world().x(node) + fx) / 2f;
            float cy = option.startsWith("b") ? sim.world().renderY(node) : (sim.world().y(node) + fy) / 2f;
            add(out, "repair/" + option + ".png", look(context, sim, cx, cy, CLOSE, true, "repair-" + option));
        }
    }

    // ---- staging ----

    private static BattleSimulation settled(JSONObject fixture, int ticks) throws Exception {
        BattleSimulation sim = HeadlessBattles.fixture(fixture);
        play(sim, ticks);
        if (sim.derelictSecurity() == null) throw new IllegalStateException("the fixture stands no derelict network");
        return sim;
    }

    private static void play(BattleSimulation sim, int ticks) {
        for (int i = 0; i < ticks; i++) if (!step(sim)) return;
    }

    /** Advances one tick; false once the battle is decided. */
    private static boolean step(BattleSimulation sim) {
        int before = sim.getSimTickIndex();
        sim.advance(BattleSimulation.TICK_DT);
        return sim.getSimTickIndex() != before;
    }

    private static long first(BattleSimulation sim, DerelictSecurityDirector director, DerelictWalkerFrame frame) {
        for (DerelictSecurityDirector.Walker walker : director.walkers()) {
            if (walker.frame() == frame && sim.squadMemberCount(walker.squadId()) > 0) {
                return sim.squadMemberAt(walker.squadId(), 0);
            }
        }
        return 0L;
    }

    private static void place(BattleSimulation sim, long id, float x, float y) {
        if (id == 0L || sim.resolveUnit(id) == 0L) return;
        sim.world().setPos(id, x, y);
    }

    private static void take(BattleSimulation sim, long id) {
        if (id != 0L && sim.resolveUnit(id) != 0L) sim.takeOffTheField(id);
    }

    private static float[] at(BattleSimulation sim, Long id) {
        if (id == null || sim.resolveUnit(id) == 0L) return null;
        return new float[] {sim.world().renderX(id), sim.world().renderY(id)};
    }

    private static BufferedImage look(SnapshotContext context, BattleSimulation sim, float x, float y, float cells,
                                      boolean roofless, String name) throws Exception {
        BattleReviewFrameRenderer renderer = BattleReviewFrameRenderer.look(context.modRoot(), W, H, x, y, cells);
        if (roofless) renderer = renderer.withoutRoofs();
        return renderer.render(sim, name);
    }

    /** Two shots side by side at 1920x1080: the middle half of each. */
    private static BufferedImage pair(BufferedImage left, BufferedImage right) {
        if (left == null || right == null) return left != null ? left : right;
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(left.getSubimage(W / 4, 0, W / 2, H), 0, 0, null);
        g.drawImage(right.getSubimage(W / 4, 0, W / 2, H), W / 2, 0, null);
        g.fillRect(W / 2 - 2, 0, 4, H);
        g.dispose();
        return out;
    }

    private static void add(List<SnapshotArtifact> out, String name, BufferedImage image) {
        if (image != null) out.add(new SnapshotArtifact(name, image));
    }

    private static void log(String format, Object... args) {
        System.out.printf("    [look/derelict-watch] " + format + "%n", args);
    }
}
