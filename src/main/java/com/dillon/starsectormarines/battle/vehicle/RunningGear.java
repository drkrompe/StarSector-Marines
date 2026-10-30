package com.dillon.starsectormarines.battle.vehicle;

/**
 * Where a hull meets the ground, as its sprite paints it: the centre lines of its tracks or wheels either side, and
 * how far ahead and behind the centre they touch down. What the running gear throws or burns, dust off loose ground,
 * smoke off a broken drive, comes off these lines rather than off the footprint's corners: an APC's wheels jut past
 * its footprint, a pickup's sit under its fenders, and a dozer's tracks end well behind its blade.
 *
 * <p>Measured off each sheet's hull frame in pixels from the frame's centre ({@link #ofFrame}) and drawn to the
 * hull's {@link VehicleType#drawnLengthCells}, so hulls sharing one sheet at different scales share it scaled.
 *
 * @param halfGaugeCells the distance from the hull's centre line to each track's or wheel row's centre line, cells
 * @param rearCells      how far behind the hull's centre the rearmost wheel or the track's rear touches down, cells
 * @param frontCells     how far ahead of it the foremost wheel or the track's front touches down, cells
 */
public record RunningGear(float halfGaugeCells, float rearCells, float frontCells) {

    /** None: a hover hull rides its skirt. */
    public static final RunningGear NONE = new RunningGear(0f, 0f, 0f);

    /** Running gear measured in frame pixels from the frame's centre, on a frame {@code framePixels} long. */
    static RunningGear ofFrame(VehicleType type, float framePixels, float gaugePx, float rearPx, float frontPx) {
        float cells = type.drawnLengthCells / framePixels;
        return new RunningGear(gaugePx * cells, rearPx * cells, frontPx * cells);
    }

    /** Whether the hull has wheels or tracks at all. */
    public boolean grounded() { return halfGaugeCells > 0f; }

    /** The gear of {@code type}, off its sheet's hull frame. */
    static RunningGear of(VehicleType type) {
        return switch (type) {
            // Six wheels jutting past the hull; the rear axle at 602 px of 768.
            case HEAVY_APC, FIRE_SUPPORT_APC, LRM_CARRIER ->
                    ofFrame(type, 768f, 162f, 218f, 187f);
            // Tracks the length of the hull, rows centred 180 px either side.
            case BREAKTHROUGH_TANK, SCREENED_TANK -> ofFrame(type, 768f, 180f, 316f, 300f);
            case LASER_TANK -> ofFrame(type, 768f, 182f, 316f, 300f);
            // The tank's tracks drawn smaller on the dozer's longer frame, ending behind the blade.
            case DOZER_BREACHER -> ofFrame(type, 768f, 168f, 296f, 300f);
            // Wheels under the fenders: the front under the bonnet, the rear under the bed's flare.
            case MILITIA_TECHNICAL, SUPPLY_TRUCK, SRM_TECHNICAL -> ofFrame(type, 768f, 146f, 236f, 244f);
            case SCOUT_CAR -> ofFrame(type, 640f, 146f, 225f, 180f);
            // The trailer's rear axles under its tail, the tractor's under its cab.
            case CARGO_CARRIER -> ofFrame(type, 1024f, 110f, 388f, 400f);
            case HOVER_SCOUT, DRONE_SKIMMER -> NONE;
        };
    }
}
