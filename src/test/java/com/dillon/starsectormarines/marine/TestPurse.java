package com.dillon.starsectormarines.marine;

import java.util.function.UnaryOperator;

/**
 * A company purse for tests: a credit balance, whether the fleet is docked at a civilized port
 * (it is, unless a test says otherwise), the machine shop's reclamation and an optional quote.
 * Spends and refunds move the balance; it counts what each did.
 */
public final class TestPurse implements FabricationResources {
    public long credits;
    public boolean atMarket = true;
    /** Whether the purse takes refunds; one that does not stands for a fleet with nowhere to put them. */
    public boolean accepts = true;
    public Reclamation reclamation = Reclamation.NONE;
    public UnaryOperator<FabricationCost> quote = UnaryOperator.identity();
    public int spends;
    public long refunded;

    public TestPurse(long credits) { this.credits = credits; }

    public static TestPurse rich() { return new TestPurse(10_000_000); }

    public TestPurse away() { atMarket = false; return this; }

    public TestPurse reclaiming(Reclamation reclamation) { this.reclamation = reclamation; return this; }

    @Override public long credits() { return credits; }
    @Override public boolean atMarket() { return atMarket; }
    @Override public FabricationCost quote(FabricationCost base) { return base == null ? null : quote.apply(base); }
    @Override public Reclamation reclamation() { return reclamation; }

    @Override public boolean spend(FabricationCost cost) {
        if (cost == null || credits < cost.credits()) return false;
        credits -= cost.credits();
        spends++;
        return true;
    }

    @Override public boolean credit(FabricationCost refund) {
        if (refund == null || !accepts) return false;
        credits += refund.credits();
        refunded += refund.credits();
        return true;
    }
}

