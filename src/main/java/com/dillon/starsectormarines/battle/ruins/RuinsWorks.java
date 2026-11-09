package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Marker;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective;
import com.dillon.starsectormarines.battle.objective.side.SideField;
import com.dillon.starsectormarines.battle.objective.side.SideObjectiveService.Offer;
import com.dillon.starsectormarines.battle.objective.side.SideObjectiveService.OfferVerb;
import com.dillon.starsectormarines.battle.objective.side.SideTrigger;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Branch;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Consumer;
import com.dillon.starsectormarines.battle.sally.SallyDoors;
import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.battle.salvage.SalvageResource;
import com.dillon.starsectormarines.battle.satchel.DemolitionKit;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.SatchelChargeSpec;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

import java.util.ArrayList;
import java.util.List;

/**
 * A ruins site's works while the battle runs ({@code ruins-works.md}): the power hall, a junction per layer, and the
 * seal doors they power. The company restarts the hall by holding its console, and works a junction either way, any
 * number of times; both are offered on the field through the side objective's world prompt, though neither is a job
 * with an outcome. What the works pay: a sealed store's field cache, and the vault's powered breach.
 *
 * <p>The wiring, the same on every site: junction N feeds its layer's sealed store (the first side room), or the way
 * on (the layer's gate, when it has one, and at the last layer the vault's seal). Every junction starts on the way on,
 * and the hall's capacity is the way on's load plus one, so it runs everything onward and one store besides: the
 * player chooses which store, or gives up the vault's power for a store at the last layer.
 *
 * <p>Seal doors are shut as wall at setup, each cell like the wall beside it. A powered door opens as built passage; an
 * unpowered open door shuts once nobody stands in it or in the room it keeps. A breach through a shut door forces it
 * open for good, so nothing is locked for good. The forced way is the player's: a seal door's prompt and the collapse's
 * offer to set a charge, and a satchel carrier who comes within reach of it sets one there.
 *
 * <p>The records terminal is read like the hall is restarted. Until it is read, the sealed stores are not marked and
 * a junction does not say what it feeds until it has been thrown once; reading it marks the whole plan.
 */
public final class RuinsWorks {

    /** Seconds infantry hold the hall's console to restart it. UNTUNED. */
    public static final float RESTART_SECONDS = 6f;
    /** Seconds infantry work a junction box to throw it. UNTUNED. */
    public static final float THROW_SECONDS = 2f;
    /** How near the company must be to a console or box for its prompt to come up. UNTUNED. */
    public static final float PROMPT_CELLS = 10f;
    /** Seconds infantry hold the records terminal's console to read it. UNTUNED. */
    public static final float READ_SECONDS = 4f;
    /** How far past a satchel's contact range a carrier may stand from the cell he sets it on. UNTUNED. */
    static final float CHARGE_SLACK = 1.5f;
    /** How near a soldier must stand to a console or box to work it. */
    public static final float WORK_CELLS = 2.5f;
    static final String KEY = "ruins-works/";
    static final String HALL = KEY + "hall";
    static final String JUNCTION = KEY + "junction/";
    static final String RECORDS = KEY + "records";
    static final String DOOR = KEY + "door/";
    static final String COLLAPSE = KEY + "collapse";
    static final String FORCE = "force";
    public static final String VAULT_SEAL = "vault-seal";

    /**
     * A console or box the company works, at its stand cell. A console in a building of its own (the hall, the records
     * office) carries the building's footprint {@code area} (left, bottom, right, top): anyone of the company in it, or
     * within {@link #WORK_MARGIN} of its walls, works the console, since a squad sent to a room holds where its
     * tactical nodes are, not at the console. A box in the open has no area and is worked within {@link #WORK_CELLS}.
     */
    public record Site(String key, String name, int x, int y, int[] area) {
        public Site(String key, String name, int x, int y) { this(key, name, x, y, null); }
    }

    /** How far outside a console's building or a store's room the company still works it: a squad sent to a door stands two or three cells short of it. UNTUNED. */
    static final int WORK_MARGIN = 3;

