package com.dillon.starsectormarines.battle.control;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.tender.BuildOption;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.tender.TenderPilot;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.LowHoverClearance;
import com.dillon.starsectormarines.battle.warship.WarshipPosture;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

/** Battle-owned remote-order mailbox. Only the serial tick mutates ships or construction. */
public final class ShipCommandService {
    public enum Notice { NONE, MOVING, HOLDING, AUTONOMOUS, LAID, CANCELLED, DEFENCE,
        UNAVAILABLE, GROUNDED, PILOTED, OFF_MAP, UNSEEN, BLOCKED, FULL, NO_CHARGES }
    private enum Kind { MOVE, HOLD, AUTO, BUILD, CANCEL, DEFENCE }
    private record Request(Kind kind, Warship ship, Tender tender, BuildOption option, float x, float y,
                           long helmGeneration) {
        Request(Kind kind, Warship ship, Tender tender, BuildOption option, float x, float y) {
            this(kind, ship, tender, option, x, y, ship == null ? -1 : ship.helmGeneration());
        }
    }
    private final List<Request> pending = new ArrayList<>();
    private final Map<Warship, Notice> notices = new ConcurrentHashMap<>();

    public synchronized void requestMove(Warship ship, float x, float y) { pending.add(new Request(Kind.MOVE, ship, null, null, x, y)); }
    public synchronized void requestHold(Warship ship) { pending.add(new Request(Kind.HOLD, ship, null, null, 0, 0)); }
    public synchronized void requestAutonomous(Warship ship) { pending.add(new Request(Kind.AUTO, ship, null, null, 0, 0)); }
    public synchronized void requestBuild(Tender tender, BuildOption option, float x, float y) {
        pending.add(new Request(Kind.BUILD, tender == null ? null : tender.ship(), tender, option, x, y));
    }
    public synchronized void requestCancelBuilds(Tender tender) { pending.add(new Request(Kind.CANCEL, tender == null ? null : tender.ship(), tender, null, 0, 0)); }
    public synchronized void requestDefence(Tender tender) { pending.add(new Request(Kind.DEFENCE, tender == null ? null : tender.ship(), tender, null, 0, 0)); }
    public Notice notice(Warship ship) { return ship == null ? Notice.NONE : notices.getOrDefault(ship, Notice.NONE); }

    /** Eligibility is deliberately exact-side; allied mission assets do not become player assets. */
    public static Notice eligibility(Warship ship, Tender tender, Faction player) {
        if (ship == null || ship.faction != player || ship.destroyed() || ship.departing() || ship.departed()
                || ship.posture() == WarshipPosture.WITHDRAW || ship.stationSecondsRemaining() <= 0) return Notice.UNAVAILABLE;
        if (tender != null && tender.grounded()) return Notice.GROUNDED;
        if (ship.piloted()) return Notice.PILOTED;
        if (ship.lowPassing()) return Notice.UNAVAILABLE;
        return Notice.NONE;
    }

    /** Flight destinations need observed, bounded ground; terrain does not obstruct hovering. */
    public static Notice placement(Warship ship, Tender tender, Faction player, float x, float y,
                                   NavigationGrid grid, BiPredicate<Integer, Integer> revealed) {
        Notice eligible = eligibility(ship, tender, player);
        if (eligible != Notice.NONE) return eligible;
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        if (!Float.isFinite(x) || !Float.isFinite(y) || !grid.inBounds(cx, cy)) return Notice.OFF_MAP;
        if (ship.lowHovering() && !LowHoverClearance.sweptClear(grid, ship.x, ship.y, x, y,
                ship.lowHoverWidth(), ship.lowHoverHeight())) return Notice.BLOCKED;
        return revealed.test(cx, cy) ? Notice.NONE : Notice.UNSEEN;
    }

    public void drain(List<Warship> ships, List<Tender> tenders, Faction player, NavigationGrid grid,
                      BiPredicate<Integer, Integer> revealed) {
        List<Request> requests;
        synchronized (this) { requests = new ArrayList<>(pending); pending.clear(); }
        notices.keySet().removeIf(ship -> !member(ship, ships, tenders));
        for (Request request : requests) {
            Warship ship = request.ship();
            if (ship == null) continue;
            Tender tender = null;
            for (Tender candidate : tenders) if (candidate.ship() == ship) { tender = candidate; break; }
            Notice result = member(ship, ships, tenders) ? eligibility(ship, tender, player) : Notice.UNAVAILABLE;
            if (request.helmGeneration() != ship.helmGeneration()) result = Notice.PILOTED;
            if (request.tender() != null && request.tender() != tender) result = Notice.UNAVAILABLE;
            if (result == Notice.NONE) result = apply(request, tender, player, grid, revealed);
            notices.put(ship, result);
        }
    }

    private static boolean member(Warship ship, List<Warship> ships, List<Tender> tenders) {
        if (ships.contains(ship)) return true;
        for (Tender tender : tenders) {
            if (tender.ship() == ship && tender.grounded() && tender.anchorAlive()) return true;
        }
        return false;
    }

    private Notice apply(Request request, Tender tender, Faction player, NavigationGrid grid,
                         BiPredicate<Integer, Integer> revealed) {
        Warship ship = request.ship();
        switch (request.kind()) {
            case MOVE -> {
                Notice destination = placement(ship, tender, player, request.x(), request.y(), grid, revealed);
                if (destination != Notice.NONE) return destination;
                if (tender != null) tender.cancelAll();
                ship.remoteOrder(Warship.RemoteOrder.MOVE, request.x(), request.y());
                return Notice.MOVING;
            }
            case HOLD -> {
                if (tender != null) tender.cancelAll();
                ship.remoteOrder(Warship.RemoteOrder.HOLD, ship.x, ship.y);
                return Notice.HOLDING;
            }
            case AUTO -> { ship.remoteAutonomous(); return Notice.AUTONOMOUS; }
            case CANCEL -> { if (tender == null) return Notice.UNAVAILABLE; tender.cancelAll(); return Notice.CANCELLED; }
            case DEFENCE -> { if (tender == null) return Notice.UNAVAILABLE; tender.defenceOut(!tender.defenceOut()); return Notice.DEFENCE; }
            case BUILD -> {
                if (tender == null || request.option() == null || !BuildOption.ALL.contains(request.option())) return Notice.UNAVAILABLE;
                TenderPilot.Placement where = TenderPilot.placement(tender, request.x(), request.y(), grid, revealed);
                if (where != TenderPilot.Placement.OPEN) return switch (where) {
                    case UNSEEN -> Notice.UNSEEN;
                    case OFF_MAP -> Notice.OFF_MAP;
                    default -> Notice.BLOCKED;
                };
                if (TenderPilot.liveSites(tender) >= TenderPilot.MAX_SITES) return Notice.FULL;
                if (tender.metered() && request.option().cost() > tender.stock()) return Notice.NO_CHARGES;
                if (tender.lay(request.option(), request.x(), request.y(), grid) == null) return Notice.BLOCKED;
                ship.remoteOrder(Warship.RemoteOrder.BUILD, ship.x, ship.y);
                return Notice.LAID;
            }
        }
        return Notice.UNAVAILABLE;
    }
}
