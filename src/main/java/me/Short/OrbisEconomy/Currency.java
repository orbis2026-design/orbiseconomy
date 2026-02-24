package me.Short.OrbisEconomy;

import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.text.DecimalFormat;

public class Currency
{

    private final String id;
    private final String nameSingular;
    private final String namePlural;
    private final BigDecimal defaultBalance;
    private final BigDecimal maxBalance;
    private final int decimalPlaces;
    private final String format;
    private final RoundingMode roundingMode;

    // Constructor
    public Currency(String id, String nameSingular, String namePlural,
                    BigDecimal defaultBalance, BigDecimal maxBalance,
                    int decimalPlaces, String format, RoundingMode roundingMode)
    {
        this.id = id;
        this.nameSingular = nameSingular;
        this.namePlural = namePlural;
        this.defaultBalance = defaultBalance;
        this.maxBalance = maxBalance;
        this.decimalPlaces = decimalPlaces;
        this.format = format;
        this.roundingMode = roundingMode;
    }

    // Format an amount using this currency's display format
    public String formatAmount(BigDecimal amount)
    {
        DecimalFormat df;
        if (decimalPlaces > 0 && amount.stripTrailingZeros().scale() > 0)
        {
            df = new DecimalFormat("#,##0." + StringUtils.repeat("0", decimalPlaces));
        }
        else
        {
            df = new DecimalFormat("#,##0");
        }

        String name = amount.compareTo(BigDecimal.ONE) == 0 ? nameSingular : namePlural;

        return format
                .replace("<amount>", df.format(amount))
                .replace("<name>", name);
    }

    // ----- Getters -----

    public String getId()
    {
        return id;
    }

    public String getNameSingular()
    {
        return nameSingular;
    }

    public String getNamePlural()
    {
        return namePlural;
    }

    public BigDecimal getDefaultBalance()
    {
        return defaultBalance;
    }

    public BigDecimal getMaxBalance()
    {
        return maxBalance;
    }

    public int getDecimalPlaces()
    {
        return decimalPlaces;
    }

    public String getFormat()
    {
        return format;
    }

    public RoundingMode getRoundingMode()
    {
        return roundingMode;
    }

}
