package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

/** A compact generator house with an accessible plant face and a separate electrical service bank. */
public final class InstallationUtilityFitting implements RoomFitting {
    @Override public RoomPurpose purpose() { return RoomPurpose.ENGINE_ROOM; }

    @Override public void fit(RoomFloor floor) {
        int w = floor.canonicalWidth(), h = floor.canonicalHeight();
        if (w < 6 || h < 6) return;
        if (w < 8 || h < 8) {
            compact(floor, w, h);
            return;
        }
        int aisle = h - 4;
        InstallationArmoryFitting.reserve(floor, 0, aisle, w, 2);
        InstallationArmoryFitting.doors(floor, w, h, aisle, aisle + 1);
        InstallationWorkshopFitting.station(floor, (w - 6) / 2, aisle - 4, "ship-engineering.generator",
                w / 2, aisle, w / 2, aisle - 1, Affordance.TEND);
        InstallationWorkshopFitting.station(floor, 0, h - 2, "ship-engineering.switchgear",
                1, h - 3, 1, h - 2, Affordance.TEND);
        InstallationWorkshopFitting.station(floor, w - 3, h - 1, "ship-engineering.control",
                w - 2, h - 2, w - 2, h - 1, Affordance.READOUT);
        InstallationWorkshopFitting.station(floor, w - 1, h - 2, "colony-furnishings.locker",
                w - 1, h - 3, w - 1, h - 2, Affordance.STOW);
    }

    /** The same turbine/cooling pair as a fort, facing a full-width two-cell service aisle. */
    private static void compact(RoomFloor floor, int w, int h) {
        int aisle = h - 3;
        InstallationArmoryFitting.reserve(floor, 0, aisle, w, 2);
        InstallationArmoryFitting.doors(floor, w, h, aisle, aisle + 1);
        PowerPlantKit.TURBINE.station(floor, 0, aisle - 3, 1, aisle, 1, aisle - 1);
        PowerPlantKit.COOLANT.station(floor, w - 2, aisle - 3, w - 1, aisle, w - 1, aisle - 1);
        InstallationWorkshopFitting.station(floor, 0, h - 1, "ship-engineering.control",
                1, h - 2, 1, h - 1, Affordance.READOUT);
    }
}
