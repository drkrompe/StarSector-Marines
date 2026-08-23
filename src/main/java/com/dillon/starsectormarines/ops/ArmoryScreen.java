package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.CaptainCandidate;
import com.dillon.starsectormarines.marine.FireTeamBillet;
import com.dillon.starsectormarines.marine.FireTeamGearDelta;
import com.dillon.starsectormarines.marine.FireTeamRefitPreview;
import com.dillon.starsectormarines.marine.FireTeamTemplateCard;
import com.dillon.starsectormarines.marine.FireTeamTemplateAvailability;
import com.dillon.starsectormarines.marine.FireTeamTemplateCards;
import com.dillon.starsectormarines.marine.FireTeamTemplateResult;
import com.dillon.starsectormarines.marine.MarineArmory;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics;
import com.dillon.starsectormarines.marine.SquadArrangement;
import com.dillon.starsectormarines.marine.SquadArrangementPreview;
import com.dillon.starsectormarines.marine.Status;
import com.dillon.starsectormarines.ops.detachment.PersonnelReadiness;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.PanelWidget;
import com.dillon.starsectormarines.ui.SelectableRowWidget;
import com.dillon.starsectormarines.ui.SpriteThumbWidget;
import com.dillon.starsectormarines.ui.StatBarWidget;
import com.dillon.starsectormarines.ui.TextFieldWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Fabrication plus squad-centric persistent personnel management. */
public final class ArmoryScreen implements Screen {

    private enum Tab { PERSONNEL, LOADOUTS, DESIGNER, ARRANGEMENTS }
    private enum InventoryTab { WEAPONS, ARMOR, SPECIAL }
    private enum InventoryState { AVAILABLE, OUT_OF_STOCK, LOCKED, INSTALLED, MARINE_UNAVAILABLE }
    private enum ReadoutFormat { DECIMAL, INTEGER, PERCENT }
    private enum WeaponTab {
        RIFLE("Rifle", MarineWeapon.FIELD_RIFLE),
        PULSE("Pulse", MarineWeapon.PULSE_RIFLE),
        MACHINE_GUN("LMG", MarineWeapon.SMG),
        RAILGUN("Railgun", MarineWeapon.DMR);

        final String label;
        final MarineWeapon weapon;

        WeaponTab(String label, MarineWeapon weapon) {
            this.label = label;
            this.weapon = weapon;
        }
    }

    private static final float PAD = 16f;
    private static final float GAP = 10f;
    private static final float BUTTON_H = 32f;
    private static final float SQUAD_ROW_H = 36f;
    private static final float MEMBER_ROW_H = 46f;
    private static final float SQUAD_COL_W = 224f;
    private static final Color HEADER = new Color(0xC8, 0xE0, 0xFF);
    private static final Color VALUE = new Color(0xFF, 0xE0, 0x70);
    private static final Color MUTED = new Color(0x92, 0x9A, 0xA5);
    private static final Color GOOD = new Color(0x80, 0xD8, 0x98);
    private static final Color BAD = new Color(0xE0, 0x70, 0x70);
    private static final Color DAMAGE_BAR = new Color(0xE4, 0x78, 0x68);
    private static final Color DPS_BAR = new Color(0xF0, 0xB8, 0x52);
    private static final Color RANGE_BAR = new Color(0x72, 0xB8, 0xE8);
    private static final Color ACCURACY_BAR = new Color(0x72, 0xD2, 0x8D);

