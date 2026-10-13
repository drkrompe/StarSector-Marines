package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.battery.FireControl;
import com.dillon.starsectormarines.battle.battery.FortressBattery;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
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
        private boolean wrecked;
        private boolean resolved;
        private int minX, minY, maxX, maxY;
        private boolean[] mask = new boolean[0];
        private float centreX, centreY;

        Place(FortAspect aspect, long coreId, Faction owner) {
            this.aspect = aspect;
            this.coreId = coreId;
            this.control = new FireControl(owner);
        }

        public FortAspect aspect() {
            return aspect;
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
    private FortressBattery battery;
    private float readyRounds = READY_ROUNDS;
    private boolean magazineBlown;

    Fort(int id, FortKind kind) {
        this.id = id;
        this.kind = kind;
    }

    /** A fort learns its kind from the centrepiece that is tagged to it: a platform or an emitter. */
    void become(FortKind kind) {
        this.kind = kind;
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

    void addPlace(Place place) {
        places.put(place.aspect, place);
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
