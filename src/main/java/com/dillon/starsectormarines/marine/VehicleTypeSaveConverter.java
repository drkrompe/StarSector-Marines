package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.thoughtworks.xstream.converters.SingleValueConverter;

/**
 * Writes a {@link VehicleType} into a save by its name and reads it back through {@link VehicleType#fromSavedName}, so
 * a hull a save names that has since been retired (the dozer breacher) loads as the hull it was carried to instead of
 * failing the load. Registered for every save by the mod plugin's {@code configureXStream}.
 */
public final class VehicleTypeSaveConverter implements SingleValueConverter {

    @Override
    @SuppressWarnings("rawtypes")
    public boolean canConvert(Class type) {
        return type != null && VehicleType.class.isAssignableFrom(type);
    }

    @Override
    public String toString(Object value) {
        return value == null ? null : ((VehicleType) value).name();
    }

    @Override
    public Object fromString(String name) {
        VehicleType type = VehicleType.fromSavedName(name);
        if (type == null) throw new IllegalArgumentException("No vehicle type saved as " + name);
        return type;
    }
}
