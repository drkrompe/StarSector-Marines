package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.GroundMarineArrival;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.SquadCommandClaim;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent.HardpointType;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.power.SpawnInOnslaught;
import com.dillon.starsectormarines.battle.power.YardConstruction;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleEngine;
import com.dillon.starsectormarines.battle.vehicle.VehicleFootprint;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehiclePlating;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestPlan;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * A disposable diagnostic battle for feeling how machines drive: every mech and vehicle in the game parked in two ranks
 * on a generated colony map, a third rank of medium tanks fitted with each gun of the rotary ladder ({@link #GUN_LADDER})
 * to compare, then the Scorpion and Onager with a missile rack and a laser in their omni cards ({@link #COLOUR_HULLS})
 * and a laser tank fitted with the PL-3 (Pulse) ({@link #LASER_VARIANTS}) to drive beside the second rank's PL-3, with Morrow in its low hover beside them, and no enemy and no end. Behind the machines
 * stands a rank of infantry: one squad for each Marine primary ({@link #SQUAD_PRIMARIES}), all at service grade, whose
 * seats carry every special between them, so each weapon can be fired under direct control. Each is the player's,
 * so it answers move orders and direct control. The Diagnostics tab of the debug missions lists it, and
 * {@code gradlew playBattle -Pbattle=proving-ground} opens it straight away.
 *
 * <p>The ranks stand on the clear window with the most buildings and wild ground around it, and on green worlds
 * {@link #GROVES} of the generator's own trees are planted ahead of them, so there is something to drive past, round
 * and through within a few seconds of any machine.
 */
public final class ProvingGroundSetup {

    /** A seed whose map has a clear window for the ranks; not every map does. */
    public static final long SEED = 7L;

    private static final int MAP_W = 280;
    private static final int MAP_H = 168;
    private static final int HULLS_PER_RANK = 8;
    /** Cells from one machine's centre to the next along a rank; the Wayline hauler needs most of it. */
    private static final int FILE_SPACING = 8;
    private static final int RANK_SPACING = 12;
    private static final int WIDTH = FILE_SPACING * HULLS_PER_RANK;
    /** The machines' two ranks and the gun ladder's. */
    private static final int RANKS = 3;
    /** Cells along the window's south edge for the infantry rank, behind the machines. */
    private static final int INFANTRY_DEPTH = 6;
    private static final int HEIGHT = INFANTRY_DEPTH + RANK_SPACING * RANKS;
    /**
     * The infantry rank's squads, one per Marine primary; drone weapons are left out. Every marine is issued at
     * service grade, a regular, so the weapons differ and nothing else does.
     */
    private static final String[] SQUAD_PRIMARIES = {WeaponRegistry.STARTER_PRIMARY_ID, WeaponRegistry.PULSE_RIFLE_ID,
            WeaponRegistry.SMG_ID, WeaponRegistry.SQUAD_AUTOMATIC_ID, WeaponRegistry.DMR_ID};
    /** Marines in each infantry squad; the specials are dealt round the seats, so there must be one for each. */
    private static final int SQUAD_SEATS = 4;
    /** Cells between marines in a squad's line. */
    private static final int SEAT_SPACING = 2;
    /**
     * The guns a medium tank carries in the third rank, light calibre to medium (mechs-nouns.md, rotary gun): the
     * chaingun, the AC-6 (QS) and the AC-6, each on the same hull, so the difference is only the gun.
     */
    private static final MechWeaponComponent[] GUN_LADDER = {
            MechWeaponComponent.CHAINGUN, MechWeaponComponent.AC_6_QS, MechWeaponComponent.AC_6};
    private static final VehicleType GUN_LADDER_HULL = VehicleType.MEDIUM_TANK;
    /**
     * The laser tank's other large laser, parked at the end of the third rank: the second rank's laser tank
     * carries its standard PL-3 ({@link VehicleType#standardPart}), so the two lasers can be driven side by side.
     */
    private static final MechWeaponComponent[] LASER_VARIANTS = {MechWeaponComponent.PL_3_SWEEP};
    /**
     * The gun tanks, whose omni cards take any colour (hegemony-armor-line.md), fielded in the third rank after the
     * gun ladder with the missile and the energy part a stock spawn of each fits; the ranks above hold their cannons.
     */
    private static final VehicleType[] COLOUR_HULLS = {VehicleType.MEDIUM_TANK, VehicleType.BREAKTHROUGH_TANK};
    private static final HardpointType[] OTHER_COLOURS = {HardpointType.MISSILE, HardpointType.ENERGY};
    /** How far around the ranks a building or wild ground counts as nearby. */
    private static final int SURROUND = 20;
    /** Cells of either kind that count, so that a town with nothing wild does not outscore a little of both. */
    private static final int ENOUGH_OF_EITHER = 400;
    private static final int SEARCH_STEP = 2;
    private static final float FACING_NORTH = 0f;
    /** Woods and lone trees north of the ranks, as x, y and radius in cells, y counted from the ranks' far edge. */
    private static final int[][] GROVES = {{8, 9, 6}, {30, 17, 8}, {54, 9, 6}, {20, 4, 1}, {42, 5, 1}};
    /** Clear cells kept between Morrow's hull and the ranks, and all round it. */
    private static final int MORROW_GAP = 3;

    private ProvingGroundSetup() {
    }

    private record Site(int x0, int y0) {
        int x(int local) { return x0 + local; }
        int y(int local) { return y0 + local; }
        boolean holds(int x, int y) { return x >= x0 && x < x0 + WIDTH && y >= y0 && y < y0 + HEIGHT; }
    }

    public static BattleSimulation create(SurfacePalette surface, long seed) {
        TargetProfile profile = new TargetProfile(3, 3, 1, 0, "independent",
                EnumSet.of(EconomicFunction.HABITATION), surface, SettlementLink.ROAD);
        MapResult map = new BspCityGenerator().generate(MAP_W, MAP_H, seed, null, profile);
        Site site = site(map.grid, map.topology);
        List<Doodad> trees = surface == SurfacePalette.VERDANT ? plantGroves(map, site, seed) : List.of();
        BattleSimulation sim = BattleSetup.buildMap(map, List.of(), List.of(), seed).sim();
        sim.setSurface(surface);
        trees.forEach(sim::addDoodad);
        MechVariant[] mechs = MechVariant.values();
        for (int i = 0; i < mechs.length; i++) spawnMech(sim, mechs[i], site.x(fileX(i)), site.y(rankY(0)));
        ConvoyService convoy = sim.convoy();
        VehicleType[] hulls = VehicleType.values();
        for (int i = 0; i < hulls.length; i++) {
            int slot = mechs.length + i;
            float x = site.x(fileX(slot % HULLS_PER_RANK)) + 0.5f;
            float y = site.y(rankY(slot / HULLS_PER_RANK)) + 0.5f;
            VehicleType type = hulls[i];
            if (!VehicleFootprint.isPoseFeasible(x, y, FACING_NORTH, type.visualLengthCells, type.visualWidthCells,
                    sim.getGrid())) {
                throw new IllegalStateException(String.format(Locale.ROOT,
                        "%s has no room at (%.1f, %.1f) on seed %d", type, x, y, seed));
            }
            long id = convoy.spawn(type, Faction.MARINE, VehicleMission.deployed(x, y));
            convoy.body(id).teleport(x, y, FACING_NORTH);
        }
        // The machines that spill past two ranks stand at the head of the third; the gun ladder starts after them.
        int file = Math.max(0, mechs.length + hulls.length - 2 * HULLS_PER_RANK);
        for (MechWeaponComponent gun : GUN_LADDER) parkFitted(convoy, site, file++, GUN_LADDER_HULL, gun);
        for (VehicleType hull : COLOUR_HULLS) {
            for (HardpointType colour : OTHER_COLOURS) parkFitted(convoy, site, file++, hull, hull.standardPart(colour));
        }
        for (MechWeaponComponent gun : LASER_VARIANTS) parkFitted(convoy, site, file++, VehicleType.LASER_TANK, gun);
        spawnInfantry(sim, site);
        spawnMorrow(sim, map, site);
        // A launched company stands between the ranks, and the opening camera frames the same spot.
        sim.setGroundMarineArrival(new GroundMarineArrival(site.x(WIDTH / 2), site.y(HEIGHT / 2),
                SquadCommandClaim.mission("proving-ground", "Company watches the test drives")));
        sim.playerCommandsCompany();
        sim.addObjective(new TestDrive());
        return sim;
    }

    /** A hull fitted with {@code gun}, parked at {@code file} of the third rank. */
    private static void parkFitted(ConvoyService convoy, Site site, int file, VehicleType hull, MechWeaponComponent gun) {
        float x = site.x(fileX(file)) + 0.5f;
        float y = site.y(rankY(2)) + 0.5f;
        VehicleDeploymentSpec spec = new VehicleDeploymentSpec(null, null, hull, VehiclePlating.FACTORY,
                VehicleEngine.STOCK, gun);
        long id = convoy.spawn(spec, Faction.MARINE, VehicleMission.deployed(x, y));
        convoy.body(id).teleport(x, y, FACING_NORTH);
    }

    /**
     * Morrow in its low hover, as Working Passage flies it, on the nearest clear ground beside the ranks, with its
     * recovery builds on the power bar so they can be laid from it.
     */
    private static void spawnMorrow(BattleSimulation sim, MapResult map, Site site) {
        Tender morrow;
        try {
            morrow = Tender.lowHover(Faction.MARINE, 0f, 0f, SpawnInOnslaught.PRESENCE);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to fit Morrow", e);
        }
        // Low hover points the bow east, so the hull's length runs along x and its beam along y.
        int w = (int) Math.ceil(morrow.ship().lengthCells) + 2 * MORROW_GAP;
        int h = (int) Math.ceil(morrow.ship().widthCells) + 2 * MORROW_GAP;
        float cx = site.x0 + WIDTH / 2f;
        float cy = site.y0 + HEIGHT / 2f;
        int[] best = null;
        float bestDistance = Float.MAX_VALUE;
        for (int y0 = 0; y0 + h <= map.grid.getHeight(); y0 += SEARCH_STEP) {
            for (int x0 = 0; x0 + w <= map.grid.getWidth(); x0 += SEARCH_STEP) {
                if (overlaps(x0, y0, w, h, site) || !clear(map.grid, map.topology, x0, y0, w, h)) continue;
                float d = (float) Math.hypot(x0 + w / 2f - cx, y0 + h / 2f - cy);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = new int[]{x0, y0};
                }
            }
        }
        if (best == null) throw new IllegalStateException("No clear " + w + "x" + h + " ground for Morrow");
        try {
            morrow = Tender.lowHover(Faction.MARINE, best[0] + w / 2f, best[1] + h / 2f, SpawnInOnslaught.PRESENCE);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to fit Morrow", e);
        }
        sim.tenders().add(sim, morrow);
        for (YardConstruction yard : YardConstruction.forEachOption(morrow)) sim.getCommandPowerService().register(yard);
    }

    /** Whether the window comes within {@link #MORROW_GAP} of the ranks. */
    private static boolean overlaps(int x0, int y0, int w, int h, Site site) {
        return x0 < site.x0 + WIDTH + MORROW_GAP && x0 + w > site.x0 - MORROW_GAP
                && y0 < site.y0 + HEIGHT + MORROW_GAP && y0 + h > site.y0 - MORROW_GAP;
    }

    private static int fileX(int file) {
        return FILE_SPACING / 2 + file * FILE_SPACING;
    }

    private static int rankY(int rank) {
        return INFANTRY_DEPTH + RANK_SPACING / 2 + rank * RANK_SPACING;
    }

    /**
     * The infantry rank: a squad for each of {@link #SQUAD_PRIMARIES}, each in a line across its share of the window's
     * width, and every installed special handed to one seat, in turn across the squads, so all of them are on the field.
     * Like the mechs, each squad holds its ground until ordered.
     */
    private static void spawnInfantry(BattleSimulation sim, Site site) {
        List<SpecialEquipmentDef> specials = new ArrayList<>(SpecialEquipmentRegistry.installed().all());
        if (specials.size() > SQUAD_PRIMARIES.length * SQUAD_SEATS) {
            throw new IllegalStateException(specials.size() + " specials do not fit "
                    + SQUAD_PRIMARIES.length * SQUAD_SEATS + " infantry seats");
        }
        int y = site.y(INFANTRY_DEPTH / 2);
        int share = WIDTH / SQUAD_PRIMARIES.length;
        for (int s = 0; s < SQUAD_PRIMARIES.length; s++) {
            int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
            Squad squad = sim.getSquad(squadId);
            int centre = site.x(s * share + share / 2);
            int x0 = centre - (SQUAD_SEATS - 1) * SEAT_SPACING / 2;
            for (int seat = 0; seat < SQUAD_SEATS; seat++) {
                int dealt = seat * SQUAD_PRIMARIES.length + s;
                SpecialEquipmentDef special = dealt < specials.size() ? specials.get(dealt) : null;
                MarineLoadout loadout = new MarineLoadout(UnitRole.COMBATANT, null, SQUAD_PRIMARIES[s],
                        EquipmentGrade.SERVICE, SoldierProfile.REGULAR,
                        special != null ? special.id() : null, special != null ? special.startingAmmo() : 0);
                int x = x0 + seat * SEAT_SPACING;
                EntitySpec spec = new EntitySpec(SQUAD_PRIMARIES[s] + "-" + (seat + 1), Faction.MARINE,
                        UnitType.MARINE, x, y).squad(squadId).home(x, y);
                loadout.seedInto(spec);
                long id = sim.spawn(spec);
                if (squad.leaderId == 0L) squad.leaderId = id;
            }
            squad.originalSize = SQUAD_SEATS;
            squad.assignedObjective = ObjectiveAssignment.defendArea(squadId, centre, y);
        }
    }

    /** One standard mech as a lance of its own. Left with no errand it would walk off, so it holds its slot until ordered. */
    private static void spawnMech(BattleSimulation sim, MechVariant variant, int x, int y) {
        MechDeploymentSpec deployment = MechDeploymentSpec.standard(variant);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        EntitySpec spec = new EntitySpec(variant.id, Faction.MARINE, UnitType.HEAVY_MECH, x, y);
        variant.applyTo(spec);
        deployment.applyTo(spec);
        float reach = 0f;
        for (MechMountSlot slot : MechMountSlot.values()) {
            MechWeaponComponent weapon = deployment.weaponAt(slot);
            if (weapon != null) reach = Math.max(reach, weapon.weaponDef().range);
        }
        spec.attackRange(reach).visionRange(Math.max(variant.visionRange, reach));
        spec.squad(squadId);
        long id = sim.spawn(spec);
        MechLoadoutComponent loadout = deployment.createLoadout(deployment.role());
        loadout.applyFrame(deployment.frame());
        loadout.installMissileReplenisher(deployment.missileReplenisher());
        sim.world().attachMechLoadout(id, loadout);
        sim.getRoster().machineHosts().register(id, deployment.deployedFrame().systems());
        Squad squad = sim.getSquad(squadId);
        squad.originalSize = 1;
        squad.aliveMembers = 1;
        squad.assignedObjective = ObjectiveAssignment.defendArea(squadId, x, y);
    }

    /** Stamps the trees that fit on open ground into the map, for the simulation to be built over; returns their doodads. */
    private static List<Doodad> plantGroves(MapResult map, Site site, long seed) {
        int w = map.grid.getWidth();
        int h = map.grid.getHeight();
        boolean[] paint = new boolean[w * h];
        for (int[] grove : GROVES) {
            int cx = site.x(grove[0]);
            int cy = site.y(HEIGHT + grove[1]);
            int r = grove[2];
            for (int y = Math.max(0, cy - r); y <= Math.min(h - 1, cy + r); y++) {
                for (int x = Math.max(0, cx - r); x <= Math.min(w - 1, cx + r); x++) {
                    if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) paint[y * w + x] = true;
                }
            }
        }
        List<ForestField.Tree> fitting = new ArrayList<>();
        for (ForestField.Tree tree : ForestField.build(w, h, seed, ForestPlan.Woodland.MIXED, paint).trees()) {
            if (fits(tree, map, site)) fitting.add(tree);
        }
        List<Doodad> doodads = new ArrayList<>();
        ForestBrush.stampTrees(fitting, map.grid, map.topology, doodads, null, false);
        return doodads;
    }

    /** Whether every cell the crown closes is the open natural ground {@link ForestBrush} accepts, clear of the ranks. */
    private static boolean fits(ForestField.Tree tree, MapResult map, Site site) {
        CellTopology topology = map.topology;
        for (int y = tree.y(); y < tree.y() + tree.height(); y++) {
            for (int x = tree.x(); x < tree.x() + tree.width(); x++) {
                if (!tree.blocks(x, y)) continue;
                boolean natural = switch (topology.getGroundKind(x, y)) {
                    case GRASS, DIRT, STONE, SAND, SNOW, HARDPAN -> true;
                    default -> false;
                };
                if (!natural || site.holds(x, y) || blocked(map.grid, topology, x, y) || topology.isWall(x, y)
                        || topology.isFixture(x, y) || topology.getSurface(x, y) != 0
                        || (topology.roadSurface() != null && topology.roadSurface().underlay(x, y) != null)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The clear window with the most buildings and wild ground around it, each capped at {@link #ENOUGH_OF_EITHER}. */
    private static Site site(NavigationGrid grid, CellTopology topology) {
        Site best = null;
        int bestScore = -1;
        for (int y0 = SURROUND; y0 + HEIGHT + SURROUND <= grid.getHeight(); y0 += SEARCH_STEP) {
            for (int x0 = SURROUND; x0 + WIDTH + SURROUND <= grid.getWidth(); x0 += SEARCH_STEP) {
                if (!clear(grid, topology, x0, y0)) continue;
                int score = surroundings(topology, x0, y0);
                if (score > bestScore) {
                    bestScore = score;
                    best = new Site(x0, y0);
                }
            }
        }
        if (best == null) throw new IllegalStateException("No clear " + WIDTH + "x" + HEIGHT + " window on this map");
        return best;
    }

    private static boolean clear(NavigationGrid grid, CellTopology topology, int x0, int y0) {
        return clear(grid, topology, x0, y0, WIDTH, HEIGHT);
    }

    private static boolean clear(NavigationGrid grid, CellTopology topology, int x0, int y0, int w, int h) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                if (blocked(grid, topology, x, y)) return false;
            }
        }
        return true;
    }

    /** A cell a machine cannot be parked on: not walkable, inside a building, on water or off the world. */
    private static boolean blocked(NavigationGrid grid, CellTopology topology, int x, int y) {
        if (!grid.isWalkable(x, y) || grid.isDoorway(x, y) || topology.getBuildingId(x, y) != 0) return true;
        CellTopology.GroundKind kind = topology.getGroundKind(x, y);
        return kind == CellTopology.GroundKind.INDOOR || kind == CellTopology.GroundKind.WATER
                || kind == CellTopology.GroundKind.VOID;
    }

    private static int surroundings(CellTopology topology, int x0, int y0) {
        int buildings = 0;
        int nature = 0;
        for (int y = y0 - SURROUND; y < y0 + HEIGHT + SURROUND; y++) {
            for (int x = x0 - SURROUND; x < x0 + WIDTH + SURROUND; x++) {
                if (topology.getBuildingId(x, y) != 0) buildings++;
                if (topology.getNatureOverlayIndex(x, y) >= 0) nature++;
            }
        }
        return Math.min(buildings, ENOUGH_OF_EITHER) + Math.min(nature, ENOUGH_OF_EITHER);
    }

    /**
     * Keeps the battle open: without an objective the elimination backstop would win it at once, with no enemy on the
     * field. Mission completion stays on, since switching it off would also refuse the player's Retreat.
     */
    private static final class TestDrive implements Objective {
        @Override public Faction owningFaction() { return Faction.MARINE; }
        @Override public void tick(BattleView sim) { }
        @Override public boolean isComplete() { return false; }
        @Override public boolean isFailed() { return false; }
        @Override public String displayName() { return Strings.get("provingGroundObjective"); }
    }
}
