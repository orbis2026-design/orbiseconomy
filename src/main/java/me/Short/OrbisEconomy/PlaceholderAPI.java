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

        // %orbiseconomy_balance_formatted_<currencyId>%
        if (normalizedParams.startsWith("balance_formatted_"))
        {
            String currencyId = normalizedParams.substring("balance_formatted_".length());

            if (currencyId.isBlank())
            {
                return getBalanceFallback();
            }

            return resolvePlayerBalance(player, currencyId, true);
        }

        // %orbiseconomy_balance_<currencyId>%
        if (normalizedParams.startsWith("balance_"))
        {
            String currencyId = normalizedParams.substring("balance_".length());

            if (currencyId.isBlank())
            {
                return getBalanceFallback();
            }

            return resolvePlayerBalance(player, currencyId, false);
        }

        // %orbiseconomy_top_<currencyId>_<position>_<type...>%
        if (normalizedParams.startsWith("top_"))
        {
            String topPayload = normalizedParams.substring("top_".length());

            if (topPayload.isBlank())
            {
                return getTopFallback(null);
            }

            String[] topSections = topPayload.split("_", 3);

            if (topSections.length < 3 || topSections[0].isBlank() || topSections[1].isBlank() || topSections[2].isBlank())
            {
                String fallbackType = topSections.length == 3 ? topSections[2] : null;
                return getTopFallback(fallbackType);
            }

            return resolveTopPlaceholder(topSections[0], topSections[1], topSections[2]);
        }

        String[] splitParams = normalizedParams.split("_");

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
            return getBalanceFallback();
        }

        String normalizedCurrencyId = currencyId.toLowerCase();
        Currency currency = instance.getCurrencies().get(normalizedCurrencyId);

        if (currency == null)
        {
            return getBalanceFallback();
        }

        PlayerAccount account = instance.getPlayerAccounts().get(player.getUniqueId());

        if (account == null)
        {
            return getBalanceFallback();
        }

        BigDecimal balance = account.getBalance(normalizedCurrencyId);
        return formatted ? currency.formatAmount(balance) : balance.toPlainString();
    }

    private String resolveTopPlaceholder(String currencyId, String positionInput, String type)
    {
        if (currencyId == null || positionInput == null || type == null)
        {
            return getTopFallback(type);
        }

        String normalizedCurrencyId = currencyId.toLowerCase();
        Currency currency = instance.getCurrencies().get(normalizedCurrencyId);

        if (currency == null)
        {
            return getTopFallback(type);
        }

        int position;

        try
        {
            position = Integer.parseInt(positionInput);
        }
        catch (NumberFormatException ignored)
        {
            return getTopFallback(type);
        }

        if (position <= 0)
        {
            return getTopFallback(type);
        }

        List<Map.Entry<UUID, BigDecimal>> topBalances = instance.getBalanceTop().getTopBalances(normalizedCurrencyId);

        if (position > topBalances.size())
        {
            return getTopFallback(type);
        }

        Map.Entry<UUID, BigDecimal> entry = topBalances.get(position - 1);

        if (type.equals("name"))
        {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
            String name = offlinePlayer.getName();
            return name == null ? getTopFallback(type) : name;
        }

        if (type.equals("balance"))
        {
            return entry.getValue().toPlainString();
        }

        if (type.equals("formatted") || type.equals("balance_formatted"))
        {
            return currency.formatAmount(entry.getValue());
        }

        return getTopFallback(type);
    }

    private String getBalanceFallback()
    {
        return instance.getConfig().getString("placeholders.balance-none", "0");
    }

    private String getTopFallback(String type)
    {
        if (type != null && !type.isBlank())
        {
            return instance.getConfig().getString("placeholders.balancetop-position-" + type + "-none",
                    instance.getConfig().getString("placeholders.top-invalid", "N/A"));
        }

        return instance.getConfig().getString("placeholders.top-invalid", "N/A");
    }

}
