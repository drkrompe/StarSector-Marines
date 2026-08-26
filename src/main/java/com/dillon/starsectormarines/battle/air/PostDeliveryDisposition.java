package com.dillon.starsectormarines.battle.air;

/** What a shuttle does after its current payload has fully deboarded. */
public enum PostDeliveryDisposition {
    /** Leave the landing zone immediately, retaining the ordinary exit and re-arm lifecycle. */
    DEPART,
    /** Preserve the legacy behavior: armed craft loiter, while unarmed craft depart. */
    LOITER_IF_ARMED
}
