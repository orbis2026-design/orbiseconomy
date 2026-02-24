package me.Short.OrbisEconomy;

import java.math.BigDecimal;
import java.util.Map;

public final class PlayerAccountSnapshot
{

    private final Map<String, BigDecimal> balances;
    private final boolean acceptingPayments;

    // Constructor
    public PlayerAccountSnapshot(Map<String, BigDecimal> balances, boolean acceptingPayments)
    {
        this.balances = balances;
        this.acceptingPayments = acceptingPayments;
    }

}