    private final PowerGraph graph;
    private final Site hall;
    private final List<Site> junctions;
    private final List<SealDoor> doors;
    private final boolean[] open;
    private final boolean[] forced;
    private final MapEditor.PassageRecord[][] shut;
    private final List<SalvageCache> stores;
    private final Site records;
    private final SealDoor collapse;
    private final boolean[] known;
    private boolean read;
    private boolean cleared;
    private String forceKey;
    private RuinsVaultObjective vault;
    private boolean installed;
    private String pressedKey;
    private String pressedVerb;
    private String workKey;
    private String workVerb;
    private float work;

    RuinsWorks(PowerGraph graph, Site hall, List<Site> junctions, List<SealDoor> doors, List<SalvageCache> stores,
               Site records, SealDoor collapse) {
        this.graph = graph;
        this.records = records;
        this.collapse = collapse;
        this.known = new boolean[junctions.size()];
        this.hall = hall;
        this.junctions = List.copyOf(junctions);
        this.doors = List.copyOf(doors);
        this.stores = List.copyOf(stores);
        this.open = new boolean[doors.size()];
        this.forced = new boolean[doors.size()];
        this.shut = new MapEditor.PassageRecord[doors.size()][];
    }

    /** The works a delve's published ground carries, wired as the class says. */
    public static RuinsWorks of(RuinsDelve delve) {
        List<Consumer> consumers = new ArrayList<>();
        List<SealDoor> doors = new ArrayList<>();
        List<SalvageCache> stores = new ArrayList<>();
        List<Site> junctions = new ArrayList<>();
        List<Branch> initial = new ArrayList<>();
        int onward = 0;
        for (RuinsDelve.Layer layer : delve.layers()) {
            int j = layer.index();
            RuinsDelve.Junction box = delve.junctions().get(j);
            junctions.add(new Site(JUNCTION + j, Strings.format("ruinsWorksJunctionName", j + 1),
                    box.stand().x(), box.stand().y()));
            initial.add(Branch.ONWARD);
            RuinsDelve.SideRoom room = layer.rooms().get(0);
            RuinsDelve.Area f = room.footprint();
            String storeId = "store-" + (j + 1);
            doors.add(new SealDoor(storeId, Strings.format("ruinsWorksStoreName", j + 1),
                    new int[][]{{room.door().x(), room.door().y()}}, room.interior().x(), room.interior().y(),
                    new int[]{f.left(), f.bottom(), f.right(), f.top()}, true));
            consumers.add(new Consumer(storeId, j, Branch.STORE));
            SalvageResource resource = SalvageResource.values()[j % SalvageResource.values().length];
            // The find lies just inside the door and is recovered by holding the room, as the hall is worked.
            int cacheX = room.door().x() + Integer.signum(room.interior().x() - room.door().x());
            int cacheY = room.door().y() + Integer.signum(room.interior().y() - room.door().y());
            stores.add(new SalvageCache("sealed-" + storeId, cacheX, cacheY, resource, resource.cacheQuantity)
                    .heldFromRoom(f.left(), f.bottom(), f.right(), f.top(), WORK_MARGIN));
            for (RuinsDelve.Way way : layer.ways()) {
                if (way.kind() != RuinsDelve.WayKind.GATE) continue;
                RuinsDelve.Area span = way.span();
                int[][] cells = new int[span.height()][];
                for (int y = span.bottom(); y <= span.top(); y++) cells[y - span.bottom()] = new int[]{span.left(), y};
                String gateId = "gate-" + (j + 1);
                doors.add(new SealDoor(gateId, Strings.format("ruinsWorksGateName", j + 1), cells,
                        way.far().x(), way.far().y(), new int[]{span.left(), span.bottom(), span.right(), span.top()},
                        false));
                consumers.add(new Consumer(gateId, j, Branch.ONWARD));
                onward++;
            }
        }
        int last = delve.layerCount() - 1;
        // On scattered ground the interior is only the bunker round the vault: its one door is sealed on the way on.
        RuinsDelve.Interior interior = delve.interior();
        if (interior != null && interior.firstLayer() == delve.layerCount()) {
            for (int e = 0; e < interior.entrances().size(); e++) {
                RuinsDelve.Area door = interior.entrances().get(e);
                String id = "bunker-" + (e + 1);
                doors.add(new SealDoor(id, Strings.get("ruinsWorksBunkerName"), cells(door), door.right() + 1,
                        door.centreY(), box(door), false));
                consumers.add(new Consumer(id, last, Branch.ONWARD));
                onward++;
            }
        }
        // The vault's inner door is its seal: power opens it for the powered breach, a charge forces it for the slow one.
        RuinsDelve.Vault vaultPlace = delve.vault();
        int ix = vaultPlace.innerDoor().x(), iy = vaultPlace.innerDoor().y();
        doors.add(new SealDoor(VAULT_SEAL, Strings.get("ruinsWorksVaultSealName"), new int[][]{{ix, iy}},
                vaultPlace.source().x(), vaultPlace.source().y(), box(vaultPlace.footprint()), false));
        consumers.add(new Consumer(VAULT_SEAL, last, Branch.ONWARD));
        onward++;
        RuinsDelve.PowerHall hallPlace = delve.powerHall();
        Site hall = new Site(HALL, Strings.get("ruinsWorksHallName"), hallPlace.console().x(), hallPlace.console().y(),
                box(hallPlace.footprint()));
        Site records = delve.records() == null ? null : new Site(RECORDS, Strings.get("ruinsWorksRecordsName"),
                delve.records().console().x(), delve.records().console().y(), box(delve.records().footprint()));
        SealDoor collapse = null;
        if (delve.collapse() != null && delve.collapse().shortWay() != null) {
            RuinsDelve.Way way = delve.collapse().shortWay();
            RuinsDelve.Area span = way.span();
            int[][] cells = new int[span.height()][];
            for (int y = span.bottom(); y <= span.top(); y++) cells[y - span.bottom()] = new int[]{span.left(), y};
            collapse = new SealDoor("collapse", Strings.get("ruinsWorksCollapseName"), cells, way.far().x(),
                    way.far().y(), new int[]{span.left(), span.bottom(), span.right(), span.top()}, false);
        }
        return new RuinsWorks(new PowerGraph(onward + 1, initial, consumers), hall, junctions, doors, stores, records,
                collapse);
    }

