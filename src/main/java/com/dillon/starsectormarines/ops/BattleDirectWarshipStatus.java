package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.warship.LowPass;
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
        cards.add(helmCard(ship));
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
            cards.add(batteryCard("GUNS", Strings.get("battleWsGuns"), ship, guns, Warship.FireGroup.GUNS, held, gunsExposed));
        }
        if (lances.total > 0) {
            cards.add(lanceCard(ship, lances));
        }
        if (missiles.total > 0) {
            cards.add(batteryCard("MISSILES", Strings.get("battleWsMissiles"), ship, missiles,
                    Warship.FireGroup.MISSILES, held, missilesExposed));
        }
        if (pointDefence > 0) {
            cards.add(new ShipCard("PD", Strings.get("battleWsPd"), Strings.format("battleWsPdIntercepts", intercepts),
                    Strings.format("battleWsPdMounts", pointDefence), Strings.get("battleWsAuto"), Tone.READY, 1f));
        }
        if (!ship.shieldType().equals("none")) {
            cards.add(shieldCard(ship));
        }
        cards.add(passCard(ship));
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
        String hold = group == Warship.FireGroup.GUNS ? Strings.get("battleWsHoldLmb") : Strings.get("battleWsHoldRmb");
        String detail = laid != null ? area(laid) : held > 0 ? Strings.format("battleWsAutoCount", held) : hold;
        String resource = b.limitedAmmo ? Strings.format("battleWsRounds", b.ammo) : Strings.format("battleWsReadyOfTotal", b.ready, b.total);
        String state;
        Tone tone;
        if (ship.groundFire() == Warship.GroundFire.HOLD) {
            state = Strings.get("battleWsHold");
            tone = Tone.WARN;
        } else if (ship.overloaded()) {
            state = Strings.get("battleWsOverload");
            tone = Tone.WARN;
        } else if (b.limitedAmmo && b.ammo <= 0) {
            state = Strings.get("battleWsEmpty");
            tone = Tone.WARN;
        } else if (laid != null && exposed) {
            state = Strings.get("battleWsDanger");
            tone = Tone.WARN;
        } else if (b.bursting > 0) {
            state = Strings.get("battleWsFiring");
            tone = Tone.BUSY;
        } else if (ship.groundFire() == Warship.GroundFire.HEAVY && group == Warship.FireGroup.GUNS) {
            state = Strings.get("battleWsHeavy");
            tone = Tone.BUSY;
        } else if (laid != null) {
            state = Strings.get("battleWsLaid");
            tone = Tone.READY;
        } else if (held > 0) {
            state = Strings.get("battleWsAuto");
            tone = Tone.READY;
        } else {
            state = Strings.get("battleWsIdle");
            tone = Tone.BUSY;
        }
        return new ShipCard(key, name, detail, resource, state, tone, fraction(b.ready, b.total));
    }

    private static ShipCard lanceCard(Warship ship, Battery b) {
        String state;
        Tone tone;
        if (ship.groundFire() == Warship.GroundFire.HOLD) {
            state = Strings.get("battleWsHold");
            tone = Tone.WARN;
        } else if (ship.overloaded()) {
            state = Strings.get("battleWsOverload");
            tone = Tone.WARN;
        } else if (b.sweeping > 0) {
            state = Strings.get("battleWsBurning");
            tone = Tone.BUSY;
        } else if (b.ready > 0) {
            state = Strings.get("battleWsReady");
            tone = Tone.READY;
        } else {
            state = Strings.get("battleWsResting");
            tone = Tone.BUSY;
        }
        return new ShipCard("LANCES", Strings.get("battleWsLances"), Strings.get("battleWsLancesHint"),
                b.limitedAmmo ? Strings.format("battleWsRounds", b.ammo) : Strings.format("battleWsReadyOfTotal", b.ready, b.total), state, tone, fraction(b.ready, b.total));
    }

    /** What the pilot flies with, and how to get out, so the strip says how to start. */
    static ShipCard helmCard(Warship ship) {
        float speed = ship.speed();
        return new ShipCard("HELM", Strings.get("battleWsHelmFly"), Strings.get("battleWsHelmLeave"), "",
                speed > 0.05f ? Strings.get("battleWsUnderway") : Strings.get("battleWsHover"), speed > 0.05f ? Tone.BUSY : Tone.READY, 1f);
    }

    static ShipCard shieldCard(Warship ship) {
        String state = Strings.get("battleWsShield" + ship.shieldState());
        Tone tone = switch (ship.shieldState()) {
            case "up" -> Tone.READY;
            case "off" -> Tone.WARN;
            default -> Tone.BUSY;
        };
        return new ShipCard("SHIELD", Strings.get("battleWsShieldKey"), Strings.format(ship.shieldAuto() ? "battleWsShieldAuto" : "battleWsShieldHeld",
                Strings.get("battleWsShieldType" + ship.shieldType())), Math.round(ship.shieldLevel() * 100f) + "%",
                state, tone, ship.shieldLevel());
    }

    /** The low pass: the key, what it would cost in station time at the least, and whether one is flying. */
    static ShipCard passCard(Warship ship) {
        LowPass pass = ship.lowPass();
        float left = ship.stationSecondsRemaining();
        String time = left == Float.POSITIVE_INFINITY ? "" : Strings.format("battleWsStationSeconds", Math.round(left));
        if (ship.lowPassing()) {
            boolean onLine = pass.onLine();
            return new ShipCard("PASS", Strings.get("battleWsPassKey"), Strings.get(onLine ? "battleWsPassOnLine" : "battleWsPassRunningIn"), time,
                    Strings.get(onLine ? "battleWsPassing" : "battleWsRunIn"), Tone.BUSY,
                    Math.max(0f, Math.min(1f, pass.progress() / Math.max(1f, pass.length()))));
        }
        float least = LowPass.stationCost(LowPass.secondsToCover(LowPass.MIN_LENGTH_CELLS,
                ship.maxSpeed, ship.acceleration));
        boolean affordable = left == Float.POSITIVE_INFINITY || left >= least;
        return new ShipCard("PASS", Strings.get("battleWsPassKey"), Strings.get("battleWsPassToCursor"), time,
                Strings.get(affordable ? "battleWsReady" : "battleWsNoTime"), affordable ? Tone.READY : Tone.WARN, affordable ? 1f : 0f);
    }

    private static ShipCard automationCard(Warship ship) {
        boolean on = ship.isDirected();
        String detail = ship.hasBattery(Warship.FireGroup.MISSILES)
                ? Strings.format("battleWsDirectorMissiles", ship.missilePreset().name()) : Strings.get("battleWsDirectorHold");
        String policy = switch (ship.groundFire()) {
            case ALL -> Strings.get("battleWsPolicyAll");
            case HEAVY -> Strings.get("battleWsPolicyHeavy");
            case HOLD -> Strings.get("battleWsPolicyNone");
        };
        return new ShipCard("DIRECTOR", Strings.get("battleWsDirectorKey"), detail, Strings.format("battleWsPolicy", policy), Strings.get(on ? "battleWsOn" : "battleWsOff"),
                on ? Tone.READY : Tone.BUSY, on ? 1f : 0f);
    }

    static FluxStatus flux(Warship ship) {
        if (!ship.fluxLimited()) return new FluxStatus(false, 0f, Strings.get("battleWsFluxOff"), Strings.get("battleWsUnlimited"), Tone.READY);
        float fraction = Math.max(0f, Math.min(1f, ship.fluxFraction()));
        String state = Strings.get("battleWsFlux" + ship.fluxState());
        if (ship.overloaded()) state = Strings.format("battleWsFluxOverload", state, String.format(Locale.ROOT, "%.1f", ship.overloadSecondsRemaining()));
        Tone tone = ship.overloaded() ? Tone.WARN : fraction >= SOFT_FLUX ? Tone.WARN : Tone.READY;
        return new FluxStatus(true, fraction, Strings.format("battleWsFluxPercent", Math.round(fraction * 100f)), state, tone);
    }

    private static String area(WarshipOrder.FireOnArea area) {
        return Strings.format("battleWsArea", Math.round(area.x()), Math.round(area.y()));
    }

    private static float fraction(int part, int whole) {
        return whole <= 0 ? 0f : Math.max(0f, Math.min(1f, (float) part / whole));
    }
}
