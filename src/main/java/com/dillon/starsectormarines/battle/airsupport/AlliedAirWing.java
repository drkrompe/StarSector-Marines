package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.Runway;
import com.dillon.starsectormarines.battle.world.gen.layout.Laid;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The world's military air wing over a Rav Bloom battle (`air-nouns.md`, Forward observer): an {@link Faction#ALLY} pool
 * of bombers and fighters its own fire-support officer directs, and the forward observers the military posts with its
 * formations, through whom alone it can be called. The militia, the townspeople and the company bring no radio onto
 * this net.
 *
 * <p>Air cannot clear the map: the wing is a few airframes replaced slowly, every call needs an observer with the ground
 * in sight, and the officer judges the anti-air only from what the side has seen, so a mass's unseen spore colonies take
 * aircraft.
 *
 * <p>Where the mission laid it airbases ({@link WingAirbase}), the wing is their bays and flies from them, and keeps a
 * standing patrol of fighters over the formations while the air is contested ({@link StandingPatrol}).
 */
public final class AlliedAirWing {

    private AlliedAirWing() {}

    /** Salts the roving teams' kit rolls, seeded from the wing's home so one map rolls the same teams. */
    private static final long ROVING_SALT = 0x524F56494E47L;

    /**
     * The wing: two bombers back every minute, two fighters every forty-five seconds. UNTUNED first-pass
     * mission-design choice, smaller and slower than a Conquest company's own pool, since the wing serves a whole front.
     */
    static final AirPoolAllowance WING = AirPoolAllowance.of(
            new AirPoolStock(2, 2, 60f), new AirPoolStock(2, 2, 45f), true);

    /**
     * The wing with airbases on the map: its establishment is the bays, a bomber for every bomber's bay and a fighter
     * for every fighter's, and what it has to fly is what stands airworthy in them ({@link AirPoolService}). Replaced
     * only by the bays' crews' rebuilds, so the regeneration here is never read.
     */
    static AirPoolAllowance basedWing(List<WingAirbase> bases) {
        int bombers = 0, fighters = 0;
        for (WingAirbase base : bases) {
            for (var berth : base.berths()) {
                if (base.kindOf(berth) == AirPoolKind.BOMBER) bombers++;
                else fighters++;
            }
        }
        return AirPoolAllowance.of(new AirPoolStock(bombers, bombers, 0f), new AirPoolStock(fighters, fighters, 0f), true);
    }

    /** Standing patrol fighters up at once over a based wing's formations, for each of its bases. UNTUNED. */
    static final int PATROL_UP_PER_BASE = 2;

    /**
     * Gives the battle's allies the wing, flying in over {@code home} (ground behind their own line), posts one
     * observer with each of {@code posts}, and sends roving observer teams up from home now and then
     * ({@link RovingObservers}, watching from {@link BloomVantages}); returns the posts. Called once at setup, before the battle's first tick; any
     * pool the battle already declared for another side is kept.
     */
    public static ForwardObservers install(BattleSimulation sim, float homeX, float homeY,
                                           List<? extends ForwardObservers.Post> posts) {
        return install(sim, homeX, homeY, posts, List.of(), List.of(), s -> false);
    }

    /**
     * As {@link #install(BattleSimulation, float, float, List)}, the wing based on {@code bases} when there are any: its
     * establishment the bases' bays, its sorties rolling off their strips, and a standing patrol of two fighters a base
     * over {@code stations}, flown while {@code contested} says the air is.
     */
    public static ForwardObservers install(BattleSimulation sim, float homeX, float homeY,
                                           List<? extends ForwardObservers.Post> posts, List<WingAirbase> bases,
                                           List<? extends StandingPatrol.Station> stations,
                                           Predicate<BattleSimulation> contested) {
        ForwardObservers observers = new ForwardObservers(Faction.ALLY, posts);
        AirPoolService previous = sim.airPool();
        AirPoolDeclaration declared = previous == null ? AirPoolDeclaration.NONE : previous.declaration();
        AirPoolService service = new AirPoolService(declared.with(Faction.ALLY, bases.isEmpty() ? WING : basedWing(bases)));
        RovingObservers teams = new RovingObservers(Faction.ALLY, homeX, homeY, BloomVantages.of(homeX, homeY),
                (Float.floatToIntBits(homeX) * 31L + Float.floatToIntBits(homeY)) ^ ROVING_SALT);
        service.observedFrom(observers).rovingFrom(teams).homeAt(Faction.ALLY, homeX, homeY);
        if (!bases.isEmpty()) {
            for (WingAirbase base : bases) service.basedAt(base);
            if (!stations.isEmpty()) service.patrolledBy(new StandingPatrol(Faction.ALLY, stations,
                    PATROL_UP_PER_BASE * bases.size(), contested));
        }
        sim.setAirPool(service);
        return observers;
    }

