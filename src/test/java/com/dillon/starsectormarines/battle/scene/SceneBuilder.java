package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Stands a behaviour scene's world up: arena, walls, squads with kit and
 * orders, and the loose bodies that keep a side present.
 *
 * <p>Every scene written so far opens with the same two hundred lines — fill a
 * grid, wall it, mint a squad, roll its kit, seed it into six specs, spawn
 * them, then remember to write {@code originalSize} and {@code aliveMembers}
 * because liveness is read before the first tick. That preamble is where the
 * mistakes live rather than in the behaviour under test, so it is written once
 * here and a scene is forty lines of what makes it different.
 *
 * <p>The builder is one-shot: {@link #build()} spawns everything it was told
 * about and hands back a {@link SceneWorld}. Building twice would spawn the
 * same roster into a second simulation while the first one's keys still name
 * ids in the first, so it is refused.
 */
public final class SceneBuilder {

    /**
     * The kit seed a squad gets when it is not given one. Fixed and named so
     * two scenes that both take the default are comparable, and so a scene that
     * wants a different roll says so.
     */
    public static final long DEFAULT_KIT_SEED = 20260901L;

    private static final int DEFAULT_SQUAD_SIZE = 6;

    private final int width;
    private final int height;
    private final NavigationGrid grid;
    private final CellTopology topology;
    private final List<DefensePost> posts = new ArrayList<>();
    private final List<Doodad> doodads = new ArrayList<>();
    private final List<Object> spawnOrder = new ArrayList<>();
    private final Map<String, SquadSpec> squads = new LinkedHashMap<>();
    private final Map<String, UnitEntry> units = new LinkedHashMap<>();
    private boolean missionCompletion;
    private boolean built;
    private Long simSeed;
    /** The generated map a look scene stands on, built through the production path; null for an arena. */
    private MapResult terrain;
    private SurfacePalette surface;
    /** Added to every coordinate the builder is given: the corner of the window a look scene stands in. */
    private int originX;
    private int originY;

    private SceneBuilder(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Scene arena must be positive: "
                    + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.grid = new NavigationGrid(width, height);
        this.topology = new CellTopology(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
    }

    private SceneBuilder(NavigationGrid grid, CellTopology topology) {
        this(grid, topology, grid.getWidth(), grid.getHeight());
    }

    private SceneBuilder(NavigationGrid grid, CellTopology topology, int width, int height) {
        this.width = width;
        this.height = height;
        this.grid = grid;
        this.topology = topology;
    }

    /**
     * A scene over terrain a generator stage has already stamped, rather than over open floor: the fort scene stands
     * the production battery fort on a bare field and plays the battle on the grid and topology it produced.
     */
    public static SceneBuilder over(NavigationGrid grid, CellTopology topology) {
        return new SceneBuilder(grid, topology);
    }

    /**
     * The doodads a generator laid alongside the terrain it stamped (a fort yard's sandbag rings, a belt's fence),
     * handed to the simulation as {@code BattleSetup} hands a map's. A scene that stamps and drops them fights on
     * ground the shipped map would not have.
     */
    public SceneBuilder doodads(List<Doodad> laid) {
        checkOpen();
        doodads.addAll(laid);
        return this;
    }

    /** Defence posts the generator stamped, spawned through the production path when the scene is built. */
    public SceneBuilder posts(List<DefensePost> stamped) {
        checkOpen();
        posts.addAll(stamped);
        return this;
    }

    /**
     * A scene over real generated ground, for a look frame rather than a verdict: {@code site} is a window of
     * {@code map} (see {@link LookGround#openSite}) and every coordinate given to this builder, its squads and units
     * is local to it, so a scene written for a {@code site.width() x site.height()} arena stands in the window
     * unchanged. The simulation is built as {@code BattleSetup.buildMap} builds a battle's — the map's tactical map,
     * buildings, nature cover and doodads — with {@code ground}'s surface. A scene that spawns hulls itself turns its
     * coordinates with {@link LookGround.Site#x(float)} and {@link LookGround.Site#y(float)}.
     */
    public static SceneBuilder onTerrain(LookGround ground, MapResult map, LookGround.Site site) {
        SceneBuilder builder = new SceneBuilder(map.grid, map.topology, site.width(), site.height());
        builder.terrain = map;
        builder.surface = ground.surface();
        builder.originX = site.x0();
        builder.originY = site.y0();
        return builder;
    }

    /** An arena of walkable floor, which every scene then cuts walls into. */
    public static SceneBuilder openGround(int width, int height) {
        return new SceneBuilder(width, height);
    }

    /**
     * Whether the simulation is allowed to decide the battle. Off by default,
     * and the default is the one scenes want: a scene is about one behaviour
     * and not about who wins, and a decided battle stops ticking everything the
     * scene came to watch — the airfield raid loop, whose defenders are three
     * parked hulls, recorded six marines standing perfectly still for its whole
     * length until this was turned off.
     */
    public SceneBuilder missionCompletion(boolean enabled) {
        this.missionCompletion = enabled;
        return this;
    }

    /**
     * Seeds the simulation's one random stream, which decides every hit and morale roll. A scene that compares
     * outcomes across many runs of one world varies this and nothing else; omitted, the sim's fixed default seed.
     */
    public SceneBuilder seed(long seed) {
        checkOpen();
        this.simSeed = seed;
        return this;
    }

    /** An inclusive rectangle of impassable cells. */
    public SceneBuilder wall(int x0, int y0, int x1, int y1) {
        checkOpen();
        int loX = Math.min(x0, x1);
        int hiX = Math.max(x0, x1);
        int loY = Math.min(y0, y1);
        int hiY = Math.max(y0, y1);
        for (int y = loY + originY; y <= hiY + originY; y++) {
            for (int x = loX + originX; x <= hiX + originX; x++) {
                if (grid.inBounds(x, y)) grid.setWalkable(x, y, false);
            }
        }
        return this;
    }

    /** A whole column made impassable — a room with no way out of this side. */
    public SceneBuilder seal(int x) {
        return wall(x, 0, x, height - 1);
    }

    /**
     * A sealed column with one doorway, which is what gives the zone detector
     * two zones that are genuinely connected. Doorway cells carry no zone of
     * their own, so read a zone from either side of the wall rather than from
     * {@code doorY} itself.
     */
    public SceneBuilder wallWithDoor(int x, int doorY) {
        seal(x);
        return doorway(x, doorY);
    }

    /**
     * One walkable doorway cell, cut through whatever was there.
     *
     * <p>{@link #wallWithDoor} is the corridor case — a whole column sealed
     * with one way through — and a room is the other one: four walls a scene
     * lays itself, with the door somewhere in them. <b>The doorway marker is
     * what makes it a door</b> rather than a gap: the zone detector links two
     * zones through a portal, and a hole left as ordinary floor merges the room
     * into the field around it. A scene that then orders a squad to "the room"
     * has ordered it to the zone it is already standing in, and records
     * something else entirely — which is what this method exists to have
     * stopped happening.
     */
    public SceneBuilder doorway(int x, int y) {
        checkOpen();
        x += originX;
        y += originY;
        if (grid.inBounds(x, y)) {
            grid.setWalkableFloor(x, y);
            grid.setDoorway(x, y, true);
        }
        return this;
    }

    /**
     * Declares a squad under {@code key}, to be spawned by {@link #build()}.
     *
     * <p>Defaults are a six-strong armed marine squad. <b>Armed and squadded is
     * the default because an unarmed, unsquadded spawn is scenery</b>: a unit
     * from a bare {@code EntitySpec} carries no loadout and cannot fire, and
     * target acquisition runs off the squad, so an "ambush" of such bodies is
     * six people standing in a field watching. A scene that records a contested
     * crossing against them has recorded a crossing that was never contested.
     */
    public SquadSpec squad(String key) {
        checkOpen();
        claim(key);
        SquadSpec spec = new SquadSpec(this, key);
        squads.put(key, spec);
        spawnOrder.add(spec);
        return spec;
    }

    /**
     * One unsquadded body under {@code key} — the "far defender nobody can
     * reach" every scene keeps alive, because the simulation returns
     * immediately once a side is absent and a scene with a lone faction never
     * advances a tick.
     */
    public SceneBuilder unit(String key, Faction faction, UnitType type, int x, int y) {
        return unit(key, faction, type, x, y, spec -> { });
    }

    /** As {@link #unit(String, Faction, UnitType, int, int)}, with the spec handed to {@code customiser} before it spawns. */
    public SceneBuilder unit(String key, Faction faction, UnitType type, int x, int y,
                             Consumer<EntitySpec> customiser) {
        checkOpen();
        claim(key);
        UnitEntry entry = new UnitEntry(key, Objects.requireNonNull(faction, "faction"),
                Objects.requireNonNull(type, "type"), x, y,
                Objects.requireNonNull(customiser, "customiser"));
        units.put(key, entry);
        spawnOrder.add(entry);
        return this;
    }

    /** Spawns everything declared, in declaration order, and closes the builder. */
    public SceneWorld build() {
        checkOpen();
        built = true;
        BattleSimulation sim;
        if (terrain != null) {
            sim = BattleSetup.buildMap(terrain, List.of(), List.of(), simSeed == null ? BattleSimulation.DEFAULT_SEED : simSeed).sim();
            sim.setSurface(surface);
        } else {
            sim = simSeed == null
                    ? new BattleSimulation(grid, topology)
                    : new BattleSimulation(grid, topology, simSeed);
        }
        sim.setMissionCompletionEnabled(missionCompletion);
        for (Doodad doodad : doodads) sim.addDoodad(doodad);
        long[] structures = new long[0];
        if (!posts.isEmpty()) {
            sim.setDefensePosts(posts);
            structures = BattleSetup.spawnDefensePostTurrets(sim, posts).toLongArray();
        }

        Map<String, Integer> squadIds = new LinkedHashMap<>();
        Map<String, long[]> members = new LinkedHashMap<>();
        Map<String, Long> unitIds = new LinkedHashMap<>();
        for (Object entry : spawnOrder) {
            if (entry instanceof SquadSpec squad) {
                int id = squad.spawnInto(sim);
                squadIds.put(squad.key, id);
                members.put(squad.key, squad.spawned);
            } else {
                UnitEntry unit = (UnitEntry) entry;
                unitIds.put(unit.key, unit.spawnInto(sim, originX, originY));
            }
        }
        return new SceneWorld(sim, width, height, squadIds, members, unitIds, structures);
    }

    private void claim(String key) {
        Objects.requireNonNull(key, "key");
        if (squads.containsKey(key) || units.containsKey(key)) {
            throw new IllegalArgumentException("Key already used in this scene: " + key);
        }
    }

    private void checkOpen() {
        if (built) throw new IllegalStateException("This SceneBuilder has already been built");
    }

    /** One declared squad. Fluent; {@link #done()} returns to the builder. */
    public static final class SquadSpec {

        private final SceneBuilder owner;
        private final String key;

        private Faction faction = Faction.MARINE;
        private UnitType type = UnitType.MARINE;
        private int size = DEFAULT_SQUAD_SIZE;
        private int atX;
        private int atY;
        private int fileStepX;
        private int fileStepY;
        private boolean armed = true;
        private long kitSeed = DEFAULT_KIT_SEED;
        private String primaryWeaponId;
        private MarineLoadout[] issuedKit;
        private String specialEquipmentId;
        private int specialSeats;
        private int specialAmmo;
        private boolean stationary;
        private Consumer<EntitySpec> each = spec -> { };
        private float health = -1f;
        private float armorCapacity;
        private float armorRating;
        private IntFunction<ObjectiveAssignment> assignment;
        private MechVariant mechVariant;
        private MechRole mechRole;
        private MechDeploymentSpec mechDeployment;
        private long[] spawned = new long[0];

        private SquadSpec(SceneBuilder owner, String key) {
            this.owner = owner;
            this.key = key;
        }

        public SquadSpec faction(Faction faction) {
            this.faction = Objects.requireNonNull(faction, "faction");
            return this;
        }

        public SquadSpec type(UnitType type) {
            this.type = Objects.requireNonNull(type, "type");
            return this;
        }

        public SquadSpec size(int size) {
            if (size <= 0) throw new IllegalArgumentException("Squad size must be positive: " + size);
            this.size = size;
            return this;
        }

        /** Cluster origin: members fill four to a row from {@code (x - 2, y)}. */
        public SquadSpec at(int x, int y) {
            this.atX = x;
            this.atY = y;
            this.fileStepX = 0;
            this.fileStepY = 0;
            return this;
        }

        /**
         * Single file from {@code (x, y)}, one member per {@code (stepX, stepY)}.
         *
         * <p>The four-to-a-row cluster is a shape the arena has to be able to
         * hold, and a corridor cannot: laid out that way in a three-cell
         * passage half the squad spawns inside the walls. It is also the wrong
         * shape for a scene about who is standing in whose lane, which needs
         * the squad in column and would otherwise be measuring a formation the
         * builder chose rather than the one the scene is named for.
         */
        public SquadSpec inFile(int x, int y, int stepX, int stepY) {
            if (stepX == 0 && stepY == 0) {
                throw new IllegalArgumentException("A file needs a direction to run in");
            }
            this.atX = x;
            this.atY = y;
            this.fileStepX = stepX;
            this.fileStepY = stepY;
            return this;
        }

        /** Rolls the squad's primaries from {@code seed} instead of {@link #DEFAULT_KIT_SEED}. */
        public SquadSpec kit(long seed) {
            this.armed = true;
            this.kitSeed = seed;
            return this;
        }

        /**
         * Issues one named primary to every member instead of rolling the
         * squad's kit — {@code "weapon.smg"}, {@code "weapon.pulse-rifle"}.
         *
         * <p><b>For a scene whose geometry is measured in weapon reach.</b> The
         * rolled kit hands out weapons whose range differs by ten cells and
         * gives one seat a rocket tube, so "the squad cannot reach that
         * contact" is true of five marines and false of the sixth — and a scene
         * built on that distance records five men standing still beside one man
         * walking, which is neither of the behaviours it set out to compare.
         * A kit seed can be hunted until it happens to come out uniform, and
         * then quietly stops being uniform the next time the roll tables move;
         * naming the weapon cannot.
         */
        public SquadSpec primary(String weaponId) {
            this.armed = true;
            this.primaryWeaponId = Objects.requireNonNull(weaponId, "weaponId");
            return this;
        }

        /**
         * Issues this exact kit, one loadout per seat, in seat order: what a campaign deployment hands the battle.
         * The squad's size becomes the kit's length.
         */
        public SquadSpec loadouts(MarineLoadout[] kit) {
            Objects.requireNonNull(kit, "kit");
            if (kit.length == 0) throw new IllegalArgumentException("A kit needs at least one seat");
            this.armed = true;
            this.issuedKit = kit.clone();
            this.size = kit.length;
            return this;
        }

        /**
         * Issues one piece of special equipment (a satchel kit, say) to the first {@code seats} members of a squad whose
         * primaries are named with {@link #primary}; the rest carry nothing special. A squad's demolition capability is
         * read from exactly this kit.
         */
        public SquadSpec special(String specialId, int seats) {
            return special(specialId, seats, 0);
        }

        /** As {@link #special(String, int)}, with the rounds an ammunition-limited item starts with. */
        public SquadSpec special(String specialId, int seats, int ammo) {
            this.specialEquipmentId = Objects.requireNonNull(specialId, "specialId");
            this.specialSeats = seats;
            this.specialAmmo = ammo;
            return this;
        }

        /**
         * Spawns without primaries. Only for a scene whose question is about
         * something other than fire — an unarmed squad cannot contest anything.
         */
        public SquadSpec unarmed() {
            this.armed = false;
            return this;
        }

        /**
         * Gives every member this much health instead of the type's. For a scene whose question is about what
         * fire does to nerve rather than to bodies: a death drains a fire team by a fixed 0.30 and shrinks its
         * ceiling, so once marines start dying the casualty order decides everything else a comparison reads.
         */
        public SquadSpec health(float maxHp) {
            if (!(maxHp > 0f)) throw new IllegalArgumentException("Health must be positive: " + maxHp);
            this.health = maxHp;
            return this;
        }

        /** Issues every member armor of this capacity and rating, as a worn pattern would. */
        public SquadSpec armor(float capacity, float rating) {
            if (!(capacity > 0f) || !(rating > 0f)) {
                throw new IllegalArgumentException("Armor needs positive capacity and rating");
            }
            this.armorCapacity = capacity;
            this.armorRating = rating;
            return this;
        }

        /**
         * Handed every member's spec after its kit and squad are seeded and before it spawns: for what the
         * builder does not model, such as a garrison's role and home cell.
         */
        public SquadSpec each(Consumer<EntitySpec> customiser) {
            this.each = Objects.requireNonNull(customiser, "customiser");
            return this;
        }

        /** Immobile: a fixed emplacement, or a body that must stay where it was put. */
        public SquadSpec stationary() {
            this.stationary = true;
            return this;
        }

        /** The mission assignment written onto the squad once its id exists. */
        public SquadSpec assigned(IntFunction<ObjectiveAssignment> assignment) {
            this.assignment = Objects.requireNonNull(assignment, "assignment");
            return this;
        }

        /**
         * Makes this a mech lance of {@code variant} under {@code role}.
         *
         * <p>The loadout component is what routes a unit to the mech dispatcher
         * at all — without it these are ordinary bodies running the infantry
         * ladder, and a scene would record the infantry answer twice under two
         * names. A mech carries no infantry kit, so this and {@link #kit} are
         * alternatives rather than a pair.
         */
        public SquadSpec mech(MechVariant variant, MechRole role) {
            this.mechVariant = Objects.requireNonNull(variant, "variant");
            this.mechRole = Objects.requireNonNull(role, "role");
            this.type = UnitType.HEAVY_MECH;
            return this;
        }

        /**
         * Makes this a mech lance fitted as {@code deployment} says: its weapons, frame and role, spawned the way
         * {@code MechSupportPayload} spawns a dropped machine. Attack and vision range follow the fitted weapons, so a
         * refit arm is not left with the factory gun's reach.
         */
        public SquadSpec mech(MechDeploymentSpec deployment) {
            mech(deployment.variant(), deployment.role());
            this.mechDeployment = deployment;
            return this;
        }

        public SceneBuilder done() {
            return owner;
        }

        private static float fittedRange(MechDeploymentSpec deployment) {
            float reach = 0f;
            for (MechMountSlot slot : MechMountSlot.values()) {
                MechWeaponComponent c = deployment.weaponAt(slot);
                if (c != null) reach = Math.max(reach, c.weaponDef().range);
            }
            return reach;
        }

        /** Every seat carrying {@link #primaryWeaponId} and nothing else issued. */
        private MarineLoadout[] uniformKit() {
            MarineLoadout[] kit = new MarineLoadout[size];
            for (int i = 0; i < size; i++) {
                boolean carries = i < specialSeats;
                kit[i] = new MarineLoadout(UnitRole.COMBATANT, null, primaryWeaponId,
                        carries ? specialEquipmentId : null,
                        carries ? (specialAmmo > 0 ? specialAmmo
                                : SpecialEquipmentRegistry.require(specialEquipmentId).startingAmmo()) : 0);
            }
            return kit;
        }

        private int spawnInto(BattleSimulation sim) {
            if (mechVariant != null && !type.hasChassis()) {
                throw new IllegalStateException("Squad '" + key + "' asks for a mech variant on "
                        + type + ", which has no chassis");
            }
            int squadId = sim.mintSquad(faction, type);
            MarineLoadout[] kit = armed && mechVariant == null
                    ? (issuedKit != null ? issuedKit : primaryWeaponId != null ? uniformKit()
                            : InfantryLoadoutRolls.playerSquad(size, new Random(kitSeed)))
                    : null;
            spawned = new long[size];
            for (int i = 0; i < size; i++) {
                boolean inFile = fileStepX != 0 || fileStepY != 0;
                EntitySpec spec = inFile
                        ? new EntitySpec(key + "-" + i, faction, type,
                                owner.originX + atX + i * fileStepX, owner.originY + atY + i * fileStepY)
                        : new EntitySpec(key + "-" + i, faction, type,
                                owner.originX + atX - 2 + i % 4, owner.originY + atY + i / 4);
                if (mechVariant != null) mechVariant.applyTo(spec);
                if (mechDeployment != null) {
                    mechDeployment.applyTo(spec);
                    float reach = fittedRange(mechDeployment);
                    spec.attackRange(reach).visionRange(Math.max(mechVariant.visionRange, reach));
                }
                if (kit != null) kit[i].seedInto(spec);
                spec.squad(squadId);
                if (stationary) spec.moveSpeed(0f);
                if (health > 0f) spec.health(health);
                if (armorCapacity > 0f) spec.armor(armorCapacity, armorRating);
                each.accept(spec);
                spawned[i] = sim.spawn(spec);
                if (mechDeployment != null) {
                    MechLoadoutComponent loadout = mechDeployment.createLoadout(mechDeployment.role());
                    loadout.applyFrame(mechDeployment.frame());
                    loadout.installMissileReplenisher(mechDeployment.missileReplenisher());
                    sim.world().attachMechLoadout(spawned[i], loadout);
                    sim.getRoster().machineHosts().register(spawned[i], mechDeployment.deployedFrame().systems());
                } else if (mechVariant != null) {
                    sim.world().attachMechLoadout(spawned[i],
                            mechVariant.createLoadout(mechRole));
                }
            }
            Squad squad = sim.getSquad(squadId);
            if (squad != null) {
                // Written at spawn rather than left for the first tick: scenes
                // read liveness to decide whether a squad still stands, and a
                // squad reporting nobody alive before the sim has run once is
                // read as destroyed on the tick it was created.
                squad.originalSize = size;
                squad.aliveMembers = size;
                if (assignment != null) squad.assignedObjective = assignment.apply(squadId);
            }
            return squadId;
        }
    }

    /** One declared loose body. */
    private record UnitEntry(String key, Faction faction, UnitType type, int x, int y,
                             Consumer<EntitySpec> customiser) {

        private long spawnInto(BattleSimulation sim, int originX, int originY) {
            EntitySpec spec = new EntitySpec(key, faction, type, originX + x, originY + y);
            customiser.accept(spec);
            return sim.spawn(spec);
        }
    }
}
