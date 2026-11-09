package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Marker;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective;
import com.dillon.starsectormarines.battle.door.HardenedWalls;
import com.dillon.starsectormarines.battle.door.LockedDoor;
import com.dillon.starsectormarines.battle.setup.RuinsDefence;
import com.dillon.starsectormarines.battle.objective.side.SideField;
import com.dillon.starsectormarines.battle.objective.side.SideObjectiveService.Offer;
import com.dillon.starsectormarines.battle.objective.side.SideObjectiveService.OfferVerb;
import com.dillon.starsectormarines.battle.objective.side.SideTrigger;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Branch;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Consumer;
import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.battle.salvage.SalvageResource;
import com.dillon.starsectormarines.battle.satchel.DemolitionKit;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.SatchelChargeSpec;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

import java.util.ArrayList;
import java.util.List;

/**
 * A ruins site's works while the battle runs ({@code ruins-expedition.md}, Works): the power hall, a junction per layer, and the
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
    static final String RECORDS_KEY = KEY + "records-key";
    static final String KEYPAD = KEY + "keypad";
    static final String DOOR = KEY + "door/";
    static final String COLLAPSE = KEY + "collapse";
    static final String WAKE = KEY + "wake/";
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
    /** The published ground the works were wired on; null for works built without one. */
    private RuinsDelve delve;
    private final Site hall;
    private final List<Site> junctions;
    private final List<SealDoor> doors;
    private final List<LockedDoor> locks;
    /** The vault suite's footprint, whose walls are hardened at install; null for none. */
    private int[] vaultShell;
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
    /** The keypad by the vault's door, the second of its two keys with the records console; null for none. */
    Site keypad;
    /** How this site's vault door opens beyond its power, drawn from the site's ground. */
    VaultAccess access = VaultAccess.TWO_KEYS;
    /** The vault door's keys ({@code ruins-expedition.md}, Works). */
    final VaultKeys keys = new VaultKeys();
    /** The vault code, for {@link VaultAccess#RECORDS_CODE}. */
    final VaultCode code = new VaultCode();
    private int keysHeld;
    /** Each landing court whose lift the works power on an empty site: its consumer id and ground box. */
    private final List<Court> courts = new ArrayList<>();
    /** For each of {@link #courts}, its index among the vault objective's lift points, or -1; set at install. */
    private int[] courtLifts = new int[0];

    /**
     * A wake point ({@code ruins-pressure.md}, slice 2): where the occupant's stir pushes come up in a layer inside, the
     * floor of a side room that is not a store. A company charge going off over it collapses it for good, and no push
     * comes up in that layer again.
     *
     * @param layer the layer it serves
     * @param spot  its one cell as a span the works' force path can set a charge on
     */
    public record WakePoint(int layer, SealDoor spot) {
        public int x() { return spot.cells()[0][0]; }

        public int y() { return spot.cells()[0][1]; }
    }

    private final List<WakePoint> wakes = new ArrayList<>();
    private final List<Boolean> wakeCollapsed = new ArrayList<>();
    /** The company's charges armed at the last look, {id, x, y, blast radius}, to see which went off where. */
    private final List<float[]> armed = new ArrayList<>();

    /** A landing court's lift, a consumer on its layer's store side. */
    record Court(String id, String name, int[] ground) { }
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
        List<LockedDoor> built = new ArrayList<>();
        for (SealDoor door : doors) {
            built.add(new LockedDoor(door.id(), door.cells(), door.likeX(), door.likeY(), door.keep(), door.hardened()));
        }
        this.locks = List.copyOf(built);
    }

    /**
     * The works a delve's published ground carries, wired as the class says. An empty site ({@link RuinsDefence#NONE})
     * always pays off: its last layer seals both side rooms as stores, so it has a second store, and each landing
     * court's lift is powered with its layer's store. An occupied site leaves its seal gates and bunker door open
     * (owner pick {@code ruins-occupied-locks}); its stores and vault stay locked. The vault's access is drawn from the
     * site's ground ({@link VaultAccess}).
     */
    public static RuinsWorks of(RuinsDelve delve, RuinsDefence defence) {
        boolean empty = defence == RuinsDefence.NONE;
        int layerCount = delve.layerCount();
        int[] onwardBy = new int[layerCount], storeBy = new int[layerCount];
        List<Court> courts = new ArrayList<>();
        List<Consumer> consumers = new ArrayList<>();
        List<SealDoor> doors = new ArrayList<>();
        List<SalvageCache> stores = new ArrayList<>();
        List<Site> junctions = new ArrayList<>();
        List<Branch> initial = new ArrayList<>();
        for (RuinsDelve.Layer layer : delve.layers()) {
            int j = layer.index();
            RuinsDelve.Junction box = delve.junctions().get(j);
            junctions.add(new Site(JUNCTION + j, Strings.format("ruinsWorksJunctionName", j + 1),
                    box.stand().x(), box.stand().y()));
            initial.add(Branch.ONWARD);
            int sealed = storesIn(j, delve.layerCount(), layer.rooms().size(), defence);
            for (int r = 0; r < sealed; r++) {
                RuinsDelve.SideRoom room = layer.rooms().get(r);
                RuinsDelve.Area f = room.footprint();
                int n = stores.size() + 1;
                String storeId = "store-" + n;
                doors.add(new SealDoor(storeId, Strings.format("ruinsWorksStoreName", n),
                        new int[][]{{room.door().x(), room.door().y()}}, room.interior().x(), room.interior().y(),
                        new int[]{f.left(), f.bottom(), f.right(), f.top()}, true));
                consumers.add(new Consumer(storeId, j, Branch.STORE));
                storeBy[j]++;
                SalvageResource resource = SalvageResource.values()[(n - 1) % SalvageResource.values().length];
                // The find lies just inside the door and is recovered by holding the room, as the hall is worked.
                int cacheX = room.door().x() + Integer.signum(room.interior().x() - room.door().x());
                int cacheY = room.door().y() + Integer.signum(room.interior().y() - room.door().y());
                stores.add(new SalvageCache("sealed-" + storeId, cacheX, cacheY, resource, resource.cacheQuantity)
                        .heldFromRoom(f.left(), f.bottom(), f.right(), f.top(), WORK_MARGIN)
                        // A sealed store keeps the old colony's hardware (RuinsFindTier.Place.SEALED_STORE).
                        .asTechCache());
            }
            if (empty && layer.court() != null) {
                // The court's lift comes up with its layer's store: the way on, or the store and the near lift.
                String id = "court-" + (j + 1);
                consumers.add(new Consumer(id, j, Branch.STORE));
                storeBy[j]++;
                courts.add(new Court(id, Strings.format("ruinsWorksCourtName", j + 1), box(layer.court().ground())));
            }
            for (RuinsDelve.Way way : layer.ways()) {
                if (!empty || way.kind() != RuinsDelve.WayKind.GATE) continue;
                RuinsDelve.Area span = way.span();
                int[][] cells = new int[span.height()][];
                for (int y = span.bottom(); y <= span.top(); y++) cells[y - span.bottom()] = new int[]{span.left(), y};
                String gateId = "gate-" + (j + 1);
                doors.add(new SealDoor(gateId, Strings.format("ruinsWorksGateName", j + 1), cells,
                        way.far().x(), way.far().y(), new int[]{span.left(), span.bottom(), span.right(), span.top()},
                        false));
                consumers.add(new Consumer(gateId, j, Branch.ONWARD));
                onwardBy[j]++;
            }
        }
        int last = delve.layerCount() - 1;
        // On scattered ground the interior is only the bunker round the vault: its one door is sealed on the way on.
        RuinsDelve.Interior interior = delve.interior();
        if (empty && interior != null && interior.firstLayer() == delve.layerCount()) {
            for (int e = 0; e < interior.entrances().size(); e++) {
                RuinsDelve.Area door = interior.entrances().get(e);
                String id = "bunker-" + (e + 1);
                doors.add(new SealDoor(id, Strings.get("ruinsWorksBunkerName"), cells(door), door.right() + 1,
                        door.centreY(), box(door), false));
                consumers.add(new Consumer(id, last, Branch.ONWARD));
                onwardBy[last]++;
            }
        }
        // The vault's inner door is its seal, hardened: its power and its access open it, for the powered breach.
        RuinsDelve.Vault vaultPlace = delve.vault();
        int ix = vaultPlace.innerDoor().x(), iy = vaultPlace.innerDoor().y();
        doors.add(new SealDoor(VAULT_SEAL, Strings.get("ruinsWorksVaultSealName"), new int[][]{{ix, iy}},
                vaultPlace.source().x(), vaultPlace.source().y(), box(vaultPlace.footprint()), false, true));
        VaultAccess access = VaultAccess.draw(siteSeed(delve), delve.records() != null, onwardBy[last] > 0);
        if (access == VaultAccess.CROSSED_FEED) {
            consumers.add(new Consumer(VAULT_SEAL, last, Branch.STORE));
            storeBy[last]++;
        } else {
            consumers.add(new Consumer(VAULT_SEAL, last, Branch.ONWARD));
            onwardBy[last]++;
        }
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
        RuinsWorks works = new RuinsWorks(new PowerGraph(capacity(onwardBy, storeBy), initial, consumers), hall, junctions,
                doors, stores, records, collapse);
        works.delve = delve;
        works.vaultShell = box(vaultPlace.footprint());
        works.access = access;
        works.courts.addAll(courts);
        if (!empty && defence != null) works.placeWakes(delve, defence);
        if (records != null) {
            // The keypad stands on the antechamber's floor in front of the vault's door, toward its outer door.
            int kx = ix + Integer.signum(vaultPlace.door().x() - ix);
            works.keypad = new Site(KEYPAD, Strings.get("ruinsWorksKeypadName"), kx, iy);
        }
        return works;
    }

    /**
     * One wake point in each layer inside that has a side room past its stores, on that room's floor; named for who
     * comes up through it.
     */
    private void placeWakes(RuinsDelve delve, RuinsDefence defence) {
        int firstInside = delve.interior() != null ? delve.interior().firstLayer() : 0;
        String name = Strings.get(switch (defence) {
            case SECURITY -> "ruinsWakeHatch";
            case CREW -> "ruinsWakeCrawlway";
            default -> "ruinsWakeBoreMound";
        });
        for (RuinsDelve.Layer layer : delve.layers()) {
            RuinsDelve.SideRoom room = wakeRoom(layer, firstInside, delve.layerCount(), defence);
            if (room == null) continue;
            int x = room.interior().x(), y = room.interior().y();
            SealDoor spot = new SealDoor("wake-" + (layer.index() + 1),
                    Strings.format("ruinsWakeName", name, layer.index() + 1), new int[][]{{x, y}}, x, y,
                    new int[]{x, y, x, y}, false);
            wakes.add(new WakePoint(layer.index(), spot));
            wakeCollapsed.add(false);
        }
    }

    /** The side room a layer's wake point stands in: its first past the stores, inside only; null for none. */
    static RuinsDelve.SideRoom wakeRoom(RuinsDelve.Layer layer, int firstInside, int layerCount, RuinsDefence defence) {
        if (layer.index() < firstInside) return null;
        int stores = storesIn(layer.index(), layerCount, layer.rooms().size(), defence);
        return stores < layer.rooms().size() ? layer.rooms().get(stores) : null;
    }

    /** The site's wake points, in layer order. */
    public List<WakePoint> wakePoints() { return List.copyOf(wakes); }

    /** Layer {@code layer}'s wake point, or null where it has none. */
    public WakePoint wakePoint(int layer) {
        for (WakePoint wake : wakes) if (wake.layer() == layer) return wake;
        return null;
    }

    /** Whether layer {@code layer}'s wake point has been collapsed by a charge. */
    public boolean wakeCollapsed(int layer) {
        for (int w = 0; w < wakes.size(); w++) if (wakes.get(w).layer() == layer) return wakeCollapsed.get(w);
        return false;
    }

    /** Whether a charge of {@code radius} gone off at {@code (x, y)} collapses a wake point at cell {@code (wx, wy)}. */
    static boolean collapses(int wx, int wy, float x, float y, float radius) {
        float dx = wx + 0.5f - x, dy = wy + 0.5f - y;
        float reach = radius + 0.5f;
        return dx * dx + dy * dy <= reach * reach;
    }

    /** The company's charges that went off since the last look collapse the wake points they cover. */
    private void watchCharges(BattleSimulation sim) {
        if (wakes.isEmpty()) return;
        List<float[]> now = new ArrayList<>();
        for (var charge : sim.satchelCharges().activeCharges()) {
            if (charge.sourceFaction() == Faction.MARINE) {
                now.add(new float[]{charge.id(), charge.x(), charge.y(), charge.blastRadius()});
            }
        }
        for (float[] was : armed) {
            boolean still = false;
            for (float[] is : now) still |= is[0] == was[0];
            if (still) continue;
            for (int w = 0; w < wakes.size(); w++) {
                if (!wakeCollapsed.get(w) && collapses(wakes.get(w).x(), wakes.get(w).y(), was[1], was[2], was[3])) {
                    wakeCollapsed.set(w, true);
                }
            }
        }
        armed.clear();
        armed.addAll(now);
    }

    /**
     * The hall's capacity: the whole way on, plus room for one layer's store side in place of its way on, at least one
     * consumer more. Any one junction thrown to its store side never overloads the hall.
     */
    static int capacity(int[] onwardByLayer, int[] storeByLayer) {
        int onward = 0, slack = 1;
        for (int j = 0; j < onwardByLayer.length; j++) {
            onward += onwardByLayer[j];
            slack = Math.max(slack, storeByLayer[j] - onwardByLayer[j]);
        }
        return onward + slack;
    }

    /** A seed read off the site's ground, so the same ground draws the same vault access. */
    static long siteSeed(RuinsDelve delve) {
        RuinsDelve.Vault v = delve.vault();
        long h = 0x5EA1ED5EEDL;
        int[] parts = {v.footprint().left(), v.footprint().bottom(), v.footprint().right(), v.footprint().top(),
                v.innerDoor().x(), v.innerDoor().y(), delve.layerCount(), delve.powerHall().console().x(),
                delve.powerHall().console().y()};
        for (int p : parts) h = VaultAccess.mix(h + p);
        return h;
    }

    /**
     * How many of a layer's side rooms are sealed stores: its first, and on an empty site's last layer its second too,
     * so an empty site always has two stores.
     */
    static int storesIn(int layer, int layerCount, int rooms, RuinsDefence defence) {
        int wanted = defence == RuinsDefence.NONE && layer == layerCount - 1 ? 2 : 1;
        return Math.min(wanted, rooms);
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
    /** The published ground the works were wired on; null for works built without one. */
    public RuinsDelve delve() { return delve; }
    public List<SealDoor> doors() { return doors; }
    public boolean isOpen(int door) { return locks.get(door).isOpen(); }
    public boolean isForced(int door) { return locks.get(door).isForced(); }
    public Site hall() { return hall; }
    public List<Site> junctions() { return junctions; }
    public Site records() { return records; }
    public boolean recordsRead() { return read; }
    public SealDoor collapse() { return collapse; }
    public boolean collapseCleared() { return cleared; }
    /** Whether the player knows what junction {@code j} feeds: thrown once, or the records read. */
    public boolean junctionKnown(int j) { return read || known[j]; }
    /** The vault keypad, or null on a site without the two keys. */
    public Site keypad() { return keypad; }
    /** This site's vault access. */
    public VaultAccess access() { return access; }
    /** Whether the vault's door waits on its two keys: a two-key site with a records console and a keypad. */
    public boolean vaultNeedsKeys() { return access == VaultAccess.TWO_KEYS && keypad != null && records != null; }
    /** Whether the vault's door waits on its code: a records-code site with a records console and a keypad. */
    public boolean vaultNeedsCode() { return access == VaultAccess.RECORDS_CODE && keypad != null && records != null; }
    /** Whether the vault's keys are turned, or it needs none. */
    public boolean vaultKeysTurned() { return !vaultNeedsKeys() || keys.turned(); }
    /** Whether the vault door's access is met beyond its power: keys turned, code entered, or nothing more needed. */
    public boolean vaultAccessMet() { return vaultKeysTurned() && (!vaultNeedsCode() || code.entered()); }

    /**
     * Shuts every seal door as the wall beside it and hardens the vault suite's walls, so no heavy drives through them
     * and no charge opens them. At setup, before the battle's field finds are placed.
     */
    public void install(BattleSimulation sim) {
        if (installed) return;
        installed = true;
        for (Objective objective : sim.getObjectives()) {
            if (objective instanceof RuinsVaultObjective found) vault = found;
        }
        if (vaultShell != null) {
            HardenedWalls.harden(sim.getGrid(), sim.getTopology(), vaultShell[0], vaultShell[1], vaultShell[2],
                    vaultShell[3]);
        }
        for (LockedDoor lock : locks) lock.install(sim);
        courtLifts = new int[courts.size()];
        for (int c = 0; c < courts.size(); c++) {
            courtLifts[c] = -1;
            if (vault == null) continue;
            int[] g = courts.get(c).ground();
            List<RuinsVaultObjective.LiftPoint> lifts = vault.liftPoints();
            for (int i = 0; i < lifts.size(); i++) {
                RuinsVaultObjective.LiftPoint lift = lifts.get(i);
                if (lift.kind() != RuinsVaultObjective.LiftPoint.Kind.COURT) continue;
                int[] r = lift.route();
                int ex = r[r.length - 2], ey = r[r.length - 1];
                if (ex >= g[0] && ex <= g[2] && ey >= g[1] && ey <= g[3]) courtLifts[c] = i;
            }
        }
    }


    /** One tick: the player's press, the work at a console or box, then the doors and the vault's seal. */
    public void tick(BattleSimulation sim) {
        if (!installed || sim.isComplete()) return;
        watchCharges(sim);
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
        boolean vaultPowered = (vaultNeedsKeys() || vaultNeedsCode()) && graph.powered(graph.consumer(VAULT_SEAL));
        if (vaultNeedsKeys()) {
            boolean atRecords = worked(sim, field, records), atKeypad = worked(sim, field, keypad);
            keysHeld = (atRecords ? 1 : 0) + (atKeypad ? 1 : 0);
            keys.tick(vaultPowered, atRecords, atKeypad, BattleSimulation.TICK_DT);
        }
        if (vaultNeedsCode()) code.tick(vaultPowered, read, worked(sim, field, keypad), BattleSimulation.TICK_DT);
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
        if (sim.ruinsPressure() != null) sim.ruinsPressure().loud(RuinsPressure.HALL_RESTART);
    }

    private static float required(String key) {
        return key.equals(HALL) ? RESTART_SECONDS : key.equals(RECORDS) ? READ_SECONDS : THROW_SECONDS;
    }

    /** The door or collapse the player asked a charge on, or null once it stands open. */
    private SealDoor forceTarget(String key) {
        if (COLLAPSE.equals(key)) return cleared ? null : collapse;
        for (int w = 0; w < wakes.size(); w++) {
            if ((WAKE + wakes.get(w).spot().id()).equals(key)) return wakeCollapsed.get(w) ? null : wakes.get(w).spot();
        }
        for (int d = 0; d < doors.size(); d++) {
            if ((DOOR + doors.get(d).id()).equals(key)) {
                return isOpen(d) || doors.get(d).hardened() ? null : doors.get(d);
            }
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


    /**
     * Opens what is powered, shuts what lost power once it is clear, and hands the vault its seal's power. Once the
     * vault is breached every door on the way on stays open, so the haul is never shut in behind a lost feed.
     */
    void settle(BattleSimulation sim) {
        if (collapse != null && !cleared && LockedDoor.breached(sim, collapse.cells())) cleared = true;
        boolean vaultBreached = vault != null && vault.breached();
        for (int d = 0; d < doors.size(); d++) {
            SealDoor door = doors.get(d);
            boolean powered = graph.powered(graph.consumer(door.id()));
            // The vault's door needs its access met as well as its power.
            boolean keyed = !VAULT_SEAL.equals(door.id()) || vaultAccessMet();
            LockedDoor lock = locks.get(d);
            lock.settle(sim, powered && keyed || !door.store() && vaultBreached);
            lock.setReady(powered && !lock.isOpen());
        }
        if (vault != null) {
            vault.setVaultPowered(graph.powered(graph.consumer(VAULT_SEAL)));
            for (int c = 0; c < courts.size(); c++) {
                if (courtLifts[c] >= 0) vault.setLiftPowered(courtLifts[c], courtPowered(c));
            }
        }
    }

    /** Whether court {@code c}'s lift is powered. */
    private boolean courtPowered(int c) { return graph.powered(graph.consumer(courts.get(c).id())); }

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
            if (isOpen(d) || (door.store() && !read)) continue;
            if (door.hardened()) {
                lockedOffer(out, field, DOOR + door.id(), door, graph.powered(graph.consumer(door.id())));
                continue;
            }
            forceOffer(out, field, DOOR + door.id(), door, Strings.get("ruinsWorksDoorSealed"),
                    Strings.get("ruinsWorksForceDetail"));
        }
        if (collapse != null && !cleared) {
            forceOffer(out, field, COLLAPSE, collapse, Strings.get("ruinsWorksCollapseChoked"),
                    Strings.get("ruinsWorksCollapseDetail"));
        }
        for (int w = 0; w < wakes.size(); w++) {
            if (wakeCollapsed.get(w)) continue;
            forceOffer(out, field, WAKE + wakes.get(w).spot().id(), wakes.get(w).spot(), Strings.get("ruinsWakeOpen"),
                    Strings.get("ruinsWakeDetail"));
        }
        // Once the vault's door is powered, the records console is a key: its card says so.
        if (vaultNeedsKeys() && !keys.turned() && graph.powered(graph.consumer(VAULT_SEAL)) && !RECORDS.equals(workKey)
                && near(field, records, PROMPT_CELLS)) {
            out.add(new Offer(RECORDS_KEY, records.x(), records.y(), SideTrigger.Interact.Layout.CARD, records.name(),
                    Strings.format("ruinsWorksKeysHeld", keysHeld),
                    Strings.format("ruinsWorksRecordsKeyDetail", seconds(VaultKeys.TURN_SECONDS)), List.of()));
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

    /** A hardened door's card: no verb, only what opens it and what it still lacks. */
    private void lockedOffer(List<Offer> out, SideField field, String key, SealDoor target, boolean powered) {
        int[] m = target.middle();
        if (field.nearest(SideField.Who.COMBATANT, m[0] + .5f, m[1] + .5f, PROMPT_CELLS) == 0L) return;
        boolean vaultDoor = VAULT_SEAL.equals(target.id());
        String tag = vaultDoor ? vaultStatus(powered) : Strings.get("ruinsWorksDoorLocked");
        String detail = !vaultDoor ? Strings.get("ruinsWorksLockedDetail") : vaultDetail(powered);
        out.add(new Offer(key, m[0], m[1], SideTrigger.Interact.Layout.CARD, target.name(), tag, detail, List.of()));
    }

    /** The vault door's short state while shut: what it lacks. */
    String vaultStatus(boolean powered) {
        if (!powered) return Strings.get("ruinsWorksVaultNoPower");
        if (vaultNeedsKeys()) return Strings.get("ruinsWorksVaultTurnKeys");
        if (vaultNeedsCode()) return Strings.get(read ? "ruinsWorksVaultEnterCode" : "ruinsWorksVaultNeedsCode");
        return Strings.get("ruinsWorksDoorLocked");
    }

    /** The vault door's prompt: what opens it, for this site's access. */
    String vaultDetail(boolean powered) {
        if (vaultNeedsKeys()) {
            return powered ? Strings.format("ruinsWorksVaultNeedsKeys", seconds(VaultKeys.TURN_SECONDS), keysHeld)
                    : Strings.get("ruinsWorksVaultNeedsPower");
        }
        if (vaultNeedsCode()) {
            if (!powered) return Strings.get("ruinsWorksVaultCodeNeedsPower");
            return read ? Strings.format("ruinsWorksVaultCodeEnter", seconds(VaultCode.ENTER_SECONDS))
                    : Strings.get("ruinsWorksVaultCodeUnknown");
        }
        if (access == VaultAccess.CROSSED_FEED) return Strings.get("ruinsWorksVaultCrossedDetail");
        return Strings.get("ruinsWorksLockedDetail");
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
            boolean feedsVault = j == junctions.size() - 1;
            markers.add(new Marker(box.key(), box.name(), box.x(), box.y(), Strings.get(!junctionKnown(j)
                    ? "ruinsWorksFeedingUnknown" : graph.position(j) == Branch.STORE ? "ruinsWorksFeedingStoreShort"
                    : feedsVault && access != VaultAccess.CROSSED_FEED ? "ruinsWorksFeedingVaultShort"
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
            if (door.store() && !read && !isOpen(d)) continue;
            int[] m = door.middle();
            boolean powered = graph.powered(graph.consumer(door.id()));
            String status = isForced(d) ? Strings.get("ruinsWorksDoorForced")
                    : isOpen(d) ? Strings.get("ruinsWorksDoorOpen")
                    : VAULT_SEAL.equals(door.id()) ? vaultStatus(powered)
                    : Strings.get(door.hardened() ? "ruinsWorksDoorLocked" : "ruinsWorksDoorSealed");
            markers.add(new Marker(KEY + door.id(), door.name(), m[0], m[1], status,
                    isOpen(d) ? MissionObjectiveSnapshot.State.COMPLETE : active));
        }
        if (vaultNeedsKeys()) {
            markers.add(new Marker(keypad.key(), keypad.name(), keypad.x(), keypad.y(), keys.turned()
                    ? Strings.get("ruinsWorksKeysTurned") : Strings.format("ruinsWorksKeysHeld", keysHeld),
                    keys.turned() ? MissionObjectiveSnapshot.State.COMPLETE : active));
        }
        if (vaultNeedsCode()) {
            markers.add(new Marker(keypad.key(), keypad.name(), keypad.x(), keypad.y(), Strings.get(code.entered()
                    ? "ruinsWorksCodeEntered" : read ? "ruinsWorksCodeKnown" : "ruinsWorksCodeUnknown"),
                    code.entered() ? MissionObjectiveSnapshot.State.COMPLETE : active));
        }
        for (int w = 0; w < wakes.size(); w++) {
            WakePoint wake = wakes.get(w);
            boolean shut = wakeCollapsed.get(w);
            markers.add(new Marker(WAKE + wake.spot().id(), wake.spot().name(), wake.x(), wake.y(),
                    Strings.get(shut ? "ruinsWakeCollapsed" : "ruinsWakeOpen"),
                    shut ? MissionObjectiveSnapshot.State.COMPLETE : active));
        }
        for (int c = 0; c < courts.size(); c++) {
            int[] g = courts.get(c).ground();
            boolean lit = courtPowered(c);
            markers.add(new Marker(KEY + courts.get(c).id(), courts.get(c).name(), (g[0] + g[2]) / 2,
                    (g[1] + g[3]) / 2, Strings.get(lit ? "ruinsWorksLiftPowered" : "ruinsWorksLiftNoPower"),
                    lit ? MissionObjectiveSnapshot.State.COMPLETE : active));
        }
        return new Item("zz-ruins-works", Strings.get("ruinsWorksName"), running && !overloaded
                ? MissionObjectiveSnapshot.State.COMPLETE : active, phase, "", instruction,
                Strings.format("ruinsWorksLoad", graph.load(), graph.capacity()), Math.max(0f, Math.min(1f, progress)),
                overloaded ? Strings.get("ruinsWorksOverloadBlocker") : "", markers, hall.key(), true);
    }
}
