package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.BarracksEquipmentIssue;
import com.dillon.starsectormarines.marine.EquipmentIssueResources;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics.PersonnelDrawResult;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadArmorDoctrine;
import com.dillon.starsectormarines.marine.SquadProfile;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.ui.retained.reactive.MutableSignal;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** View-local selection and drafts over campaign commands, profiles and exact issue. */
public final class BarracksCommandModel {
    private static final int PAGE_SIZE = 10;
    private final MarineRoster roster;
    private final BarracksEquipmentIssue equipment;
    private final IntSupplier cargoMarines;
    private final Function<String, PersonnelDrawResult> reinforce;
    private final Consumer<String> inspect;
    private final Runnable changed;
    private final MutableSignal<Integer> revision;
    private final Map<String, Object> props = new LinkedHashMap<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private String command = "all";
    private String profileFilter;
    private String profileId;
    private String targetCaptain;
    private boolean targetChosen;
    private boolean attention;
    private boolean detail;
    private boolean editing;
    private String search = "";
    private String draftName = "Line infantry";
    private int weaponIndex;
    private int armorIndex;
    private int page;
    private String feedback = "Select a command, then mark squads for orders. Inspect opens their quarters.";

    public BarracksCommandModel(Reactor reactor, MarineRoster roster, EquipmentIssueResources resources,
                                IntSupplier cargoMarines, Function<String, PersonnelDrawResult> reinforce,
                                Consumer<String> inspect, Runnable changed) {
        this.roster = roster;
        this.equipment = new BarracksEquipmentIssue(roster, resources);
        this.cargoMarines = cargoMarines;
        this.reinforce = reinforce;
        this.inspect = inspect;
        this.changed = changed;
        revision = reactor.signal(0);
        bind(reactor, "commands", this::commands);
        bind(reactor, "squads", this::squads);
        bind(reactor, "profiles", this::profiles);
        bind(reactor, "profilesEmpty", () -> roster.armory().squadProfiles().isEmpty()
                ? "No saved profiles. Choose NEW to pair a loadout and tactic sheet." : "");
        bind(reactor, "overviewClasses", () -> detail ? "command-board hidden" : "command-board");
        bind(reactor, "detailClasses", () -> detail ? "barracks-body" : "barracks-body hidden");
        bind(reactor, "ordersClasses", () -> editing ? "order-body hidden" : "order-body");
        bind(reactor, "editorClasses", () -> editing ? "order-body" : "order-body hidden");
        bind(reactor, "title", this::title);
        bind(reactor, "summary", () -> lineSquads().size() + " squads / " + roster.lineReadySoldiers().size()
                + " ready personnel / " + roster.readyReserveCount() + " reserves");
        bind(reactor, "scopeSummary", () -> filtered().size() + " squads in view / " + selected.size() + " selected");
        bind(reactor, "empty", () -> filtered().isEmpty() ? "No squads match this view. Raise new formations in Armory." : "");
        bind(reactor, "pageLabel", () -> "PAGE " + (pageNumber() + 1) + " / " + pages());
        bind(reactor, "previousDisabled", () -> pageNumber() == 0);
        bind(reactor, "nextDisabled", () -> pageNumber() + 1 >= pages());
        bind(reactor, "search", () -> search);
        bind(reactor, "attentionLabel", () -> attention ? "ATTENTION ONLY" : "ALL STATES");
        bind(reactor, "filterLabel", () -> profileFilter == null ? "ALL PROFILES" : roster.armory().squadProfile(profileFilter).name());
        bind(reactor, "selection", () -> selected.size() + " SQUADS SELECTED");
        bind(reactor, "selectionDisabled", selected::isEmpty);
        bind(reactor, "profileDisabled", () -> selected.isEmpty() || profileId == null);
        bind(reactor, "target", () -> !targetChosen ? "Choose receiving command" : targetCaptain == null
                ? "Independent / Acting NCO" : captainName(roster.byId(targetCaptain)));
        bind(reactor, "assignmentReason", () -> !targetChosen ? "Choose a command to preview capacity."
                : roster.previewCaptainAssignment(targetCaptain, selected).reason());
        bind(reactor, "assignmentDisabled", () -> !targetChosen
                || !roster.previewCaptainAssignment(targetCaptain, selected).valid());
        bind(reactor, "recruitment", this::recruitment);
        bind(reactor, "reinforceDisabled", () -> Math.min(vacancies(), roster.readyReserveCount() + cargoMarines.getAsInt()) == 0);
        bind(reactor, "issueSummary", () -> {
            var plan = equipment.preview(selected);
            return plan.eligible() + " eligible / " + plan.skipped() + " skipped";
        });
        bind(reactor, "issueCost", () -> equipment.preview(selected).cost().display());
        bind(reactor, "issueDisabled", () -> {
            var plan = equipment.preview(selected);
            return plan.eligible() == 0 || !plan.affordable();
        });
        bind(reactor, "issueReason", () -> {
            var plan = equipment.preview(selected);
            if (!selected.isEmpty() && !plan.affordable()) return "Insufficient cargo for the combined bill.";
            return "Issue skips stationed, incomplete and recovering squads. Fill vacancies first.";
        });
        bind(reactor, "feedback", () -> feedback);
        bind(reactor, "draftName", () -> draftName);
        bind(reactor, "draftWeapon", () -> weapons().isEmpty() ? "No known loadouts" : draftWeapon().displayName());
        bind(reactor, "draftArmor", () -> draftArmor().displayName());
        bind(reactor, "saveDisabled", () -> draftName.isBlank() || draftName.trim().length() > 48 || weapons().isEmpty());
        props.put("editSearch", (Consumer<String>) value -> { search = value; resetSelection(); });
        props.put("editName", (Consumer<String>) value -> { draftName = value; refresh(); });
        props.put("attention", (Runnable) () -> { attention = !attention; resetSelection(); });
        props.put("filter", (Runnable) this::cycleFilter);
        props.put("selectAll", (Runnable) () -> { filtered().stream().filter(s -> !s.stationed()).forEach(s -> selected.add(s.id())); refresh(); });
        props.put("clear", (Runnable) () -> { selected.clear(); refresh(); });
        props.put("previous", (Runnable) () -> { page = Math.max(0, pageNumber() - 1); refresh(); });
        props.put("next", (Runnable) () -> { page = Math.min(pages() - 1, pageNumber() + 1); refresh(); });
        props.put("back", (Runnable) this::showOverview);
        props.put("targetPrevious", (Runnable) () -> cycleTarget(-1));
        props.put("targetNext", (Runnable) () -> cycleTarget(1));
        props.put("assignCommand", (Runnable) this::assignCommand);
        props.put("assignProfile", (Runnable) this::assignProfile);
        props.put("reinforce", (Runnable) this::reinforceSelected);
        props.put("issue", (Runnable) this::issueSelected);
        props.put("newProfile", (Runnable) () -> { editing = true; refresh(); });
        props.put("cancelProfile", (Runnable) () -> { editing = false; refresh(); });
        props.put("weaponPrevious", (Runnable) () -> { weaponIndex--; refresh(); });
        props.put("weaponNext", (Runnable) () -> { weaponIndex++; refresh(); });
        props.put("armorPrevious", (Runnable) () -> { armorIndex--; refresh(); });
        props.put("armorNext", (Runnable) () -> { armorIndex++; refresh(); });
        props.put("saveProfile", (Runnable) this::saveProfile);
    }