    private final WidgetRoot widgets = new WidgetRoot();
    private PositionAPI position;
    private MarineOpsContext ctx;
    private MarineRoster roster;
    private String selectedSquadId;
    private String selectedSoldierId;
    private int selectedTeamIndex;
    private Tab tab = Tab.PERSONNEL;
    private int squadPage;
    private int memberPage;
    private final List<TextFieldWidget> textFields = new ArrayList<>();
    private String templateFeedback;
    private boolean templateSucceeded;
    private String loadoutFeedback;
    private boolean loadoutSucceeded;
    private InventoryTab inventoryTab = InventoryTab.WEAPONS;
    private WeaponTab weaponTab = WeaponTab.RIFLE;
    private MarineWeapon browsedWeapon = MarineWeapon.FIELD_RIFLE;
    private EquipmentGrade browsedGrade = EquipmentGrade.SERVICE;
    private MarineArmorPattern browsedArmor = MarineArmorPattern.ARMORLESS;
    private MarineSecondary browsedSecondary = MarineSecondary.ROCKET_LAUNCHER;
    private int inventoryScroll;
    private int candidatePage;
    private int templatePage;
    private String previewTemplateId;
    private int designerTemplatePage;
    private String selectedDesignerTemplateId;
    private TemplateDraft templateDraft;
    private String designerFeedback;
    private boolean designerSucceeded;
    private int arrangementPage;
    private String selectedArrangementId;
    private ArrangementDraft arrangementDraft;
    private String arrangementFeedback;
    private boolean arrangementSucceeded;
    private String lastInventoryClickKey;
    private long lastInventoryClickNanos;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        MarineRosterScript script = MarineRosterScript.getInstance();
        roster = script != null ? script.roster() : null;
        if (roster != null) {
            roster.bootstrapInitialComplement(MarineSquad.CAPACITY);
            roster.reserveSquad();
            if (roster.squadById(selectedSquadId) == null && !roster.squads().isEmpty()) {
                selectedSquadId = roster.squads().get(0).id();
            }
            selectFirstSoldierIfNeeded();
        }
        rebuild();
    }

    private void rebuild() {
        widgets.clear();
        textFields.clear();
        if (position == null || ctx == null) return;
        float left = position.getX() + PAD;
        float top = position.getY() + position.getHeight() - PAD;

        addButton(left, position.getY() + PAD, 120f, "Back",
                ctx::returnFromArmory, HEADER);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "Fleet Armory", left, top, HEADER));
        if (roster == null) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "Marine roster unavailable.", left, top - 38f, MUTED));
            return;
        }

        float tabY = top - 52f;
        addButton(left, tabY, 130f, "Personnel",
                () -> { tab = Tab.PERSONNEL; rebuild(); },
                tab == Tab.PERSONNEL ? VALUE : HEADER);
        addButton(left + 140f, tabY, 174f, "Fire-team Loadouts",
                () -> { tab = Tab.LOADOUTS; selectFirstSoldierIfNeeded(); rebuild(); },
                tab == Tab.LOADOUTS ? VALUE : HEADER);
        addButton(left + 324f, tabY, 174f, "Template Designer",
                () -> { tab = Tab.DESIGNER; ensureDesignerDraft(); rebuild(); },
                tab == Tab.DESIGNER ? VALUE : HEADER);
        addButton(left + 508f, tabY, 190f, "Squad Arrangements",
                () -> { tab = Tab.ARRANGEMENTS; ensureArrangementDraft(); rebuild(); },
                tab == Tab.ARRANGEMENTS ? VALUE : HEADER);

        int personnelTarget = ctx.getArmoryPersonnelTarget();
        if (personnelTarget > 0) {
            PersonnelReadiness readiness = PersonnelReadiness.assess(
                    roster, Collections.emptySet(), personnelTarget);
            int enlistable = Math.min(readiness.companyShortfall(),
                    MarinePersonnelLogistics.availableRecruits());
            String label = readiness.ready() ? "Return Ready"
                    : enlistable > 0 ? "Enlist " + enlistable + " & Return"
                    : "Need " + readiness.companyShortfall() + " · No Cargo";
            Runnable action = readiness.ready() ? ctx::returnFromArmory
                    : enlistable > 0 ? () -> {
                        MarinePersonnelLogistics.enlistLine(
                                roster, readiness.companyShortfall());
                        ctx.returnFromArmory();
                    } : null;
            addButton(left + 132f, position.getY() + PAD, 236f,
                    label, action, action != null ? GOOD : BAD);
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "Mission personnel: " + readiness.companyReady() + " / "
                            + readiness.requiredSeats() + " company-ready",
                    left + 380f, position.getY() + PAD + BUTTON_H - 6f,
                    readiness.ready() ? GOOD : VALUE));
        }

        if (tab == Tab.LOADOUTS) {
            buildLoadouts(left, top - 92f);
            return;
        }
        if (tab == Tab.DESIGNER) {
            buildTemplateDesigner(left, top - 92f);
            return;
        }
        if (tab == Tab.ARRANGEMENTS) {
            buildArrangementDesigner(left, top - 92f);
            return;
        }

        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "Organize squads, personnel, command assignments and reserves.",
                left + 394f, tabY + BUTTON_H - 6f, MUTED));
        float managementTop = top - 112f;
        float managementBottom = position.getY() + PAD + BUTTON_H + 12f;
        managementTop = buildCandidateIntake(left, managementTop,
                position.getWidth() - 2f * PAD);
        buildSquadList(left, managementTop, managementBottom);
        buildSelectedSquad(left + SQUAD_COL_W + GAP, managementTop, managementBottom,
                position.getWidth() - 2f * PAD - SQUAD_COL_W - GAP);
    }

    private float buildCandidateIntake(float left, float top, float width) {
        List<CaptainCandidate> candidates = roster.availableCaptainCandidates();
        if (candidates.isEmpty()) {
            candidatePage = 0;
            return top;
        }

        candidatePage = Math.max(0, Math.min(candidatePage, candidates.size() - 1));
        CaptainCandidate candidate = candidates.get(candidatePage);
        String trait = candidate.startingTrait() == null ? "No specialty"
                : candidate.startingTrait().displayName();
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "RECOVERED CAPTAIN CANDIDATE", left, top + 24f, VALUE));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                candidate.name() + " · " + candidate.startingRank().displayName()
                        + " · " + trait + " · Derelict salvage, day "
                        + (int) candidate.discoveredAtDay(),
                left, top - 2f, HEADER));

        float buttonY = top - 38f;
        float right = left + width;
        Runnable commission = roster.hasRoom() ? () -> {
            roster.acceptCaptainCandidate(candidate.sourceKey());
            rebuild();
        } : null;
        addButton(right - 160f, buttonY, 160f,
                roster.hasRoom() ? "Offer Commission" : "Captain Roster Full",
                commission, commission != null ? GOOD : MUTED);
        if (candidates.size() > 1) {
            addButton(right - 330f, buttonY, 72f, "Prev", candidatePage > 0 ? () -> {
                candidatePage--;
                rebuild();
            } : null, candidatePage > 0 ? HEADER : MUTED);
            addButton(right - 250f, buttonY, 82f,
                    "Next " + (candidatePage + 1) + "/" + candidates.size(),
                    candidatePage + 1 < candidates.size() ? () -> {
                        candidatePage++;
                        rebuild();
                    } : null, candidatePage + 1 < candidates.size() ? HEADER : MUTED);
        }
        return top - 82f;
    }

    /** MechLab-style workspace: formation, installed kit, item dossier and inventory. */
    private void buildLoadouts(float left, float top) {
        MarineArmory armory = roster.armory();
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "Parts & materials: " + armory.fabricationMaterials()
                        + "    Victories: " + armory.victories()
                        + "    High-risk: " + armory.highRiskVictories()
                        + "    Select a fire team, then assign a reusable template",
                left, top + 24f, VALUE));

        float rosterW = 236f;
        float paperX = left + rosterW + GAP;
        float paperW = 318f;
        float right = position.getX() + position.getWidth() - PAD;
        float dossierX = paperX + paperW + 18f;
        float remaining = right - dossierX;
        float inventoryW = Math.max(360f, Math.min(480f, remaining * 0.42f));
        float inventoryX = right - inventoryW;
        float dossierW = Math.max(220f, inventoryX - 18f - dossierX);
        buildLoadoutRoster(left, top, rosterW);
        buildPaperDoll(paperX, top, paperW);
        buildItemDossier(dossierX, top, dossierW);
        buildInventoryBrowser(inventoryX, top, inventoryW);
    }

    private void buildTemplateDesigner(float left, float top) {
        ensureDesignerDraft();
        MarineArmory armory = roster.armory();
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "Fire-team templates are reusable and may be saved without recipes, stock, or materials. "
                        + "Assignment still requires a complete physical kit.",
                left, top + 24f, VALUE));

        float libraryW = 270f;
        float editorX = left + libraryW + 24f;
        float right = position.getX() + position.getWidth() - PAD;
        float editorW = right - editorX;
        buildDesignerLibrary(left, top, libraryW);

        if (templateDraft == null) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "No fire-team template is available.", editorX, top - 18f, MUTED));
            return;
        }

        FireTeamTemplateCard selected = armory.templateCardById(selectedDesignerTemplateId);
        boolean builtIn = selected != null
                && FireTeamTemplateCards.isStarterId(selected.id());
        boolean assigned = selected != null && roster.isFireTeamTemplateAssigned(selected.id());
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "FOUR-BILLET TEMPLATE", editorX, top - 12f, HEADER));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                selected == null ? "Unsaved draft"
                        : builtIn ? "Built-in · clone or save as new to customize"
                        : assigned ? "Custom · assigned teams retain this revision"
                        : "Custom · not currently assigned",
                editorX + 226f, top - 12f,
                builtIn ? MUTED : assigned ? VALUE : GOOD));

        float nameY = top - 62f;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "TEMPLATE NAME", editorX, nameY + 25f, MUTED));
        TextFieldWidget templateName = new TextFieldWidget(editorX + 132f, nameY,
                Math.min(360f, editorW - 132f), BUTTON_H,
                Fonts.ORBITRON_20, 32, "Template name");
        templateName.setText(templateDraft.name);
        templateName.setOnChange(value -> templateDraft.name = value);
        addTextField(templateName);

        float rowTop = top - 116f;
        for (int billetIndex = 0; billetIndex < MarineSquad.TEAM_SIZE; billetIndex++) {
            buildDesignerBillet(editorX, rowTop - billetIndex * 98f,
                    editorW, billetIndex);
        }

        float actionY = position.getY() + 78f;
        addButton(editorX, actionY, 126f, "New Draft", this::newTemplateDraft, HEADER);
        addButton(editorX + 136f, actionY, 142f, "Clone Selected",
                selected != null ? this::cloneSelectedTemplate : null,
                selected != null ? HEADER : MUTED);
        addButton(editorX + 288f, actionY, 126f, "Rename Only",
                selected != null && !builtIn ? this::renameSelectedTemplate : null,
                selected != null && !builtIn ? HEADER : MUTED);
        addButton(editorX + 424f, actionY, 154f, "Save as New",
                this::saveTemplateDraft, GOOD);
        addButton(editorX + 588f, actionY, 104f, "Delete",
                selected != null && !builtIn ? this::deleteSelectedTemplate : null,
                selected != null && !builtIn ? BAD : MUTED);

        if (designerFeedback != null) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, designerFeedback,
                    editorX, position.getY() + 62f,
                    designerSucceeded ? GOOD : BAD));
        }
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "Changing billet fields edits this draft only. Save as New creates a new stable template; "
                        + "no fielded team is silently refitted.",
                editorX, position.getY() + 34f, MUTED));
    }

    private void buildArrangementDesigner(float left, float top) {
        ensureArrangementDraft();
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "Compose Alpha, Bravo and Charlie from reusable fire-team templates. "
                        + "Saving is free; issuing still uses finite fleet stock.",
                left, top + 24f, VALUE));

        float libraryW = 270f;
        float editorX = left + libraryW + 24f;
        float right = position.getX() + position.getWidth() - PAD;
        float editorW = right - editorX;
        buildArrangementLibrary(left, top, libraryW);
        if (arrangementDraft == null) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "Create a fire-team template before composing a squad arrangement.",
                    editorX, top - 18f, MUTED));
            return;
        }

        SquadArrangement selected = roster.armory().squadArrangementById(
                selectedArrangementId);
        MarineSquad squad = roster.squadById(selectedSquadId);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "THREE-TEAM ARRANGEMENT", editorX, top - 12f, HEADER));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                squad != null && !squad.reserve()
                        ? "Target squad · " + squad.name()
                        : "Select a line squad in Loadouts before issuing",
                editorX + 290f, top - 12f,
                squad != null && !squad.reserve() ? GOOD : BAD));
        addButton(editorX + editorW - 86f, top - 44f, 38f, "<",
                () -> cycleArrangementTarget(-1), HEADER);
        addButton(editorX + editorW - 42f, top - 44f, 38f, ">",
                () -> cycleArrangementTarget(1), HEADER);

        float nameY = top - 62f;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "ARRANGEMENT NAME", editorX, nameY + 25f, MUTED));
        TextFieldWidget nameField = new TextFieldWidget(editorX + 190f, nameY,
                Math.min(360f, editorW - 190f), BUTTON_H,
                Fonts.ORBITRON_20, 32, "Arrangement name");
        nameField.setText(arrangementDraft.name);
        nameField.setOnChange(value -> arrangementDraft.name = value);
        addTextField(nameField);

        float rowTop = top - 116f;
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            buildArrangementTeamRow(editorX, rowTop - team * 72f, editorW, team);
        }

        if (selected != null) {
            buildArrangementTransactionPreview(editorX, top - 340f, editorW,
                    squad, selected);
        } else {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "Save this draft to preview its squad-wide inventory transaction.",
                    editorX, top - 350f, MUTED));
        }

        float actionY = position.getY() + 78f;
        addButton(editorX, actionY, 112f, "New Draft",
                this::newArrangementDraft, HEADER);
        addButton(editorX + 122f, actionY, 152f, "Capture Squad",
                squad != null && !squad.reserve()
                        ? this::captureSelectedSquadArrangement : null,
                squad != null && !squad.reserve() ? HEADER : MUTED);
        addButton(editorX + 284f, actionY, 122f, "Rename Only",
                selected != null ? this::renameSelectedArrangement : null,
                selected != null ? HEADER : MUTED);
        addButton(editorX + 416f, actionY, 130f, "Save as New",
                this::saveArrangementDraft, GOOD);
        addButton(editorX + 556f, actionY, 92f, "Delete",
                selected != null ? this::deleteSelectedArrangement : null,
                selected != null ? BAD : MUTED);
        SquadArrangementPreview preview = selected != null && squad != null
                ? roster.previewSquadArrangement(squad.id(), selected.id()) : null;
        addButton(editorX + 658f, actionY, Math.max(132f, editorW - 658f),
                squad != null ? "Issue to " + shortSquadName(squad) : "Select Squad",
                preview != null && preview.canApply()
                        ? () -> applySelectedArrangement(squad, selected) : null,
                preview != null && preview.canApply() ? VALUE : MUTED);

        if (arrangementFeedback != null) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, arrangementFeedback,
                    editorX, position.getY() + 58f,
                    arrangementSucceeded ? GOOD : BAD));
        }
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "Changing a team edits this draft only. Existing squads keep their three current assignments.",
                editorX, position.getY() + 34f, MUTED));
    }

    private void buildArrangementLibrary(float x, float top, float width) {
        List<SquadArrangement> arrangements = roster.armory().squadArrangements();
        int pageSize = 8;
        int pages = Math.max(1, (arrangements.size() + pageSize - 1) / pageSize);
        arrangementPage = Math.max(0, Math.min(arrangementPage, pages - 1));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "ARRANGEMENT LIBRARY", x, top - 12f, HEADER));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                arrangements.size() + " saved plans", x, top - 38f, MUTED));

        float y = top - 82f;
        int start = arrangementPage * pageSize;
        int end = Math.min(arrangements.size(), start + pageSize);
        for (int i = start; i < end; i++) {
            SquadArrangement arrangement = arrangements.get(i);
            boolean selected = arrangement.id().equals(selectedArrangementId);
            int fielded = roster.squadArrangementFieldedCount(arrangement.id());
            addButton(x, y, width,
                    (selected ? "> " : "  ") + arrangement.displayName()
                            + " · " + fielded + " fielded",
                    () -> selectArrangement(arrangement.id()),
                    selected ? VALUE : fielded > 0 ? GOOD : HEADER);
            y -= 42f;
        }
        if (arrangements.isEmpty()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "No saved arrangements yet.", x, y, MUTED));
        }
        if (pages > 1) {
            addButton(x, y - 4f, 96f, "Previous", arrangementPage > 0 ? () -> {
                arrangementPage--;
                rebuild();
            } : null, arrangementPage > 0 ? HEADER : MUTED);
            addButton(x + 106f, y - 4f, 120f,
                    "Next " + (arrangementPage + 1) + "/" + pages,
                    arrangementPage + 1 < pages ? () -> {
                        arrangementPage++;
                        rebuild();
                    } : null, arrangementPage + 1 < pages ? HEADER : MUTED);
        }
    }

    private void buildArrangementTeamRow(float x, float top, float width, int teamIndex) {
        FireTeamTemplateCard template = roster.armory().templateCardById(
                arrangementDraft.templateIds[teamIndex]);
        widgets.add(new PanelWidget(x, top - 50f, width, 60f));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                fireTeamName(teamIndex), x + 10f, top - 4f, HEADER));
        addButton(x + 142f, top - 44f, 40f, "<",
                () -> cycleArrangementTemplate(teamIndex, -1), HEADER);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                template != null ? template.displayName() : "Missing template",
                x + 198f, top - 4f, template != null ? VALUE : BAD));
        addButton(x + width - 50f, top - 44f, 40f, ">",
                () -> cycleArrangementTemplate(teamIndex, 1), HEADER);
    }

    private void buildArrangementTransactionPreview(float x, float top, float width,
                                                     MarineSquad squad,
                                                     SquadArrangement arrangement) {
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "SQUAD-WIDE ISSUE PREVIEW", x, top, HEADER));
        if (squad == null || squad.reserve()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "Select a line squad in Loadouts to calculate returns and issue.",
                    x, top - 28f, BAD));
            return;
        }
        SquadArrangementPreview preview = roster.previewSquadArrangement(
                squad.id(), arrangement.id());
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                preview.canApply() ? "READY · all three teams change together"
                        : templateMessage(preview.result()),
                x, top - 28f, preview.canApply() ? GOOD : BAD));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "FREE  +RETURN  /NEED", x + width - 156f, top, MUTED));
        float y = top - 58f;
        int shown = Math.min(5, preview.gear().size());
        for (int i = 0; i < shown; i++) {
            FireTeamGearDelta delta = preview.gear().get(i);
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    shortGearLabel(delta.label()), x + 10f, y,
                    delta.sufficient() ? HEADER : BAD));
            String free = delta.unlimited() ? "∞" : Integer.toString(delta.free());
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    free + "  +" + delta.returned() + "  /" + delta.required(),
                    x + width - 146f, y,
                    delta.sufficient() ? delta.netIssue() > 0 ? VALUE : GOOD : BAD));
            y -= 25f;
        }
        if (preview.gear().size() > shown) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "+" + (preview.gear().size() - shown) + " more catalog lines",
                    x + 10f, y, MUTED));
        }
    }

    private void buildDesignerLibrary(float x, float top, float width) {
        List<FireTeamTemplateCard> cards = roster.armory().templateCards();
        int pageSize = 8;
        int pages = Math.max(1, (cards.size() + pageSize - 1) / pageSize);
        designerTemplatePage = Math.max(0, Math.min(designerTemplatePage, pages - 1));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "TEMPLATE LIBRARY", x, top - 12f, HEADER));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                cards.size() + " designs", x, top - 38f, MUTED));

        float y = top - 82f;
        int start = designerTemplatePage * pageSize;
        int end = Math.min(cards.size(), start + pageSize);
        for (int i = start; i < end; i++) {
            FireTeamTemplateCard card = cards.get(i);
            boolean selected = card.id().equals(selectedDesignerTemplateId);
            boolean builtIn = FireTeamTemplateCards.isStarterId(card.id());
            FireTeamTemplateAvailability availability =
                    roster.fireTeamTemplateAvailability(card.id());
            addButton(x, y, width,
                    (selected ? "> " : "  ") + card.displayName()
                            + " · " + availability.fielded() + "F/"
                            + availability.readyToIssue() + "R",
                    () -> selectDesignerTemplate(card.id()),
                    selected ? VALUE : builtIn ? HEADER : GOOD);
            y -= 42f;
        }
        if (pages > 1) {
            addButton(x, y - 4f, 96f, "Previous", designerTemplatePage > 0 ? () -> {
                designerTemplatePage--;
                rebuild();
            } : null, designerTemplatePage > 0 ? HEADER : MUTED);
            addButton(x + 106f, y - 4f, 120f,
                    "Next " + (designerTemplatePage + 1) + "/" + pages,
                    designerTemplatePage + 1 < pages ? () -> {
                        designerTemplatePage++;
                        rebuild();
                    } : null, designerTemplatePage + 1 < pages ? HEADER : MUTED);
        }
    }

    private void buildDesignerBillet(float x, float top, float width, int index) {
        FireTeamBillet billet = templateDraft.billets[index];
        float panelY = top - 72f;
        widgets.add(new PanelWidget(x, panelY, width, 84f));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "BILLET " + (index + 1), x + 10f, top, HEADER));

        float gap = 8f;
        float roleW = Math.max(126f, width * 0.16f);
        float primaryW = Math.max(152f, width * 0.21f);
        float gradeW = Math.max(112f, width * 0.13f);
        float armorW = Math.max(170f, width * 0.23f);
        float secondaryW = Math.max(154f,
                width - roleW - primaryW - gradeW - armorW - 4f * gap - 20f);
        float fieldY = panelY + 12f;
        float fieldX = x + 10f;

        TextFieldWidget role = new TextFieldWidget(fieldX, fieldY, roleW, BUTTON_H,
                Fonts.ORBITRON_20, 24, "Billet role");
        role.setText(billet.name());
        role.setOnChange(value -> updateDraftBilletName(index, value));
        addTextField(role);
        fieldX += roleW + gap;
        addButton(fieldX, fieldY, primaryW,
                "Primary: " + billet.primary().displayName(),
                () -> cycleDraftPrimary(index), HEADER);
        fieldX += primaryW + gap;
        addButton(fieldX, fieldY, gradeW,
                billet.grade().displayName, () -> cycleDraftGrade(index), VALUE);
        fieldX += gradeW + gap;
        addButton(fieldX, fieldY, armorW,
                "Armor: " + shortArmorName(billet.armor()),
                () -> cycleDraftArmor(index), HEADER);
        fieldX += armorW + gap;
        addButton(fieldX, fieldY, secondaryW,
                billet.secondary() == null ? "Special: None" : "Special: Rockets",
                () -> cycleDraftSecondary(index), billet.secondary() == null ? MUTED : GOOD);
    }

    private void ensureDesignerDraft() {
        MarineArmory armory = roster != null ? roster.armory() : null;
        if (armory == null) return;
        FireTeamTemplateCard selected = armory.templateCardById(selectedDesignerTemplateId);
        if (selected == null && templateDraft == null && !armory.templateCards().isEmpty()) {
            selected = armory.templateCards().get(0);
            selectedDesignerTemplateId = selected.id();
        }
        if (templateDraft == null && selected != null) {
            templateDraft = TemplateDraft.from(selected);
        }
    }

    private void selectDesignerTemplate(String cardId) {
        FireTeamTemplateCard card = roster.armory().templateCardById(cardId);
        if (card == null) return;
        selectedDesignerTemplateId = card.id();
        templateDraft = TemplateDraft.from(card);
        designerFeedback = null;
        rebuild();
    }

    private void ensureArrangementDraft() {
        MarineArmory armory = roster != null ? roster.armory() : null;
        if (armory == null) return;
        SquadArrangement selected = armory.squadArrangementById(selectedArrangementId);
        if (selected == null && arrangementDraft == null
                && !armory.squadArrangements().isEmpty()) {
            selected = armory.squadArrangements().get(0);
            selectedArrangementId = selected.id();
        }
        if (arrangementDraft == null) {
            arrangementDraft = selected != null ? ArrangementDraft.from(selected)
                    : defaultArrangementDraft();
        }
    }

    private ArrangementDraft defaultArrangementDraft() {
        List<FireTeamTemplateCard> templates = roster.armory().templateCards();
        if (templates.isEmpty()) return null;
        String fallback = templates.get(0).id();
        String[] templateIds = {fallback, fallback, fallback};
        MarineSquad squad = roster.squadById(selectedSquadId);
        if (squad != null && !squad.reserve()) {
            for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
                String assigned = squad.teamTemplateCardId(team);
                if (roster.armory().templateCardById(assigned) != null) {
                    templateIds[team] = assigned;
                }
            }
        }
        String name = squad != null && !squad.reserve()
                ? squad.name() + " Arrangement" : "New Squad Arrangement";
        return new ArrangementDraft(name, templateIds);
    }

    private void selectArrangement(String arrangementId) {
        SquadArrangement arrangement = roster.armory().squadArrangementById(arrangementId);
        if (arrangement == null) return;
        selectedArrangementId = arrangement.id();
        arrangementDraft = ArrangementDraft.from(arrangement);
        arrangementFeedback = null;
        rebuild();
    }

    private void newArrangementDraft() {
        selectedArrangementId = null;
        arrangementDraft = defaultArrangementDraft();
        arrangementFeedback = arrangementDraft != null
                ? "Unsaved arrangement ready" : "No fire-team templates available";
        arrangementSucceeded = arrangementDraft != null;
        rebuild();
    }

    private void captureSelectedSquadArrangement() {
        MarineSquad squad = roster.squadById(selectedSquadId);
        if (squad == null || squad.reserve()) {
            arrangementSucceeded = false;
            arrangementFeedback = "Select a line squad in Loadouts first";
            rebuild();
            return;
        }
        String[] templateIds = new String[MarineSquad.TEAMS_PER_SQUAD];
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            templateIds[team] = squad.teamTemplateCardId(team);
            if (roster.armory().templateCardById(templateIds[team]) == null) {
                arrangementSucceeded = false;
                arrangementFeedback = "Assign a template to all three fire teams first";
                rebuild();
                return;
            }
        }
        selectedArrangementId = null;
        arrangementDraft = new ArrangementDraft(
                squad.name() + " Arrangement", templateIds);
        arrangementSucceeded = true;
        arrangementFeedback = "Current squad composition captured as an unsaved draft";
        rebuild();
    }

    private void cycleArrangementTemplate(int teamIndex, int direction) {
        List<FireTeamTemplateCard> templates = roster.armory().templateCards();
        if (arrangementDraft == null || templates.isEmpty()) return;
        int current = 0;
        for (int i = 0; i < templates.size(); i++) {
            if (templates.get(i).id().equals(arrangementDraft.templateIds[teamIndex])) {
                current = i;
                break;
            }
        }
        int next = Math.floorMod(current + direction, templates.size());
        arrangementDraft.templateIds[teamIndex] = templates.get(next).id();
        selectedArrangementId = null;
        arrangementFeedback = "Draft changed · save as new to keep it";
        arrangementSucceeded = true;
        rebuild();
    }

    private void cycleArrangementTarget(int direction) {
        List<MarineSquad> lineSquads = roster.squads().stream()
                .filter(squad -> !squad.reserve()).toList();
        if (lineSquads.isEmpty()) return;
        int current = 0;
        for (int i = 0; i < lineSquads.size(); i++) {
            if (lineSquads.get(i).id().equals(selectedSquadId)) {
                current = i;
                break;
            }
        }
        MarineSquad next = lineSquads.get(Math.floorMod(current + direction,
                lineSquads.size()));
        selectedSquadId = next.id();
        selectedTeamIndex = 0;
        selectedSoldierId = null;
        selectFirstSoldierIfNeeded();
        arrangementFeedback = null;
        rebuild();
    }

    private void renameSelectedArrangement() {
        boolean renamed = roster.armory().renameSquadArrangement(
                selectedArrangementId, arrangementDraft.name);
        arrangementSucceeded = renamed;
        arrangementFeedback = renamed ? "Arrangement renamed"
                : "Arrangement requires a name";
        rebuild();
    }

    private void saveArrangementDraft() {
        try {
            SquadArrangement saved = roster.armory().createSquadArrangement(
                    arrangementDraft.name, Arrays.asList(arrangementDraft.templateIds));
            selectedArrangementId = saved.id();
            arrangementDraft = ArrangementDraft.from(saved);
            arrangementSucceeded = true;
            arrangementFeedback = "New squad arrangement saved · stock not evaluated";
            moveArrangementPageTo(saved.id());
        } catch (IllegalArgumentException ex) {
            arrangementSucceeded = false;
            arrangementFeedback = "Arrangement needs a name and three available templates";
        }
        rebuild();
    }

    private void deleteSelectedArrangement() {
        boolean deleted = roster.armory().deleteSquadArrangement(selectedArrangementId);
        arrangementSucceeded = deleted;
        arrangementFeedback = deleted ? "Arrangement deleted · squads unchanged"
                : "Arrangement unavailable";
        selectedArrangementId = null;
        arrangementDraft = null;
        ensureArrangementDraft();
        rebuild();
    }

    private void applySelectedArrangement(MarineSquad squad,
                                          SquadArrangement arrangement) {
        FireTeamTemplateResult result = roster.applySquadArrangement(
                squad.id(), arrangement.id());
        arrangementSucceeded = result == FireTeamTemplateResult.APPLIED;
        arrangementFeedback = arrangementSucceeded
                ? arrangement.displayName() + " issued to " + squad.name()
                : templateMessage(result);
        templateFeedback = arrangementFeedback;
        templateSucceeded = arrangementSucceeded;
        rebuild();
    }

    private void moveArrangementPageTo(String arrangementId) {
        List<SquadArrangement> arrangements = roster.armory().squadArrangements();
        for (int i = 0; i < arrangements.size(); i++) {
            if (arrangements.get(i).id().equals(arrangementId)) {
                arrangementPage = i / 8;
                return;
            }
        }
    }

    private void newTemplateDraft() {
        FireTeamTemplateCard field = roster.armory().templateCardById(
                FireTeamTemplateCards.FIELD_ID);
        selectedDesignerTemplateId = null;
        templateDraft = field != null ? TemplateDraft.from(field)
                : TemplateDraft.standard();
        templateDraft.name = "New Template";
        designerFeedback = "Unsaved draft ready";
        designerSucceeded = true;
        rebuild();
    }

    private void cloneSelectedTemplate() {
        FireTeamTemplateCard clone = roster.armory().cloneTemplateCard(selectedDesignerTemplateId);
        if (clone == null) {
            designerSucceeded = false;
            designerFeedback = "Selected template is unavailable";
        } else {
            selectedDesignerTemplateId = clone.id();
            templateDraft = TemplateDraft.from(clone);
            designerSucceeded = true;
            designerFeedback = "Custom template cloned";
            moveDesignerPageTo(clone.id());
        }
        rebuild();
    }

    private void renameSelectedTemplate() {
        boolean renamed = roster.armory().renameTemplateCard(
                selectedDesignerTemplateId, templateDraft.name);
        designerSucceeded = renamed;
        designerFeedback = renamed ? "Custom template renamed"
                : "Built-in templates cannot be renamed";
        rebuild();
    }

    private void saveTemplateDraft() {
        try {
            FireTeamTemplateCard saved = roster.armory().createTemplateCard(
                    templateDraft.name, templateDraft.billetList());
            selectedDesignerTemplateId = saved.id();
            templateDraft = TemplateDraft.from(saved);
            designerSucceeded = true;
            designerFeedback = "New custom template saved · stock not evaluated";
            moveDesignerPageTo(saved.id());
        } catch (IllegalArgumentException ex) {
            designerSucceeded = false;
            designerFeedback = "Template requires a name and four complete billets";
        }
        rebuild();
    }

    private void deleteSelectedTemplate() {
        if (roster.isFireTeamTemplateReferenced(selectedDesignerTemplateId)) {
            designerSucceeded = false;
            designerFeedback = "Referenced template cannot be deleted · update squads and arrangements first";
            rebuild();
            return;
        }
        boolean deleted = roster.deleteFireTeamTemplate(selectedDesignerTemplateId);
        designerSucceeded = deleted;
        designerFeedback = deleted ? "Custom template deleted"
                : "Built-in template cannot be deleted";
        selectedDesignerTemplateId = null;
        templateDraft = null;
        ensureDesignerDraft();
        rebuild();
    }

    private void moveDesignerPageTo(String cardId) {
        List<FireTeamTemplateCard> cards = roster.armory().templateCards();
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i).id().equals(cardId)) {
                designerTemplatePage = i / 8;
                return;
            }
        }
    }

    private void updateDraftBilletName(int index, String value) {
        FireTeamBillet old = templateDraft.billets[index];
        templateDraft.billets[index] = new FireTeamBillet(value, old.primary(),
                old.grade(), old.secondary(), old.armor());
    }

    private void cycleDraftPrimary(int index) {
        FireTeamBillet old = templateDraft.billets[index];
        MarineWeapon[] values = MarineWeapon.values();
        MarineWeapon next = values[(old.primary().ordinal() + 1) % values.length];
        replaceDraftBillet(index, next, old.grade(), old.secondary(), old.armor());
    }

    private void cycleDraftGrade(int index) {
        FireTeamBillet old = templateDraft.billets[index];
        EquipmentGrade[] values = EquipmentGrade.values();
        EquipmentGrade next = values[(old.grade().ordinal() + 1) % values.length];
        replaceDraftBillet(index, old.primary(), next, old.secondary(), old.armor());
    }

    private void cycleDraftArmor(int index) {
        FireTeamBillet old = templateDraft.billets[index];
        MarineArmorPattern[] values = MarineArmorPattern.values();
        MarineArmorPattern next = values[(old.armor().ordinal() + 1) % values.length];
        replaceDraftBillet(index, old.primary(), old.grade(), old.secondary(), next);
    }

    private void cycleDraftSecondary(int index) {
        FireTeamBillet old = templateDraft.billets[index];
        MarineSecondary[] values = MarineSecondary.values();
        MarineSecondary next = old.secondary() == null ? values[0]
                : old.secondary().ordinal() + 1 < values.length
                        ? values[old.secondary().ordinal() + 1] : null;
        replaceDraftBillet(index, old.primary(), old.grade(), next, old.armor());
    }

    private void replaceDraftBillet(int index, MarineWeapon primary,
                                    EquipmentGrade grade, MarineSecondary secondary,
                                    MarineArmorPattern armor) {
        FireTeamBillet old = templateDraft.billets[index];
        templateDraft.billets[index] = new FireTeamBillet(
                old.name(), primary, grade, secondary, armor);
        rebuild();
    }

    private void addTextField(TextFieldWidget field) {
        textFields.add(field);
        widgets.add(field);
    }

    private static String shortArmorName(MarineArmorPattern armor) {
        return switch (armor) {
            case ARMORLESS -> "Fatigues";
            case CHARCOAL -> "Charcoal";
            case BLUE_SCOUT -> "Navy Scout";
            case RED_ELITE -> "Crimson Elite";
            case OUTLAW -> "Outlaw";
            case ARMY_GREEN -> "Army Green";
            case MILITIA -> "Militia";
        };
    }

    private void buildLoadoutRoster(float x, float top, float width) {
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "FORMATION", x, top - 14f, HEADER));
        int pageSize = 3;
        int pages = Math.max(1, (roster.squads().size() + pageSize - 1) / pageSize);
        int selectedIndex = 0;
        for (int i = 0; i < roster.squads().size(); i++) {
            if (roster.squads().get(i).id().equals(selectedSquadId)) {
                selectedIndex = i;
                break;
            }
        }
        if (selectedIndex < squadPage * pageSize
                || selectedIndex >= (squadPage + 1) * pageSize) {
            squadPage = selectedIndex / pageSize;
        }
        squadPage = Math.max(0, Math.min(squadPage, pages - 1));
        if (pages > 1) {
            addButton(x + width - 72f, top - 38f, 32f, "<", squadPage > 0 ? () -> {
                squadPage--;
                selectFirstSquadOnLoadoutPage(pageSize);
                rebuild();
            } : null, squadPage > 0 ? HEADER : MUTED);
            addButton(x + width - 36f, top - 38f, 32f, ">", squadPage + 1 < pages ? () -> {
                squadPage++;
                selectFirstSquadOnLoadoutPage(pageSize);
                rebuild();
            } : null, squadPage + 1 < pages ? HEADER : MUTED);
        }
        float y = top - 78f;
        int start = squadPage * pageSize;
        int end = Math.min(roster.squads().size(), start + pageSize);
        for (int i = start; i < end; i++) {
            MarineSquad squad = roster.squads().get(i);
            boolean selected = squad.id().equals(selectedSquadId);
            addButton(x, y, width, (selected ? "> " : "  ") + squad.name()
                    + "  " + roster.readyCount(squad), () -> {
                selectedSquadId = squad.id();
                selectedSoldierId = null;
                selectedTeamIndex = 0;
                selectFirstSoldierIfNeeded();
                loadoutFeedback = null;
                clearDoubleClick();
                rebuild();
            }, selected ? VALUE : HEADER);
            y -= 38f;
        }

        MarineSquad selectedSquad = roster.squadById(selectedSquadId);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "FIRE TEAMS", x, y - 8f, HEADER));
        y -= 54f;
        if (selectedSquad != null) {
            for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
                int teamIndex = team;
                boolean selected = selectedTeamIndex == team;
                String templateName = assignedTemplateName(selectedSquad, team);
                addButton(x, y, width,
                        (selected ? "> " : "  ") + fireTeamName(team)
                                + "  " + readyTeamMembers(selectedSquad, team)
                                + "/" + MarineSquad.TEAM_SIZE + " · " + templateName,
                        () -> {
                            selectedTeamIndex = teamIndex;
                            selectedSoldierId = null;
                            templateFeedback = null;
                            selectFirstSoldierIfNeeded();
                            loadoutFeedback = null;
                            clearDoubleClick();
                            rebuild();
                        }, selected ? VALUE : HEADER);
                y -= 38f;
            }

            widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                    "TEAM MEMBERS", x, y - 4f, HEADER));
            y -= 50f;
            for (String memberId : selectedSquad.teamMembers(selectedTeamIndex)) {
                MarineSoldier soldier = roster.soldierById(memberId);
                if (soldier == null || y < position.getY() + 136f) continue;
                boolean selected = soldier.id().equals(selectedSoldierId);
                addButton(x, y, width, (selected ? "> " : "  ") + soldier.name()
                        + " · " + statusLabel(soldier), () -> {
                    selectedSoldierId = soldier.id();
                    previewTemplateId = null;
                    loadoutFeedback = null;
                    clearDoubleClick();
                    rebuild();
                }, selected ? VALUE : soldier.status() == MarineSoldierStatus.ACTIVE
                        ? HEADER : MUTED);
                y -= 38f;
            }
        }

        if (selectedSquad != null && !selectedSquad.reserve()) {
            float presetY = position.getY() + 72f;
            List<FireTeamTemplateCard> cards = roster.armory().templateCards();
            int templatePageSize = 4;
            int templatePages = Math.max(1,
                    (cards.size() + templatePageSize - 1) / templatePageSize);
            templatePage = Math.max(0, Math.min(templatePage, templatePages - 1));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "FIRE-TEAM TEMPLATES · FIELD/READY",
                    x, presetY + 92f, HEADER));
            if (templatePages > 1) {
                addButton(x + width - 72f, presetY + 84f, 32f, "<",
                        templatePage > 0 ? () -> {
                            templatePage--;
                            rebuild();
                        } : null, templatePage > 0 ? HEADER : MUTED);
                addButton(x + width - 36f, presetY + 84f, 32f, ">",
                        templatePage + 1 < templatePages ? () -> {
                            templatePage++;
                            rebuild();
                        } : null, templatePage + 1 < templatePages ? HEADER : MUTED);
            }
            int i = 0;
            int templateStart = templatePage * templatePageSize;
            int templateEnd = Math.min(cards.size(), templateStart + templatePageSize);
            for (int templateIndex = templateStart;
                 templateIndex < templateEnd; templateIndex++) {
                FireTeamTemplateCard card = cards.get(templateIndex);
                float bx = x + (i % 2) * (width / 2f + 2f);
                float by = presetY + (1 - i / 2) * 38f;
                boolean assigned = card.id().equals(
                        selectedSquad.teamTemplateCardId(selectedTeamIndex));
                boolean previewed = card.id().equals(previewTemplateId);
                FireTeamTemplateAvailability availability =
                        roster.fireTeamTemplateAvailability(card.id());
                addButton(bx, by, width / 2f - 4f,
                        (previewed ? "> " : assigned ? "* " : "")
                                + shortTemplateName(card.displayName()) + " · "
                                + availability.fielded() + "/"
                                + availability.readyToIssue(), () -> {
                    previewTemplateId = card.id();
                    templateFeedback = null;
                    rebuild();
                }, previewed ? VALUE : assigned ? GOOD
                        : availability.recipesUnlocked() ? HEADER : MUTED);
                i++;
            }
            if (templateFeedback != null) {
                widgets.add(new LabelWidget(Fonts.ORBITRON_20, templateFeedback,
                        x, position.getY() + 58f, templateSucceeded ? GOOD : BAD));
            }
        }
    }

    private void selectFirstSquadOnLoadoutPage(int pageSize) {
        int index = squadPage * pageSize;
        if (index >= roster.squads().size()) return;
        selectedSquadId = roster.squads().get(index).id();
        selectedSoldierId = null;
        selectedTeamIndex = 0;
        selectFirstSoldierIfNeeded();
    }

    private void buildTemplateTransactionPreview(float x, float top, float width,
                                                 FireTeamTemplateCard card) {
        MarineSquad squad = roster.squadById(selectedSquadId);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "SELECTED TEMPLATE", x, top - 10f, HEADER));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                card.displayName() + " → " + fireTeamName(selectedTeamIndex),
                x, top - 38f, VALUE));
        FireTeamTemplateAvailability availability =
                roster.fireTeamTemplateAvailability(card.id());
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                availability.fielded() + " fielded · " + availability.readyToIssue()
                        + " additional complete kits ready",
                x, top - 64f, availability.recipesUnlocked() ? GOOD : BAD));

        if (squad == null || squad.reserve()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "Select a line fire team to preview this issue.",
                    x, top - 102f, BAD));
            return;
        }

        FireTeamRefitPreview preview = roster.previewFireTeamTemplate(
                squad.id(), selectedTeamIndex, card.id());
        float panelY = top - 404f;
        widgets.add(new PanelWidget(x, panelY, width, 320f));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "GEAR", x + 10f, top - 94f, MUTED));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "FREE  +RETURN  /NEED", x + width - 156f, top - 94f, MUTED));

        int maxRows = 10;
        float y = top - 124f;
        int shown = Math.min(maxRows, preview.gear().size());
        for (int i = 0; i < shown; i++) {
            FireTeamGearDelta delta = preview.gear().get(i);
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    shortGearLabel(delta.label()), x + 10f, y,
                    delta.sufficient() ? HEADER : BAD));
            String free = delta.unlimited() ? "∞" : Integer.toString(delta.free());
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    free + "  +" + delta.returned() + "  /" + delta.required(),
                    x + width - 146f, y,
                    delta.sufficient() ? delta.netIssue() > 0 ? VALUE : GOOD : BAD));
            y -= 26f;
        }
        if (preview.gear().size() > maxRows) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "+" + (preview.gear().size() - maxRows) + " more catalog lines",
                    x + 10f, y, MUTED));
        }

        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                preview.canApply() ? "READY TO ISSUE" : templateMessage(preview.result()),
                x, top - 432f, preview.canApply() ? GOOD : BAD));

        float issueY = top - 478f;
        float targetW = (width - 12f) / 3f;
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            int targetTeam = team;
            FireTeamRefitPreview targetPreview = roster.previewFireTeamTemplate(
                    squad.id(), team, card.id());
            boolean alreadyAssigned = card.id().equals(squad.teamTemplateCardId(team));
            addButton(x + team * (targetW + 6f), issueY, targetW,
                    (alreadyAssigned ? "Reissue " : "Issue ") + shortTeamName(team),
                    targetPreview.canApply()
                            ? () -> applyPreviewTemplateToTeam(squad, targetTeam, card) : null,
                    targetPreview.canApply() ? team == selectedTeamIndex ? VALUE : GOOD : MUTED);
        }

        float swapY = top - 520f;
        int swapButton = 0;
        for (int team = 0; team < MarineSquad.TEAMS_PER_SQUAD; team++) {
            if (team == selectedTeamIndex || squad.teamTemplateCardId(team) == null
                    || squad.teamTemplateCardId(selectedTeamIndex) == null) continue;
            int otherTeam = team;
            FireTeamRefitPreview swapPreview = roster.previewFireTeamTemplateSwap(
                    squad.id(), selectedTeamIndex, squad.id(), team);
            addButton(x + swapButton * 156f, swapY, 148f,
                    "Swap ↔ " + fireTeamName(team),
                    () -> swapSelectedTeamWith(squad, otherTeam),
                    swapPreview.canApply() ? HEADER : BAD);
            swapButton++;
        }
    }

    private void applyPreviewTemplateToTeam(MarineSquad squad, int teamIndex,
                                            FireTeamTemplateCard card) {
        FireTeamTemplateResult result = roster.applyFireTeamTemplate(
                squad.id(), teamIndex, card.id());
        templateSucceeded = result == FireTeamTemplateResult.APPLIED;
        templateFeedback = templateSucceeded
                ? card.displayName() + " issued to " + fireTeamName(teamIndex)
                : templateMessage(result);
        selectedTeamIndex = teamIndex;
        selectedSoldierId = null;
        selectFirstSoldierIfNeeded();
        rebuild();
    }

    private void swapSelectedTeamWith(MarineSquad squad, int otherTeamIndex) {
        int firstTeamIndex = selectedTeamIndex;
        FireTeamTemplateResult result = roster.swapFireTeamTemplates(
                squad.id(), firstTeamIndex, squad.id(), otherTeamIndex);
        templateSucceeded = result == FireTeamTemplateResult.APPLIED;
        templateFeedback = templateSucceeded
                ? fireTeamName(firstTeamIndex) + " ↔ " + fireTeamName(otherTeamIndex)
                        + " templates swapped"
                : templateMessage(result);
        if (templateSucceeded) {
            previewTemplateId = squad.teamTemplateCardId(firstTeamIndex);
        }
        selectedSoldierId = null;
        selectFirstSoldierIfNeeded();
        rebuild();
    }

    private void buildPaperDoll(float x, float top, float width) {
        FireTeamTemplateCard previewTemplate = roster.armory().templateCardById(
                previewTemplateId);
        if (previewTemplate != null) {
            buildTemplateTransactionPreview(x, top, width, previewTemplate);
            return;
        }
        MarineSoldier soldier = selectedSoldier();
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                soldier != null ? soldier.name().toUpperCase() : "NO MARINE SELECTED",
                x, top - 10f, HEADER));
        if (soldier == null) return;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                soldier.profile().shortLabel() + " · " + soldier.experienceXp() + " XP",
                x, top - 36f, VALUE));
        boolean editable = canEdit(soldier);

        float primaryY = top - 174f;
        float primaryH = 118f;
        widgets.add(new PanelWidget(x, primaryY, width, primaryH));
        widgets.add(new SpriteThumbWidget(weaponIcon(soldier.primary()),
                x + 10f, primaryY + 10f, 76f, primaryH - 20f));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, "PRIMARY",
                x + 96f, primaryY + primaryH - 10f, MUTED));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                soldier.primary().catalogName(soldier.primaryGrade()) + " · "
                        + soldier.primaryGrade().displayName,
                x + 96f, primaryY + primaryH - 40f, GOOD));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                soldier.primary() == MarineWeapon.FIELD_RIFLE
                        ? "Unlimited fleet issue" : soldier.primary().displayName(),
                x + 96f, primaryY + 30f, MUTED));
        boolean primaryCanReset = editable && (soldier.primary() != MarineWeapon.FIELD_RIFLE
                || soldier.primaryGrade() != EquipmentGrade.SERVICE);
        addButton(x + width - 31f, primaryY + primaryH - 31f, 24f, 24f, "X",
                primaryCanReset ? () -> resetPrimary(soldier) : null,
                primaryCanReset ? BAD : MUTED);

        float armorY = top - 386f;
        widgets.add(new PanelWidget(x + 36f, armorY, width - 72f, 190f));
        widgets.add(new SpriteThumbWidget(soldier.armor().iconPath,
                x + 54f, armorY + 38f, width - 108f, 122f));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "ARMOR · TIER " + soldier.armor().tierMark(), x + 48f, armorY + 174f, MUTED));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                soldier.armor().displayName, x + 48f, armorY + 30f, GOOD));
        boolean armorCanReset = editable && soldier.armor() != MarineArmorPattern.ARMORLESS;
        addButton(x + width - 67f, armorY + 159f, 24f, 24f, "X",
                armorCanReset ? () -> resetArmor(soldier) : null,
                armorCanReset ? BAD : MUTED);

        float secondaryY = top - 488f;
        widgets.add(new PanelWidget(x, secondaryY, width, 82f));
        if (soldier.secondary() != null) {
            widgets.add(new SpriteThumbWidget(secondaryIcon(soldier.secondary()),
                    x + 12f, secondaryY + 8f, 82f, 66f));
        }
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, "SECONDARY",
                x + 104f, secondaryY + 66f, MUTED));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                soldier.secondary() != null ? soldier.secondary().displayName() : "Empty slot",
                x + 104f, secondaryY + 36f,
                soldier.secondary() != null ? GOOD : MUTED));
        boolean secondaryCanRemove = editable && soldier.secondary() != null;
        addButton(x + width - 31f, secondaryY + 51f, 24f, 24f, "X",
                secondaryCanRemove ? () -> removeSecondary(soldier) : null,
                secondaryCanRemove ? BAD : MUTED);

        if (loadoutFeedback != null) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, loadoutFeedback,
                    x, secondaryY - 9f, loadoutSucceeded ? GOOD : BAD));
        }
        buildMarineReadout(x, secondaryY - 40f, width, soldier);
    }

    /** Persistent whole-kit summary; baseline is an FR-1 Rook in fatigues. */
    private void buildMarineReadout(float x, float top, float width, MarineSoldier soldier) {
        MarineWeapon baseWeapon = MarineWeapon.FIELD_RIFLE;
        EquipmentGrade baseGrade = EquipmentGrade.SERVICE;
        MarineArmorPattern baseArmor = MarineArmorPattern.ARMORLESS;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "MARINE PERFORMANCE", x, top, HEADER));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "BASELINE · FR-1 ROOK + FATIGUES", x, top - 25f, MUTED));

        float tableTop = top - 52f;
        float rowH = 22f;
        int rowCount = 9;
        widgets.add(new PanelWidget(x, tableTop - 24f - rowCount * rowH,
                width, 28f + rowCount * rowH));
        float baseRight = x + width * 0.47f;
        float kitRight = x + width * 0.70f;
        float changeX = x + width * 0.76f;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, "STAT", x + 8f, tableTop, MUTED));
        addRightAligned("BASE", baseRight, tableTop, MUTED);
        addRightAligned("KIT", kitRight, tableTop, HEADER);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "CHANGE", changeX, tableTop, MUTED));

        float y = tableTop - 27f;
        addReadoutLine("DPS",
                InfantryCombatStats.estimatedDps(baseWeapon, baseGrade, soldier.profile()),
                InfantryCombatStats.estimatedDps(soldier.primary(), soldier.primaryGrade(), soldier.profile()),
                ReadoutFormat.DECIMAL, x, width, y);
        y -= rowH;
        addReadoutLine("RANGE", InfantryCombatStats.range(baseWeapon, baseGrade),
                InfantryCombatStats.range(soldier.primary(), soldier.primaryGrade()),
                ReadoutFormat.INTEGER, x, width, y);
        float[] accuracyBands = { 0.20f, 0.60f, 1.00f };
        String[] accuracyLabels = { "ACC NEAR", "ACC MED", "ACC FAR" };
        for (int i = 0; i < accuracyBands.length; i++) {
            y -= rowH;
            addReadoutLine(accuracyLabels[i],
                    InfantryCombatStats.accuracyAtRangeFraction(
                            baseWeapon, baseGrade, soldier.profile(), accuracyBands[i]),
                    InfantryCombatStats.accuracyAtRangeFraction(
                            soldier.primary(), soldier.primaryGrade(), soldier.profile(), accuracyBands[i]),
                    ReadoutFormat.PERCENT, x, width, y);
        }
        y -= rowH;
        addReadoutLine("HEALTH", UnitType.MARINE.maxHp + baseArmor.bonusHp,
                UnitType.MARINE.maxHp + soldier.armor().bonusHp,
                ReadoutFormat.INTEGER, x, width, y);
        y -= rowH;
        addReadoutLine("BLOCK", baseArmor.damageReduction, soldier.armor().damageReduction,
                ReadoutFormat.PERCENT, x, width, y);
        y -= rowH;
        addReadoutLine("MOVE", UnitType.MARINE.moveSpeed * baseArmor.moveSpeedMult,
                UnitType.MARINE.moveSpeed * soldier.armor().moveSpeedMult,
                ReadoutFormat.DECIMAL, x, width, y);
        y -= rowH;
        addReadoutLine("EVASION", 1f - baseArmor.incomingAccuracyMult,
                1f - soldier.armor().incomingAccuracyMult,
                ReadoutFormat.PERCENT, x, width, y);
    }

    private void addReadoutLine(String label, float baseline, float current,
                                ReadoutFormat format, float x, float width, float y) {
        float baseRight = x + width * 0.47f;
        float kitRight = x + width * 0.70f;
        float changeX = x + width * 0.76f;
        float delta = current - baseline;
        Color deltaColor = delta > 0.0005f ? GOOD : delta < -0.0005f ? BAD : MUTED;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, label, x + 8f, y, MUTED));
        addRightAligned(formatReadout(baseline, format), baseRight, y, MUTED);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, "→", x + width * 0.51f, y, MUTED));
        addRightAligned(formatReadout(current, format), kitRight, y, VALUE);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                formatReadoutDelta(delta, format), changeX, y, deltaColor));
    }

    private void addRightAligned(String text, float right, float y, Color color) {
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, text,
                right - Fonts.ORBITRON_20.measureWidth(text), y, color));
    }

    private static String formatReadout(float value, ReadoutFormat format) {
        return switch (format) {
            case DECIMAL -> fmt(value);
            case INTEGER -> Integer.toString(Math.round(value));
            case PERCENT -> pct(value);
        };
    }

    private static String formatReadoutDelta(float delta, ReadoutFormat format) {
        if (Math.abs(delta) <= 0.0005f) return "=";
        return switch (format) {
            case DECIMAL -> String.format(java.util.Locale.ROOT, "%+.2f", delta);
            case INTEGER -> String.format(java.util.Locale.ROOT, "%+d", Math.round(delta));
            case PERCENT -> String.format(java.util.Locale.ROOT, "%+dpp", Math.round(delta * 100f));
        };
    }

    private void buildInventoryBrowser(float x, float top, float width) {
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, "INVENTORY", x, top - 10f, HEADER));
        float tabY = top - 76f;
        float tabW = (width - 8f) / 3f;
        addInventoryTab(x, tabY, tabW, InventoryTab.WEAPONS, "Weapons");
        addInventoryTab(x + tabW + 4f, tabY, tabW, InventoryTab.ARMOR, "Armor");
        addInventoryTab(x + 2f * (tabW + 4f), tabY, tabW, InventoryTab.SPECIAL, "Special");

        float listTop = top - 116f;
        float counterY = top - 88f;
        if (inventoryTab == InventoryTab.WEAPONS) {
            float familyY = top - 116f;
            float familyW = (width - 12f) / WeaponTab.values().length;
            for (int i = 0; i < WeaponTab.values().length; i++) {
                WeaponTab family = WeaponTab.values()[i];
                addWeaponTab(x + i * (familyW + 4f), familyY, familyW, family);
            }
            listTop = top - 164f;
            counterY = top - 136f;
        }

        int itemCount = inventoryItemCount();
        float rowH = 64f;
        float rowGap = 6f;
        float listBottom = position.getY() + 72f;
        int visibleRows = Math.max(1, (int) ((listTop - listBottom) / (rowH + rowGap)));
        int maxScroll = Math.max(0, itemCount - visibleRows);
        inventoryScroll = Math.max(0, Math.min(inventoryScroll, maxScroll));
        int end = Math.min(itemCount, inventoryScroll + visibleRows);
        String section = inventoryTab == InventoryTab.WEAPONS ? weaponTab.label.toUpperCase()
                : inventoryTab.name();
        String count = itemCount > 0
                ? "  " + (inventoryScroll + 1) + "-" + end + " / " + itemCount
                : "  · STANDARD ISSUE";
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                section + count + (maxScroll > 0 ? "  · wheel to scroll" : ""),
                x, counterY, MUTED));
        if (itemCount == 0) {
            buildServiceRifleBay(x, listTop - 116f, width);
            return;
        }
        widgets.add(new ScrollRegionWidget(x, listBottom, width, listTop - listBottom,
                delta -> scrollInventory(delta, visibleRows)));

        float y = listTop - rowH;
        for (int index = inventoryScroll; index < end; index++) {
            switch (inventoryTab) {
                case WEAPONS -> addWeaponInventoryRow(x, y, width, rowH, index);
                case ARMOR -> addArmorInventoryRow(x, y, width, rowH, index);
                case SPECIAL -> addSpecialInventoryRow(x, y, width, rowH, index);
            }
            y -= rowH + rowGap;
        }
    }

    private void addInventoryTab(float x, float y, float w, InventoryTab target, String label) {
        addButton(x, y, w, label, () -> {
            inventoryTab = target;
            inventoryScroll = 0;
            clearDoubleClick();
            rebuild();
        }, inventoryTab == target ? VALUE : HEADER);
    }

    private void addWeaponTab(float x, float y, float w, WeaponTab target) {
        addButton(x, y, w, target.label, () -> {
            weaponTab = target;
            browsedWeapon = target.weapon;
            browsedGrade = target == WeaponTab.RIFLE
                    ? EquipmentGrade.SERVICE : browsedGrade;
            inventoryScroll = 0;
            clearDoubleClick();
            rebuild();
        }, weaponTab == target ? VALUE : HEADER);
    }

    private void buildServiceRifleBay(float x, float y, float width) {
        MarineSoldier soldier = selectedSoldier();
        boolean installed = soldier != null && soldier.primary() == MarineWeapon.FIELD_RIFLE
                && soldier.primaryGrade() == EquipmentGrade.SERVICE;
        boolean canEquip = !installed && soldier != null && roster.canAllocatePrimary(
                soldier.id(), MarineWeapon.FIELD_RIFLE, EquipmentGrade.SERVICE);
        InventoryState state = inventoryState(true, installed, soldier, canEquip);
        widgets.add(new SelectableRowWidget(x, y, width, 104f, true, false,
                () -> selectWeaponRow(MarineWeapon.FIELD_RIFLE, EquipmentGrade.SERVICE, true)));
        widgets.add(new SpriteThumbWidget(weaponIcon(MarineWeapon.FIELD_RIFLE),
                x + 10f, y + 10f, 78f, 84f));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                MarineWeapon.FIELD_RIFLE.catalogName(EquipmentGrade.SERVICE),
                x + 100f, y + 78f, GOOD));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                inventoryStateLabel(state) + " · UNLIMITED", x + 100f, y + 50f,
                inventoryStateColor(state)));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                installed ? "Current fleet issue" : "Double-click to equip",
                x + 100f, y + 24f, MUTED));
    }

    private int inventoryItemCount() {
        return switch (inventoryTab) {
            case WEAPONS -> weaponTab == WeaponTab.RIFLE ? 0 : EquipmentGrade.values().length;
            case ARMOR -> MarineArmorPattern.values().length;
            case SPECIAL -> MarineSecondary.values().length;
        };
    }

    private void scrollInventory(int delta, int visibleRows) {
        int step = delta > 0 ? -1 : 1;
        int max = Math.max(0, inventoryItemCount() - visibleRows);
        int next = Math.max(0, Math.min(inventoryScroll + step, max));
        if (next == inventoryScroll) return;
        inventoryScroll = next;
        rebuild();
    }

    private void addWeaponInventoryRow(float x, float y, float w, float h, int index) {
        MarineWeapon weapon = weaponTab.weapon;
        EquipmentGrade grade = EquipmentGrade.values()[index];
        MarineArmory armory = roster.armory();
        MarineSoldier soldier = selectedSoldier();
        boolean unlocked = armory.isPrimaryUnlocked(weapon, grade);
        boolean selected = browsedWeapon == weapon && browsedGrade == grade;
        widgets.add(new SelectableRowWidget(x, y, w, h, selected, !unlocked, () -> {
            selectWeaponRow(weapon, grade, unlocked);
        }));
        widgets.add(new SpriteThumbWidget(weaponIcon(weapon), x + 8f, y + 6f, 54f, h - 12f));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                weapon.catalogName(grade) + " · " + grade.displayName,
                x + 72f, y + h - 9f, unlocked ? weapon.tracerColor() : MUTED));
        boolean installed = soldier != null && soldier.primary() == weapon
                && soldier.primaryGrade() == grade;
        boolean canEquip = !installed && soldier != null
                && roster.canAllocatePrimary(soldier.id(), weapon, grade);
        InventoryState state = inventoryState(unlocked, installed, soldier, canEquip);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                inventoryStateLabel(state) + (unlocked
                        ? "  · FLEET " + armory.ownedPrimary(weapon, grade) : ""),
                x + 72f, y + 27f, inventoryStateColor(state)));
        boolean canPrint = armory.canPrintPrimary(weapon, grade);
        addButton(x + w - 31f, y + 5f, 24f, 24f, "+", canPrint ? () -> {
            loadoutSucceeded = armory.printPrimary(weapon, grade);
            loadoutFeedback = loadoutSucceeded ? weapon.catalogName(grade) + " fabricated"
                    : "Insufficient materials";
            rebuild();
        } : null, canPrint ? VALUE : MUTED);
    }

    private void addArmorInventoryRow(float x, float y, float w, float h, int index) {
        MarineArmorPattern armor = sortedArmors()[index];
        MarineArmory armory = roster.armory();
        MarineSoldier soldier = selectedSoldier();
        boolean unlocked = armory.isArmorUnlocked(armor);
        boolean selected = browsedArmor == armor;
        widgets.add(new SelectableRowWidget(x, y, w, h, selected, !unlocked,
                () -> selectArmorRow(armor, unlocked)));
        widgets.add(new SpriteThumbWidget(armor.iconPath, x + 8f, y + 5f, 58f, h - 10f));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                armor.displayName + " · Tier " + armor.tierMark(),
                x + 76f, y + h - 9f, unlocked ? HEADER : MUTED));
        boolean installed = soldier != null && soldier.armor() == armor;
        boolean canEquip = !installed && soldier != null && roster.canAllocateArmor(soldier.id(), armor);
        InventoryState state = inventoryState(unlocked, installed, soldier, canEquip);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                inventoryStateLabel(state) + (unlocked
                        ? "  · FLEET " + armory.ownedArmor(armor) : ""),
                x + 76f, y + 27f, inventoryStateColor(state)));
        boolean canPrint = armory.canPrintArmor(armor);
        addButton(x + w - 31f, y + 5f, 24f, 24f, "+", canPrint ? () -> {
            loadoutSucceeded = armory.printArmor(armor);
            loadoutFeedback = loadoutSucceeded ? armor.displayName + " fabricated"
                    : "Insufficient materials";
            rebuild();
        } : null, canPrint ? VALUE : MUTED);
    }

    private void addSpecialInventoryRow(float x, float y, float w, float h, int index) {
        MarineSecondary secondary = MarineSecondary.values()[index];
        MarineArmory armory = roster.armory();
        MarineSoldier soldier = selectedSoldier();
        boolean unlocked = armory.isSecondaryUnlocked(secondary);
        boolean selected = browsedSecondary == secondary;
        widgets.add(new SelectableRowWidget(x, y, w, h, selected, !unlocked,
                () -> selectSpecialRow(secondary, unlocked)));
        widgets.add(new SpriteThumbWidget(secondaryIcon(secondary), x + 8f, y + 5f, 58f, h - 10f));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, secondary.displayName(),
                x + 76f, y + h - 9f, unlocked ? HEADER : MUTED));
        boolean installed = soldier != null && soldier.secondary() == secondary;
        boolean canEquip = !installed && soldier != null
                && roster.canAllocateSecondary(soldier.id(), secondary);
        InventoryState state = inventoryState(unlocked, installed, soldier, canEquip);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                inventoryStateLabel(state) + (unlocked
                        ? "  · FLEET " + armory.ownedSecondary(secondary) : ""),
                x + 76f, y + 27f, inventoryStateColor(state)));
        boolean canPrint = armory.canPrintSecondary(secondary);
        addButton(x + w - 31f, y + 5f, 24f, 24f, "+", canPrint ? () -> {
            loadoutSucceeded = armory.printSecondary(secondary);
            loadoutFeedback = loadoutSucceeded ? secondary.displayName() + " fabricated"
                    : "Insufficient materials";
            rebuild();
        } : null, canPrint ? VALUE : MUTED);
    }

    private void selectWeaponRow(MarineWeapon weapon, EquipmentGrade grade, boolean unlocked) {
        String key = weapon.name() + ":" + grade.name();
        boolean doubleClick = recordInventoryClick(key);
        browsedWeapon = weapon;
        browsedGrade = grade;
        loadoutFeedback = null;
        MarineSoldier soldier = selectedSoldier();
        if (doubleClick && unlocked && soldier != null
                && roster.canAllocatePrimary(soldier.id(), weapon, grade)) {
            equipPrimary(soldier, weapon, grade);
            return;
        }
        rebuild();
    }

    private void selectArmorRow(MarineArmorPattern armor, boolean unlocked) {
        boolean doubleClick = recordInventoryClick("armor:" + armor.name());
        browsedArmor = armor;
        loadoutFeedback = null;
        MarineSoldier soldier = selectedSoldier();
        if (doubleClick && unlocked && soldier != null
                && roster.canAllocateArmor(soldier.id(), armor)) {
            equipArmor(soldier, armor);
            return;
        }
        rebuild();
    }

    private void selectSpecialRow(MarineSecondary secondary, boolean unlocked) {
        boolean doubleClick = recordInventoryClick("special:" + secondary.name());
        browsedSecondary = secondary;
        loadoutFeedback = null;
        MarineSoldier soldier = selectedSoldier();
        if (doubleClick && unlocked && soldier != null
                && roster.canAllocateSecondary(soldier.id(), secondary)) {
            equipSecondary(soldier, secondary);
            return;
        }
        rebuild();
    }

    private boolean recordInventoryClick(String key) {
        long now = System.nanoTime();
        boolean doubleClick = key.equals(lastInventoryClickKey)
                && now - lastInventoryClickNanos <= 450_000_000L;
        lastInventoryClickKey = key;
        lastInventoryClickNanos = now;
        return doubleClick;
    }

    private void clearDoubleClick() {
        lastInventoryClickKey = null;
        lastInventoryClickNanos = 0L;
    }

    private InventoryState inventoryState(boolean unlocked, boolean installed,
                                          MarineSoldier soldier, boolean canEquip) {
        if (!unlocked) return InventoryState.LOCKED;
        if (installed) return InventoryState.INSTALLED;
        if (!canEdit(soldier)) return InventoryState.MARINE_UNAVAILABLE;
        return canEquip ? InventoryState.AVAILABLE : InventoryState.OUT_OF_STOCK;
    }

    private static String inventoryStateLabel(InventoryState state) {
        return switch (state) {
            case AVAILABLE -> "AVAILABLE";
            case OUT_OF_STOCK -> "OUT OF STOCK";
            case LOCKED -> "LOCKED";
            case INSTALLED -> "INSTALLED";
            case MARINE_UNAVAILABLE -> "UNAVAILABLE";
        };
    }

    private static Color inventoryStateColor(InventoryState state) {
        return switch (state) {
            case AVAILABLE -> GOOD;
            case OUT_OF_STOCK -> BAD;
            case INSTALLED -> VALUE;
            case LOCKED, MARINE_UNAVAILABLE -> MUTED;
        };
    }

    private static MarineArmorPattern[] sortedArmors() {
        return Arrays.stream(MarineArmorPattern.values())
                .sorted(Comparator.comparingInt((MarineArmorPattern armor) -> armor.tier)
                        .thenComparing(armor -> armor.displayName))
                .toArray(MarineArmorPattern[]::new);
    }

    private void buildItemDossier(float x, float top, float width) {
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, "ITEM DOSSIER", x, top - 10f, HEADER));
        switch (inventoryTab) {
            case WEAPONS -> buildWeaponDossier(x, top, width);
            case ARMOR -> buildArmorDossier(x, top, width);
            case SPECIAL -> buildSpecialDossier(x, top, width);
        }
    }

    private void buildWeaponDossier(float x, float top, float width) {
        MarineSoldier soldier = selectedSoldier();
        MarineWeapon weapon = browsedWeapon;
        EquipmentGrade grade = browsedGrade;
        boolean unlocked = roster.armory().isPrimaryUnlocked(weapon, grade);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, weapon.catalogName(grade),
                x + 170f, top - 48f, weapon.tracerColor()));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                weapon == MarineWeapon.FIELD_RIFLE ? "Unlimited fleet service issue"
                        : grade.displayName + " pattern · Tier " + grade.tierMark(),
                x + 170f, top - 76f, VALUE));
        widgets.add(new SpriteThumbWidget(weaponIcon(weapon), x, top - 260f, 150f, 150f));
        addWrappedText(weaponFlavor(weapon), x + 170f, top - 112f,
                Math.max(120f, width - 180f), MUTED, 5);
        addEquipPrimaryButton(x, top, width, soldier, weapon, grade, unlocked);

        if (soldier == null) return;
        float volley = InfantryCombatStats.volleyDamage(weapon, grade);
        float dps = InfantryCombatStats.estimatedDps(weapon, grade, soldier.profile());
        float range = InfantryCombatStats.range(weapon, grade);
        float labelX = x;
        float barX = x + 112f;
        float barW = Math.max(70f, Math.min(330f, width - 180f));
        float y = top - 368f;
        addStatRow("DAMAGE", fmt(volley), volley / maxVolleyDamage(), labelX, barX, y, barW, DAMAGE_BAR);
        y -= 28f;
        addStatRow("EST. DPS", fmt(dps), dps / maxEstimatedDps(soldier), labelX, barX, y, barW, DPS_BAR);
        y -= 28f;
        addStatRow("RANGE", Integer.toString(Math.round(range)), range / maxEffectiveRange(),
                labelX, barX, y, barW, RANGE_BAR);
        float[] fractions = { 0.20f, 0.60f, 1.00f };
        String[] labels = { "ACC NEAR", "ACC MED", "ACC FAR" };
        for (int i = 0; i < fractions.length; i++) {
            y -= 28f;
            float accuracy = InfantryCombatStats.accuracyAtRangeFraction(
                    weapon, grade, soldier.profile(), fractions[i]);
            addStatRow(labels[i], pct(accuracy), accuracy,
                    labelX, barX, y, barW, ACCURACY_BAR);
        }
    }

    private void buildArmorDossier(float x, float top, float width) {
        MarineSoldier soldier = selectedSoldier();
        MarineArmorPattern armor = browsedArmor;
        boolean unlocked = roster.armory().isArmorUnlocked(armor);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, armor.displayName,
                x + 190f, top - 48f, HEADER));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "Protection package · Tier " + armor.tierMark(),
                x + 190f, top - 76f, VALUE));
        widgets.add(new SpriteThumbWidget(armor.iconPath, x, top - 278f, 170f, 178f));
        addWrappedText(armorFlavor(armor), x + 190f, top - 112f,
                Math.max(120f, width - 200f), MUTED, 6);
        addEquipArmorButton(x, top, width, soldier, armor, unlocked);

        float labelX = x;
        float barX = x + 150f;
        float barW = Math.max(70f, Math.min(330f, width - 220f));
        float y = top - 380f;
        addStatRow("DAMAGE BLOCK", pct(armor.damageReduction),
                armor.damageReduction / armorLadderMax(p -> p.damageReduction),
                labelX, barX, y, barW, DAMAGE_BAR);
        y -= 30f;
        addStatRow("BONUS HEALTH", "+" + Math.round(armor.bonusHp),
                armor.bonusHp / armorLadderMax(p -> p.bonusHp),
                labelX, barX, y, barW, GOOD);
        y -= 30f;
        addStatRow("MOVE SPEED", pct(armor.moveSpeedMult),
                armor.moveSpeedMult / armorLadderMax(p -> p.moveSpeedMult),
                labelX, barX, y, barW, RANGE_BAR);
        y -= 30f;
        float evade = 1f - armor.incomingAccuracyMult;
        addStatRow("EVASION", "+" + pct(evade),
                evade / armorLadderMax(p -> 1f - p.incomingAccuracyMult),
                labelX, barX, y, barW, ACCURACY_BAR);
    }

    /**
     * Largest value of one armor stat across the whole pattern ladder, so a
     * dossier bar fills at the best available package instead of at a
     * hand-copied constant. The constants this replaced were authored against
     * the pre-S1 ladder and silently overflowed their bars when the tier gap
     * widened.
     */
    private static float armorLadderMax(ToFloat<MarineArmorPattern> stat) {
        float max = 0f;
        for (MarineArmorPattern pattern : MarineArmorPattern.values()) {
            max = Math.max(max, stat.of(pattern));
        }
        return max > 0f ? max : 1f;
    }

    /** One float field of {@code T}; lets {@link #armorLadderMax} take a field reference. */
    private interface ToFloat<T> {
        float of(T value);
    }

    private void buildSpecialDossier(float x, float top, float width) {
        MarineSoldier soldier = selectedSoldier();
        MarineSecondary secondary = browsedSecondary;
        boolean unlocked = roster.armory().isSecondaryUnlocked(secondary);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, secondary.displayName(),
                x + 190f, top - 48f, VALUE));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "Limited-ammunition support weapon", x + 190f, top - 76f, MUTED));
        widgets.add(new SpriteThumbWidget(secondaryIcon(secondary), x, top - 260f, 170f, 150f));
        addWrappedText(secondaryFlavor(secondary), x + 190f, top - 112f,
                Math.max(120f, width - 200f), MUTED, 6);
        addEquipSecondaryButton(x, top, width, soldier, secondary, unlocked);

        float labelX = x;
        float barX = x + 150f;
        float barW = Math.max(70f, Math.min(330f, width - 220f));
        float y = top - 368f;
        addStatRow("DAMAGE", fmt(secondary.damage()), secondary.damage() / 42f,
                labelX, barX, y, barW, DAMAGE_BAR);
        y -= 30f;
        addStatRow("RANGE", Integer.toString(Math.round(secondary.range())), secondary.range() / 38f,
                labelX, barX, y, barW, RANGE_BAR);
        y -= 30f;
        addStatRow("ACCURACY", pct(secondary.accuracy()), secondary.accuracy(),
                labelX, barX, y, barW, ACCURACY_BAR);
        y -= 30f;
        addStatRow("ANTI-ARMOR", fmt(secondary.vsTurretMult()) + "x",
                secondary.vsTurretMult() / 3.5f, labelX, barX, y, barW, DPS_BAR);
        y -= 30f;
        addStatRow("AMMUNITION", Integer.toString(secondary.startingAmmo()),
                secondary.startingAmmo() / 4f, labelX, barX, y, barW, VALUE);
    }

    private void buildSquadList(float x, float top, float bottom) {
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "SQUADS", x, top + 24f, HEADER));
        int pageSize = Math.max(3, (int) ((top - bottom - 44f) / SQUAD_ROW_H));
        int pages = Math.max(1, (roster.squads().size() + pageSize - 1) / pageSize);
        squadPage = Math.max(0, Math.min(squadPage, pages - 1));
        int start = squadPage * pageSize;
        int end = Math.min(roster.squads().size(), start + pageSize);
        float y = top - 12f;
        for (int i = start; i < end; i++) {
            MarineSquad squad = roster.squads().get(i);
            boolean selected = squad.id().equals(selectedSquadId);
            int unavailable = roster.squadMembers(squad).size() - roster.readyCount(squad);
            String label = (selected ? "> " : "  ") + squad.name()
                    + "  " + roster.readyCount(squad)
                    + (squad.reserve() ? " ready" : "/" + MarineSquad.CAPACITY)
                    + (unavailable > 0 ? "  +" + unavailable + " unavailable" : "");
            addButton(x, y - BUTTON_H + 6f, SQUAD_COL_W, label, () -> {
                selectedSquadId = squad.id();
                selectedSoldierId = null;
                selectedTeamIndex = 0;
                selectFirstSoldierIfNeeded();
                memberPage = 0;
                templateFeedback = null;
                rebuild();
            }, selected ? VALUE : HEADER);
            y -= SQUAD_ROW_H;
        }
        if (pages > 1) {
            addButton(x, bottom, 104f, "Prev", squadPage > 0 ? () -> {
                squadPage--;
                rebuild();
            } : null, squadPage > 0 ? HEADER : MUTED);
            addButton(x + 112f, bottom, 112f, "Next " + (squadPage + 1) + "/" + pages,
                    squadPage + 1 < pages ? () -> {
                        squadPage++;
                        rebuild();
                    } : null, squadPage + 1 < pages ? HEADER : MUTED);
        }
    }

    private void buildSelectedSquad(float x, float top, float bottom, float width) {
        MarineSquad squad = roster.squadById(selectedSquadId);
        if (squad == null) return;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                squad.reserve() ? "RESERVE POOL" : "SELECTED SQUAD",
                x, top + 24f, HEADER));
        if (!squad.reserve()) {
            TextFieldWidget renameField = new TextFieldWidget(x, top - 14f, 210f, BUTTON_H,
                    Fonts.ORBITRON_20, 22, "Squad name");
            renameField.setText(squad.name());
            renameField.setOnChange(value -> roster.renameSquad(squad.id(), value));
            addTextField(renameField);
        } else {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                    squad.name(), x, top - 2f, VALUE));
        }

        float actionX = x + 222f;
        int vacancies = roster.vacancies(squad);
        MarineSoldier readyReserve = roster.firstReadyReserve();
        boolean cargoAvailable = MarinePersonnelLogistics.availableRecruits() > 0;
        boolean canRecruit = !squad.stationed() && (squad.reserve() || vacancies > 0)
                && (cargoAvailable || (!squad.reserve() && readyReserve != null));
        String recruitLabel = squad.stationed() ? "Stationed Away"
                : squad.reserve() ? "Enlist (1)"
                : vacancies <= 0 ? "Fully Manned"
                : readyReserve != null ? "Assign Reserve" : "Enlist (1)";
        addButton(actionX, top - 14f, 138f,
                recruitLabel,
                canRecruit ? () -> {
                    if (!squad.reserve() && roster.firstReadyReserve() != null) {
                        roster.fillVacancyFromReserve(squad.id());
                    } else {
                        MarinePersonnelLogistics.enlist(roster, squad.id());
                    }
                    rebuild();
                } : null, canRecruit ? GOOD : MUTED);
        addButton(actionX + 148f, top - 14f, 130f, "New Squad", () -> {
            MarineSquad created = roster.createSquad();
            selectedSquadId = created.id();
            squadPage = Integer.MAX_VALUE;
            memberPage = 0;
            rebuild();
        }, HEADER);

        if (!squad.reserve()) {
            float commandY = top - 52f;
            buildHomeCommand(squad, x, commandY, width);
            buildSquadLeader(squad, x + width * 0.5f, commandY);
        }

        float rowTop = top - (squad.reserve() ? 58f : 96f);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "Marine / status          Development          Field kit",
                x, rowTop + 18f, HEADER));
        List<MarineSoldier> members = roster.squadMembers(squad);
        int pageSize = Math.max(1, (int) ((rowTop - bottom - 38f) / MEMBER_ROW_H));
        int pages = Math.max(1, (members.size() + pageSize - 1) / pageSize);
        memberPage = Math.max(0, Math.min(memberPage, pages - 1));
        int start = memberPage * pageSize;
        int end = Math.min(members.size(), start + pageSize);
        float rowY = rowTop - 16f;
        for (int i = start; i < end; i++) {
            addSoldierRow(members.get(i), x, rowY, width);
            rowY -= MEMBER_ROW_H;
        }
        if (members.isEmpty()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    squad.reserve() ? "No marines held in reserve."
                            : "Empty squad — reinforce or transfer personnel here.",
                    x, rowY, MUTED));
        }
        if (pages > 1) {
            addButton(x, bottom, 130f, "Prev Members", memberPage > 0 ? () -> {
                memberPage--;
                rebuild();
            } : null, memberPage > 0 ? HEADER : MUTED);
            addButton(x + 140f, bottom, 160f,
                    "Next " + (memberPage + 1) + "/" + pages,
                    memberPage + 1 < pages ? () -> {
                        memberPage++;
                        rebuild();
                    } : null, memberPage + 1 < pages ? HEADER : MUTED);
        }
    }

    /** Names the NCO actually leading the squad, beside the officer who commands it. */
    private void buildSquadLeader(MarineSquad squad, float x, float y) {
        MarineSoldier leader = roster.squadLeader(squad);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                leader != null
                        ? "Squad leader: " + leader.enlistedRank().displayName()
                                + " " + leader.name()
                        : "Squad leader: none fit for duty",
                x, y + 25f, leader != null ? VALUE : MUTED));
    }

    private void buildHomeCommand(MarineSquad squad, float x, float y, float width) {
        MarineCaptain current = roster.captainForSquad(squad.id());
        MarineCaptain next = roster.nextAssignableCaptain(squad.id());
        String command = current != null
                ? current.name() + " · " + current.rank().displayName()
                        + " · " + roster.squadsCommandedBy(current.id()).size()
                        + "/" + current.rank().squadCommandCap() + " squads"
                        + (current.status() == Status.ACTIVE
                                ? "" : " · " + current.status().name())
                : "Unassigned";
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "Home command: " + command, x, y + 25f,
                current == null ? MUTED
                        : current.status() == Status.ACTIVE
                                ? GOOD : BAD));

        float clearW = 96f;
        float assignW = 178f;
        float clearX = x + width - clearW;
        float assignX = clearX - GAP - assignW;
        String assignLabel = next != null
                ? (current == null ? "Assign → " : "Change → ") + shortCaptainName(next)
                : current == null ? "No Eligible Captain" : "No Alternate";
        addButton(assignX, y, assignW, assignLabel, next != null ? () -> {
            roster.assignCaptainToSquad(next.id(), squad.id());
            rebuild();
        } : null, next != null ? HEADER : MUTED);
        boolean canClear = current != null && !squad.stationed();
        addButton(clearX, y, clearW, "Unassign", canClear ? () -> {
            roster.clearSquadCaptain(squad.id());
            rebuild();
        } : null, canClear ? HEADER : MUTED);
    }

    private void addSoldierRow(MarineSoldier soldier, float x, float y, float w) {
        boolean ready = soldier.status() == MarineSoldierStatus.ACTIVE;
        String kit = soldier.primary().catalogName(soldier.primaryGrade())
                + " / " + soldier.armor().displayName
                + (soldier.secondary() != null ? " / Rockets" : "");
        String billet = soldier.enlistedRank().leads()
                ? soldier.enlistedRank().abbreviation() + " " : "";
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                billet + soldier.name() + "  " + statusLabel(soldier), x, y + 26f,
                ready ? HEADER : statusColor(soldier.status())));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                soldier.profile().shortLabel() + "  " + soldier.experienceXp() + " XP",
                x + w * 0.25f, y + 26f, VALUE));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                kit, x + w * 0.43f, y + 26f, ready ? GOOD : MUTED));
        if (!ready) return;

        MarineSquad current = roster.squadForSoldier(soldier.id());
        MarineSquad target = roster.nextTransferTarget(soldier.id());
        float moveW = 138f;
        float loadoutW = 108f;
        boolean reserve = current != null && current.reserve();
        boolean stationed = current != null && current.stationed();
        addButton(x + w - moveW, y + 4f, moveW,
                stationed ? "Stationed Away"
                        : reserve ? "Demobilize +1"
                        : target != null ? "Move → " + shortSquadName(target) : "No Vacancy",
                stationed ? null : reserve ? () -> {
                    MarinePersonnelLogistics.release(roster, soldier.id());
                    rebuild();
                } : target != null ? () -> {
                    roster.transferSoldier(soldier.id(), target.id());
                    rebuild();
                } : null, !stationed && (reserve || target != null) ? HEADER : MUTED);
        addButton(x + w - moveW - loadoutW - 8f, y + 4f, loadoutW, "Loadout",
                () -> {
                    selectedSoldierId = soldier.id();
                    previewTemplateId = null;
                    MarineSquad squad = roster.squadForSoldier(soldier.id());
                    if (squad != null) selectedTeamIndex = squad.teamIndexOf(soldier.id());
                    tab = Tab.LOADOUTS;
                    rebuild();
                }, HEADER);
    }

    private static String statusLabel(MarineSoldier soldier) {
        if (soldier.status() == MarineSoldierStatus.ACTIVE) return "RTD";
        if (soldier.status() == MarineSoldierStatus.WIA) {
            return "WIA · D" + (int) Math.ceil(soldier.unavailableUntilDay());
        }
        return soldier.status().name();
    }

    private static Color statusColor(MarineSoldierStatus status) {
        if (status == MarineSoldierStatus.WIA) return VALUE;
        if (status == MarineSoldierStatus.KIA) return BAD;
        return MUTED;
    }

    private static String shortSquadName(MarineSquad squad) {
        if (squad.reserve()) return "Reserve";
        String name = squad.name();
        return name.length() <= 10 ? name : name.substring(0, 10);
    }

    private static String shortTemplateName(String name) {
        if (name == null || name.isBlank()) return "Template";
        return name.length() <= 11 ? name : name.substring(0, 10) + "…";
    }

    private static String shortGearLabel(String label) {
        if (label == null || label.isBlank()) return "Gear";
        return label.length() <= 20 ? label : label.substring(0, 19) + "…";
    }

    private static String shortTeamName(int teamIndex) {
        return switch (teamIndex) {
            case 0 -> "A";
            case 1 -> "B";
            case 2 -> "C";
            default -> "?";
        };
    }

    private static String shortCaptainName(MarineCaptain captain) {
        String name = captain.name();
        if (name == null || name.isEmpty()) return "Captain";
        return name.length() <= 12 ? name : name.substring(0, 12);
    }

    private static String templateMessage(FireTeamTemplateResult result) {
        return switch (result) {
            case APPLIED -> "Template assigned · equipment issued";
            case INVALID_FIRE_TEAM -> "Select a line fire team";
            case TEAM_NOT_READY -> "Team needs four RTD marines";
            case STATIONED -> "Squad is stationed away";
            case UNKNOWN_TEMPLATE -> "Fire-team template unavailable";
            case UNKNOWN_ARRANGEMENT -> "Squad arrangement unavailable";
            case LOCKED_RECIPE -> "Recipe locked";
            case INSUFFICIENT_PRIMARIES -> "Not enough primary weapons";
            case INSUFFICIENT_ARMOR -> "Not enough armor";
            case INSUFFICIENT_SECONDARIES -> "Not enough support weapons";
        };
    }

    private String assignedTemplateName(MarineSquad squad, int teamIndex) {
        FireTeamTemplateCard card = roster.armory().templateCardById(
                squad.teamTemplateCardId(teamIndex));
        return card != null ? card.displayName() : "Unassigned";
    }

    private int readyTeamMembers(MarineSquad squad, int teamIndex) {
        int ready = 0;
        for (String memberId : squad.teamMembers(teamIndex)) {
            MarineSoldier soldier = roster.soldierById(memberId);
            if (soldier != null && soldier.status() == MarineSoldierStatus.ACTIVE) ready++;
        }
        return ready;
    }

    private static String fireTeamName(int teamIndex) {
        return switch (teamIndex) {
            case 0 -> "ALPHA";
            case 1 -> "BRAVO";
            case 2 -> "CHARLIE";
            default -> "TEAM";
        };
    }

    private void selectFirstSoldierIfNeeded() {
        if (roster == null) return;
        MarineSoldier selected = roster.soldierById(selectedSoldierId);
        MarineSquad squad = roster.squadById(selectedSquadId);
        if (selected != null && squad != null
                && squad.teamMembers(selectedTeamIndex).contains(selected.id())) return;
        selectedSoldierId = null;
        if (squad == null) return;
        List<String> members = squad.teamMembers(selectedTeamIndex);
        if (!members.isEmpty()) selectedSoldierId = members.get(0);
    }

    private MarineSoldier selectedSoldier() {
        return roster != null ? roster.soldierById(selectedSoldierId) : null;
    }

    private boolean canEdit(MarineSoldier soldier) {
        if (soldier == null || soldier.status() != MarineSoldierStatus.ACTIVE) return false;
        MarineSquad squad = roster.squadForSoldier(soldier.id());
        return squad != null && !squad.stationed();
    }

    private void resetPrimary(MarineSoldier soldier) {
        loadoutSucceeded = roster.allocatePrimary(soldier.id(),
                MarineWeapon.FIELD_RIFLE, EquipmentGrade.SERVICE);
        loadoutFeedback = loadoutSucceeded ? "Primary returned to standard field issue"
                : "No standard field rifle available";
        rebuild();
    }

    private void resetArmor(MarineSoldier soldier) {
        loadoutSucceeded = roster.allocateArmor(soldier.id(), MarineArmorPattern.ARMORLESS);
        loadoutFeedback = loadoutSucceeded ? "Armor returned to stores" : "No fatigues available";
        rebuild();
    }

    private void removeSecondary(MarineSoldier soldier) {
        loadoutSucceeded = roster.allocateSecondary(soldier.id(), null);
        loadoutFeedback = loadoutSucceeded ? "Secondary returned to stores" : "Secondary unavailable";
        rebuild();
    }

    private void addEquipPrimaryButton(float x, float top, float width, MarineSoldier soldier,
                                       MarineWeapon weapon, EquipmentGrade grade, boolean unlocked) {
        boolean installed = soldier != null && soldier.primary() == weapon
                && soldier.primaryGrade() == grade;
        boolean canEquip = !installed && unlocked && soldier != null
                && roster.canAllocatePrimary(soldier.id(), weapon, grade);
        String label = installed ? "INSTALLED" : !unlocked ? "LOCKED"
                : !canEdit(soldier) ? "MARINE UNAVAILABLE"
                : canEquip ? weapon == MarineWeapon.FIELD_RIFLE ? "RESTORE ISSUE" : "EQUIP"
                        : "OUT OF STOCK";
        addButton(x + Math.max(0f, width - 210f), top - 318f, Math.min(200f, width), label,
                canEquip ? () -> equipPrimary(soldier, weapon, grade) : null,
                canEquip ? GOOD : label.equals("OUT OF STOCK") ? BAD : MUTED);
    }

    private void equipPrimary(MarineSoldier soldier, MarineWeapon weapon, EquipmentGrade grade) {
        loadoutSucceeded = roster.allocatePrimary(soldier.id(), weapon, grade);
        loadoutFeedback = loadoutSucceeded ? weapon.catalogName(grade) + " equipped"
                : "No unassigned copy available";
        clearDoubleClick();
        rebuild();
    }

    private void addEquipArmorButton(float x, float top, float width, MarineSoldier soldier,
                                     MarineArmorPattern armor, boolean unlocked) {
        boolean installed = soldier != null && soldier.armor() == armor;
        boolean canEquip = !installed && unlocked && soldier != null
                && roster.canAllocateArmor(soldier.id(), armor);
        String label = installed ? "INSTALLED" : !unlocked ? "LOCKED"
                : !canEdit(soldier) ? "MARINE UNAVAILABLE"
                : canEquip ? "EQUIP" : "OUT OF STOCK";
        addButton(x + Math.max(0f, width - 210f), top - 330f, Math.min(200f, width), label,
                canEquip ? () -> equipArmor(soldier, armor) : null,
                canEquip ? GOOD : label.equals("OUT OF STOCK") ? BAD : MUTED);
    }

    private void equipArmor(MarineSoldier soldier, MarineArmorPattern armor) {
        loadoutSucceeded = roster.allocateArmor(soldier.id(), armor);
        loadoutFeedback = loadoutSucceeded ? armor.displayName + " equipped"
                : "No unassigned suit available";
        clearDoubleClick();
        rebuild();
    }

    private void addEquipSecondaryButton(float x, float top, float width, MarineSoldier soldier,
                                         MarineSecondary secondary, boolean unlocked) {
        boolean installed = soldier != null && soldier.secondary() == secondary;
        boolean canEquip = !installed && unlocked && soldier != null
                && roster.canAllocateSecondary(soldier.id(), secondary);
        String label = installed ? "INSTALLED" : !unlocked ? "LOCKED"
                : !canEdit(soldier) ? "MARINE UNAVAILABLE"
                : canEquip ? "EQUIP" : "OUT OF STOCK";
        addButton(x + Math.max(0f, width - 210f), top - 318f, Math.min(200f, width), label,
                canEquip ? () -> equipSecondary(soldier, secondary) : null,
                canEquip ? GOOD : label.equals("OUT OF STOCK") ? BAD : MUTED);
    }

    private void equipSecondary(MarineSoldier soldier, MarineSecondary secondary) {
        loadoutSucceeded = roster.allocateSecondary(soldier.id(), secondary);
        loadoutFeedback = loadoutSucceeded ? secondary.displayName() + " equipped"
                : "No unassigned special item available";
        clearDoubleClick();
        rebuild();
    }

    private void addWrappedText(String text, float x, float top, float width,
                                Color color, int maxLines) {
        List<String> lines = Fonts.ORBITRON_20.wrapLines(text, width);
        float lineH = Fonts.ORBITRON_20.getLineHeight();
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            String line = lines.get(i);
            if (i == maxLines - 1 && lines.size() > maxLines) line += "...";
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, line, x, top - i * lineH, color));
        }
    }

    private void addStatRow(String label, String value, float fill,
                            float labelX, float barX, float y, float barW, Color color) {
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, label, labelX, y + 14f, MUTED));
        widgets.add(new StatBarWidget(barX, y + 2f, barW, 9f, fill, color));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, value,
                barX + barW + 7f, y + 14f, VALUE));
    }

    private static float maxVolleyDamage() {
        float max = 1f;
        for (MarineWeapon weapon : playerWeapons()) {
            max = Math.max(max, InfantryCombatStats.volleyDamage(weapon, EquipmentGrade.MASTERWORK));
        }
        return max;
    }

    private static float maxEstimatedDps(MarineSoldier soldier) {
        float max = 1f;
        for (MarineWeapon weapon : playerWeapons()) {
            max = Math.max(max, InfantryCombatStats.estimatedDps(
                    weapon, EquipmentGrade.MASTERWORK, soldier.profile()));
        }
        return max;
    }

    private static float maxEffectiveRange() {
        float max = 1f;
        for (MarineWeapon weapon : playerWeapons()) {
            max = Math.max(max, InfantryCombatStats.range(weapon, EquipmentGrade.MASTERWORK));
        }
        return max;
    }

    private static MarineWeapon[] playerWeapons() {
        return new MarineWeapon[] { MarineWeapon.FIELD_RIFLE, MarineWeapon.PULSE_RIFLE,
                MarineWeapon.SMG, MarineWeapon.DMR };
    }

    private static String weaponIcon(MarineWeapon weapon) {
        if (weapon == null) return null;
        return switch (weapon) {
            case FIELD_RIFLE -> "graphics/battle/marine-modular-topdown/variants/weapons/rifle.png";
            case PULSE_RIFLE, DRONE_PULSE ->
                    "graphics/battle/marine-modular-topdown/variants/weapons/laser-gun.png";
            case SMG -> "graphics/battle/marine-modular-topdown/variants/weapons/smg.png";
            case DMR -> "graphics/battle/marine-modular-topdown/variants/weapons/dmr.png";
        };
    }

    private static String secondaryIcon(MarineSecondary secondary) {
        return secondary.armoryIconPath();
    }

    private static String weaponFlavor(MarineWeapon weapon) {
        return switch (weapon) {
            case FIELD_RIFLE -> "The FR-1 Rook is built for colonial stores, not parade decks. "
                    + "Its long action and indifferent barrel reward patient, close-range fire.";
            case PULSE_RIFLE -> "The PLS-series Lancer is fleet boarding doctrine in one rugged energy arm: a controlled "
                    + "three-pulse burst, forgiving handling, and enough reach for most compartments.";
            case SMG -> "The LMG-series Rattler is a compact saturation weapon for door teams and maintenance corridors. "
                    + "It owns the near room, but its grouping dissolves rapidly across open ground.";
            case DMR -> "The RG-series Longbow is a magnetic marksman's rifle tuned for deliberate shots through long lanes. "
                    + "Slow cycling is the price of exceptional reach and punishing impact.";
            case DRONE_PULSE -> "A lightweight autonomous pulse package not issued to line marines.";
        };
    }

    private static String armorFlavor(MarineArmorPattern armor) {
        return switch (armor) {
            case ARMORLESS -> "Void-rated fatigues and a web harness. Almost no protection, but nothing "
                    + "impedes a marine sprinting between cover.";
            case CHARCOAL -> "Fleet-standard composite plates over a sealed pressure layer. A dependable "
                    + "balance of trauma protection, endurance, and boarding mobility.";
            case BLUE_SCOUT -> "Low-mass naval reconnaissance plates with a narrow silhouette. Scout teams "
                    + "trade stopping power for speed and the best evasion profile in the armory.";
            case RED_ELITE -> "A crimson assault shell reserved for breach leaders. Dense overlapping plates "
                    + "absorb brutal punishment, though the wearer moves with deliberate weight.";
            case OUTLAW -> "Recovered frontier plate cut down and re-strapped by shipboard artificers. "
                    + "Quick, surprisingly resilient, and never quite regulation.";
            case ARMY_GREEN -> "A reinforced surface-warfare harness built to stay upright under sustained fire. "
                    + "Its extra layers favor protection over rapid repositioning.";
            case MILITIA -> "Standardized local-defense plates refurbished for fleet use. Modest protection "
                    + "without a meaningful mobility penalty.";
        };
    }

    private static String secondaryFlavor(MarineSecondary secondary) {
        return switch (secondary) {
            case ROCKET_LAUNCHER -> "An Annihilator-pattern disposable tube cluster. Fire teams carry it for "
                    + "hardened emplacements and emergency wall breaching; the blast does not distinguish friend from foe.";
            case ANTI_MATERIEL_RIFLE -> "The Breachlight braces a magnetic heavy round through armor seams "
                    + "without endangering friendlies nearby. It carries four shots and cannot breach walls.";
        };
    }

    private static String fmt(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String pct(float value) {
        return Math.round(value * 100f) + "%";
    }

    private static final class TemplateDraft {
        private String name;
        private final FireTeamBillet[] billets;

        private TemplateDraft(String name, FireTeamBillet[] billets) {
            this.name = name;
            this.billets = billets;
        }

        private static TemplateDraft from(FireTeamTemplateCard card) {
            FireTeamBillet[] copies = new FireTeamBillet[MarineSquad.TEAM_SIZE];
            for (int i = 0; i < copies.length; i++) {
                FireTeamBillet source = card.billet(i);
                copies[i] = new FireTeamBillet(source.name(), source.primary(),
                        source.grade(), source.secondary(), source.armor());
            }
            return new TemplateDraft(card.displayName(), copies);
        }

        private static TemplateDraft standard() {
            FireTeamBillet[] billets = new FireTeamBillet[MarineSquad.TEAM_SIZE];
            for (int i = 0; i < billets.length; i++) {
                billets[i] = new FireTeamBillet(i == 0 ? "Team Leader" : "Rifleman",
                        MarineWeapon.FIELD_RIFLE, EquipmentGrade.SERVICE,
                        null, MarineArmorPattern.ARMORLESS);
            }
            return new TemplateDraft("New Template", billets);
        }

        private List<FireTeamBillet> billetList() {
            return List.of(billets);
        }
    }

    private static final class ArrangementDraft {
        private String name;
        private final String[] templateIds;

        private ArrangementDraft(String name, String[] templateIds) {
            this.name = name;
            this.templateIds = Arrays.copyOf(templateIds, templateIds.length);
        }

        private static ArrangementDraft from(SquadArrangement arrangement) {
            return new ArrangementDraft(arrangement.displayName(),
                    arrangement.templateIds().toArray(String[]::new));
        }
    }

    private void addButton(float x, float y, float w, String text,
                           Runnable action, Color color) {
        addButton(x, y, w, BUTTON_H, text, action, color);
    }

    private void addButton(float x, float y, float w, float h, String text,
                           Runnable action, Color color) {
        widgets.add(new ButtonWidget(x, y, w, h, action));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, text,
                x + 8f, y + h - 6f, color));
    }

    @Override public void advance(float dt) { widgets.advance(dt); }
    @Override public void render(float alphaMult) { widgets.render(alphaMult); }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (events != null) {
            for (InputEventAPI event : events) {
                if (!event.isLMBDownEvent()) continue;
                TextFieldWidget clicked = null;
                for (TextFieldWidget field : textFields) {
                    if (field.contains(event.getX(), event.getY())) clicked = field;
                }
                for (TextFieldWidget field : textFields) {
                    field.setFocused(field == clicked);
                }
            }
        }
        for (TextFieldWidget field : textFields) {
            if (field.isFocused()) {
                field.routeKeys(events);
                break;
            }
        }
        widgets.processInput(events);
    }
}
