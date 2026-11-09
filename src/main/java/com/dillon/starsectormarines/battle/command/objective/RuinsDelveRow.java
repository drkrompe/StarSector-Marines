package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.Item;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot.State;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective.RuinsPhase;
import com.dillon.starsectormarines.i18n.Strings;

import java.util.ArrayList;
import java.util.List;

/**
 * The Ruins family's Delve row ({@code ruins-expedition.md}, The rail shows the phase, and Judging the forces): beside
 * the vault's card, it heads with the phase and the pull-out point and gives the player three honest reads, none of
 * which names an enemy unit or place: the company, what it has, and what lies ahead. At a stand it reports the tally
 * since the last one and the next layer's close reading.
 *
 * <p>A Ruins rail runs compact (the works' side objective), where a row shows only its heading and one short line:
 * that line is the company at a glance, or a fresh stand's tally. The whole reading, the close reading's sign with it,
 * is the row's instruction, shown where the rail has room.
 */
public final class RuinsDelveRow {

    private RuinsDelveRow() {}

    /**
     * Everything the row reads, as the company knows it.
     *
     * @param insideCircle     how many of the company stand inside an extraction circle now
     * @param effectives       the company's combatants on the ground
     * @param landed           every combatant the company has landed
     * @param squadsBelowHalf  squads with fewer than half their landed strength left
     * @param findsBanked      field finds secured, which a pull-out brings home
     * @param storesOpened     the site's sealed stores the company has opened
     * @param routeBack        cells from the company's farthest combatant back to the pull-out point, or {@code -1}
     * @param layer            the deepest layer the company stands on, from 1
     * @param vaultRoute       cells from the vault to the nearest held lift point, or {@code -1} until it is sighted
     * @param stand            the last stand's report, or null before the first
     * @param standFresh       whether that stand was taken just now, so its tally leads the row
     */
    public record Picture(RuinsPhase phase, int insideCircle, int effectives, int landed, int squadsBelowHalf,
                          int machinesLost, int findsBanked, int storesOpened, int routeBack, int layer, int layers,
                          int vaultRoute, Stand stand, boolean standFresh) { }

    /**
     * A stand's report: the losses and finds since the last stand, and the next layer's close reading.
     *
     * @param where   what was held: the entry or a layer's stand
     * @param bandKey the next layer's strength band, or null when no layer lies ahead
     * @param signKey the occupant's sign in the next layer, or null when no layer lies ahead
     */
    public record Stand(String where, int down, int machinesLost, int findsBanked, String bandKey, String signKey) { }

    /** The row's id: the vault's own, so the row sorts straight after its card. */
    public static String id(String vaultId) { return vaultId + "-delve"; }

    public static Item item(String vaultId, Picture p) {
        String heading = Strings.get(p.phase() == RuinsPhase.APPROACH ? "ruinsDelveRowApproach" : "ruinsDelveRowDelve");
        String depth = p.phase() == RuinsPhase.APPROACH ? Strings.format("ruinsDelveRowOutside", p.layers())
                : Strings.format("ruinsDelveRowLayer", p.layer(), p.layers());
        String line = p.standFresh() && p.stand() != null ? tally(p.stand()) : glance(p);
        String whole = p.stand() == null ? reads(p) : stand(p.stand()) + " " + reads(p);
        return new Item(id(vaultId), Strings.get("ruinsDelveRowName"), State.ACTIVE, heading, depth, whole, line, -1,
                "", List.of(), "");
    }

    /** The company at a glance, for the compact rail: in a circle, on their feet, finds, and depth. */
    static String glance(Picture p) {
        String depth = p.phase() == RuinsPhase.APPROACH ? Strings.get("ruinsDelveGlanceOutside")
                : Strings.format("ruinsDelveGlanceLayer", p.layer(), p.layers());
        return Strings.format("ruinsDelveGlance", p.insideCircle(), p.effectives(), p.landed(), p.findsBanked(), depth);
    }

    /** The three reads, joined: the company, what it has, and the way back from the vault once it is sighted. */
    static String reads(Picture p) {
        List<String> company = new ArrayList<>();
        company.add(Strings.format("ruinsDelveRowInCircle", p.insideCircle()));
        company.add(Strings.format("ruinsDelveRowEffectives", p.effectives(), p.landed()));
        if (p.squadsBelowHalf() > 0) company.add(Strings.format("ruinsDelveRowBelowHalf", p.squadsBelowHalf()));
        if (p.machinesLost() > 0) company.add(Strings.format("ruinsDelveRowMachinesLost", p.machinesLost()));
        List<String> has = new ArrayList<>();
        has.add(Strings.format("ruinsDelveRowFinds", p.findsBanked()));
        if (p.storesOpened() > 0) has.add(Strings.format("ruinsDelveRowStores", p.storesOpened()));
        if (p.routeBack() >= 0) has.add(Strings.format("ruinsDelveRowRouteBack", p.routeBack()));
        StringBuilder out = new StringBuilder(String.join(", ", company)).append(". ")
                .append(String.join(", ", has)).append('.');
        if (p.vaultRoute() >= 0) out.append(' ').append(Strings.format("ruinsDelveRowVaultRoute", p.vaultRoute()));
        return out.toString();
    }

    /** A stand's tally on one short line: what was held, the losses and finds since the last stand, the next band. */
    static String tally(Stand s) {
        String line = Strings.format("ruinsDelveTally", s.where(), s.down(), s.findsBanked());
        return s.bandKey() == null ? line : Strings.format("ruinsDelveTallyNext", line, Strings.get(s.bandKey()));
    }

    /** A stand's tally and the next layer's close reading, then the choice; empty before the first stand. */
    static String stand(Stand s) {
        if (s == null) return "";
        String out = Strings.format("ruinsDelveStandHeld", s.where(), counts(s));
        if (s.bandKey() != null) {
            out += " " + Strings.format("ruinsDelveStandReading", Strings.get(s.bandKey()), Strings.get(s.signKey()));
        }
        return out + " " + Strings.get("ruinsDelveStandChoice");
    }

    private static String counts(Stand s) {
        List<String> tally = new ArrayList<>();
        tally.add(Strings.format("ruinsDelveStandDown", s.down()));
        if (s.machinesLost() > 0) tally.add(Strings.format("ruinsDelveStandMachines", s.machinesLost()));
        tally.add(Strings.format("ruinsDelveStandFinds", s.findsBanked()));
        return String.join(", ", tally);
    }
}
