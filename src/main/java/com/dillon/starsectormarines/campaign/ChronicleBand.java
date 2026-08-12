package com.dillon.starsectormarines.campaign;

/** Why a learned event crossed the Chronicle editor's newsworthiness bar. */
public enum ChronicleBand {
    INTIMATE,
    EPIC;

    private static final ChronicleBand[] VALUES = values();

    public static ChronicleBand fromByte(byte b) {
        return VALUES[b & 0xFF];
    }

    public byte toByte() {
        return (byte) ordinal();
    }
}