    private void bind(Reactor reactor, String key, Supplier<?> supplier) {
        props.put(key, reactor.computed(() -> { revision.get(); return supplier.get(); }));
    }
    public Map<String, Object> props() { return props; }
    public Set<String> selected() { return Set.copyOf(selected); }
    public boolean inspecting() { revision.get(); return detail; }
    public void refresh() { revision.update(n -> n + 1); }
    public void leave() { selected.clear(); editing = false; targetChosen = false; refresh(); }
    public void showOverview() { detail = false; refresh(); }
    public void inspect(String id) { inspect.accept(id); detail = true; refresh(); }
    public void selectCommand(String id) { command = id; resetSelection(); }
    public void selectProfile(String id) { profileId = id; refresh(); }
    public void toggle(String id) {
        MarineSquad squad = roster.squadById(id);
        if (squad == null || squad.reserve() || squad.stationed()) return;
        if (!selected.remove(id)) selected.add(id);
        refresh();
    }
    private void resetSelection() { selected.clear(); page = 0; refresh(); }
    private List<MarineSquad> lineSquads() { return roster.squads().stream().filter(s -> !s.reserve()).toList(); }
    private String captainName(MarineCaptain captain) {
        return captain == null ? "Independent / Acting NCO" : captain.rank().displayName() + " " + captain.name();
    }
    private String group(MarineSquad squad) {
        return roster.byId(squad.homeCaptainId()) == null ? "independent" : squad.homeCaptainId();
    }
    private String title() { return "all".equals(command) ? "COMPANY FORMATIONS" : captainName(roster.byId(command)); }
    private String profileName(MarineSquad squad) {
        SquadProfile profile = roster.armory().squadProfile(squad.squadProfileId());
        if (profile != null) return profile.name();
        var weapon = roster.armory().weaponDoctrineById(squad.weaponDoctrineId());
        return weapon != null ? weapon.displayName() : "Individual issue";
    }
    private boolean needsAttention(MarineSquad squad) {
        if (squad.stationed()) return false;
        if (roster.readyCount(squad) < MarineSquad.CAPACITY) return true;
        var plan = equipment.preview(Set.of(squad.id()));
        return plan.eligible() == 0 || !plan.cost().isZero();
    }
    private List<MarineSquad> filtered() {
        return lineSquads().stream()
                .filter(s -> "all".equals(command) || group(s).equals(command))
                .filter(s -> profileFilter == null || Objects.equals(profileFilter, s.squadProfileId()))
                .filter(s -> !attention || needsAttention(s))
                .filter(s -> (s.name() + " " + profileName(s)).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
                .toList();
    }
    private int pages() { return Math.max(1, (filtered().size() + PAGE_SIZE - 1) / PAGE_SIZE); }
    private int pageNumber() { return Math.min(page, pages() - 1); }
    private List<Map<String, Object>> commands() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(commandRow("all", null, lineSquads()));
        for (MarineCaptain captain : roster.all()) {
            var squads = lineSquads().stream().filter(s -> captain.id().equals(group(s))).toList();
            rows.add(commandRow(captain.id(), captain, squads));
        }
        rows.add(commandRow("independent", null, lineSquads().stream().filter(s -> group(s).equals("independent")).toList()));
        return rows;
    }
    private Map<String, Object> commandRow(String id, MarineCaptain captain, List<MarineSquad> squads) {
        long ready = squads.stream().filter(s -> !s.stationed() && roster.readyCount(s) == MarineSquad.CAPACITY).count();
        return row("id", "command:" + id,
                "commanderportraitId", "command:" + id + ":portrait", "commandercopyId", "command:" + id + ":copy",
                "commandnameId", "command:" + id + ":name", "commandsummaryId", "command:" + id + ":summary",
                "commandcapacityId", "command:" + id + ":capacity",
                "name", id.equals("all") ? "All formations" : captainName(captain),
                "portrait", captain != null && captain.portraitSprite() != null ? captain.portraitSprite() : "graphics/ui/console/service-plate.svg",
                "summary", ready + " ready / " + squads.size() + " assigned",
                "capacity", captain == null ? id.equals("all") ? "Company roll" : "Independent mission execution"
                        : captain.status() + " / " + squads.size() + " of " + captain.rank().squadCommandCap() + " command slots",
                "classes", command.equals(id) ? "command-card selected" : "command-card",
                "select", (Runnable) () -> selectCommand(id));
    }
    private List<Map<String, Object>> squads() {
        var filtered = filtered();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MarineSquad squad : filtered.subList(Math.min(filtered.size(), pageNumber() * PAGE_SIZE),
                Math.min(filtered.size(), (pageNumber() + 1) * PAGE_SIZE))) {
            int ready = roster.readyCount(squad);
            String status = squad.stationed() ? "ON STATION" : ready == MarineSquad.CAPACITY ? "READY" : roster.vacancies(squad) > 0 ? "UNDERSTRENGTH" : "RECOVERING";
            var plan = equipment.preview(Set.of(squad.id()));
            String equipmentState = plan.eligible() == 0 ? plan.entries().get(0).reason()
                    : plan.cost().isZero() ? "Equipment conforms" : "Refit pending";
            rows.add(row("id", "formation:" + squad.id(), "markId", "formation:" + squad.id() + ":mark",
                    "formationidentityId", "formation:" + squad.id() + ":identity",
                    "formationnameId", "formation:" + squad.id() + ":name",
                    "formationstatusId", "formation:" + squad.id() + ":status",
                    "formationreadinessId", "formation:" + squad.id() + ":readiness",
                    "formationprofileId", "formation:" + squad.id() + ":profile",
                    "formationdetailId", "formation:" + squad.id() + ":detail",
                    "inspectId", "formation:" + squad.id() + ":inspect", "name", squad.name(),
                    "profile", profileName(squad), "status", status,
                    "detail", ready + "/12 ready / " + roster.vacancies(squad) + " vacant / " + equipmentState,
                    "classes", selected.contains(squad.id()) ? "formation selected" : "formation",
                    "mark", selected.contains(squad.id()) ? "X" : "+", "disabled", squad.stationed(),
                    "toggle", (Runnable) () -> toggle(squad.id()), "inspect", (Runnable) () -> inspect(squad.id())));
        }
        return rows;
    }
    private List<Map<String, Object>> profiles() {
        return roster.armory().squadProfiles().stream().map(profile -> row(
                "id", profile.id(), "profilenameId", profile.id() + ":name",
                "profiledetailId", profile.id() + ":detail", "name", profile.name(),
                "detail", roster.armory().weaponDoctrineById(profile.weaponDoctrineId()).displayName()
                        + "\n" + roster.armory().armorDoctrineById(profile.armorDoctrineId()).displayName(),
                "classes", profile.id().equals(profileId) ? "profile-card selected" : "profile-card",
                "select", (Runnable) () -> selectProfile(profile.id()))).toList();
    }
    private void cycleFilter() {
        var profiles = roster.armory().squadProfiles();
        int index = -1;
        for (int i = 0; i < profiles.size(); i++) if (profiles.get(i).id().equals(profileFilter)) index = i;
        profileFilter = index + 1 < profiles.size() ? profiles.get(index + 1).id() : null;
        resetSelection();
    }
    private void cycleTarget(int step) {
        List<String> targets = new ArrayList<>();
        targets.add(null);
        roster.active().forEach(c -> targets.add(c.id()));
        int index = targetChosen ? targets.indexOf(targetCaptain) : step > 0 ? -1 : 0;
        targetCaptain = targets.get(Math.floorMod(index + step, targets.size()));
        targetChosen = true;
        refresh();
    }
    public void assignCommand() {
        if (!targetChosen) return;
        var result = roster.assignCaptainToSquads(targetCaptain, selected);
        feedback = result.valid() ? result.changedSquadCount() + " squads assigned to " + captainName(roster.byId(targetCaptain)) + "." : result.reason();
        changed.run();
    }
    public void assignProfile() {
        boolean applied = roster.assignSquadProfile(selected, profileId);
        feedback = applied ? selected.size() + " squads assigned. Issue equipment separately when ready; no cargo spent."
                : "Select a profile and available squads. Stationed squads cannot change profile.";
        changed.run();
    }
    private int vacancies() {
        return lineSquads().stream().filter(s -> selected.contains(s.id()) && !s.stationed()).mapToInt(roster::vacancies).sum();
    }
    private String recruitment() {
        int vacancies = vacancies();
        int reserves = Math.min(vacancies, roster.readyReserveCount());
        int cargo = Math.min(vacancies - reserves, cargoMarines.getAsInt());
        return vacancies + " open billets / " + reserves + " reserves + " + cargo + " cargo marines\n"
                + (vacancies - reserves - cargo) + " remain vacant. Wounded retain their billets.";
    }
    public void reinforceSelected() {
        int reserves = 0;
        int cargo = 0;
        for (MarineSquad squad : lineSquads()) {
            if (!selected.contains(squad.id()) || squad.stationed()) continue;
            PersonnelDrawResult result = reinforce.apply(squad.id());
            reserves += result.reservesAssigned(); cargo += result.cargoMarinesConsumed();
        }
        feedback = "Filled " + (reserves + cargo) + " billets: " + reserves + " reserves, " + cargo
                + " cargo marines. Equipment issue remains a separate order.";
        changed.run();
    }
    public void issueSelected() {
        var result = equipment.apply(selected);
        feedback = result.succeeded() ? "Issued equipment to " + result.issued() + " squads; " + result.skipped() + " skipped."
                : "Issue blocked. Check personnel readiness, known equipment and the combined cargo bill.";
        changed.run();
    }
    private List<SquadWeaponDoctrine> weapons() {
        return roster.armory().weaponDoctrines().stream().filter(w -> roster.armory().canAuthorWeaponDoctrine(w.issues())).toList();
    }
    private SquadWeaponDoctrine draftWeapon() { var list = weapons(); return list.get(Math.floorMod(weaponIndex, list.size())); }
    private SquadArmorDoctrine draftArmor() { var list = roster.armory().armorDoctrines(); return list.get(Math.floorMod(armorIndex, list.size())); }
    public void saveProfile() {
        if (weapons().isEmpty()) return;
        SquadProfile profile = roster.armory().saveSquadProfile(draftName, draftWeapon().id(), draftArmor().id());
        if (profile == null) { feedback = "Use a name of 1–48 characters and known equipment."; refresh(); return; }
        profileId = profile.id(); editing = false;
        feedback = "Saved " + profile.name() + ". Assign it to selected squads; equipment changes only on issue.";
        changed.run();
    }
    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}
