package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipOrder;
import com.dillon.starsectormarines.battle.warship.WarshipPilot;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Carrier;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Durability;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.FluxStatus;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.ShipCard;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Snapshot;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Tone;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.WarshipStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only values for a piloted warship: its weapon groups, flux, shield, hull and the
 * ship's own automation. Nothing here owns a figure; every number is read off the hull.
 * The armour line is the hull's rating shown whole: armour is a rating in this model and
 * is never spent down, so a fraction of it would be a made-up number.
 */
final class BattleDirectWarshipStatus {
    private static final float SOFT_FLUX = 0.85f;

    private BattleDirectWarshipStatus() { }

    static Snapshot snapshot(BattleSimulation sim, Warship ship) {
        if (ship == null || ship.destroyed()) return Snapshot.EMPTY;
        boolean gunsExposed = WarshipPilot.endangersFriendlies(sim, ship,
                ship.laidArea(Warship.FireGroup.GUNS));
        boolean missilesExposed = WarshipPilot.endangersFriendlies(sim, ship,
                ship.laidArea(Warship.FireGroup.MISSILES));
        return snapshot(ship, gunsExposed, missilesExposed);
    }

    static Snapshot snapshot(Warship ship, boolean gunsExposed, boolean missilesExposed) {
        Durability durability = new Durability(ship.hitPoints(), ship.maxHitPoints,
                ship.armour, ship.armour, ship.armour);
        return new Snapshot(0L, ship.hullId, Carrier.WARSHIP, durability, List.of(), List.of(),
                null, null, status(ship, gunsExposed, missilesExposed));
    }

    /** The cards and flux gauge for a hull; {@code exposed} says a laid area would put a round near a friendly. */
    static WarshipStatus status(Warship ship, boolean gunsExposed, boolean missilesExposed) {
        List<ShipCard> cards = new ArrayList<>();
        Battery guns = new Battery();
        Battery lances = new Battery();
        Battery missiles = new Battery();
        int pointDefence = 0;
        int intercepts = 0;
        for (Warship.Mount m : ship.mounts()) {
            if (m.pointDefence()) {
                pointDefence++;
                intercepts += m.intercepts;
            } else if (m.lance()) {
                lances.add(m);
            } else if (m.weapon.beam()) {
                // A beam too weak to cut ground is silent against it and not a battery here.
            } else if (m.group() == Warship.FireGroup.MISSILES) {
                missiles.add(m);
            } else {
                guns.add(m);
            }
        }
        int laidCount = (ship.laidArea(Warship.FireGroup.GUNS) != null ? 1 : 0)
                + (ship.laidArea(Warship.FireGroup.MISSILES) != null ? 1 : 0);
        int held = ship.areas().size() - laidCount;
        if (guns.total > 0) {
            cards.add(batteryCard("GUNS", "[LMB] GUNS", ship, guns, Warship.FireGroup.GUNS, held, gunsExposed));
        }
        if (lances.total > 0) {
            cards.add(lanceCard(ship, lances));
        }
        if (missiles.total > 0) {
            cards.add(batteryCard("MISSILES", "[RMB] MISSILES", ship, missiles,
                    Warship.FireGroup.MISSILES, held, missilesExposed));
        }
        if (pointDefence > 0) {
            cards.add(new ShipCard("PD", "POINT DEF", "intercepts " + intercepts,
                    pointDefence + " mounts", "AUTO", Tone.READY, 1f));
        }
        if (!ship.shieldType().equals("none")) {
            cards.add(shieldCard(ship));
        }
        cards.add(automationCard(ship));
        return new WarshipStatus(cards, flux(ship));
    }

    private static final class Battery {
        int total;
        int ready;
        int bursting;
        int sweeping;
        int ammo;
        boolean limitedAmmo;

        void add(Warship.Mount m) {
            total++;
            if (m.cooldownSeconds() <= 0f && m.burstRemaining() == 0 && !m.sweeping()) ready++;
            if (m.burstRemaining() > 0) bursting++;
            if (m.sweeping()) sweeping++;
            if (m.ammoRemaining() >= 0) {
                limitedAmmo = true;
                ammo += m.ammoRemaining();
            }
        }
    }

