package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.control.DirectControlSession;
import com.dillon.starsectormarines.battle.tender.BuildOption;
import com.dillon.starsectormarines.battle.tender.ConstructionOrder;
import com.dillon.starsectormarines.battle.tender.Tender;
import com.dillon.starsectormarines.battle.tender.TenderPilot;
import com.dillon.starsectormarines.battle.tender.TenderSystem;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Carrier;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Durability;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.ShipCard;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Snapshot;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.Tone;
import com.dillon.starsectormarines.ops.BattleDirectControlStatus.WarshipStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only values for a piloted drone tender: helm, what is selected to build, the site being
 * worked, both halves of the complement, the shield, and the hull's flux. Every number is read
 * off the tender; the strip owns none of them.
 */
final class BattleDirectTenderStatus {

    private BattleDirectTenderStatus() { }

    static Snapshot snapshot(DirectControlSession session) {
        Tender tender = session.controlledTender();
        if (tender == null || tender.ship().destroyed()) return Snapshot.EMPTY;
        Warship ship = tender.ship();
        Durability durability = new Durability(ship.hitPoints(), ship.maxHitPoints,
                ship.armour, ship.armour, ship.armour);
        return new Snapshot(0L, ship.hullId, Carrier.TENDER, durability, List.of(), List.of(), null, null,
                status(tender, session.selectedWeapon(), session.tenderPad().notice()));
    }

    /**
     * The equipment strip for {@code tender}.
     *
     * @param slot   the number-key slot selected, 0 for the key 1
     * @param notice how the last attempt to lay a site went, if recent
     */
    static WarshipStatus status(Tender tender, int slot, TenderPilot.Notice notice) {
        Warship ship = tender.ship();
        List<ShipCard> cards = new ArrayList<>();
        cards.add(BattleDirectWarshipStatus.helmCard(ship));
        cards.add(optionCard(slot));
        cards.add(siteCard(tender, slot, notice));
        cards.add(buildersCard(tender));
        cards.add(defenceCard(tender));
        if (!ship.shieldType().equals("none")) cards.add(BattleDirectWarshipStatus.shieldCard(ship));
        return new WarshipStatus(cards, BattleDirectWarshipStatus.flux(ship));
    }

    /** What the number keys offer: the selected option, and the slots no option fills yet. */
    static ShipCard optionCard(int slot) {
        BuildOption chosen = TenderPilot.option(slot);
        String detail = chosen == null ? Strings.format("tenderStatusOptionEmpty", slot + 1)
                : Strings.format("tenderStatusOptionChosen", slot + 1, Strings.upper(Strings.resolve(chosen.label())));
        StringBuilder rest = new StringBuilder();
        for (int i = 0; i < TenderPilot.OPTION_SLOTS; i++) {
            if (i == slot) continue;
            BuildOption other = TenderPilot.option(i);
            if (rest.length() > 0) rest.append(' ');
            rest.append(Strings.format(other == null ? "tenderStatusSlotSoon" : "tenderStatusSlotOk", i + 1));
        }
        return new ShipCard("OPTION", Strings.get("tenderStatusOptionKey"), detail, rest.toString(),
                Strings.get(chosen == null ? "tenderStatusSoon" : "tenderStatusSelected"), chosen == null ? Tone.WARN : Tone.READY,
                chosen == null ? 0f : 1f);
    }

