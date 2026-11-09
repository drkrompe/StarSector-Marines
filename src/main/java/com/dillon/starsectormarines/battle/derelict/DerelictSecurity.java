package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective;
import com.dillon.starsectormarines.battle.drone.Flock;
import com.dillon.starsectormarines.battle.drone.FlockLivery;
import com.dillon.starsectormarines.battle.drone.FlockWeights;
import com.dillon.starsectormarines.battle.mech.MechSpawnPlacement;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;

import java.util.ArrayList;
import java.util.List;

/**
 * Stands a derelict site's security up in a ruins battle (`derelict-security-network.md`): its zones laid along the
 * ground between the vault and the way out, its walkers posted in them by the grade's strength
 * (`derelict-lore-guide.md`, Strength by grade), and its director installed. The ruins occupant seeding
 * (`ruins-occupants.md`) calls {@link #install} for a site whose occupant is derelict security. Each zone puts one flock
 * up over its node (`derelict-drone-flocks.md`); cradles, posts and nodes are their own slices and are not stood up
 * here.
 */
public final class DerelictSecurity {

    /** One zone of the plan: its node cell, its perimeter and its walkers' posts. */
    public record ZonePlan(int id, int nodeX, int nodeY, int perimeterCells, List<int[]> posts) {
        public ZonePlan {
            posts = List.copyOf(posts);
        }
    }

    /** Swarm drones in each zone's flock (`derelict-lore-guide.md`: twelve to sixteen). UNTUNED. */
    public static final int FLOCK_SIZE = 14;
    /** Distinct posts each zone offers its walkers along the route. */
    static final int POSTS_PER_ZONE = 5;

    private DerelictSecurity() {
    }

    /** Security zones by grade: one per flock the grade fields. */
    public static int zones(RuinsGrade grade) {
        return switch (grade) {
            case SCATTERED -> 1;
            case WIDESPREAD -> 2;
            case EXTENSIVE -> 3;
            case VAST -> 4;
        };
    }

    /** The grade's heavies, the vault frame first where there is one. Starting points (UNTUNED). */
    public static List<DerelictWalkerFrame> heavies(RuinsGrade grade) {
        return switch (grade) {
            case SCATTERED -> List.of(DerelictWalkerFrame.LINE);
            case WIDESPREAD -> List.of(DerelictWalkerFrame.LINE, DerelictWalkerFrame.LINE);
            case EXTENSIVE -> List.of(DerelictWalkerFrame.LINE, DerelictWalkerFrame.LINE, DerelictWalkerFrame.SIEGE);
            case VAST -> List.of(DerelictWalkerFrame.VAULT, DerelictWalkerFrame.LINE, DerelictWalkerFrame.LINE,
                    DerelictWalkerFrame.LINE, DerelictWalkerFrame.SIEGE);
        };
    }