    private static ShipCard batteryCard(String key, String name, Warship ship, Battery b,
                                        Warship.FireGroup group, int held, boolean exposed) {
        WarshipOrder.FireOnArea laid = ship.laidArea(group);
        String detail = laid != null ? area(laid) : held > 0 ? "AUTO " + held : "NO AREA";
        String resource = b.limitedAmmo ? b.ammo + " rd" : b.ready + "/" + b.total;
        String state;
        Tone tone;
        if (ship.groundFire() == Warship.GroundFire.HOLD) {
            state = "HOLD";
            tone = Tone.WARN;
        } else if (ship.overloaded()) {
            state = "OVERLOAD";
            tone = Tone.WARN;
        } else if (b.limitedAmmo && b.ammo <= 0) {
            state = "EMPTY";
            tone = Tone.WARN;
        } else if (laid != null && exposed) {
            state = "DANGER";
            tone = Tone.WARN;
        } else if (b.bursting > 0) {
            state = "FIRING";
            tone = Tone.BUSY;
        } else if (ship.groundFire() == Warship.GroundFire.HEAVY && group == Warship.FireGroup.GUNS) {
            state = "HEAVY";
            tone = Tone.BUSY;
        } else if (laid != null) {
            state = "LAID";
            tone = Tone.READY;
        } else if (held > 0) {
            state = "AUTO";
            tone = Tone.READY;
        } else {
            state = "IDLE";
            tone = Tone.BUSY;
        }
        return new ShipCard(key, name, detail, resource, state, tone, fraction(b.ready, b.total));
    }

    private static ShipCard lanceCard(Warship ship, Battery b) {
        String state;
        Tone tone;
        if (ship.groundFire() == Warship.GroundFire.HOLD) {
            state = "HOLD";
            tone = Tone.WARN;
        } else if (ship.overloaded()) {
            state = "OVERLOAD";
            tone = Tone.WARN;
        } else if (b.sweeping > 0) {
            state = "BURNING";
            tone = Tone.BUSY;
        } else if (b.ready > 0) {
            state = "READY";
            tone = Tone.READY;
        } else {
            state = "RESTING";
            tone = Tone.BUSY;
        }
        return new ShipCard("LANCES", "LANCES", "with LMB",
                b.limitedAmmo ? b.ammo + " rd" : b.ready + "/" + b.total, state, tone, fraction(b.ready, b.total));
    }

    private static ShipCard shieldCard(Warship ship) {
        String state = ship.shieldState().toUpperCase(Locale.ROOT);
        Tone tone = switch (ship.shieldState()) {
            case "up" -> Tone.READY;
            case "off" -> Tone.WARN;
            default -> Tone.BUSY;
        };
        return new ShipCard("SHIELD", "[E] SHIELD", ship.shieldType().toUpperCase(Locale.ROOT)
                + (ship.shieldAuto() ? " AUTO" : " HELD"), Math.round(ship.shieldLevel() * 100f) + "%",
                state, tone, ship.shieldLevel());
    }

    private static ShipCard automationCard(Warship ship) {
        boolean on = ship.isDirected();
        String detail = ship.hasBattery(Warship.FireGroup.MISSILES)
                ? "[3] MSL " + ship.missilePreset().name() : "[1] HOLD FIRE";
        String policy = switch (ship.groundFire()) {
            case ALL -> "ALL";
            case HEAVY -> "HEAVY";
            case HOLD -> "NONE";
        };
        return new ShipCard("DIRECTOR", "[2] DIRECTOR", detail, "[4] " + policy, on ? "ON" : "OFF",
                on ? Tone.READY : Tone.BUSY, on ? 1f : 0f);
    }

    private static FluxStatus flux(Warship ship) {
        if (!ship.fluxLimited()) return new FluxStatus(false, 0f, "FLUX OFF", "UNLIMITED", Tone.READY);
        float fraction = Math.max(0f, Math.min(1f, ship.fluxFraction()));
        String state = ship.fluxState().toUpperCase(Locale.ROOT);
        if (ship.overloaded()) state += String.format(Locale.ROOT, " %.1fs", ship.overloadSecondsRemaining());
        Tone tone = ship.overloaded() ? Tone.WARN : fraction >= SOFT_FLUX ? Tone.WARN : Tone.READY;
        return new FluxStatus(true, fraction, "FLUX " + Math.round(fraction * 100f) + "%", state, tone);
    }

    private static String area(WarshipOrder.FireOnArea area) {
        return "AREA " + Math.round(area.x()) + "," + Math.round(area.y());
    }

    private static float fraction(int part, int whole) {
        return whole <= 0 ? 0f : Math.max(0f, Math.min(1f, (float) part / whole));
    }
}
