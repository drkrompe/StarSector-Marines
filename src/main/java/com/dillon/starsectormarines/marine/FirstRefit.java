package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Saved, optional technology transfer followed by free Machine Shop fitting. */
public final class FirstRefit implements Serializable {
    public enum Path {
        STEADY_LINE("Steady Line", "wayline:steady-line", SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_ARMOR,
                "Learn Milspec Stalwart automatics. Three gunners anchor nine riflemen. Protection uses the armor specs you know."),
        FORWARD_SCREEN("Forward Screen", "wayline:forward-screen", SquadEquipmentDoctrines.LEAGUE_MOBILE_ARMOR,
                "Learn Milspec Lancer pulse rifles and Janus scout suits. Six pulse rifles and five scout billets trade line protection for eyes and mobility.");
        public final String label, weapons, armor, description;
        public String label() { return Strings.get("openingRefitPath" + name()); }
        public String description() { return Strings.get("openingRefitPathDescription" + name()); }
        Path(String label, String weapons, String armor, String description) {
            this.label = label; this.weapons = weapons; this.armor = armor; this.description = description;
        }
    }

    /**
     * The name a claimed plan's weapon definition is saved under. It stays English so
     * every save holds the same value; {@link SquadWeaponDoctrine#displayName()}
     * recognises it and shows the translated name.
     */
    static String savedDoctrineName(Path path) {
        return "Wayline / " + path.label;
    }

    public static boolean isWeaponPlan(String id) {
        for (Path path : Path.values()) if (path.weapons.equals(id)) return true;
        return false;
    }

    private boolean claimed;
    // Null with claimed=true is the old, already-awarded two-path bundle. Never revoke it.
    private Path chosenPath;
    public Path chosenPath() { return chosenPath; }
    private String issuedSquadId;
    public boolean claimed() { return claimed; }
    public boolean complete() { return issuedSquadId != null; }
    public String issuedSquadId() { return issuedSquadId; }
    public static boolean available(MarineRoster roster) {
        return roster != null && roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId)
                && !roster.firstRefit().complete();
    }

    /** Explicit, once-only technology choice. Never changes kit or grants cargo. */
    public boolean claim(MarineRoster roster, Path path) {
        if (!available(roster) || claimed || path == null) return false;
        MarineArmory armory = roster.armory();
        // Both paths' weapon cards are registered; only the chosen pair is collected.
        // The other stays a card to find through markets, patrons and recovery.
        registerWeaponCards(armory);
        chosenPath = path;
        claimed = true;
        learnCards(armory, path);
        return true;
    }

    /** Registers every path's weapon card with the armory, without collecting any of them. */
    public static void registerWeaponCards(MarineArmory armory) {
        for (Path plan : Path.values()) {
            armory.ensureWeaponDoctrine(plan.weapons, savedDoctrineName(plan), weapons(plan));
        }
    }

    public static List<SquadWeaponIssue> weapons(Path path) {
        List<SquadWeaponIssue> issues = new ArrayList<>();
        for (int i = 0; i < MarineSquad.CAPACITY; i++) {
            boolean upgraded = path == Path.STEADY_LINE ? i % 4 == 3 : i % 4 < 2;
            String primary = !upgraded ? WeaponRegistry.STARTER_PRIMARY_ID
                    : path == Path.STEADY_LINE ? WeaponRegistry.SQUAD_AUTOMATIC_ID : WeaponRegistry.PULSE_RIFLE_ID;
            issues.add(new SquadWeaponIssue(upgraded ? path == Path.STEADY_LINE ? "Automatic Rifleman" : "Pulse Rifleman" : BilletRoles.RIFLEMAN,
                    primary, upgraded ? EquipmentGrade.MILSPEC : EquipmentGrade.SURPLUS, null));
        }
        return List.copyOf(issues);
    }

    /** Non-mutating offer preview: includes only this path's potential transfer. */
    public SquadEquipmentPreview preview(MarineRoster roster, String squadId, Path path) {
        MarineSquad squad = roster.squadById(squadId);
        if (path == null || squad == null || squad.reserve()) {
            return new SquadEquipmentPreview(SquadEquipmentResult.INVALID_SQUAD, List.of());
        }
        if (claimed && technologyKnown(roster, path)) return roster.previewSquadEquipment(squadId, path.weapons, path.armor);
        SquadArmorDoctrine armor = ArmorIssueResolver.issueSheet(SquadEquipmentDoctrines.armorPlanById(path.armor));
        List<SquadWeaponIssue> weapons = weapons(path);
        List<SquadEquipmentBillet> billets = new ArrayList<>();
        for (int i = 0; i < MarineSquad.CAPACITY; i++) {
            SquadWeaponIssue weapon = weapons.get(i);
            billets.add(new SquadEquipmentBillet(weapon.role(), weapon.primaryId(), weapon.grade(),
                    weapon.specialEquipmentId(), armor.issueId(i)));
        }
        return new SquadEquipmentPreview(squad.stationed() ? SquadEquipmentResult.STATIONED
                : claimed ? SquadEquipmentResult.UNKNOWN_CARD : SquadEquipmentResult.APPLIED, billets);
    }

    public static boolean technologyKnown(MarineRoster roster, Path path) {
        return technologyKnown(roster.armory(), path);
    }

    static boolean technologyKnown(MarineArmory armory, Path path) {
        return path != null && armory.knowsWeaponCard(path.weapons) && armory.knowsArmorPlan(path.armor);
    }

    /** The refit hands over its formation whole: the path's weapon card and tactic sheet. */
    private static void learnCards(MarineArmory armory, Path path) {
        armory.learnLoadoutCard(new LoadoutCard(LoadoutCard.Kind.WEAPON, path.weapons));
        armory.acquireArmorPlan(path.armor);
    }

    /** Repairs a saved claim's cards without fitting equipment. */
    void repairCardKnowledge(MarineArmory armory) {
        if (!claimed) return;
        for (Path path : Path.values()) {
            if (chosenPath == null || chosenPath == path) learnCards(armory, path);
        }
    }

    public SquadEquipmentResult issue(MarineRoster roster, String squadId, Path path, EquipmentIssueResources resources) {
        if (!available(roster) || !claimed || path == null) return SquadEquipmentResult.INVALID_SQUAD;
        if (!technologyKnown(roster, path)) return SquadEquipmentResult.UNKNOWN_CARD;
        SquadEquipmentResult result = roster.applySquadEquipment(squadId, path.weapons, path.armor, resources);
        if (result == SquadEquipmentResult.APPLIED) issuedSquadId = squadId;
        return result;
    }
}