    /**
     * Bases the wing on {@code lot}, an airbase lot laid on {@code map}: the strip, the sheds and the allied apron
     * berths inside the lot's fence, as {@link #baseOnLot(BattleSimulation, Runway, List, List)} bases it on them.
     */
    public static FieldAirBase baseOnLot(BattleSimulation sim, MapResult map, Laid lot) {
        int[] lo = lot.cells().get(0), hi = lot.cells().get(1);
        Runway strip = null;
        for (Runway r : map.runways) if (inside(lo, hi, Math.round(r.centreX()), Math.round(r.centreY()))) strip = r;
        List<Gantry> sheds = new ArrayList<>();
        for (Gantry g : map.shelters) if (inside(lo, hi, g.centerX, g.centerY)) sheds.add(g);
        List<LandingPad> pads = new ArrayList<>();
        for (LandingPad p : map.landingPads) {
            if (p.purpose == LandingPad.Purpose.ALLIED_AIRFIELD && inside(lo, hi, p.centerX, p.centerY)) pads.add(p);
        }
        return baseOnLot(sim, strip, sheds, pads);
    }

    private static boolean inside(int[] lo, int[] hi, int x, int y) {
        return x >= lo[0] && x <= hi[0] && y >= lo[1] && y <= hi[1];
    }

    /**
     * Bases the wing {@link #install} gave the allies on an airbase lot of their own ({@code cordon-nouns.md}, Air base):
     * {@code strip} installed as the field's runway, then one airframe of the
     * establishment in each of {@code sheds}, bombers first, rolling out over the strip for every sortie and home to the
     * shed after; any the sheds cannot hold (or every one, with no strip) stands on the lot's apron {@code pads}, nose
     * to the pad's approach, lifting off it. From then on the wing takes off from and lands on those berths, its stock
     * is what stands ready there, and a loss is replaced on its berth after the wing's own regeneration time. Returns
     * the base, or {@code null} when the battle has no wing or the lot gave it nowhere to stand.
     */
    public static FieldAirBase baseOnLot(BattleSimulation sim, Runway strip, List<Gantry> sheds, List<LandingPad> pads) {
        AirPoolService pool = sim.airPool();
        if (pool == null || !pool.hasPool(Faction.ALLY) || (sheds.isEmpty() && pads.isEmpty())) return null;
        FieldAirBase base = new FieldAirBase(Faction.ALLY, sim.getAirfieldService(), WING);
        if (strip != null) base.installRunway(strip);
        int shed = 0, pad = 0;
        for (AirPoolKind kind : AirPoolKind.values()) {
            for (int i = 0; i < WING.stock(kind).cap(); i++) {
                if (strip != null && shed < sheds.size()) base.addShed(sheds.get(shed++), kind);
                else if (pad < pads.size()) {
                    LandingPad stand = pads.get(pad++);
                    base.addPad(stand, kind, AirBody.facingToward(stand.approach.dx, stand.approach.dy));
                }
            }
        }
        if (base.stands().isEmpty()) return null;
        pool.basedAt(Faction.ALLY, base);
        return base;
    }

    /**
     * The fallback where no lot was laid: one stand on open ground for each airframe of the wing's establishment,
     * bombers first, at {@code sites} in order, every nose on {@code facingDegrees}. Otherwise as {@link #baseOnLot}.
     */
    public static FieldAirBase baseAt(BattleSimulation sim, List<int[]> sites, float facingDegrees) {
        AirPoolService pool = sim.airPool();
        if (pool == null || !pool.hasPool(Faction.ALLY) || sites == null || sites.isEmpty()) return null;
        FieldAirBase base = new FieldAirBase(Faction.ALLY, sim.getAirfieldService(), WING);
        int next = 0;
        for (AirPoolKind kind : AirPoolKind.values()) {
            for (int i = 0; i < WING.stock(kind).cap() && next < sites.size(); i++, next++) {
                int[] site = sites.get(next);
                base.addStand(site[0], site[1], kind, facingDegrees);
            }
        }
        pool.basedAt(Faction.ALLY, base);
        return base;
    }

    /** Stands an air base for the wing lays: one for each airframe of its establishment. */
    public static int stands() {
        int n = 0;
        for (AirPoolKind kind : AirPoolKind.values()) n += WING.stock(kind).cap();
        return n;
    }
}
