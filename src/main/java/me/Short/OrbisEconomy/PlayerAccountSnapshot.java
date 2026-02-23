package me.Short.OrbisEconomy;

import java.math.BigDecimal;

public final class PlayerAccountSnapshot
{

    private final BigDecimal balance;
    private final boolean acceptingPayments;
    private final BigDecimal orbsBalance;

    // Constructor
    public PlayerAccountSnapshot(BigDecimal balance, boolean acceptingPayments, BigDecimal orbsBalance)
    {
        this.balance = balance;
        this.acceptingPayments = acceptingPayments;
        this.orbsBalance = orbsBalance;
    }

}
