package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.IntegralSystemService;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.ui.panel.WeaponSymbols;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Render-free projection for the retained selected-squad roster. */
final class BattleSquadOverlayModel {

    static final int FIRE_TEAM_COUNT = 3;
    static final int MEMBERS_PER_TEAM = 4;

    private static final String TOOLTIP_VISIBLE = "squad-tooltip";
    private static final String TOOLTIP_HIDDEN =
            "squad-tooltip squad-tooltip-hidden";

    private final MutableSignal<String> squadTitle;
    private final MutableSignal<String> squadStrength;
    private final MutableSignal<String> moraleLabel;
    private final MutableSignal<String> moraleStyle;
    private final MutableSignal<String> defendAreaLabel;
    private final MutableSignal<String> defendAreaClasses;
    private final MutableSignal<List<FireTeamView>> fireTeams;
    private final MutableSignal<String> tooltipClasses;
    private final MutableSignal<String> tooltipTitle;
    private final MutableSignal<String> tooltipTeam;
    private final MutableSignal<String> tooltipPrimary;
    private final MutableSignal<String> tooltipSpecial;
    private final MutableSignal<String> tooltipSystem;
    private final MutableSignal<String> tooltipProfile;
    private final Runnable backAction;
    private final Runnable defendAreaAction;

    private final Map<String, MemberTile> tilesById = new HashMap<>();
    private String hoveredTileId;

    BattleSquadOverlayModel(Reactor reactor, Runnable backAction,
                            Runnable defendAreaAction) {
        this.backAction = backAction;
        this.defendAreaAction = defendAreaAction;
        squadTitle = reactor.signal("SQUAD");
        squadStrength = reactor.signal("0/0");
        moraleLabel = reactor.signal("MORALE --");
        moraleStyle = reactor.signal("width: 0%;");
        defendAreaLabel = reactor.signal("DEFEND AREA");
        defendAreaClasses = reactor.signal("squad-defend-area");
        fireTeams = reactor.signal(emptyTeams());
        tooltipClasses = reactor.signal(TOOLTIP_HIDDEN);
        tooltipTitle = reactor.signal("");
        tooltipTeam = reactor.signal("");
        tooltipPrimary = reactor.signal("");
        tooltipSpecial = reactor.signal("");
        tooltipSystem = reactor.signal("");
        tooltipProfile = reactor.signal("");
    }

    BattleSquadOverlayModel(Reactor reactor, Runnable backAction) {
        this(reactor, backAction, () -> { });
    }

