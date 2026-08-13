package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;
import com.dillon.starsectormarines.marine.MarineArmory;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.MarinePersonnelLogistics;
import com.dillon.starsectormarines.marine.SquadEquipmentPreset;
import com.dillon.starsectormarines.marine.SquadPresetResult;
import com.dillon.starsectormarines.marine.Status;
import com.dillon.starsectormarines.ops.detachment.PersonnelReadiness;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.SpriteThumbWidget;
import com.dillon.starsectormarines.ui.StatBarWidget;
import com.dillon.starsectormarines.ui.TextFieldWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.Color;
import java.util.Collections;
import java.util.List;

/** Fabrication plus squad-centric persistent personnel management. */
public final class ArmoryScreen implements Screen {

    private enum Tab { PERSONNEL, LOADOUTS }

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
    private Tab tab = Tab.PERSONNEL;
    private int squadPage;
    private int memberPage;
    private TextFieldWidget renameField;
    private String presetFeedback;
    private boolean presetSucceeded;
    private String loadoutFeedback;
    private boolean loadoutSucceeded;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        MarineRosterScript script = MarineRosterScript.getInstance();
        roster = script != null ? script.roster() : null;
        if (roster != null) {
            roster.bootstrapInitialComplement(10);
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
        renameField = null;
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
        addButton(left, tabY, 154f, "Personnel",
                () -> { tab = Tab.PERSONNEL; rebuild(); },
                tab == Tab.PERSONNEL ? VALUE : HEADER);
        addButton(left + 164f, tabY, 210f, "Equipment Loadouts",
                () -> { tab = Tab.LOADOUTS; selectFirstSoldierIfNeeded(); rebuild(); },
                tab == Tab.LOADOUTS ? VALUE : HEADER);

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

        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "Organize fireteams, personnel, command assignments and reserves.",
                left + 394f, tabY + BUTTON_H - 6f, MUTED));
        float managementTop = top - 112f;
        float managementBottom = position.getY() + PAD + BUTTON_H + 12f;
        buildSquadList(left, managementTop, managementBottom);
        buildSelectedSquad(left + SQUAD_COL_W + GAP, managementTop, managementBottom,
                position.getWidth() - 2f * PAD - SQUAD_COL_W - GAP);
    }

    /** Equipment-only workspace: formation browser, paper-doll slots and item grid. */
    private void buildLoadouts(float left, float top) {
        MarineArmory armory = roster.armory();
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "Parts & materials: " + armory.fabricationMaterials()
                        + "    Victories: " + armory.victories()
                        + "    High-risk: " + armory.highRiskVictories()
                        + "    Select to equip · + fabricates one",
                left, top + 24f, VALUE));

        float rosterW = 236f;
        float paperX = left + rosterW + GAP;
        float paperW = 318f;
        float inventoryX = paperX + paperW + 18f;
        float inventoryW = position.getX() + position.getWidth() - PAD - inventoryX;
        buildLoadoutRoster(left, top, rosterW);
        buildPaperDoll(paperX, top, paperW);
        buildEquipmentInventory(inventoryX, top, inventoryW);
    }

    private void buildLoadoutRoster(float x, float top, float width) {
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "FORMATION", x, top - 14f, HEADER));
        int pageSize = 4;
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
        float y = top - 58f;
        int start = squadPage * pageSize;
        int end = Math.min(roster.squads().size(), start + pageSize);
        for (int i = start; i < end; i++) {
            MarineSquad squad = roster.squads().get(i);
            boolean selected = squad.id().equals(selectedSquadId);
            addButton(x, y, width, (selected ? "> " : "  ") + squad.name()
                    + "  " + roster.readyCount(squad), () -> {
                selectedSquadId = squad.id();
                selectedSoldierId = null;
                selectFirstSoldierIfNeeded();
                loadoutFeedback = null;
                rebuild();
            }, selected ? VALUE : HEADER);
            y -= 38f;
        }

        MarineSquad selectedSquad = roster.squadById(selectedSquadId);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "MARINES", x, y - 8f, HEADER));
        y -= 50f;
        if (selectedSquad != null) {
            for (MarineSoldier soldier : roster.squadMembers(selectedSquad)) {
                if (y < position.getY() + 136f) break;
                boolean selected = soldier.id().equals(selectedSoldierId);
                addButton(x, y, width, (selected ? "> " : "  ") + soldier.name()
                        + " · " + statusLabel(soldier), () -> {
                    selectedSoldierId = soldier.id();
                    loadoutFeedback = null;
                    rebuild();
                }, selected ? VALUE : soldier.status() == MarineSoldierStatus.ACTIVE
                        ? HEADER : MUTED);
                y -= 38f;
            }
        }

        if (selectedSquad != null && !selectedSquad.reserve()) {
            float presetY = position.getY() + 72f;
            widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                    "FIRETEAM PRESET", x, presetY + 92f, HEADER));
            int i = 0;
            for (SquadEquipmentPreset preset : SquadEquipmentPreset.values()) {
                float bx = x + (i % 2) * (width / 2f + 2f);
                float by = presetY + (1 - i / 2) * 38f;
                addButton(bx, by, width / 2f - 4f, preset.displayName, () -> {
                    SquadPresetResult result = roster.applySquadPreset(selectedSquad.id(), preset);
                    presetSucceeded = result == SquadPresetResult.APPLIED;
                    presetFeedback = presetMessage(result);
                    rebuild();
                }, HEADER);
                i++;
            }
            if (presetFeedback != null) {
                widgets.add(new LabelWidget(Fonts.ORBITRON_20, presetFeedback,
                        x, position.getY() + 58f, presetSucceeded ? GOOD : BAD));
            }
        }
    }

    private void selectFirstSquadOnLoadoutPage(int pageSize) {
        int index = squadPage * pageSize;
        if (index >= roster.squads().size()) return;
        selectedSquadId = roster.squads().get(index).id();
        selectedSoldierId = null;
        selectFirstSoldierIfNeeded();
    }

    private void buildPaperDoll(float x, float top, float width) {
        MarineSoldier soldier = selectedSoldier();
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                soldier != null ? soldier.name().toUpperCase() : "NO MARINE SELECTED",
                x, top - 10f, HEADER));
        if (soldier == null) return;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                soldier.profile().shortLabel() + " · " + soldier.experienceXp() + " XP",
                x, top - 36f, VALUE));
        boolean editable = canEdit(soldier);

        float primaryY = top - 220f;
        float primaryH = 164f;
        addButton(x, primaryY, width, primaryH, "", editable ? () -> {
            loadoutSucceeded = roster.cyclePrimary(soldier.id());
            loadoutFeedback = loadoutSucceeded ? "Primary cycled" : "No available primary";
            rebuild();
        } : null, editable ? HEADER : MUTED);
        widgets.add(new SpriteThumbWidget(weaponIcon(soldier.primary()),
                x + 10f, primaryY + 72f, 72f, 80f));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, "PRIMARY",
                x + 92f, primaryY + primaryH - 10f, MUTED));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                soldier.primary().displayName + " · " + soldier.primaryGrade().displayName
                        + " [" + soldier.primaryGrade().tierMark() + "]",
                x + 92f, primaryY + primaryH - 36f, GOOD));

        MarineWeapon weapon = soldier.primary();
        EquipmentGrade grade = soldier.primaryGrade();
        float volley = InfantryCombatStats.volleyDamage(weapon, grade);
        float dps = InfantryCombatStats.estimatedDps(weapon, grade, soldier.profile());
        float effectiveRange = InfantryCombatStats.range(weapon, grade);
        float statLabelX = x + 92f;
        float barX = x + 144f;
        float barW = Math.max(56f, width - 206f);
        addStatRow("DMG", fmt(volley), volley / maxVolleyDamage(),
                statLabelX, barX, primaryY + 64f, barW, DAMAGE_BAR);
        addStatRow("DPS", fmt(dps), dps / maxEstimatedDps(soldier),
                statLabelX, barX, primaryY + 44f, barW, DPS_BAR);
        addStatRow("RNG", Integer.toString(Math.round(effectiveRange)),
                effectiveRange / maxEffectiveRange(),
                statLabelX, barX, primaryY + 24f, barW, RANGE_BAR);
        addAccuracyBands(x + 12f, primaryY + 7f, width - 24f, weapon, grade, soldier);

        float armorY = top - 430f;
        addButton(x + 36f, armorY, width - 72f, 190f, "", editable ? () -> {
            loadoutSucceeded = roster.cycleArmor(soldier.id());
            loadoutFeedback = loadoutSucceeded ? "Armor cycled" : "No available armor";
            rebuild();
        } : null, editable ? HEADER : MUTED);
        widgets.add(new SpriteThumbWidget(soldier.armor().iconPath,
                x + 54f, armorY + 38f, width - 108f, 122f));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "ARMOR · TIER " + soldier.armor().tierMark(), x + 48f, armorY + 174f, MUTED));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                soldier.armor().displayName, x + 48f, armorY + 30f, GOOD));

        float secondaryY = top - 532f;
        addButton(x, secondaryY, width, 82f, "", editable ? () -> {
            MarineSecondary next = soldier.secondary() == null
                    ? MarineSecondary.ROCKET_LAUNCHER : null;
            loadoutSucceeded = roster.allocateSecondary(soldier.id(), next);
            loadoutFeedback = loadoutSucceeded
                    ? next == null ? "Secondary stowed" : "Secondary equipped"
                    : "No launcher available";
            rebuild();
        } : null, editable ? HEADER : MUTED);
        if (soldier.secondary() != null) {
            widgets.add(new SpriteThumbWidget(soldier.secondary().aimSpritePath,
                    x + 12f, secondaryY + 8f, 82f, 66f));
        }
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, "SECONDARY",
                x + 104f, secondaryY + 66f, MUTED));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                soldier.secondary() != null ? soldier.secondary().displayName : "Empty slot",
                x + 104f, secondaryY + 36f,
                soldier.secondary() != null ? GOOD : MUTED));

        if (loadoutFeedback != null) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, loadoutFeedback,
                    x, position.getY() + 58f, loadoutSucceeded ? GOOD : BAD));
        }
    }

    private void buildEquipmentInventory(float x, float top, float width) {
        MarineSoldier soldier = selectedSoldier();
        MarineArmory armory = roster.armory();
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "WEAPON RACK", x, top - 10f, HEADER));
        MarineWeapon[] weapons = playerWeapons();
        EquipmentGrade[] grades = EquipmentGrade.values();
        float cellGap = 6f;
        float cellW = Math.max(86f, (width - 3f * cellGap) / 4f);
        float cellH = 66f;
        float y = top - 96f;
        for (int row = 0; row < weapons.length; row++) {
            MarineWeapon weapon = weapons[row];
            for (int col = 0; col < grades.length; col++) {
                EquipmentGrade grade = grades[col];
                float bx = x + col * (cellW + cellGap);
                addPrimaryCell(bx, y, cellW, cellH, soldier, weapon, grade, armory);
            }
            y -= cellH + cellGap;
        }

        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "ARMOR VAULT", x, y + 18f, HEADER));
        y -= 88f;
        int col = 0;
        for (MarineArmorPattern armor : MarineArmorPattern.values()) {
            float bx = x + (col % 4) * (cellW + cellGap);
            float by = y - (col / 4) * 82f;
            addArmorCell(bx, by, cellW, 76f, soldier, armor, armory);
            col++;
        }

        float secondaryY = position.getY() + 70f;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "SPECIAL ISSUE", x, secondaryY + 94f, HEADER));
        boolean unlocked = armory.isSecondaryUnlocked(MarineSecondary.ROCKET_LAUNCHER);
        addButton(x, secondaryY, Math.min(width, 300f), 64f,
                "Launcher [" + armory.ownedSecondary(MarineSecondary.ROCKET_LAUNCHER) + "]",
                canEdit(soldier) && unlocked ? () -> equipSecondary(soldier) : null,
                unlocked ? HEADER : MUTED);
        widgets.add(new SpriteThumbWidget(MarineSecondary.ROCKET_LAUNCHER.aimSpritePath,
                x + 8f, secondaryY + 6f, 56f, 52f));
        boolean canPrint = armory.canPrintSecondary(MarineSecondary.ROCKET_LAUNCHER);
        addButton(x + Math.min(width, 300f) - 30f, secondaryY + 5f, 24f, "+",
                canPrint ? () -> {
                    loadoutSucceeded = armory.printSecondary(MarineSecondary.ROCKET_LAUNCHER);
                    loadoutFeedback = loadoutSucceeded ? "Launcher fabricated" : "Insufficient materials";
                    rebuild();
                } : null, canPrint ? VALUE : MUTED);
    }

    private void addPrimaryCell(float x, float y, float w, float h, MarineSoldier soldier,
                                MarineWeapon weapon, EquipmentGrade grade, MarineArmory armory) {
        boolean unlocked = armory.isPrimaryUnlocked(weapon, grade);
        addButton(x, y, w, h, "", canEdit(soldier) && unlocked ? () -> {
            loadoutSucceeded = roster.allocatePrimary(soldier.id(), weapon, grade);
            loadoutFeedback = loadoutSucceeded ? weapon.displayName + " equipped"
                    : "No unassigned copy available";
            rebuild();
        } : null, unlocked ? HEADER : MUTED);
        widgets.add(new SpriteThumbWidget(weaponIcon(weapon), x + 4f, y + 4f, 42f, h - 8f));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                shortWeapon(weapon) + "-" + grade.tierMark(), x + 44f, y + h - 10f,
                unlocked ? weapon.tracerColor : MUTED));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                unlocked ? "[" + armory.ownedPrimary(weapon, grade) + "]" : "LOCK",
                x + 48f, y + 25f, unlocked ? VALUE : MUTED));
        boolean canPrint = armory.canPrintPrimary(weapon, grade);
        addButton(x + w - 27f, y + 4f, 23f, 23f, "+", canPrint ? () -> {
            loadoutSucceeded = armory.printPrimary(weapon, grade);
            loadoutFeedback = loadoutSucceeded ? weapon.displayName + " fabricated"
                    : "Insufficient materials";
            rebuild();
        } : null, canPrint ? VALUE : MUTED);
    }

    private void addArmorCell(float x, float y, float w, float h, MarineSoldier soldier,
                              MarineArmorPattern armor, MarineArmory armory) {
        boolean unlocked = armory.isArmorUnlocked(armor);
        addButton(x, y, w, h, "", canEdit(soldier) && unlocked ? () -> {
            loadoutSucceeded = roster.allocateArmor(soldier.id(), armor);
            loadoutFeedback = loadoutSucceeded ? armor.displayName + " equipped"
                    : "No unassigned suit available";
            rebuild();
        } : null, unlocked ? HEADER : MUTED);
        widgets.add(new SpriteThumbWidget(armor.iconPath, x + 4f, y + 20f, w - 8f, h - 24f));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                "T" + armor.tierMark() + " [" + armory.ownedArmor(armor) + "]",
                x + 6f, y + h - 6f, unlocked ? VALUE : MUTED));
        boolean canPrint = armory.canPrintArmor(armor);
        addButton(x + w - 27f, y + 4f, 23f, 23f, "+", canPrint ? () -> {
            loadoutSucceeded = armory.printArmor(armor);
            loadoutFeedback = loadoutSucceeded ? armor.displayName + " fabricated"
                    : "Insufficient materials";
            rebuild();
        } : null, canPrint ? VALUE : MUTED);
    }

    private void equipSecondary(MarineSoldier soldier) {
        loadoutSucceeded = roster.allocateSecondary(soldier.id(), MarineSecondary.ROCKET_LAUNCHER);
        loadoutFeedback = loadoutSucceeded ? "Launcher equipped" : "No unassigned launcher available";
        rebuild();
    }

    private void buildSquadList(float x, float top, float bottom) {
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                "FIRETEAMS", x, top + 24f, HEADER));
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
                selectFirstSoldierIfNeeded();
                memberPage = 0;
                presetFeedback = null;
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
                squad.reserve() ? "RESERVE POOL" : "SELECTED FIRETEAM",
                x, top + 24f, HEADER));
        if (!squad.reserve()) {
            renameField = new TextFieldWidget(x, top - 14f, 210f, BUTTON_H,
                    Fonts.ORBITRON_20, 22, "Fireteam name");
            renameField.setText(squad.name());
            renameField.setOnChange(value -> roster.renameSquad(squad.id(), value));
            widgets.add(renameField);
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
        addButton(actionX + 148f, top - 14f, 130f, "New Fireteam", () -> {
            MarineSquad created = roster.createFireteam();
            selectedSquadId = created.id();
            squadPage = Integer.MAX_VALUE;
            memberPage = 0;
            rebuild();
        }, HEADER);

        if (!squad.reserve()) {
            float commandY = top - 52f;
            buildHomeCommand(squad, x, commandY, width);

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
                            : "Empty fireteam — reinforce or transfer personnel here.",
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

    private void buildHomeCommand(MarineSquad squad, float x, float y, float width) {
        MarineCaptain current = roster.captainForSquad(squad.id());
        MarineCaptain next = roster.nextAssignableCaptain(squad.id());
        String command = current != null
                ? current.name() + " · " + current.rank().displayName()
                        + " · " + roster.squadsCommandedBy(current.id()).size()
                        + "/" + current.rank().fireteamCap() + " teams"
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
        String kit = soldier.primary().displayName + "-" + soldier.primaryGrade().tierMark()
                + " / " + soldier.armor().displayName
                + (soldier.secondary() != null ? " / Rockets" : "");
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                soldier.name() + "  " + statusLabel(soldier), x, y + 26f,
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

    private static String shortCaptainName(MarineCaptain captain) {
        String name = captain.name();
        if (name == null || name.isEmpty()) return "Captain";
        return name.length() <= 12 ? name : name.substring(0, 12);
    }

    private static String presetMessage(SquadPresetResult result) {
        return switch (result) {
            case APPLIED -> "Issued to all RTD personnel";
            case STATIONED -> "Fireteam is stationed away";
            case NO_READY_PERSONNEL -> "No RTD personnel";
            case LOCKED_RECIPE -> "Recipe locked";
            case INSUFFICIENT_WEAPONS -> "Not enough weapons";
            case INSUFFICIENT_ARMOR -> "Not enough armor";
        };
    }

    private void selectFirstSoldierIfNeeded() {
        if (roster == null) return;
        MarineSoldier selected = roster.soldierById(selectedSoldierId);
        MarineSquad squad = roster.squadById(selectedSquadId);
        if (selected != null && squad != null
                && roster.squadMembers(squad).contains(selected)) return;
        selectedSoldierId = null;
        if (squad == null) return;
        List<MarineSoldier> members = roster.squadMembers(squad);
        if (!members.isEmpty()) selectedSoldierId = members.get(0).id();
    }

    private MarineSoldier selectedSoldier() {
        return roster != null ? roster.soldierById(selectedSoldierId) : null;
    }

    private boolean canEdit(MarineSoldier soldier) {
        if (soldier == null || soldier.status() != MarineSoldierStatus.ACTIVE) return false;
        MarineSquad squad = roster.squadForSoldier(soldier.id());
        return squad != null && !squad.stationed();
    }

    private void addStatRow(String label, String value, float fill,
                            float labelX, float barX, float y, float barW, Color color) {
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, label, labelX, y + 14f, MUTED));
        widgets.add(new StatBarWidget(barX, y + 2f, barW, 9f, fill, color));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, value,
                barX + barW + 7f, y + 14f, VALUE));
    }

    private void addAccuracyBands(float x, float y, float width, MarineWeapon weapon,
                                  EquipmentGrade grade, MarineSoldier soldier) {
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, "ACC", x, y + 15f, MUTED));
        float groupX = x + 48f;
        float gap = 6f;
        float bandW = (width - 48f - 2f * gap) / 3f;
        float[] fractions = { 0.20f, 0.60f, 1.00f };
        String[] names = { "N", "M", "MAX" };
        for (int i = 0; i < fractions.length; i++) {
            float accuracy = InfantryCombatStats.accuracyAtRangeFraction(
                    weapon, grade, soldier.profile(), fractions[i]);
            float bx = groupX + i * (bandW + gap);
            widgets.add(new StatBarWidget(bx, y + 1f, bandW, 8f, accuracy, ACCURACY_BAR));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    names[i] + " " + pct(accuracy), bx, y + 15f, GOOD));
        }
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

    private static String shortWeapon(MarineWeapon weapon) {
        return switch (weapon) {
            case FIELD_RIFLE -> "FLD";
            case PULSE_RIFLE -> "PLS";
            case SMG -> "SMG";
            case DMR -> "DMR";
            case DRONE_PULSE -> "DRN";
        };
    }

    private static String fmt(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String pct(float value) {
        return Math.round(value * 100f) + "%";
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
        if (renameField != null) renameField.routeKeys(events);
        widgets.processInput(events);
    }
}
