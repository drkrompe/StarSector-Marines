package com.dillon.starsectormarines.battle.air;
import com.dillon.starsectormarines.marine.BoatFitting;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoatDamageLedgerTest {
    @Test void tacticalServicingAndNewSortieEntitiesCannotEraseOverhaulWork() {
        var ledger = new BoatDamageLedger();
        var frame = new FittedBoat(ShuttleType.HERMES, BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE, "boat");
        ledger.observe(frame, frame.maxHp() * .25f);
        var frozen = ledger.damage();
        ledger.observe(frame, frame.maxHp());
        ledger.observe(frame, frame.maxHp() * .5f);
        ledger.observe(ShuttleType.HERMES, 0f);
        ledger.observe(FittedBoat.standard(ShuttleType.HERMES), 0f);
        assertEquals(.75f, ledger.damage().get("boat"));
        assertEquals(1, ledger.damage().size());
        assertEquals(frozen, ledger.damage());
        assertThrows(UnsupportedOperationException.class, () -> frozen.clear());
    }
}