    private static ShipCard siteCard(Tender tender, int slot, TenderPilot.Notice notice) {
        ConstructionOrder o = tender.current();
        int live = TenderPilot.liveSites(tender);
        String resource = live > 0 ? Strings.format("tenderStatusCancelSites", live)
                : Strings.format("tenderStatusSitesNone", TenderPilot.MAX_SITES);
        String detail;
        String state;
        Tone tone;
        float fill = 0f;
        if (o == null) {
            detail = Strings.get(TenderPilot.option(slot) == null ? "tenderStatusPickOption" : "tenderStatusHoldLmb");
            state = Strings.get("tenderStatusIdle");
            tone = Tone.BUSY;
        } else {
            fill = o.fraction();
            detail = Strings.format("tenderStatusSiteProgress", Strings.upper(Strings.resolve(o.option().label())),
                    Math.round(fill * 100f));
            switch (o.phase()) {
                case TRANSIT -> {
                    boolean near = Math.hypot(tender.ship().x - o.siteX(), tender.ship().y - o.siteY())
                            <= TenderSystem.PILOT_REACH_CELLS;
                    state = Strings.get(near ? "tenderStatusLaunching" : "tenderStatusFlyCloser");
                    tone = near ? Tone.BUSY : Tone.WARN;
                }
                case WORKING -> {
                    if (TenderSystem.outOfReach(tender, o)) {
                        state = Strings.get("tenderStatusOutOfRange");
                        tone = Tone.WARN;
                    } else if (o.underFire()) {
                        state = Strings.get("tenderStatusUnderFire");
                        tone = Tone.WARN;
                    } else if (o.contested()) {
                        state = Strings.get("tenderStatusStoodDown");
                        tone = Tone.WARN;
                    } else if (o.gunHeld()) {
                        state = Strings.get("tenderStatusGunHeld");
                        tone = Tone.WARN;
                    } else {
                        state = Strings.get("tenderStatusBuilding");
                        tone = Tone.BUSY;
                    }
                }
                default -> {
                    state = Strings.get("tenderStatusQueued");
                    tone = Tone.BUSY;
                }
            }
        }
        String note = noticeText(notice);
        if (note != null) {
            state = note;
            tone = notice == TenderPilot.Notice.LAID ? Tone.READY : Tone.WARN;
        }
        return new ShipCard("SITE", Strings.get("tenderStatusSiteKey"), detail, resource, state, tone, fill);
    }

    private static ShipCard buildersCard(Tender tender) {
        ConstructionOrder o = tender.current();
        boolean working = o != null && o.phase() == ConstructionOrder.Phase.WORKING && !o.contested();
        String detail = Strings.format(working ? "tenderStatusWorkingLost" : "tenderStatusOutLost",
                tender.buildersOut(), tender.buildersLost());
        String state = Strings.get(tender.buildersOut() + tender.builderReserve() == 0 ? "tenderStatusNoneLeft"
                : working ? "tenderStatusWorking" : tender.buildersOut() > 0 ? "tenderStatusOut" : "tenderStatusDocked");
        Tone tone = tender.buildersOut() + tender.builderReserve() == 0 ? Tone.WARN
                : working ? Tone.BUSY : Tone.READY;
        return new ShipCard("BUILDERS", Strings.get("tenderStatusBuildersKey"), detail,
                Strings.format("tenderStatusReady", tender.builderReserve(), tender.builderComplement()), state, tone,
                fraction(tender.builderReserve() + tender.buildersOut(), tender.builderComplement()));
    }

    private static ShipCard defenceCard(Tender tender) {
        int alive = tender.defendersOut() + tender.defenceReserve();
        String state = Strings.get(alive == 0 ? "tenderStatusNoneLeft"
                : tender.defenceOut() ? "tenderStatusDeployed" : "tenderStatusRecalled");
        Tone tone = alive == 0 ? Tone.WARN : tender.defenceOut() ? Tone.READY : Tone.BUSY;
        String detail = Strings.format("tenderStatusOutLost", tender.defendersOut(), tender.defendersLost());
        return new ShipCard("DEFENCE", Strings.get("tenderStatusDefenceKey"), detail,
                Strings.format("tenderStatusReady", tender.defenceReserve(), tender.defenceComplement()), state, tone,
                fraction(alive, tender.defenceComplement()));
    }

    /** The strip's wording for a placement outcome, or null when there is nothing to say. */
    static String noticeText(TenderPilot.Notice notice) {
        return switch (notice) {
            case NONE -> null;
            case LAID -> Strings.get("tenderStatusNoticeLaid");
            case UNSEEN -> Strings.get("tenderStatusNoticeUnseen");
            case BLOCKED -> Strings.get("tenderStatusNoticeBlocked");
            case FULL -> Strings.get("tenderStatusNoticeFull");
            case NO_CHARGES -> Strings.get("tenderStatusNoticeNoCharges");
        };
    }

    private static float fraction(int part, int whole) {
        return whole <= 0 ? 0f : Math.max(0f, Math.min(1f, (float) part / whole));
    }
}
