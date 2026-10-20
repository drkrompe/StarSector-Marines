package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.battery.FireControl;
import com.dillon.starsectormarines.battle.battery.FortressBattery;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One fort in play: the battery platform it was built around and the aspects housed in it. State only;
 * {@link FortSystem} advances it and {@code FortressBatterySystem} reads the answers it gives about the guns.
 *
 * <p>The dependency rules live here, written for sides and never for attacker or defender. The guns belong to whoever
 * holds fire control (the battery's own capture state); the fort asks, of that side, whether its magazine and its
 * power are still its to draw on. An aspect works for its holder, so a side that has taken the bunker but not the
 * power building has guns that traverse slowly, and one that has not taken the magazine fires out a ready-round count
 * and then stops. A wrecked aspect stays wrecked; a taken one can be retaken.
 */
public final class Fort {

    /** Ready rounds the battery can fire without a magazine behind it. UNTUNED first-pass figure. */
    public static final float READY_ROUNDS = 12f;
    /** How fast the guns lock, cycle and traverse when the power is not theirs. UNTUNED first-pass figure. */
    public static final float UNPOWERED_RATE = 0.25f;

    /** One aspect's place: the core structure that can be wrecked and the building interior that can be held. */
    public static final class Place {
        private final FortAspect aspect;
        private final long coreId;
        private final FireControl control;
        private final Faction originalHolder;
        private boolean wrecked;
        private boolean resolved;
        private int minX, minY, maxX, maxY;
        private boolean[] mask = new boolean[0];
        private float centreX, centreY;
        private String structureId;
        private boolean roofOpened;
        private int[] floorBounds;

        Place(FortAspect aspect, long coreId, Faction owner) {
            this.aspect = aspect;
            this.coreId = coreId;
            this.control = new FireControl(owner);
            this.originalHolder = owner;
        }

        public FortAspect aspect() {
            return aspect;
        }

        /** Who held this place when the fort was stamped, so a later holder reads as having taken it. */
        public Faction originalHolder() {
            return originalHolder;
        }

        /** Id of the structure that is this aspect's core, which names how its building is drawn; null when unknown. */
        public String structureId() {
            return structureId;
        }

        /**
         * The world cells of the building's interior floor, as inclusive bounds {minX, minY, maxX, maxY}, from the
         * authored layout and the fort's frame; null while the fort has no frame. This is what the building's art covers.
         */
        public int[] floorBounds(FortFrame frame) {
            if (frame == null) return null;
            if (floorBounds == null) {
                int[] f = frame.plan().floor(aspect);
                floorBounds = frame.bounds(f[0], f[1], f[2], f[3]);
            }
            return floorBounds;
        }

        void structureId(String structureId) {
            this.structureId = structureId;
        }

        /** Whether the building's roof has been opened because the aspect was wrecked; done once. */
        boolean roofOpened() {
            return roofOpened;
        }

        void markRoofOpened() {
            roofOpened = true;
        }

        /** The structure whose destruction wrecks this aspect. */
        public long coreId() {
            return coreId;
        }

        /** The capture state of this place; for fire control the battery's own state is the authority instead. */
        public FireControl control() {
            return control;
        }

        /** Whether the core has been destroyed; a wrecked aspect never comes back. */
        public boolean wrecked() {
            return wrecked;
        }

        void markWrecked() {
            wrecked = true;
        }

        public boolean resolved() {
            return resolved;
        }

        /** Centre of the place's core, in cells. */
        public float x() {
            return centreX;
        }

        public float y() {
            return centreY;
        }

        void resolve(boolean[] mask, int minX, int minY, int maxX, int maxY, float centreX, float centreY) {
            this.mask = mask;
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.centreX = centreX;
            this.centreY = centreY;
            this.resolved = true;
        }

        /**
         * Resolves this place as the union of the floors of its aspect's suite, read from the fort's authored plan and
         * turned with its placement: holding the aspect means holding every room of it. The floors are rectangles of the
         * fort's frame, not rooms recovered from the map, so what is held is exactly what was authored.
         */
        void resolveSuite(FortFrame frame, float centreX, float centreY) {
            List<int[]> rects = new ArrayList<>();
            for (int[] f : frame.plan().suite(aspect)) rects.add(frame.bounds(f[0], f[1], f[2], f[3]));
            int lowX = Integer.MAX_VALUE, lowY = Integer.MAX_VALUE, highX = Integer.MIN_VALUE, highY = Integer.MIN_VALUE;
            for (int[] b : rects) {
                lowX = Math.min(lowX, b[0]);
                lowY = Math.min(lowY, b[1]);
                highX = Math.max(highX, b[2]);
                highY = Math.max(highY, b[3]);
            }
            int width = highX - lowX + 1;
            boolean[] floors = new boolean[width * (highY - lowY + 1)];
            for (int[] b : rects) {
                for (int y = b[1]; y <= b[3]; y++) {
                    Arrays.fill(floors, (y - lowY) * width + (b[0] - lowX), (y - lowY) * width + (b[2] - lowX) + 1, true);
                }
            }
            resolve(floors, lowX, lowY, highX, highY, centreX, centreY);
        }

        /** Whether the cell is part of the building interior where this aspect is held. */
        public boolean contains(int x, int y) {
            if (!resolved || x < minX || x > maxX || y < minY || y > maxY) return false;
            return mask[(y - minY) * (maxX - minX + 1) + (x - minX)];
        }

        /** Number of interior cells, for evidence. */
        public int interiorCells() {
            int n = 0;
            for (boolean b : mask) if (b) n++;
            return n;
        }
    }

    private final int id;
    private FortKind kind;
    private final Map<FortAspect, Place> places = new EnumMap<>(FortAspect.class);
    private final List<Long> nests = new ArrayList<>();
    private final List<Long> airMounts = new ArrayList<>();
    private FortressBattery battery;
    private FortFrame frame;
    private int[] exteriorCells;
    private int originX, originY, quarterTurns = FortTag.UNPLACED;
    private float readyRounds = READY_ROUNDS;
    private boolean magazineBlown;

    Fort(int id, FortKind kind) {
        this.id = id;
        this.kind = kind;
    }

    /** A fort learns its kind from the centrepiece that is tagged to it: a platform or an emitter. */
    void become(FortKind kind) {
        this.kind = kind;
        this.frame = null;
        this.exteriorCells = null;
    }

    public int id() {
        return id;
    }

    public FortKind kind() {
        return kind;
    }

    /** The platform this fort is built around, or {@code null} until it has spawned. */
    public FortressBattery battery() {
        return battery;
    }

    void attach(FortressBattery battery) {
        this.battery = battery;
        battery.attachFort(this);
    }

    /** Records where the fort stands, once; later tags of the same fort say the same thing. */
    void place(int originX, int originY, int quarterTurns) {
        if (this.quarterTurns == FortTag.UNPLACED) {
            this.originX = originX;
            this.originY = originY;
            this.quarterTurns = quarterTurns;
        }
    }

    /**
     * The works of this fort that can be seen from outside, as packed {@code x, y} world cells (see
     * {@link FortFrame#exteriorCells}); empty for a fort with no placement. Worked out once.
     */
    public int[] exteriorCells() {
        if (exteriorCells != null) return exteriorCells;
        FortFrame placed = frame();
        if (placed == null) return new int[0];
        exteriorCells = placed.exteriorCells();
        return exteriorCells;
    }

    /** Where the fort's local frame stands on the map, or null when the fort was assembled without a placement. */
    public FortFrame frame() {
        if (frame == null && quarterTurns != FortTag.UNPLACED) {
            frame = new FortFrame(FortPlan.of(kind), originX, originY, quarterTurns);
        }
        return frame;
    }

    void addPlace(Place place) {
        places.put(place.aspect, place);
    }

    /** The entity ids of the anti-air mounts of this fort's yard (empty without the aspect). */
    public List<Long> airMountIds() {
        return Collections.unmodifiableList(airMounts);
    }

    void addAirMount(long id) {
        airMounts.add(id);
    }

    /** Whether the anti-air aspect is wrecked: its mounts are silent for good. */
    public boolean airDefenceWrecked() {
        Place p = places.get(FortAspect.AIR_DEFENCE);
        return p != null && p.wrecked;
    }

    /**
     * How fast {@code owner}'s anti-air mounts in this fort lock, traverse and cycle: zero while the yard is wrecked,
     * contested or held by another side, a quarter without the fort's power, full otherwise. A fort with no anti-air
     * aspect imposes nothing (1).
     */
    public float airDefenceRate(Faction owner) {
        Place p = places.get(FortAspect.AIR_DEFENCE);
        if (p == null) return 1f;
        if (p.wrecked || p.control.contested() || !owner.friendlyTo(p.control.holder())) return 0f;
        return powered(owner) ? 1f : UNPOWERED_RATE;
    }

    private int sensorReadyTick;

    /** The tick before which the fort's net cannot place another call (a call holds it for a cooldown). */
    public int sensorReadyTick() {
        return sensorReadyTick;
    }

    /** Holds the fort's net until {@code tick}. */
    public void holdSensorNetUntil(int tick) {
        sensorReadyTick = tick;
    }

    /** The entity ids of the wall nests tagged to this fort, for evidence; a command never reads these. */
    public List<Long> nestIds() {
        return List.copyOf(nests);
    }

    void addNest(long id) {
        nests.add(id);
    }

    public Place place(FortAspect aspect) {
        return places.get(aspect);
    }

    public Iterable<Place> places() {
        return places.values();
    }

    /** Ready rounds left in the pool the guns draw on when no magazine is feeding them. */
    public float readyRounds() {
        return readyRounds;
    }

    void refill() {
        readyRounds = READY_ROUNDS;
    }

    boolean magazineBlown() {
        return magazineBlown;
    }

    void markMagazineBlown() {
        magazineBlown = true;
    }

    /**
     * The side that holds the fort whole: every standing aspect held by it and none contested. {@code null} while the
     * fort is split between sides, contested, or wrecked to the last aspect. Fire control is read from the battery's own
     * capture state, which is its authority; the emitter has no holder of its own and is left out.
     */
    public Faction wholeHolder() {
        Faction holder = null;
        for (Place place : places.values()) {
            if (place.wrecked || place.aspect == FortAspect.EMITTER) continue;
            FireControl control = place.aspect == FortAspect.FIRE_CONTROL && battery != null
                    ? battery.fireControl() : place.control;
            if (control.contested()) return null;
            if (holder == null) holder = control.holder();
            else if (holder != control.holder()) return null;
        }
        return holder;
    }

    // -------------------------------------------------------------------------------------------- sally ports

    private boolean[] sallyPortsOpen = new boolean[0];

    /** Whether sally port {@code index} of the fort's plan has been unbarred. A port, once open, stays open. */
    public boolean sallyPortOpen(int index) {
        return index >= 0 && index < sallyPortsOpen.length && sallyPortsOpen[index];
    }

    /** Records that sally port {@code index} has been unbarred (by {@code SallyDoors}). */
    public void markSallyPortOpen(int index) {
        if (index >= sallyPortsOpen.length) sallyPortsOpen = Arrays.copyOf(sallyPortsOpen, index + 1);
        sallyPortsOpen[index] = true;
    }

    // -------------------------------------------------------------------------------------------- shield fort

    private Faction domeHolderNow;

    /** Records the side the dome protects as of the last tick, for the map to show. */
    void noteDomeHolder(Faction holder) {
        this.domeHolderNow = holder;
    }

    /** The side the dome protected as of the last tick, or {@code null} before the first. */
    public Faction domeHolderNow() {
        return domeHolderNow;
    }

    /** The shield generator's entity id, or {@code 0} for a fort that has none. */
    public long emitterId() {
        Place p = places.get(FortAspect.EMITTER);
        return p == null ? 0L : p.coreId();
    }

    /**
     * The side the dome protects: whoever holds the control room. A fort with no control room, or one wrecked, keeps
     * the side that held it last ({@code current}), because a wrecked room hands the dome to nobody.
     */
    public Faction domeHolder(Faction current) {
        Place p = places.get(FortAspect.CONTROL_ROOM);
        if (p == null || p.wrecked) return current;
        return p.control.holder();
    }

    /** Whether the dome is up for the side that holds it: the emitter stands and the power is that side's to draw on. */
    public boolean domePowered(Faction holder) {
        Place emitter = places.get(FortAspect.EMITTER);
        if (emitter != null && emitter.wrecked) return false;
        return powered(holder);
    }

    /** False once the fire-control bunker core is wrecked; the guns then have nothing to obey. */
    public boolean fireControlIntact() {
        Place p = places.get(FortAspect.FIRE_CONTROL);
        return p == null || !p.wrecked;
    }

    /** Whether {@code aspect} can still supply {@code owner}: standing, uncontested and held by that side. */
    private boolean supplies(FortAspect aspect, Faction owner) {
        Place p = places.get(aspect);
        if (p == null) return true;
        return !p.wrecked && !p.control.contested() && owner.friendlyTo(p.control.holder());
    }

    /** Whether the magazine is feeding {@code owner}'s guns. A fort with no magazine is not rationed. */
    public boolean magazineSupplies(Faction owner) {
        return supplies(FortAspect.MAGAZINE, owner);
    }

    /** Whether the power building is feeding {@code owner}'s guns. A fort with no power building is always powered. */
    public boolean powered(Faction owner) {
        return supplies(FortAspect.POWER, owner);
    }

    /** The rate guns lock, cycle and traverse at for {@code owner}: full when powered, slowed when not. */
    public float gunRate(Faction owner) {
        return powered(owner) ? 1f : UNPOWERED_RATE;
    }

    /** Whether {@code owner}'s guns have a round to fire: fed by the magazine or from the ready-round pool. */
    public boolean hasRounds(Faction owner) {
        return magazineSupplies(owner) || readyRounds >= 1f;
    }

    /** Spends a round for one shot; free while the magazine feeds the guns. False when there is none to spend. */
    public boolean takeRound(Faction owner) {
        if (magazineSupplies(owner)) return true;
        if (readyRounds < 1f) return false;
        readyRounds -= 1f;
        return true;
    }
}