    private static int[] box(RuinsDelve.Area a) { return new int[]{a.left(), a.bottom(), a.right(), a.top()}; }

    private static int[][] cells(RuinsDelve.Area a) {
        int[][] out = new int[a.width() * a.height()][];
        int k = 0;
        for (int y = a.bottom(); y <= a.top(); y++) for (int x = a.left(); x <= a.right(); x++) out[k++] = new int[]{x, y};
        return out;
    }

    /** The field caches behind the sealed stores, for the battle's field salvage. */
    public List<SalvageCache> storeCaches() { return stores; }

    public PowerGraph graph() { return graph; }
    public List<SealDoor> doors() { return doors; }
    public boolean isOpen(int door) { return open[door]; }
    public boolean isForced(int door) { return forced[door]; }
    public Site hall() { return hall; }
    public List<Site> junctions() { return junctions; }
    public Site records() { return records; }
    public boolean recordsRead() { return read; }
    public SealDoor collapse() { return collapse; }
    public boolean collapseCleared() { return cleared; }
    /** Whether the player knows what junction {@code j} feeds: thrown once, or the records read. */
    public boolean junctionKnown(int j) { return read || known[j]; }

    /** Shuts every seal door as the wall beside it. At setup, before the battle's field finds are placed. */
    public void install(BattleSimulation sim) {
        if (installed) return;
        installed = true;
        for (Objective objective : sim.getObjectives()) {
            if (objective instanceof RuinsVaultObjective found) vault = found;
        }
        MapEditor editor = sim.mapEditor();
        for (int d = 0; d < doors.size(); d++) {
            SealDoor door = doors.get(d);
            shut[d] = new MapEditor.PassageRecord[door.cells().length];
            for (int c = 0; c < door.cells().length; c++) {
                int x = door.cells()[c][0], y = door.cells()[c][1];
                MapEditor.PassageRecord wall = jamb(sim, editor, door, x, y);
                if (wall == null) throw new IllegalStateException(door.id() + " has no wall beside it to seal as");
                shut[d][c] = wall;
                editor.closePassage(wall);
            }
        }
    }

