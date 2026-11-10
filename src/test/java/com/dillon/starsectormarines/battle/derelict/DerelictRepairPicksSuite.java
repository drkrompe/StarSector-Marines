package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static com.dillon.starsectormarines.battle.derelict.DerelictWatchPicksSuite.CLOSE;
import static com.dillon.starsectormarines.battle.derelict.DerelictWatchPicksSuite.FIXTURE;
import static com.dillon.starsectormarines.battle.derelict.DerelictWatchPicksSuite.SETTLE_TICKS;
import static com.dillon.starsectormarines.battle.derelict.DerelictWatchPicksSuite.add;
import static com.dillon.starsectormarines.battle.derelict.DerelictWatchPicksSuite.look;
import static com.dillon.starsectormarines.battle.derelict.DerelictWatchPicksSuite.play;
import static com.dillon.starsectormarines.battle.derelict.DerelictWatchPicksSuite.settled;
import static com.dillon.starsectormarines.battle.derelict.DerelictWatchPicksSuite.step;

/**
 * Picture cards for the derelict watch's field-repair and re-arming picks (`derelict-watch.md`, sections 5 and 6), on
 * the vast derelict ruin through the look preset at 1920x1080: the MG-12 tender at rest beside its heavy and welding
 * onto it once worn and awake; a walker with its racks spent walking to its service bay and filling them. Each scene
 * is the fixture played until the network stands and then staged. Opt-in. No outcome is judged.
 */
public final class DerelictRepairPicksSuite implements SnapshotSuite {

    @Override public String id() { return "derelict-repair-picks"; }

