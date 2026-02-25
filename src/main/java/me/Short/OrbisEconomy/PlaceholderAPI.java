package me.Short.OrbisEconomy;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlaceholderAPI extends PlaceholderExpansion
{

    // Instance of "OrbisEconomy"
    private final OrbisEconomy instance;

    // Constructor
    public PlaceholderAPI(OrbisEconomy instance)
    {
        this.instance = instance;
    }

    @Override
    public @NotNull String getIdentifier()
    {
        return "orbiseconomy";
    }

    @Override
    public @NotNull String getAuthor()
    {
        List<String> authors = instance.getPluginMeta().getAuthors();
        return authors.isEmpty() ? instance.getPluginMeta().getName() : authors.getFirst();
    }

    @Override
    public @NotNull String getVersion()
    {
        return instance.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist()
    {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params)
    {
        if (params == null || params.isBlank())
        {
            return null;
        }

        String normalizedParams = params.toLowerCase();
        String[] splitParams = normalizedParams.split("_");

        // %orbiseconomy_balance_<currencyId>%
        if (splitParams.length == 2 && splitParams[0].equals("balance"))
        {
            return resolvePlayerBalance(player, splitParams[1], false);
        }

        // %orbiseconomy_balance_formatted_<currencyId>%
        if (splitParams.length == 3 && splitParams[0].equals("balance") && splitParams[1].equals("formatted"))
        {
            return resolvePlayerBalance(player, splitParams[2], true);
        }

        // %orbiseconomy_top_<currencyId>_<position>_<type>%
        if (splitParams.length == 4 && splitParams[0].equals("top"))
        {
            return resolveTopPlaceholder(splitParams[1], splitParams[2], splitParams[3]);
        }

        // Keep backward compatibility for %orbiseconomy_richest_<position>_<type>% using default "coins" currency
        if (splitParams.length == 3 && splitParams[0].equals("richest"))
        {
            return resolveTopPlaceholder("coins", splitParams[1], splitParams[2]);
        }

        // %orbiseconomy_accepting_payments%
        if (normalizedParams.equals("accepting_payments"))
        {
            if (player == null)
            {
                return null;
            }

            PlayerAccount account = instance.getPlayerAccounts().get(player.getUniqueId());
            return account == null ? "0" : Boolean.toString(account.getAcceptingPayments());
        }

        // %orbiseconomy_combined_total_balance%
        if (normalizedParams.equals("combined_total_balance"))
        {
            return instance.getBalanceTop().getCombinedTotalBalance().toPlainString();
        }

        // %orbiseconomy_combined_total_balance_formatted%
        if (normalizedParams.equals("combined_total_balance_formatted"))
        {
            return instance.getEconomy().format(instance.getBalanceTop().getCombinedTotalBalance().doubleValue());
        }

        return null;
    }

    private String resolvePlayerBalance(OfflinePlayer player, String currencyId, boolean formatted)
    {
        if (player == null || currencyId == null)
        {
            return "0";
        }

        String normalizedCurrencyId = currencyId.toLowerCase();
        Currency currency = instance.getCurrencies().get(normalizedCurrencyId);

        if (currency == null)
        {
            return "0";
        }

        PlayerAccount account = instance.getPlayerAccounts().get(player.getUniqueId());

        if (account == null)
        {
            return "0";
        }

        BigDecimal balance = account.getBalance(normalizedCurrencyId);
        return formatted ? currency.formatAmount(balance) : balance.toPlainString();
    }

    private String resolveTopPlaceholder(String currencyId, String positionInput, String type)
    {
        if (currencyId == null || positionInput == null || type == null)
        {
            return "N/A";
        }

        String normalizedCurrencyId = currencyId.toLowerCase();
        Currency currency = instance.getCurrencies().get(normalizedCurrencyId);

        if (currency == null)
        {
            return "N/A";
        }

        int position;

        try
        {
            position = Integer.parseInt(positionInput);
        }
        catch (NumberFormatException ignored)
        {
            return "N/A";
        }

        if (position <= 0)
        {
            return "N/A";
        }

        List<Map.Entry<UUID, BigDecimal>> topBalances = instance.getBalanceTop().getTopBalances(normalizedCurrencyId);

        if (position > topBalances.size())
        {
            return "N/A";
        }

        Map.Entry<UUID, BigDecimal> entry = topBalances.get(position - 1);

        if (type.equals("name"))
        {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
            String name = offlinePlayer.getName();
            return name == null ? "N/A" : name;
        }

        if (type.equals("balance"))
        {
            return entry.getValue().toPlainString();
        }

        if (type.equals("formatted"))
        {
            return currency.formatAmount(entry.getValue());
        }

        return "N/A";
    }

}
