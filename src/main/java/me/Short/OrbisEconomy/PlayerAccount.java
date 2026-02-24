package me.Short.OrbisEconomy;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class PlayerAccount
{

    // Primary storage: currency ID → balance
    private Map<String, BigDecimal> balances;

    private boolean acceptingPayments;

    // Legacy fields kept solely for Gson migration from old JSON files that still use
    // the flat "balance" and "orbsBalance" fields. These are never written by the new code
    // (PlayerAccountSnapshot no longer contains them).
    private BigDecimal balance;
    private BigDecimal orbsBalance;

    // Constructor
    public PlayerAccount(Map<String, BigDecimal> balances, boolean acceptingPayments)
    {
        this.balances = balances;
        this.acceptingPayments = acceptingPayments;
    }

    // Called after loading from Gson to migrate old flat-field format into the balances map
    public void migrateFromLegacy()
    {
        if (balances == null)
        {
            balances = new HashMap<>();
            if (balance != null)
            {
                balances.put("coins", balance);
            }
            if (orbsBalance != null)
            {
                balances.put("orbs", orbsBalance);
            }
        }
        // Clear legacy fields so they are not carried in memory unnecessarily
        balance = null;
        orbsBalance = null;
    }

    // Method to get a `PlayerAccountSnapshot` of this `PlayerAccount`
    public PlayerAccountSnapshot snapshot()
    {
        return new PlayerAccountSnapshot(new HashMap<>(balances), acceptingPayments);
    }

    // ----- Getters -----

    // Getter for a specific currency balance
    public BigDecimal getBalance(String currencyId)
    {
        return balances.getOrDefault(currencyId, BigDecimal.ZERO);
    }

    // Getter for the default "coins" currency balance - kept for Vault compatibility
    public BigDecimal getBalance()
    {
        return getBalance("coins");
    }

    // Getter for 'acceptingPayments'
    public boolean getAcceptingPayments()
    {
        return acceptingPayments;
    }

    // Getter for the full balances map
    public Map<String, BigDecimal> getBalances()
    {
        return balances;
    }

    // ----- Setters -----

    // Setter for a specific currency balance
    public void setBalance(String currencyId, BigDecimal amount)
    {
        balances.put(currencyId, amount);
    }

    // Setter for the default "coins" currency balance - kept for Vault compatibility
    public void setBalance(BigDecimal amount)
    {
        setBalance("coins", amount);
    }

    // Setter for "acceptingPayments"
    public void setAcceptingPayments(boolean acceptingPayments)
    {
        this.acceptingPayments = acceptingPayments;
    }

    // Getter for 'orbsBalance' (now stored as "orbs" in the balances map) - kept for backward compatibility
    public BigDecimal getOrbsBalance()
    {
        return getBalance("orbs");
    }

    // Setter for "orbsBalance" (now stored as "orbs" in the balances map) - kept for backward compatibility
    public void setOrbsBalance(BigDecimal orbsBalance)
    {
        setBalance("orbs", orbsBalance);
    }

}