    @Override public String label() { return "Derelict watch picks: the field tender and re-arming at the bay"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            JSONObject fixture = new JSONObject(Files.readString(context.projectRoot().resolve(FIXTURE),
                    StandardCharsets.UTF_8));
            List<SnapshotArtifact> out = new ArrayList<>();
            tender(context, fixture, out);
            rearm(context, fixture, out);
            return out;
        }
    }

    private static void tender(SnapshotContext context, JSONObject fixture, List<SnapshotArtifact> out)
            throws Exception {
        BattleSimulation sim = settled(fixture, SETTLE_TICKS);
        DerelictSecurityDirector director = sim.derelictSecurity();
        World world = sim.world();
        DerelictSecurityDirector.Walker tender = null;
        for (DerelictSecurityDirector.Walker w : director.walkers()) {
            if (w.frame() == DerelictWalkerFrame.TENDER && sim.squadMemberCount(w.squadId()) > 0) tender = w;
            if (tender != null) break;
        }
        for (DerelictSecurityDirector.Walker w : director.walkers()) {
            log("walker lance %d %s zone %d post (%d, %d) members %d", w.squadId(), w.frame(), w.zoneId(), w.postX(),
                    w.postY(), sim.squadMemberCount(w.squadId()));
        }
        if (tender == null) {
            int probe = DerelictSecurity.spawn(sim, DerelictWalkerFrame.TENDER, 407, 219);
            log("no tender stood; a probe spawn gives lance %d", probe);
            return;
        }
        long self = sim.squadMemberAt(tender.squadId(), 0);
        long heavy = 0L;
        for (DerelictSecurityDirector.Walker w : director.walkers()) {
            if (w.zoneId() != tender.zoneId() || sim.squadMemberCount(w.squadId()) <= 0) continue;
            if (DerelictWalkerClass.of(w.frame().chassis) != DerelictWalkerClass.HEAVY) continue;
            heavy = sim.squadMemberAt(w.squadId(), 0);
            break;
        }
        log("tender %d zone %d at (%.1f, %.1f); heavy %d at (%.1f, %.1f)", self, tender.zoneId(), world.x(self),
                world.y(self), heavy, world.x(heavy), world.y(heavy));
        add(out, "tender/a-at-rest.png", look(context, sim, (world.renderX(self) + world.renderX(heavy)) / 2f,
                (world.renderY(self) + world.renderY(heavy)) / 2f, CLOSE * 0.6f, true, "tender-at-rest"));
        // Worn and awake: the heavy's plate down to two fifths, its zone stirred until the tender has welded a while.
        world.setArmor(heavy, world.maxArmor(heavy) * 0.4f);
        float before = world.armor(heavy);
        for (int t = 0; t < 1800; t++) {
            if (t % 30 == 0) director.network().stir(tender.zoneId(), sim.getSimTickIndex());
            if (!step(sim)) break;
            FieldTender rules = director.fieldTender(tender.squadId());
            if (t % 240 == 0) {
                log("t=%d zone %s tender at (%.1f, %.1f) stock %.0f charge %d heavy plate %.0f / %.0f", t,
                        director.network().zone(tender.zoneId()).state(), world.x(self), world.y(self),
                        rules == null ? -1f : rules.stock(), rules == null ? 0L : rules.charge(), world.armor(heavy),
                        world.maxArmor(heavy));
            }
            if (rules != null && rules.stock() < FieldTender.TENDER_STOCK - 4f * FieldTender.TENDER_RATE * 6f) break;
        }
        log("welded: heavy plate %.0f -> %.0f", before, world.armor(heavy));
        add(out, "tender/a-welding.png", look(context, sim, (world.renderX(self) + world.renderX(heavy)) / 2f,
                (world.renderY(self) + world.renderY(heavy)) / 2f, CLOSE * 0.6f, true, "tender-welding"));
    }

    private static void rearm(SnapshotContext context, JSONObject fixture, List<SnapshotArtifact> out)
            throws Exception {
        BattleSimulation sim = settled(fixture, SETTLE_TICKS);
        DerelictSecurityDirector director = sim.derelictSecurity();
        World world = sim.world();
        DerelictSecurityDirector.Walker line = null;
        double nearest = Double.MAX_VALUE;
        for (DerelictSecurityDirector.Walker w : director.walkers()) {
            Long b = director.serviceBays().get(w.zoneId());
            if (w.frame() != DerelictWalkerFrame.LINE || sim.squadMemberCount(w.squadId()) <= 0 || b == null) continue;
            long m = sim.squadMemberAt(w.squadId(), 0);
            double d = Math.hypot(world.x(m) - world.x(b), world.y(m) - world.y(b));
            if (d < nearest) {
                nearest = d;
                line = w;
            }
        }
        if (line == null) {
            log("no line frame with a bay");
            return;
        }
        long walker = sim.squadMemberAt(line.squadId(), 0);
        long bay = director.serviceBays().get(line.zoneId());
        List<MechWeaponMount> racks = DerelictSecurityDirector.racks(world.mechLoadout(walker));
        for (MechWeaponMount rack : racks) rack.ammo = 0;
        log("line %d zone %d at (%.1f, %.1f), %d racks emptied; bay at (%.1f, %.1f)", walker, line.zoneId(),
                world.x(walker), world.y(walker), racks.size(), world.x(bay), world.y(bay));
        BayRearm.Step last = null;
        for (int t = 0; t < 7200; t++) {
            if (!step(sim)) break;
            BayRearm rules = director.rearmOf(line.squadId());
            if (t % 240 == 0) {
                log("t=%d walker at (%.1f, %.1f) order %s, racks %s", t, world.x(walker), world.y(walker),
                        director.orderOf(line.squadId()), ammo(racks));
            }
            if (rules != null && BayRearm.atBay(world.x(walker), world.y(walker), world.x(bay), world.y(bay))) {
                last = BayRearm.Step.LOAD;
                break;
            }
        }
        log("rearm: %s, racks %s", last, ammo(racks));
        add(out, "rearm/a-at-the-bay.png", look(context, sim, (world.renderX(walker) + world.renderX(bay)) / 2f,
                (world.renderY(walker) + world.renderY(bay)) / 2f, CLOSE * 0.7f, true, "rearm-at-the-bay"));
        play(sim, 600);
        log("rearm later: racks %s, order %s", ammo(racks), director.orderOf(line.squadId()));
    }

    private static String ammo(List<MechWeaponMount> racks) {
        StringBuilder s = new StringBuilder();
        for (MechWeaponMount rack : racks) s.append(rack.ammo).append('/').append(rack.capacity()).append(' ');
        return s.toString().trim();
    }

    private static void log(String format, Object... args) {
        System.out.printf("    [look/derelict-repair] " + format + "%n", args);
    }
}
