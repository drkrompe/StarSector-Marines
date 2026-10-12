package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Saved, optional first issue lesson. Templates are a reward; actual issue spends fleet cargo. */
public final class FirstRefit implements Serializable {
    public enum Path {
        STEADY_LINE("Steady Line", "wayline:steady-line", SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_ARMOR,
                "Three Milspec Stalwart automatics anchor the squad. More line armor holds ground; fewer scouts cover the approach."),
        FORWARD_SCREEN("Forward Screen", "wayline:forward-screen", SquadEquipmentDoctrines.LEAGUE_MOBILE_ARMOR,
                "Six Milspec Lancer pulse rifles and a larger scout element. More eyes and mobility, less line protection.");
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
    private String issuedSquadId;
    public boolean claimed() { return claimed; }
    public boolean complete() { return issuedSquadId != null; }
    public String issuedSquadId() { return issuedSquadId; }
    public static boolean available(MarineRoster roster) {
        return roster != null && roster.hasCompletedStory(OpeningOperationKind.RELIEF.missionId)
                && !roster.firstRefit().complete();
    }

    /** Explicit, idempotent collection reward. Never changes a soldier or grants cargo. */
    public boolean claim(MarineRoster roster) {
        if (!available(roster) || claimed) return false;
        MarineArmory armory = roster.armory();
        armory.unlockPrimary(WeaponRegistry.SQUAD_AUTOMATIC_ID, EquipmentGrade.MILSPEC);
        armory.unlockPrimary(WeaponRegistry.PULSE_RIFLE_ID, EquipmentGrade.MILSPEC);
        armory.unlockArmor(MarineArmorPattern.BLUE_SCOUT);
        for (Path path : Path.values()) {
            List<SquadWeaponIssue> issues = new ArrayList<>();
            for (int i = 0; i < MarineSquad.CAPACITY; i++) {
                boolean upgraded = path == Path.STEADY_LINE ? i % 4 == 3 : i % 4 < 2;
                String primary = !upgraded ? WeaponRegistry.STARTER_PRIMARY_ID
                        : path == Path.STEADY_LINE ? WeaponRegistry.SQUAD_AUTOMATIC_ID : WeaponRegistry.PULSE_RIFLE_ID;
                issues.add(new SquadWeaponIssue(upgraded ? path == Path.STEADY_LINE ? "Automatic rifleman" : "Pulse rifleman" : "Rifleman", primary,
                        upgraded ? EquipmentGrade.MILSPEC : EquipmentGrade.SERVICE, null));
            }
            armory.ensureWeaponDoctrine(path.weapons, "Wayline / " + path.label, issues);
        }
        claimed = true;
        return true;
    }

    public SquadEquipmentResult issue(MarineRoster roster, String squadId, Path path, EquipmentIssueResources resources) {
        if (!available(roster) || !claimed || path == null) return SquadEquipmentResult.INVALID_SQUAD;
        SquadEquipmentResult result = roster.applySquadEquipment(squadId, path.weapons, path.armor, resources);
        if (result == SquadEquipmentResult.APPLIED) issuedSquadId = squadId;
        return result;
    }
}
