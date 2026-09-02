package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.LoadoutEffectiveness;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadArmorDoctrine;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.ops.detachment.CaptainDeploymentPolicy;
import com.dillon.starsectormarines.ops.detachment.PersonnelReadiness;
import com.dillon.starsectormarines.ops.detachment.TaskForce;
import com.dillon.starsectormarines.battle.ui.panel.WeaponSymbols;
import com.dillon.starsectormarines.ops.spec.IntegralSystemCopy;
import com.dillon.starsectormarines.ops.spec.SpecSheets;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.spec.SpecSheet;
import com.dillon.starsectormarines.ui.spec.SpecSheetBinder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** MLX-authored pre-battle whole-squad assignment workspace. */
public final class SquadDeploymentScreen extends MissionFlowMlxScreen {

    static final String ROOT_COMPONENT = "squad-deployment";
    static final List<String> COMPONENT_PATHS = List.of(
            "data/ui/components/missions/squad-deployment.mlx");

    private MarineRoster roster;
    private List<SquadRow> projectedRows = List.of();
    private MemberInspector memberInspector = MemberInspector.empty();

    public SquadDeploymentScreen() {
        super(ROOT_COMPONENT, COMPONENT_PATHS);
    }

    @Override
    protected void onAttach() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        roster = script != null ? script.roster() : null;
    }

    @Override
    protected Map<String, Object> props() {
        Mission mission = context != null ? context.getSelectedMission() : null;
        int capacity = context != null ? context.getMarineDeploymentCapacity() : 0;
        if (mission != null && mission.type == MissionType.CONQUEST) {
            int selected = PersonnelReadiness.assessSelection(roster,
                    context.getSelectedMarineSquadIds(), 0).selectedReady();
            capacity = Math.max(capacity, selected);
        }
        PersonnelReadiness readiness = PersonnelReadiness.assessSelection(
                roster, context != null ? context.getSelectedMarineSquadIds() : null,
                capacity);
        TaskForce force = TaskForce.of(roster,
                context != null ? context.getSelectedCaptain() : null,
                context != null ? context.getSelectedMarineSquadIds() : null);

        Map<String, Object> props = baseProps();
        props.put("missionName", mission != null ? mission.name : "No mission selected");
        props.put("missionMeta", mission != null
                ? mission.tier.displayName + " · " + mission.type.name()
                : "Return to briefing and choose an operation.");
        props.put("readinessTone", "label title "
                + (readiness.ready() ? "tone-good" : "tone-danger"));
        props.put("readinessSummary", readiness.selectedReady() + " / " + capacity + " seats filled");
        props.put("commandSummary", force.summary());
        props.put("capacitySummary", capacity + " ready seats · "
                + readiness.companyReady() + " marines company-wide");
        props.put("reserveSummary", reserveSummary(readiness, capacity));
        projectedRows = squadRows();
        props.put("squadRows", projectedRows);
        props.put("emptyMessage", roster == null ? "Persistent roster unavailable." : "");
        putEmptyInspector(props);
        props.put("backAction", (Runnable) this::onBack);
        props.put("armoryAction", (Runnable) this::onArmory);
        return props;
    }

    static Map<String, Object> previewProps() {
        Map<String, Object> props = baseProps();
        Runnable none = () -> { };
        props.put("missionName", "SABOTAGE — First Contract");
        props.put("missionMeta", "First Contract · SABOTAGE");
        props.put("readinessTone", "label title tone-good");
        props.put("readinessSummary", "12 / 12 seats filled");
        props.put("commandSummary", "1 officer · 1 squad · 12 marines");
        props.put("capacitySummary", "12 ready seats · 48 marines company-wide");
        props.put("reserveSummary", "No reserve commitment · 1 squad active in the field");
        List<SquadRow> squads = List.of(
                previewRow(0, "1st Squad", "12 / 12 RTD", "Selected", true, none),
                previewRow(1, "2nd Squad", "11 / 12 RTD", "Available", false, none),
                previewRow(2, "3rd Squad", "8 / 12 RTD", "Available", false, none),
                previewRow(3, "4th Squad", "12 / 12 RTD", "Command limit", false, none));
        props.put("squadRows", squads);
        props.put("emptyMessage", "");
        putInspector(props, squads.get(0).fireTeams().get(0).members().get(0));
        props.put("backAction", none);
        props.put("armoryAction", none);
        return props;
    }

    private static Map<String, Object> baseProps() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", "Squad Assignment");
        return props;
    }

    private List<SquadRow> squadRows() {
        if (roster == null || context == null) return List.of();
        List<SquadRow> rows = new ArrayList<>();
        for (MarineSquad squad : roster.squads()) {
            if (squad.reserve()) continue;
            boolean selected = context.isMarineSquadSelected(squad.id());
            boolean canToggle = selected || CaptainDeploymentPolicy.canAdd(
                    roster, context.getSelectedCaptain(),
                    context.getSelectedMarineSquadIds(), squad.id());
            int ready = roster.readyCount(squad);
            int wia = countStatus(squad, MarineSoldierStatus.WIA);
            int mia = countStatus(squad, MarineSoldierStatus.MIA);
            int kia = countStatus(squad, MarineSoldierStatus.KIA);
            String casualties = casualtySummary(wia, mia, kia);
            String base = "deployment-squad-" + rows.size();
            CommanderRow commander = commanderRow(squad, selected, canToggle);
            rows.add(new SquadRow(base, "deployment-squad-card"
                    + (selected ? " selected" : ""), squad.name(),
                    ready + " / " + MarineSquad.CAPACITY + " RTD",
                    "label heading squad-card-readiness " + (selected ? "tone-accent"
                            : canToggle ? "tone-good" : "tone-danger"),
                    loadoutSummary(squad), commander.portrait(), commander.name(),
                    commander.status(), commander.statusClasses(),
                    casualties, "label squad-card-casualty "
                            + (casualties.isEmpty() ? "tone-muted" : "tone-danger"),
                    fireTeams(squad, selected), !canToggle, () -> toggle(squad.id())));
        }
        return List.copyOf(rows);
    }

    private static SquadRow previewRow(int index, String name, String readiness,
                                       String status, boolean selected, Runnable action) {
        String base = "deployment-preview-" + index;
        return new SquadRow(base, "deployment-squad-card" + (selected ? " selected" : ""),
                name, readiness, "label heading squad-card-readiness "
                        + (selected ? "tone-accent" : "tone-good"),
                "Field Rifles / Standard Plate",
                "graphics/portraits/portrait_mercenary01.png", "Lt. Mira Hale",
                "HOME COMMAND · " + status.toUpperCase(Locale.ROOT),
                "label heading squad-card-commander-status "
                        + (index == 3 ? "tone-danger" : selected ? "tone-accent" : "tone-good"),
                index == 2 ? "3 WIA · 1 KIA" : "", "label squad-card-casualty "
                        + (index == 2 ? "tone-danger" : "tone-muted"),
                previewTeams(index, selected), index == 3, action);
    }

    private List<FireTeamRow> fireTeams(MarineSquad squad, boolean selected) {
        List<String> manning = roster.manningMemberIds(squad);
        List<FireTeamRow> teams = new ArrayList<>(MarineSquad.TEAMS_PER_SQUAD);
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            List<MemberRow> members = new ArrayList<>(MarineSquad.TEAM_SIZE);
            for (int slot = 0; slot < MarineSquad.TEAM_SIZE; slot++) {
                int billet = team * MarineSquad.TEAM_SIZE + slot;
                MarineSoldier soldier = billet < manning.size()
                        ? roster.soldierById(manning.get(billet)) : null;
                members.add(memberRow(squad, soldier, team, slot, selected));
            }
            String id = "deployment-member-team-" + squad.id() + "-" + team;
            teams.add(new FireTeamRow(id, id + "-label", "TEAM " + (char) ('A' + team),
                    List.copyOf(members)));
        }
        return List.copyOf(teams);
    }

    private MemberRow memberRow(MarineSquad squad, MarineSoldier soldier,
                                int team, int slot, boolean selected) {
        String id = "deployment-member-" + squad.id() + "-" + team + "-" + slot;
        if (soldier == null) return vacantMember(id, team, slot);
        boolean ready = soldier.status() == MarineSoldierStatus.ACTIVE;
        boolean leader = soldier.id().equals(squad.leaderSoldierId());
        var primary = soldier.primaryDef();
        MarineArmorCatalogDef armor = soldier.armorDef();
        var special = soldier.specialEquipmentDef();
        int firepower = LoadoutEffectiveness.billetWeaponRating(
                primary, soldier.primaryGrade());
        int protection = LoadoutEffectiveness.billetArmorRating(armor);
        String classes = "deployment-member" + (ready ? " ready" : " unavailable")
                + (selected && ready ? " committed" : "") + (leader ? " leader" : "");
        String equipment = WeaponSymbols.primaryAbbrev(primary, soldier.primaryGrade())
                + (special != null ? "  " + WeaponSymbols.specialAbbrev(special) : "");
        return new MemberRow(id, classes, compactName(soldier), equipment,
                meter(firepower, "#78d494"), meter(protection, "#6ed7ff"),
                soldier.enlistedRank().displayName() + " " + soldier.name(),
                "FIRETEAM " + (char) ('A' + team) + " · BILLET " + (slot + 1)
                        + " · " + statusLabel(soldier),
                "FIREPOWER " + firepower, "PROTECTION " + protection,
                "PRIMARY · " + primary.catalogName(soldier.primaryGrade())
                        + " · " + soldier.primaryGrade().displayName,
                "ARMOR · " + armor.displayName() + " · " + armor.role().displayName(),
                special != null ? "SPECIAL · " + special.displayName() : "SPECIAL · NONE",
                armor.hasIntegralSystem()
                        ? "SUIT SYSTEM · " + IntegralSystemCopy.summary(armor)
                        : "SUIT SYSTEM · NONE",
                "PROFILE · " + soldier.profile().experienceTier().displayName + " / "
                        + soldier.aptitude().displayName,
                SpecSheets.weapon(primary, soldier.primaryGrade()),
                SpecSheets.armor(armor),
                special != null ? SpecSheets.special(special) : null,
                IntegralSystemCopy.carried(armor)
                        ? SpecSheets.integralSystem(armor) : null);
    }

    private static MemberRow vacantMember(String id, int team, int slot) {
        return new MemberRow(id, "deployment-member vacant", "VACANT", "NO KIT",
                meter(0, "#78d494"), meter(0, "#6ed7ff"), "Vacant billet",
                "FIRETEAM " + (char) ('A' + team) + " · BILLET " + (slot + 1),
                "FIREPOWER 0", "PROTECTION 0",
                "PRIMARY · NONE", "ARMOR · NONE", "SPECIAL · NONE",
                "SUIT SYSTEM · NONE", "PROFILE · NO MARINE WILL DEPLOY",
                null, null, null, null);
    }

    private static List<FireTeamRow> previewTeams(int squadIndex, boolean selected) {
        String[] names = {"Hale", "Vega", "Okafor", "Chen", "Singh", "Ibarra",
                "Sato", "Nwosu", "Kovacs", "Park", "Voss", "Reyes"};
        String[] weapons = {"RIF-II", "RIF-II", "SAW-II", "RKT", "DMR-II", "RIF-II",
                "SMG-I", "FRG", "RIF-II", "SMK", "SAW-II", "RIF-II"};
        List<FireTeamRow> teams = new ArrayList<>();
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            List<MemberRow> members = new ArrayList<>();
            for (int slot = 0; slot < MarineSquad.TEAM_SIZE; slot++) {
                int billet = team * MarineSquad.TEAM_SIZE + slot;
                String id = "deployment-preview-member-" + squadIndex + "-" + team + "-" + slot;
                if (squadIndex == 2 && billet == 11) {
                    members.add(vacantMember(id, team, slot));
                    continue;
                }
                boolean unavailable = squadIndex == 2 && billet >= 7 && billet <= 9;
                int firepower = Math.min(92, 38 + billet * 4 + squadIndex * 3);
                int protection = Math.min(88, 42 + (billet % 4) * 9 + squadIndex * 2);
                String classes = "deployment-member " + (unavailable ? "unavailable" : "ready")
                        + (selected && !unavailable ? " committed" : "")
                        + (billet == 0 ? " leader" : "");
                members.add(new MemberRow(id, classes, names[billet], weapons[billet],
                        meter(firepower, "#78d494"), meter(protection, "#6ed7ff"),
                        (billet == 0 ? "Corporal " : "Marine ") + names[billet],
                        "FIRETEAM " + (char) ('A' + team) + " · BILLET " + (slot + 1)
                                + " · " + (unavailable ? "WIA" : "READY"),
                        "FIREPOWER " + firepower, "PROTECTION " + protection,
                        "PRIMARY · M-42 FIELD RIFLE · SERVICE",
                        "ARMOR · STANDARD LINE PLATE · LINE",
                        billet % 3 == 0 ? "SPECIAL · FRAGMENTATION GRENADES"
                                : "SPECIAL · NONE",
                        billet == 0 ? "SUIT SYSTEM · BREACHER ASSIST · READY"
                                : "SUIT SYSTEM · NONE",
                        "PROFILE · REGULAR / STEADY",
                        null, null, null, null));
            }
            String id = "deployment-preview-team-" + squadIndex + "-" + team;
            teams.add(new FireTeamRow(id, id + "-label", "TEAM " + (char) ('A' + team),
                    List.copyOf(members)));
        }
        return List.copyOf(teams);
    }

    private static String compactName(MarineSoldier soldier) {
        String name = soldier.name() == null ? "MARINE" : soldier.name().trim();
        int split = name.lastIndexOf(' ');
        String compact = split >= 0 ? name.substring(split + 1) : name;
        return (soldier.enlistedRank().leads() ? "L " : "")
                + compact.toUpperCase(Locale.ROOT);
    }

    private static String statusLabel(MarineSoldier soldier) {
        return switch (soldier.status()) {
            case ACTIVE -> "READY";
            case WIA -> "WIA · NOT DEPLOYING";
            case MIA -> "MISSING";
            case KIA -> "KILLED";
        };
    }

    private static String meter(int value, String color) {
        return "width: " + Math.max(0, Math.min(100, value))
                + "%; background-color: " + color + ";";
    }

    private static void putEmptyInspector(Map<String, Object> props) {
        props.put("inspectorClasses", "marine-inspector empty");
        props.put("inspectorTitle", "HOVER A MARINE");
        props.put("inspectorContext", "The twelve billets in each card are the people committed.");
        props.put("inspectorFirepower", "FIREPOWER —");
        props.put("inspectorProtection", "PROTECTION —");
        props.put("inspectorPrimary", "PRIMARY · —");
        props.put("inspectorArmor", "ARMOR · —");
        props.put("inspectorSpecial", "SPECIAL · —");
        props.put("inspectorSystem", "SUIT SYSTEM · —");
        props.put("inspectorProfile", "PROFILE · —");
    }

    private static void putInspector(Map<String, Object> props, MemberRow member) {
        props.put("inspectorClasses", "marine-inspector");
        props.put("inspectorTitle", member.inspectorTitle());
        props.put("inspectorContext", member.inspectorContext());
        props.put("inspectorFirepower", member.inspectorFirepower());
        props.put("inspectorProtection", member.inspectorProtection());
        props.put("inspectorPrimary", member.inspectorPrimary());
        props.put("inspectorArmor", member.inspectorArmor());
        props.put("inspectorSpecial", member.inspectorSpecial());
        props.put("inspectorSystem", member.inspectorSystem());
        props.put("inspectorProfile", member.inspectorProfile());
    }

    @Override
    protected void onDocumentBuilt(MarkupInstance instance, UiDocument built) {
        memberInspector = MemberInspector.bind(instance, projectedRows);
        memberInspector.bindSpecSheets(specSheets());
    }

    @Override
    protected void onInputProcessed() {
        memberInspector.update();
    }

    private void toggle(String squadId) {
        context.toggleMarineSquad(squadId);
        rebuildDocument();
    }

    private int countStatus(MarineSquad squad, MarineSoldierStatus status) {
        int count = 0;
        for (MarineSoldier soldier : roster.squadMembers(squad)) {
            if (soldier.status() == status) count++;
        }
        return count;
    }

    private String loadoutSummary(MarineSquad squad) {
        SquadWeaponDoctrine weapons = roster.armory().weaponDoctrineById(squad.weaponDoctrineId());
        SquadArmorDoctrine armor = roster.armory().armorDoctrineById(squad.armorDoctrineId());
        String weaponName = weapons != null ? weapons.displayName() : "Field weapons";
        String armorName = armor != null ? armor.displayName() : "Field armor";
        return weaponName + " / " + armorName;
    }

    private CommanderRow commanderRow(MarineSquad squad, boolean selected,
                                       boolean canToggle) {
        MarineCaptain home = roster.captainForSquad(squad.id());
        MarineCaptain leader = CaptainDeploymentPolicy.leaderFor(
                roster, context.getSelectedCaptain(), squad.id());
        boolean inherited = home == null || leader == null || !home.id().equals(leader.id());
        String commandKind = inherited ? "OPERATION COMMAND" : "HOME COMMAND";
        String status = !canToggle ? "COMMAND LIMIT"
                : selected ? "SELECTED" : "AVAILABLE";
        if (leader == null) {
            return new CommanderRow("", "No active commander",
                    commandKind + " · " + status,
                    "label heading squad-card-commander-status tone-danger");
        }
        return new CommanderRow(leader.portraitSprite(),
                leader.rank().displayName() + " " + leader.name(),
                commandKind + " · " + status,
                "label heading squad-card-commander-status "
                        + (!canToggle ? "tone-danger" : selected ? "tone-accent" : "tone-good"));
    }

    private static String casualtySummary(int wia, int mia, int kia) {
        List<String> parts = new ArrayList<>();
        if (wia > 0) parts.add(wia + " WIA");
        if (mia > 0) parts.add(mia + " MIA");
        if (kia > 0) parts.add(kia + " KIA");
        return String.join(" · ", parts);
    }

    private static String reserveSummary(PersonnelReadiness readiness, int capacity) {
        int reserve = Math.max(0, readiness.selectedReady() - capacity);
        if (reserve > 0) return reserve + " marines committed in reserve";
        if (readiness.selectedShortfall() > 0) {
            return readiness.selectedShortfall() + " seats remain unfilled";
        }
        return "No reserve commitment";
    }

    private void onBack() {
        if (context != null) context.goTo(ScreenId.BRIEFING);
    }

    private void onArmory() {
        if (context != null) context.openCompanyArmoryFrom(ScreenId.SQUAD_DEPLOYMENT);
    }

    @Override protected void onCancel() { onBack(); }

    @Override
    protected List<String> requiredElementIds() {
        return List.of("squad-deployment-root", "squad-deployment-header",
                "squad-deployment-body", "squad-deployment-summary",
                "squad-deployment-roster", "squad-deployment-grid",
                "squad-deployment-inspector",
                "squad-deployment-actions", "squad-deployment-back",
                "squad-deployment-armory");
    }

    record CommanderRow(String portrait, String name, String status,
                        String statusClasses) { }

    record SquadRow(String id, String classes, String name, String readiness,
                    String tone, String loadout, String commanderPortrait,
                    String commanderName, String commanderStatus,
                    String commanderStatusClasses, String casualties,
                    String casualtyTone, List<FireTeamRow> fireTeams,
                    boolean disabled, Runnable action)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "classes" -> classes; case "name" -> name;
                case "topId" -> id + "-top"; case "nameId" -> id + "-name";
                case "readinessId" -> id + "-readiness";
                case "loadoutId" -> id + "-loadout";
                case "commanderId" -> id + "-commander";
                case "commanderPortraitFrameId" -> id + "-commander-portrait-frame";
                case "commanderPortraitId" -> id + "-commander-portrait";
                case "commanderCopyId" -> id + "-commander-copy";
                case "commanderNameId" -> id + "-commander-name";
                case "commanderStatusId" -> id + "-commander-status";
                case "casualtyId" -> id + "-casualties"; case "readiness" -> readiness;
                case "tone" -> tone; case "loadout" -> loadout;
                case "commanderPortrait" -> commanderPortrait;
                case "commanderName" -> commanderName;
                case "commanderStatus" -> commanderStatus;
                case "commanderStatusClasses" -> commanderStatusClasses;
                case "casualties" -> casualties; case "casualtyTone" -> casualtyTone;
                case "fireTeams" -> fireTeams; case "fireTeamsId" -> id + "-fireteams";
                case "disabled" -> disabled; case "action" -> action; default -> null;
            };
        }
    }

    record FireTeamRow(String id, String labelId, String label, List<MemberRow> members)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "labelId" -> labelId;
                case "membersId" -> id + "-members"; case "label" -> label;
                case "members" -> members; default -> null;
            };
        }
    }

    /**
     * One marine in a squad card, and the sheets behind the four equipment lines
     * the inspector shows for them. The lines are catalog items even though the
     * inspector as a whole is a dossier, so hovering one opens the item's own
     * spec sheet ({@code ui-nouns.md}). A sheet is null where the billet
     * carries nothing of that kind.
     */
    record MemberRow(String id, String classes, String name, String equipment,
                     String firepowerStyle, String protectionStyle,
                     String inspectorTitle, String inspectorContext,
                     String inspectorFirepower, String inspectorProtection,
                     String inspectorPrimary,
                     String inspectorArmor, String inspectorSpecial,
                     String inspectorSystem, String inspectorProfile,
                     SpecSheet primarySheet, SpecSheet armorSheet,
                     SpecSheet specialSheet, SpecSheet systemSheet)
            implements MarkupPropertySource {
        @Override public Object markupProperty(String property) {
            return switch (property) {
                case "id" -> id; case "classes" -> classes;
                case "lineId" -> id + "-line";
                case "nameId" -> id + "-name"; case "equipmentId" -> id + "-equipment";
                case "firepowerTrackId" -> id + "-firepower-track";
                case "firepowerId" -> id + "-firepower";
                case "protectionTrackId" -> id + "-protection-track";
                case "protectionId" -> id + "-protection";
                case "name" -> name; case "equipment" -> equipment;
                case "firepowerStyle" -> firepowerStyle;
                case "protectionStyle" -> protectionStyle; default -> null;
            };
        }
    }

    private record MemberBinding(MemberRow member, UiElement element) { }

    private record InspectorElements(UiElement panel, UiElement title,
                                     UiElement context, UiElement firepower,
                                     UiElement protection,
                                     UiElement primary, UiElement armor,
                                     UiElement special, UiElement system,
                                     UiElement profile) { }

    static final class MemberInspector {
        private static final MemberInspector EMPTY =
                new MemberInspector(List.of(), null);

        private final List<MemberBinding> bindings;
        private final InspectorElements elements;
        private String hoveredMemberId;
        private MemberRow hoveredMember;

        private MemberInspector(List<MemberBinding> bindings,
                                InspectorElements elements) {
            this.bindings = bindings;
            this.elements = elements;
        }

        static MemberInspector empty() {
            return EMPTY;
        }

        static MemberInspector bind(MarkupInstance instance, List<SquadRow> squads) {
            List<MemberBinding> bindings = new ArrayList<>();
            for (SquadRow squad : squads) {
                for (FireTeamRow team : squad.fireTeams()) {
                    for (MemberRow member : team.members()) {
                        bindings.add(new MemberBinding(member,
                                instance.requireElement(member.id())));
                    }
                }
            }
            InspectorElements elements = new InspectorElements(
                    instance.requireElement("squad-deployment-inspector"),
                    instance.requireElement("squad-deployment-inspector-title"),
                    instance.requireElement("squad-deployment-inspector-context"),
                    instance.requireElement("squad-deployment-inspector-firepower"),
                    instance.requireElement("squad-deployment-inspector-protection"),
                    instance.requireElement("squad-deployment-inspector-primary"),
                    instance.requireElement("squad-deployment-inspector-armor"),
                    instance.requireElement("squad-deployment-inspector-special"),
                    instance.requireElement("squad-deployment-inspector-system"),
                    instance.requireElement("squad-deployment-inspector-profile"));
            return new MemberInspector(List.copyOf(bindings), elements);
        }

        void update() {
            MemberRow hovered = null;
            for (MemberBinding binding : bindings) {
                if (binding.element().hovered()) {
                    hovered = binding.member();
                    break;
                }
            }
            String nextId = hovered != null ? hovered.id() : null;
            if ((nextId == null && hoveredMemberId == null)
                    || (nextId != null && nextId.equals(hoveredMemberId))) return;
            hoveredMemberId = nextId;
            hoveredMember = hovered;
            if (hovered == null) clear();
            else show(hovered);
        }

        /**
         * Makes the inspector's four equipment lines askable.
         *
         * <p>The lines are fixed elements whose subject changes underneath them,
         * so each is bound once to a sheet read off whichever marine is hovered
         * now. The inspector's own update runs first, so the line and the sheet
         * always describe the same marine.
         */
        void bindSpecSheets(SpecSheetBinder binder) {
            if (binder == null || elements == null) return;
            binder.bind(elements.primary(), () -> sheet(MemberRow::primarySheet));
            binder.bind(elements.armor(), () -> sheet(MemberRow::armorSheet));
            binder.bind(elements.special(), () -> sheet(MemberRow::specialSheet));
            binder.bind(elements.system(), () -> sheet(MemberRow::systemSheet));
        }

        private SpecSheet sheet(Function<MemberRow, SpecSheet> of) {
            return hoveredMember == null ? null : of.apply(hoveredMember);
        }

        private void show(MemberRow member) {
            if (elements == null) return;
            elements.panel().removeClass("empty");
            elements.title().text(member.inspectorTitle());
            elements.context().text(member.inspectorContext());
            elements.firepower().text(member.inspectorFirepower());
            elements.protection().text(member.inspectorProtection());
            elements.primary().text(member.inspectorPrimary());
            elements.armor().text(member.inspectorArmor());
            elements.special().text(member.inspectorSpecial());
            elements.system().text(member.inspectorSystem());
            elements.profile().text(member.inspectorProfile());
        }

        private void clear() {
            if (elements == null) return;
            elements.panel().addClass("empty");
            elements.title().text("HOVER A MARINE");
            elements.context().text(
                    "The twelve billets in each card are the people committed.");
            elements.firepower().text("FIREPOWER —");
            elements.protection().text("PROTECTION —");
            elements.primary().text("PRIMARY · —");
            elements.armor().text("ARMOR · —");
            elements.special().text("SPECIAL · —");
            elements.system().text("SUIT SYSTEM · —");
            elements.profile().text("PROFILE · —");
        }
    }
}