    /**
     * The nearest wall to a door cell, recorded as if it stood in the door cell; null when none stands within
     * {@link #JAMB_REACH}. A room door has its jambs beside it; a gate's span may meet its wall line a cell or two
     * off where the line jogs.
     */
    private static MapEditor.PassageRecord jamb(BattleSimulation sim, MapEditor editor, SealDoor door, int x, int y) {
        for (int r = 1; r <= JAMB_REACH; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.abs(dx) + Math.abs(dy) != r) continue;
                    int nx = x + dx, ny = y + dy;
                    if (!sim.getGrid().inBounds(nx, ny) || sim.getGrid().isWalkable(nx, ny) || inDoor(door, nx, ny)) {
                        continue;
                    }
                    MapEditor.PassageRecord w = editor.recordPassage(nx, ny);
                    return new MapEditor.PassageRecord(x, y, w.flags(), w.edges(), w.wallHp(), w.wall(), w.window(),
                            w.fixture(), w.groundKind(), w.surface(), w.ownCover(), w.ownCatch(), w.towardCover(),
                            w.towardCatch());
                }
            }
        }
        return null;
    }

    /** How far from a door cell its jamb may stand. */
    static final int JAMB_REACH = 3;

    private static boolean inDoor(SealDoor door, int x, int y) {
        for (int[] c : door.cells()) if (c[0] == x && c[1] == y) return true;
        return false;
    }

    /** One tick: the player's press, the work at a console or box, then the doors and the vault's seal. */
    public void tick(BattleSimulation sim) {
        if (!installed || sim.isComplete()) return;
        SideField field = SideField.of(sim);
        if (pressedKey != null && FORCE.equals(pressedVerb)) {
            forceKey = pressedKey;
            pressedKey = null;
        }
        if (pressedKey != null) {
            Site site = site(pressedKey);
            if (site != null && near(field, site, PROMPT_CELLS)) {
                workKey = pressedKey;
                workVerb = pressedVerb;
                work = 0f;
            }
            pressedKey = null;
        }
        if (forceKey != null) tickForce(sim, field);
        if (workKey != null) {
            Site site = site(workKey);
            if (!near(field, site, PROMPT_CELLS)) {
                workKey = null;
            } else if (worked(sim, field, site)) {
                work += BattleSimulation.TICK_DT;
                if (work >= required(workKey)) finish(sim);
            } else {
                work = 0f;
            }
        }
        settle(sim);
    }

    private void finish(BattleSimulation sim) {
        if (workKey.equals(HALL)) {
            graph.restart();
            loud(sim, hall.x(), hall.y());
        } else if (workKey.equals(RECORDS)) {
            read = true;
        } else {
            int j = Integer.parseInt(workKey.substring(JUNCTION.length()));
            graph.throwJunction(j, Branch.valueOf(workVerb));
            known[j] = true;
        }
        workKey = null;
        work = 0f;
    }

    /** A restart is loud: the occupants hear it as the nearest bore waking, or the nearest security zone stirring. */
    private static void loud(BattleSimulation sim, int x, int y) {
        if (sim.ruinsSleepers() != null) sim.ruinsSleepers().loud(x + 0.5f, y + 0.5f);
        if (sim.derelictSecurity() != null) sim.derelictSecurity().network().loud(x, y, sim.getSimTickIndex());
    }

    private static float required(String key) {
        return key.equals(HALL) ? RESTART_SECONDS : key.equals(RECORDS) ? READ_SECONDS : THROW_SECONDS;
    }

    /** The door or collapse the player asked a charge on, or null once it stands open. */
    private SealDoor forceTarget(String key) {
        if (COLLAPSE.equals(key)) return cleared ? null : collapse;
        for (int d = 0; d < doors.size(); d++) {
            if ((DOOR + doors.get(d).id()).equals(key)) return open[d] ? null : doors.get(d);
        }
        return null;
    }

    /**
     * A charge on the target the player picked: the first company satchel carrier within his reach of one of its cells
     * sets it there, as on any wall ({@code SatchelChargeService.plantAtCell}), and the blast that cuts the cell forces
     * the door or clears the collapse. The order lapses once nobody of the company is near.
     */
    private void tickForce(BattleSimulation sim, SideField field) {
        SealDoor target = forceTarget(forceKey);
        if (target == null) {
            forceKey = null;
            return;
        }
        int[] m = target.middle();
        if (field.nearest(SideField.Who.COMBATANT, m[0] + .5f, m[1] + .5f, PROMPT_CELLS + target.cells().length) == 0L) {
            forceKey = null;
            return;
        }
        for (int[] c : target.cells()) if (sim.satchelCharges().hasChargeAtCell(c[0], c[1])) return;
        World world = sim.world();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE || sim.isRiding(unit)
                    || !DemolitionKit.carried(world, unit) || world.secondaryCooldownTimer(unit) > 0f) continue;
            SpecialEquipmentDef kit = world.specialEquipment(unit);
            if (kit.activation() != SpecialActivation.UTILITY_SATCHEL) continue;
            SatchelChargeSpec spec = kit.satchelChargeSpec();
            for (int[] c : target.cells()) {
                float dx = world.x(unit) - (c[0] + .5f), dy = world.y(unit) - (c[1] + .5f);
                if (dx * dx + dy * dy > sq(spec.contactRange() + CHARGE_SLACK)) continue;
                if (!sim.satchelCharges().plantAtCell(unit, c[0], c[1], Faction.MARINE, spec)) continue;
                world.setSecondaryCooldownTimer(unit, spec.cooldownSeconds());
                if (kit.usesAmmunition()) world.setSecondaryAmmo(unit, world.secondaryAmmo(unit) - 1);
                sim.noteSecondaryUsed(unit);
                forceKey = null;
                return;
            }
        }
    }

    private static float sq(float v) { return v * v; }

    /** Opens what is powered, shuts what lost power once it is clear, and hands the vault its seal's power. */
    void settle(BattleSimulation sim) {
        MapEditor editor = sim.mapEditor();
        if (collapse != null && !cleared && breached(sim, collapse)) cleared = true;
        for (int d = 0; d < doors.size(); d++) {
            if (forced[d]) continue;
            SealDoor door = doors.get(d);
            if (!open[d] && breached(sim, door)) {
                forced[d] = true;
                open[d] = true;
                if (vault != null && door.id().equals(VAULT_SEAL)) vault.forceSeal();
                continue;
            }
            boolean powered = graph.powered(graph.consumer(door.id()));
            if (powered && !open[d]) {
                for (int[] c : door.cells()) editor.openPassage(c[0], c[1], door.likeX(), door.likeY());
                open[d] = true;
            } else if (!powered && open[d] && !door.store() && vault != null && vault.breached()) {
                // Once the vault is breached the way out stays open: the haul is not shut in behind a lost feed.
                continue;
            } else if (!powered && open[d] && !occupied(sim, door)) {
                for (MapEditor.PassageRecord wall : shut[d]) editor.closePassage(wall);
                open[d] = false;
                dropRoutesThrough(sim, door);
            }
        }
        if (vault != null) vault.setVaultPowered(graph.powered(graph.consumer(VAULT_SEAL)));
    }

    private static boolean breached(BattleSimulation sim, SealDoor door) {
        for (int[] c : door.cells()) if (sim.getGrid().isWalkable(c[0], c[1])) return true;
        return false;
    }

    /** Whether any body on the ground stands in the door or the room it keeps. */
    private static boolean occupied(BattleSimulation sim, SealDoor door) {
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.isRiding(unit) || sim.identity().type(unit).isStatic()) continue;
            float x = sim.world().x(unit), y = sim.world().y(unit);
            if (door.keeps(x, y)) return true;
            for (int[] c : door.cells()) {
                if (Math.abs(x - (c[0] + .5f)) < 1.5f && Math.abs(y - (c[1] + .5f)) < 1.5f) return true;
            }
        }
        return false;
    }

    private static void dropRoutesThrough(BattleSimulation sim, SealDoor door) {
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int[] c : door.cells()) {
            box[0] = Math.min(box[0], c[0]);
            box[1] = Math.min(box[1], c[1]);
            box[2] = Math.max(box[2], c[0] + 1f);
            box[3] = Math.max(box[3], c[1] + 1f);
        }
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.isRiding(unit) || sim.identity().type(unit).isStatic()) continue;
            if (SallyDoors.routeCrosses(sim, unit, box, sim.physicalRadius(unit) + 0.5f)) sim.clearPath(unit);
        }
    }

    /** The player pressed {@code verb} on {@code key}'s prompt: it takes effect on the next tick. */
    public void choose(String key, String verb) {
        pressedKey = key;
        pressedVerb = verb;
    }

    /** Whether an offer key is the works'. */
    public static boolean owns(String key) { return key != null && key.startsWith(KEY); }

    /** The works' prompts on offer now: the cold hall and every junction the company is near, not being worked. */
    public List<Offer> offers(SideField field) {
        List<Offer> out = new ArrayList<>();
        String load = Strings.format("ruinsWorksLoad", graph.load(), graph.capacity());
        if (!graph.running() && !hall.key().equals(workKey) && near(field, hall, PROMPT_CELLS)) {
            out.add(new Offer(hall.key(), hall.x(), hall.y(), SideTrigger.Interact.Layout.CARD, hall.name(),
                    Strings.get("ruinsWorksTagCold"), Strings.format("ruinsWorksHallDetail", seconds(RESTART_SECONDS)),
                    List.of(new OfferVerb("restart", Strings.get("ruinsWorksVerbRestart"), ""))));
        }
        if (records != null && !read && !RECORDS.equals(workKey) && near(field, records, PROMPT_CELLS)) {
            out.add(new Offer(records.key(), records.x(), records.y(), SideTrigger.Interact.Layout.CARD, records.name(),
                    Strings.get("ruinsWorksTagUnread"), Strings.format("ruinsWorksRecordsDetail", seconds(READ_SECONDS)),
                    List.of(new OfferVerb("read", Strings.get("ruinsWorksVerbRead"), ""))));
        }
        for (int j = 0; j < junctions.size(); j++) {
            Site box = junctions.get(j);
            if (box.key().equals(workKey) || !near(field, box, PROMPT_CELLS)) continue;
            Branch at = graph.position(j);
            Branch other = at == Branch.STORE ? Branch.ONWARD : Branch.STORE;
            String detail = junctionKnown(j)
                    ? Strings.format("ruinsWorksJunctionDetail", Strings.get(at == Branch.STORE
                            ? "ruinsWorksFeedingStore" : "ruinsWorksFeedingOnward"))
                    : Strings.get("ruinsWorksJunctionUnknown");
            String verb = !junctionKnown(j) ? Strings.get("ruinsWorksVerbThrow")
                    : Strings.get(other == Branch.STORE ? "ruinsWorksVerbStore" : "ruinsWorksVerbOnward");
            out.add(new Offer(box.key(), box.x(), box.y(), SideTrigger.Interact.Layout.CARD, box.name(),
                    graph.running() ? load : Strings.get("ruinsWorksTagCold"), detail,
                    List.of(new OfferVerb(other.name(), verb, ""))));
        }
        for (int d = 0; d < doors.size(); d++) {
            SealDoor door = doors.get(d);
            if (open[d] || (door.store() && !read)) continue;
            forceOffer(out, field, DOOR + door.id(), door, Strings.get("ruinsWorksDoorSealed"),
                    Strings.get("ruinsWorksForceDetail"));
        }
        if (collapse != null && !cleared) {
            forceOffer(out, field, COLLAPSE, collapse, Strings.get("ruinsWorksCollapseChoked"),
                    Strings.get("ruinsWorksCollapseDetail"));
        }
        return out;
    }

    private void forceOffer(List<Offer> out, SideField field, String key, SealDoor target, String tag, String detail) {
        int[] m = target.middle();
        if (key.equals(forceKey) || field.nearest(SideField.Who.COMBATANT, m[0] + .5f, m[1] + .5f, PROMPT_CELLS) == 0L) {
            return;
        }
        out.add(new Offer(key, m[0], m[1], SideTrigger.Interact.Layout.CARD, target.name(), tag, detail,
                List.of(new OfferVerb(FORCE, Strings.get("ruinsWorksVerbForce"), ""))));
    }

    private static String seconds(float s) { return Strings.format("objectiveSeconds", Math.round(s * 10) / 10f); }

    private Site site(String key) {
        if (hall.key().equals(key)) return hall;
        if (records != null && records.key().equals(key)) return records;
        for (Site box : junctions) if (box.key().equals(key)) return box;
        return null;
    }

    /** Whether somebody of the company is where {@code site} is worked from. */
    static boolean worked(BattleSimulation sim, SideField field, Site site) {
        if (site.area() == null) return near(field, site, WORK_CELLS);
        int[] a = site.area();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long unit = sim.liveUnitAt(i);
            if (sim.identity().faction(unit) != Faction.MARINE || sim.isRiding(unit)
                    || sim.identity().type(unit).isStatic()) continue;
            float x = sim.world().x(unit), y = sim.world().y(unit);
            if (x >= a[0] - WORK_MARGIN && x < a[2] + 1 + WORK_MARGIN && y >= a[1] - WORK_MARGIN
                    && y < a[3] + 1 + WORK_MARGIN) return true;
        }
        return false;
    }

    private static boolean near(SideField field, Site site, float reach) {
        return field.nearest(SideField.Who.COMBATANT, site.x() + .5f, site.y() + .5f, reach) != 0L;
    }

    /** The works' optional row on the rail: the hall's state and load, with every console, box and seal door marked. */
    public Item item() {
        boolean running = graph.running();
        boolean overloaded = running && graph.overloaded();
        String phase = Strings.get(!running ? "ruinsWorksPhaseCold" : overloaded ? "ruinsWorksPhaseOverloaded"
                : "ruinsWorksPhaseRunning");
        String instruction = Strings.get(!running ? "ruinsWorksRestart" : overloaded ? "ruinsWorksShed"
                : "ruinsWorksRoute");
        float progress = HALL.equals(workKey) ? work / RESTART_SECONDS : running ? 1f : 0f;
        List<Marker> markers = new ArrayList<>();
        MissionObjectiveSnapshot.State active = MissionObjectiveSnapshot.State.ACTIVE;
        markers.add(new Marker(hall.key(), hall.name(), hall.x(), hall.y(), phase,
                running ? MissionObjectiveSnapshot.State.COMPLETE : active));
        for (int j = 0; j < junctions.size(); j++) {
            Site box = junctions.get(j);
            markers.add(new Marker(box.key(), box.name(), box.x(), box.y(), Strings.get(!junctionKnown(j)
                    ? "ruinsWorksFeedingUnknown" : graph.position(j) == Branch.STORE ? "ruinsWorksFeedingStoreShort"
                    : "ruinsWorksFeedingOnwardShort"), active));
        }
        if (records != null) {
            markers.add(new Marker(records.key(), records.name(), records.x(), records.y(),
                    Strings.get(read ? "ruinsWorksTagRead" : "ruinsWorksTagUnread"),
                    read ? MissionObjectiveSnapshot.State.COMPLETE : active));
        }
        if (collapse != null) {
            int[] m = collapse.middle();
            markers.add(new Marker(COLLAPSE, collapse.name(), m[0], m[1], Strings.get(cleared
                    ? "ruinsWorksCollapseCleared" : "ruinsWorksCollapseChoked"),
                    cleared ? MissionObjectiveSnapshot.State.COMPLETE : active));
        }
        for (int d = 0; d < doors.size(); d++) {
            SealDoor door = doors.get(d);
            // A sealed store is the site's secret until the records are read or it stands open.
            if (door.store() && !read && !open[d]) continue;
            int[] m = door.middle();
            String status = Strings.get(forced[d] ? "ruinsWorksDoorForced" : open[d] ? "ruinsWorksDoorOpen"
                    : "ruinsWorksDoorSealed");
            markers.add(new Marker(KEY + door.id(), door.name(), m[0], m[1], status,
                    open[d] ? MissionObjectiveSnapshot.State.COMPLETE : active));
        }
        return new Item("zz-ruins-works", Strings.get("ruinsWorksName"), running && !overloaded
                ? MissionObjectiveSnapshot.State.COMPLETE : active, phase, "", instruction,
                Strings.format("ruinsWorksLoad", graph.load(), graph.capacity()), Math.max(0f, Math.min(1f, progress)),
                overloaded ? Strings.get("ruinsWorksOverloadBlocker") : "", markers, hall.key(), true);
    }
}