    /**
     * Lays {@code count} zones along a route from the vault (its first cell) to the way out (its last): the first zone
     * keeps the vault, the rest step out toward the landing and stop short of it, each perimeter reaching the next so
     * the route is kept end to end. Each zone posts walkers at its node and a few cells either way along the route.
     *
     * @param route cell pairs, vault first
     */
    public static List<ZonePlan> zonesAlong(int[] route, int count) {
        int cells = route.length / 2;
        if (cells < 2 || count < 1) throw new IllegalArgumentException("A plan needs a route and a zone");
        // The last stretch before the landing is left to the company: zones cover the first 80% of the route.
        float span = (cells - 1) * 0.8f;
        float step = count == 1 ? 0f : span / (count - 1);
        int spacing = Math.max(1, Math.round(step));
        List<ZonePlan> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int index = Math.round(i * step);
            int perimeter = Math.max(8, spacing * 3 / 4 + 2);
            List<int[]> posts = new ArrayList<>();
            List<Integer> used = new ArrayList<>();
            for (int offset : new int[] {0, -4, 4, -8, 8, 12, 16}) {
                int at = Math.max(0, Math.min(cells - 1, index + offset));
                if (used.contains(at) || posts.size() == POSTS_PER_ZONE) continue;
                used.add(at);
                posts.add(new int[] {route[at * 2], route[at * 2 + 1]});
            }
            out.add(new ZonePlan(i, route[index * 2], route[index * 2 + 1], perimeter, posts));
        }
        return out;
    }

    /** Route cells the heavies hold in the contingency: the way out, short of the landing. */
    public static List<int[]> egressAlong(int[] route) {
        int cells = route.length / 2;
        List<int[]> out = new ArrayList<>();
        for (float share : new float[] {0.6f, 0.72f, 0.84f}) {
            int at = Math.min(cells - 1, Math.round((cells - 1) * share));
            out.add(new int[] {route[at * 2], route[at * 2 + 1]});
        }
        return out;
    }

    /**
     * Which zone each heavy stands in: the vault frame at the vault, a siege frame in the middle zone, line frames
     * from the vault outward in turn.
     */
    public static int[] zoneOf(List<DerelictWalkerFrame> heavies, int zones) {
        int[] out = new int[heavies.size()];
        int next = 0;
        for (int i = 0; i < heavies.size(); i++) {
            DerelictWalkerFrame frame = heavies.get(i);
            if (frame == DerelictWalkerFrame.VAULT) out[i] = 0;
            else if (frame == DerelictWalkerFrame.SIEGE) out[i] = zones / 2;
            else out[i] = next++ % zones;
        }
        return out;
    }

    /**
     * Spawns the grade's walkers on a ruins battle's ground and installs the director over them, linking each zone
     * to the next along the route. Returns the director, or null when the battle has no vault to lay zones from.
     */
    public static DerelictSecurityDirector install(BattleSimulation sim, RuinsGrade grade) {
        return install(sim, grade, null);
    }

    /**
     * As {@link #install(BattleSimulation, RuinsGrade)}, on ruins ground whose places are known: where the site has an
     * interior, its walkers are posted by {@link #interiorPosts} rather than along the route, each in the zone that
     * keeps its post.
     */
    public static DerelictSecurityDirector install(BattleSimulation sim, RuinsGrade grade, RuinsDelve delve) {
        return install(sim, grade, delve, FlockLivery.DOMAIN);
    }

    /** As {@link #install(BattleSimulation, RuinsGrade, RuinsDelve)}, its flocks in a lineage's livery. */
    public static DerelictSecurityDirector install(BattleSimulation sim, RuinsGrade grade, RuinsDelve delve,
                                                   FlockLivery livery) {
        RuinsVaultObjective vault = null;
        for (Objective objective : sim.getObjectives()) {
            if (objective instanceof RuinsVaultObjective ruins) vault = ruins;
        }
        if (vault == null || vault.liftPoints().isEmpty()) return null;
        int[] route = vault.liftPoints().get(0).route();
        List<ZonePlan> plans = zonesAlong(route, zones(grade));
        SecurityNetwork network = new SecurityNetwork();
        for (ZonePlan plan : plans) network.add(new SecurityZone(plan.id(), plan.nodeX(), plan.nodeY(),
                plan.perimeterCells()));
        for (int i = 1; i < plans.size(); i++) network.link(i - 1, i);

        List<DerelictWalkerFrame> heavies = heavies(grade);
        int[] zoneOf = zoneOf(heavies, plans.size());
        int[] postsUsed = new int[plans.size()];
        int[][] inside = interiorPosts(heavies, delve);
        List<DerelictSecurityDirector.Walker> walkers = new ArrayList<>();
        for (int i = 0; i < heavies.size(); i++) {
            ZonePlan plan = plans.get(zoneOf[i]);
            int[] post;
            if (inside != null) {
                post = inside[i];
                plan = plans.get(nearestZone(plans, post[0], post[1]));
            } else {
                post = plan.posts().get(postsUsed[zoneOf[i]]++ % plan.posts().size());
            }
            int squad = spawn(sim, heavies.get(i), post[0], post[1]);
            if (squad < 0) continue;
            sim.claimSquadCommand(squad, CommandAuthority.MISSION_COMMAND, DerelictSecurityDirector.ISSUER,
                    "derelict security walker at its post");
            walkers.add(new DerelictSecurityDirector.Walker(squad, heavies.get(i), plan.id(), post[0], post[1]));
        }
        DerelictSecurityDirector director = new DerelictSecurityDirector(network, walkers, egressAlong(route),
                vault.sourceCellX(), vault.sourceCellY());
        FlockSystemPort port = new FlockSystemPort();
        for (ZonePlan plan : plans) {
            int[] home = openSkyHome(route, plan, (x, y) -> sim.getTopology() != null
                    && sim.getTopology().isRoofIntact(x, y));
            Flock flock = sim.flocks().launch(sim, Faction.DEFENDER, home[0] + 0.5f, home[1] + 0.5f,
                    FLOCK_SIZE, FlockWeights.WASP, plan.perimeterCells() + FlockTasking.LEASH_CELLS, livery);
            port.add(flock, plan.id());
        }
        director.connectFlocks(port);
        sim.installDerelictSecurity(director);
        return director;
    }

    /**
     * Where each heavy stands on a site with an interior (`derelict-security-network.md`, Interiors): the vault frame
     * on the vault's approach, inside its forecourt; the siege frame just inside the first entrance; line frames in
     * the interior's chambers in turn (the entrances when it has none). Null when the ground has no interior, and the
     * route's posts serve.
     */
    public static int[][] interiorPosts(List<DerelictWalkerFrame> heavies, RuinsDelve delve) {
        if (delve == null || delve.interior() == null || delve.vault() == null) return null;
        RuinsDelve.Interior interior = delve.interior();
        List<RuinsDelve.Area> rooms = interior.chambers().isEmpty() ? interior.entrances() : interior.chambers();
        int[][] out = new int[heavies.size()][];
        int nextRoom = 0;
        for (int i = 0; i < heavies.size(); i++) {
            DerelictWalkerFrame frame = heavies.get(i);
            if (frame == DerelictWalkerFrame.VAULT || rooms.isEmpty()) {
                out[i] = new int[] {delve.vault().approach().x(), delve.vault().approach().y()};
            } else if (frame == DerelictWalkerFrame.SIEGE && !interior.entrances().isEmpty()) {
                RuinsDelve.Area entrance = interior.entrances().get(0);
                out[i] = new int[] {entrance.centreX(), entrance.centreY()};
            } else {
                RuinsDelve.Area room = rooms.get(nextRoom++ % rooms.size());
                out[i] = new int[] {room.centreX(), room.centreY()};
            }
        }
        return out;
    }

    /** The zone whose node is nearest a cell. */
    static int nearestZone(List<ZonePlan> plans, int x, int y) {
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < plans.size(); i++) {
            int distance = WalkerPosture.chebyshev(x, y, plans.get(i).nodeX(), plans.get(i).nodeY());
            if (distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Whether a cell is under an intact roof. */
    interface Roofed {
        boolean at(int x, int y);
    }

    /**
     * Where a zone's flock is homed: its node, or where the node is under a roof, the first route cell from the node
     * toward the landing that is open to the sky. A flock never enters a roofed cell (`derelict-drone-flocks.md`), so
     * an interior zone's flock keeps its doorway.
     */
    static int[] openSkyHome(int[] route, ZonePlan plan, Roofed roofed) {
        int cells = route.length / 2;
        int start = 0;
        for (int i = 0; i < cells; i++) {
            if (route[i * 2] == plan.nodeX() && route[i * 2 + 1] == plan.nodeY()) {
                start = i;
                break;
            }
        }
        for (int i = start; i < cells; i++) {
            if (!roofed.at(route[i * 2], route[i * 2 + 1])) return new int[] {route[i * 2], route[i * 2 + 1]};
        }
        return new int[] {plan.nodeX(), plan.nodeY()};
    }

    /** One walker in a lance of its own, at the nearest standable point to its post; -1 when none is. */
    private static int spawn(BattleSimulation sim, DerelictWalkerFrame frame, int postX, int postY) {
        float radius = DerelictWalkerFrame.CHASSIS.radius;
        MechSpawnPlacement.Point at = MechSpawnPlacement.nearCell(sim.getGrid(), radius, postX, postY,
                (x, y) -> true, (x, y) -> MechSpawnPlacement.unoccupied(sim.getRoster(), x, y, radius, 0L));
        if (at == null) return -1;
        int squad = sim.mintSquad(Faction.DEFENDER, UnitType.HEAVY_MECH);
        String name = "MG-40 " + frame.name().toLowerCase() + " frame";
        long id = sim.spawn(frame.applyTo(new EntitySpec(name, Faction.DEFENDER, UnitType.HEAVY_MECH,
                at.cellX(), at.cellY())).atPosition(at.x(), at.y()).squad(squad));
        sim.world().attachMechLoadout(id, frame.createLoadout());
        Squad lance = sim.getSquad(squad);
        lance.leaderId = id;
        lance.originalSize = 1;
        return squad;
    }
}
