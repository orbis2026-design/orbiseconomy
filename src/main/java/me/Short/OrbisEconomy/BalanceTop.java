package me.Short.OrbisEconomy;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BalanceTop
{

    // Ordered map of currency ID -> list of players and balances from richest to poorest
    private final Map<String, List<Map.Entry<UUID, BigDecimal>>> topBalancesByCurrency;

    // All players' balances added together
    private final BigDecimal combinedTotalBalance;

    // Constructor
    public BalanceTop(Map<String, List<Map.Entry<UUID, BigDecimal>>> topBalancesByCurrency, BigDecimal combinedTotalBalance)
    {
        this.topBalancesByCurrency = topBalancesByCurrency;
        this.combinedTotalBalance = combinedTotalBalance;
    }

    // Getter for top balances list by currency ID
    public List<Map.Entry<UUID, BigDecimal>> getTopBalances(String currencyId)
    {
        if (currencyId == null)
        {
            return Collections.emptyList();
        }

        List<Map.Entry<UUID, BigDecimal>> entries = topBalancesByCurrency.get(OrbisEconomy.normalizeCurrencyId(currencyId));
        return entries == null ? Collections.emptyList() : entries;
    }

    // Getter for "combinedTotalBalance"
    public BigDecimal getCombinedTotalBalance()
    {
        return combinedTotalBalance;
    }

}