    Map<String, Object> props() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("squadTitle", squadTitle);
        props.put("squadStrength", squadStrength);
        props.put("moraleLabel", moraleLabel);
        props.put("moraleStyle", moraleStyle);
        props.put("defendAreaLabel", defendAreaLabel);
        props.put("defendAreaClasses", defendAreaClasses);
        props.put("fireTeams", fireTeams);
        props.put("tooltipClasses", tooltipClasses);
        props.put("tooltipTitle", tooltipTitle);
        props.put("tooltipTeam", tooltipTeam);
        props.put("tooltipPrimary", tooltipPrimary);
        props.put("tooltipSpecial", tooltipSpecial);
        props.put("tooltipSystem", tooltipSystem);
        props.put("tooltipProfile", tooltipProfile);
        props.put("backAction", backAction);
        props.put("defendAreaAction", defendAreaAction);
        return props;
    }

    Presentation update(BattleSimulation sim, int selectedSquadId,
                        int targetingSquadId) {
        if (sim == null || selectedSquadId < 0) return hide();
        Squad squad = sim.getSquad(selectedSquadId);
        if (!isPlayerInfantrySquad(squad)) return hide();

        List<Long> live = new ArrayList<>();
        for (int index = 0; index < sim.liveUnitCount(); index++) {
            long unit = sim.liveUnitAt(index);
            if (!sim.squad().hasSquad(unit)
                    || sim.squad().squadId(unit) != selectedSquadId) continue;
            live.add(unit);
        }
        live.sort(Comparator
                .comparingInt((Long unit) -> sim.squad().fireTeamIndex(unit))
                .thenComparing(unit -> squad.leaderId == unit ? 0 : 1)
                .thenComparingLong(unit -> unit));

        List<MemberState> members = new ArrayList<>(live.size());
        for (long unit : live) members.add(capture(sim, squad, unit));
        String label = squad.campaignLabel != null && !squad.campaignLabel.isBlank()
                ? squad.campaignLabel : "SQUAD " + squad.id;
        Presentation result = updateProjected(new SquadState(label, squad.aliveMembers,
                Math.max(squad.aliveMembers, squad.originalSize), squad.morale, members));
        boolean targeting = selectedSquadId == targetingSquadId;
        defendAreaLabel.set(targeting ? "CANCEL AREA" : "DEFEND AREA");
        defendAreaClasses.set(targeting
                ? "squad-defend-area squad-defend-area-active"
                : "squad-defend-area");
        return result;
    }

    static boolean isPlayerInfantrySquad(Squad squad) {
        return squad != null && squad.aliveMembers > 0
                && squad.faction == Faction.MARINE && !squad.isMechSquad();
    }

    Presentation updateProjected(SquadState squad) {
        if (squad == null || squad.alive() <= 0) return hide();
        squadTitle.set(squad.label().toUpperCase(Locale.ROOT));
        squadStrength.set(squad.alive() + "/" + Math.max(squad.alive(), squad.original()));
        float morale = clamp01(squad.morale());
        moraleLabel.set("MORALE " + Math.round(morale * 100f) + "%");
        moraleStyle.set("width: " + Math.round(morale * 100f) + "%;");

        List<List<MemberState>> grouped = new ArrayList<>(FIRE_TEAM_COUNT);
        for (int team = 0; team < FIRE_TEAM_COUNT; team++) grouped.add(new ArrayList<>());
        for (MemberState member : squad.members()) {
            int team = Math.max(0, Math.min(FIRE_TEAM_COUNT - 1, member.fireTeamIndex()));
            if (grouped.get(team).size() < MEMBERS_PER_TEAM) grouped.get(team).add(member);
        }

        tilesById.clear();
        List<FireTeamView> teams = new ArrayList<>(FIRE_TEAM_COUNT);
        for (int team = 0; team < FIRE_TEAM_COUNT; team++) {
            List<MemberTile> members = new ArrayList<>(MEMBERS_PER_TEAM);
            for (int slot = 0; slot < MEMBERS_PER_TEAM; slot++) {
                String id = "battle-squad-member-" + team + "-" + slot;
                MemberTile tile = slot < grouped.get(team).size()
                        ? tile(id, team, slot, grouped.get(team).get(slot))
                        : emptyTile(id);
                members.add(tile);
                tilesById.put(id, tile);
            }
            String teamId = "battle-squad-fireteam-" + team;
            teams.add(new FireTeamView(teamId, teamId + "-header",
                    teamId + "-label", teamId + "-strength", teamId + "-members",
                    "FIRETEAM " + (char) ('A' + team), grouped.get(team).size() + "/4",
                    List.copyOf(members)));
        }
        fireTeams.set(List.copyOf(teams));
        if (hoveredTileId != null && !tilesById.containsKey(hoveredTileId)) clearHover();
        return new Presentation(true);
    }

    void hover(String tileId) {
        MemberTile tile = tileId == null ? null : tilesById.get(tileId);
        if (tile == null || !tile.occupied()) {
            clearHover();
            return;
        }
        if (tileId.equals(hoveredTileId)) return;
        hoveredTileId = tileId;
        tooltipClasses.set(TOOLTIP_VISIBLE);
        tooltipTitle.set(tile.tooltipTitle());
        tooltipTeam.set(tile.tooltipTeam());
        tooltipPrimary.set(tile.tooltipPrimary());
        tooltipSpecial.set(tile.tooltipSpecial());
        tooltipSystem.set(tile.tooltipSystem());
        tooltipProfile.set(tile.tooltipProfile());
    }

    void clearHover() {
        hoveredTileId = null;
        tooltipClasses.set(TOOLTIP_HIDDEN);
    }

    List<MemberTile> memberTiles() {
        return List.copyOf(tilesById.values());
    }

    private Presentation hide() {
        clearHover();
        return new Presentation(false);
    }

    private static MemberState capture(BattleSimulation sim, Squad squad, long unit) {
        WeaponDef primary = sim.combat().primaryWeaponDef(unit);
        EquipmentGrade grade = sim.combat().equipmentGrade(unit);
        SoldierProfile profile = sim.combat().soldierProfile(unit);
        boolean hasSpecial = sim.world().hasSecondaryWeapon(unit);
        SpecialEquipmentDef special = hasSpecial ? sim.world().specialEquipment(unit) : null;
        int specialAmmo = hasSpecial ? sim.world().secondaryAmmo(unit) : 0;
        float specialCooldown = hasSpecial ? sim.world().secondaryCooldownTimer(unit) : 0f;
        IntegralSystemService systems = sim.integralSystems();
        IntegralSystemDef system = systems.spec(unit);
        boolean hasArmor = sim.world().hasArmor(unit);
        return new MemberState(unit, sim.squad().fireTeamIndex(unit), squad.leaderId == unit,
                sim.identity().name(unit), sim.world().hp(unit), sim.world().maxHp(unit),
                hasArmor ? sim.world().armor(unit) : 0f,
                hasArmor ? sim.world().maxArmor(unit) : 0f,
                hasArmor ? sim.world().armorRating(unit) : 0f,
                primary != null ? primary.catalogName(grade) : "Field rifle",
                WeaponSymbols.primaryAbbrev(primary, grade), WeaponSymbols.primaryColor(primary),
                special != null ? special.displayName() : null,
                special != null ? WeaponSymbols.specialAbbrev(special) : null,
                specialStatus(special, specialAmmo, specialCooldown),
                system != null ? system.displayName() : null,
                systemStatus(systems, system, unit),
                profile.experienceTier().displayName,
                profile.aptitude().displayName,
                sim.role().role(unit));
    }

    private static MemberTile tile(String id, int team, int slot, MemberState member) {
        float hpFraction = member.maxHp() > 0f ? clamp01(member.hp() / member.maxHp()) : 0f;
        Color hpColor = hpFraction < 0.34f ? new Color(0xE9, 0x8B, 0x83)
                : hpFraction < 0.67f ? new Color(0xFF, 0xD4, 0x64)
                : new Color(0x78, 0xD4, 0x94);
        String classes = "member-card" + (member.leader() ? " member-leader" : "")
                + (hpFraction < 0.34f ? " member-critical" : "");
        String tag = member.leader() ? "LDR " + (slot + 1) : "M-" + (slot + 1);
        String special = member.specialAbbrev() != null ? member.specialAbbrev()
                : roleBadge(member.role());
        String fullName = member.name() == null || member.name().isBlank()
                ? "Marine " + (slot + 1) : member.name();
        String armor = member.maxArmor() > 0f
                ? " · ARMOR " + number(member.armor()) + "/" + number(member.maxArmor())
                + " R" + number(member.armorRating()) : "";
        return new MemberTile(id, id + "-line", id + "-name", id + "-primary",
                id + "-special", id + "-hp-track", id + "-hp", true, classes, tag,
                member.primaryAbbrev(), special == null ? "" : special,
                "width: " + Math.round(hpFraction * 100f) + "%; background-color: "
                        + color(hpColor) + ";",
                fullName + (member.leader() ? " · SQUAD LEADER" : ""),
                "FIRETEAM " + (char) ('A' + team) + " · "
                        + number(member.hp()) + "/" + number(member.maxHp()) + " HP" + armor,
                "PRIMARY · " + member.primaryName(),
                member.specialName() != null
                        ? "SPECIAL · " + member.specialName() + " · " + member.specialStatus()
                        : "SPECIAL · NONE",
                member.systemName() != null
                        ? "SUIT SYSTEM · " + member.systemName() + " · " + member.systemStatus()
                        : "SUIT SYSTEM · NONE",
                "PROFILE · " + member.experience() + " / " + member.aptitude()
                        + " · " + roleName(member.role()));
    }

    private static MemberTile emptyTile(String id) {
        return new MemberTile(id, id + "-line", id + "-name", id + "-primary",
                id + "-special", id + "-hp-track", id + "-hp", false,
                "member-card member-empty", "—", "", "",
                "width: 0%;", "", "", "", "", "", "");
    }

    private static List<FireTeamView> emptyTeams() {
        List<FireTeamView> teams = new ArrayList<>(FIRE_TEAM_COUNT);
        for (int team = 0; team < FIRE_TEAM_COUNT; team++) {
            List<MemberTile> members = new ArrayList<>(MEMBERS_PER_TEAM);
            for (int slot = 0; slot < MEMBERS_PER_TEAM; slot++) {
                members.add(emptyTile("battle-squad-member-" + team + "-" + slot));
            }
            String teamId = "battle-squad-fireteam-" + team;
            teams.add(new FireTeamView(teamId, teamId + "-header",
                    teamId + "-label", teamId + "-strength", teamId + "-members",
                    "FIRETEAM " + (char) ('A' + team), "0/4", List.copyOf(members)));
        }
        return List.copyOf(teams);
    }

    private static String specialStatus(SpecialEquipmentDef special, int ammo, float cooldown) {
        if (special == null) return "";
        if (special.usesAmmunition()) return Math.max(0, ammo) + " LEFT";
        return cooldown > 0f ? (int) Math.ceil(cooldown) + "S COOLDOWN" : "READY";
    }

    private static String systemStatus(IntegralSystemService systems,
                                       IntegralSystemDef system, long unit) {
        if (system == null) return "";
        if (systems.isActive(unit)) {
            return (int) Math.ceil(systems.activeRemaining(unit)) + "S ACTIVE";
        }
        if (system.usesAmmunition()) return systems.ammo(unit) + " LEFT";
        float cooldown = systems.cooldownRemaining(unit);
        return cooldown > 0f ? (int) Math.ceil(cooldown) + "S COOLDOWN" : "READY";
    }

    private static String roleBadge(UnitRole role) {
        String badge = WeaponSymbols.roleBadge(role);
        return badge != null ? badge : "";
    }

    private static String roleName(UnitRole role) {
        if (role == null) return "Combatant";
        String lower = role.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String number(float value) {
        return Integer.toString((int) Math.ceil(Math.max(0f, value)));
    }

    private static String color(Color value) {
        return String.format(Locale.ROOT, "#%02x%02x%02x",
                value.getRed(), value.getGreen(), value.getBlue());
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    record Presentation(boolean visible) { }

    record SquadState(String label, int alive, int original, float morale,
                      List<MemberState> members) {
        SquadState {
            members = members == null ? List.of() : List.copyOf(members);
        }
    }

    record MemberState(long entityId, int fireTeamIndex, boolean leader,
                       String name, float hp, float maxHp,
                       float armor, float maxArmor, float armorRating,
                       String primaryName, String primaryAbbrev, Color primaryColor,
                       String specialName, String specialAbbrev, String specialStatus,
                       String systemName, String systemStatus,
                       String experience, String aptitude, UnitRole role) { }

    record FireTeamView(String id, String headerId, String labelId, String strengthId,
                        String membersId, String label, String strength,
                        List<MemberTile> members) implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "headerId" -> headerId;
                case "labelId" -> labelId;
                case "strengthId" -> strengthId;
                case "membersId" -> membersId;
                case "label" -> label;
                case "strength" -> strength;
                case "members" -> members;
                default -> throw new IllegalArgumentException(
                        "Unknown fire-team property: " + property);
            };
        }
    }

    record MemberTile(String id, String lineId, String nameId, String primaryId,
                      String specialId, String hpTrackId, String hpId,
                      boolean occupied, String classes, String name,
                      String primary, String special, String hpStyle,
                      String tooltipTitle, String tooltipTeam, String tooltipPrimary,
                      String tooltipSpecial, String tooltipSystem, String tooltipProfile)
            implements MarkupPropertySource {
        @Override
        public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id;
                case "lineId" -> lineId;
                case "nameId" -> nameId;
                case "primaryId" -> primaryId;
                case "specialId" -> specialId;
                case "hpTrackId" -> hpTrackId;
                case "hpId" -> hpId;
                case "occupied" -> occupied;
                case "classes" -> classes;
                case "name" -> name;
                case "primary" -> primary;
                case "special" -> special;
                case "hpStyle" -> hpStyle;
                default -> throw new IllegalArgumentException(
                        "Unknown member-tile property: " + property);
            };
        }
    }
}
