package com.dillon.starsectormarines.marine;

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
        Path(String label, String weapons, String armor, String description) {
            this.label = label; this.weapons = weapons; this.armor = armor; this.description = description;
        }
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
        if (path == Path.STEADY_LINE) {
            armory.unlockPrimary(WeaponRegistry.SQUAD_AUTOMATIC_ID, EquipmentGrade.MILSPEC);
        } else {
            armory.unlockPrimary(WeaponRegistry.PULSE_RIFLE_ID, EquipmentGrade.MILSPEC);
            armory.unlockArmor(MarineArmorPattern.BLUE_SCOUT);
        }
        // Plans are reusable intent. The unchosen plan still requires its technology,
        // acquired through ordinary market, patron, recovery, or collection rewards.
        for (Path plan : Path.values()) {
            armory.ensureWeaponDoctrine(plan.weapons, "Wayline / " + plan.label, weapons(plan));
        }
        chosenPath = path;
        claimed = true;
        return true;
    }

    public static List<SquadWeaponIssue> weapons(Path path) {
        List<SquadWeaponIssue> issues = new ArrayList<>();
        for (int i = 0; i < MarineSquad.CAPACITY; i++) {
            boolean upgraded = path == Path.STEADY_LINE ? i % 4 == 3 : i % 4 < 2;
            String primary = !upgraded ? WeaponRegistry.STARTER_PRIMARY_ID
                    : path == Path.STEADY_LINE ? WeaponRegistry.SQUAD_AUTOMATIC_ID : WeaponRegistry.PULSE_RIFLE_ID;
            issues.add(new SquadWeaponIssue(upgraded ? path == Path.STEADY_LINE ? "Automatic rifleman" : "Pulse rifleman" : "Rifleman",
                    primary, upgraded ? EquipmentGrade.MILSPEC : EquipmentGrade.SERVICE, null));
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
        SquadArmorDoctrine armor = ArmorIssueResolver.resolve(SquadEquipmentDoctrines.armorPlanById(path.armor),
                pattern -> roster.armory().ownsArmorTemplate(pattern.id())
                        || path == Path.FORWARD_SCREEN && MarineArmorPattern.BLUE_SCOUT.id.equals(pattern.id()));
        List<SquadWeaponIssue> weapons = weapons(path);
        List<SquadEquipmentBillet> billets = new ArrayList<>();
        for (int i = 0; i < MarineSquad.CAPACITY; i++) {
            SquadWeaponIssue weapon = weapons.get(i);
            billets.add(new SquadEquipmentBillet(weapon.role(), weapon.primaryId(), weapon.grade(),
                    weapon.specialEquipmentId(), armor.issueId(i)));
        }
        return new SquadEquipmentPreview(squad.stationed() ? SquadEquipmentResult.STATIONED
                : claimed ? SquadEquipmentResult.MISSING_TEMPLATE : SquadEquipmentResult.APPLIED, billets);
    }

    public static boolean technologyKnown(MarineRoster roster, Path path) {
        if (path == null) return false;
        return path == Path.STEADY_LINE
                ? roster.armory().ownsPrimaryTemplate(WeaponRegistry.SQUAD_AUTOMATIC_ID, EquipmentGrade.MILSPEC)
                : roster.armory().ownsPrimaryTemplate(WeaponRegistry.PULSE_RIFLE_ID, EquipmentGrade.MILSPEC)
                    && roster.armory().ownsArmorTemplate(MarineArmorPattern.BLUE_SCOUT);
    }

    public SquadEquipmentResult issue(MarineRoster roster, String squadId, Path path, EquipmentIssueResources resources) {
        if (!available(roster) || !claimed || path == null) return SquadEquipmentResult.INVALID_SQUAD;
        if (!technologyKnown(roster, path)) return SquadEquipmentResult.MISSING_TEMPLATE;
        SquadEquipmentResult result = roster.applySquadEquipment(squadId, path.weapons, path.armor, resources);
        if (result == SquadEquipmentResult.APPLIED) issuedSquadId = squadId;
        return result;
    }
}
