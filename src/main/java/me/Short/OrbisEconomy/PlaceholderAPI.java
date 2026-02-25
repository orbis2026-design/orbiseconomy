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

        String normalizedParams = params.toLowerCase(java.util.Locale.ROOT);
        final String balanceFormattedPrefix = "balance_formatted_";
        final String balancePrefix = "balance_";
        final String topPrefix = "top_";

        // %orbiseconomy_balance_formatted_<currencyId>%
        if (normalizedParams.startsWith(balanceFormattedPrefix))
        {
            String currencyId = normalizedParams.substring(balanceFormattedPrefix.length());
            return resolvePlayerBalance(player, currencyId, true);
        }

        // %orbiseconomy_balance_<currencyId>%
        if (normalizedParams.startsWith(balancePrefix))
        {
            String currencyId = normalizedParams.substring(balancePrefix.length());
            return resolvePlayerBalance(player, currencyId, false);
        }

        // %orbiseconomy_top_<currencyId>_<position>_<type>%
        if (normalizedParams.startsWith(topPrefix))
        {
            String topParams = normalizedParams.substring(topPrefix.length());
            ParsedTopPlaceholder parsedTop = parseTopPlaceholder(topParams);

            if (parsedTop == null)
            {
                return "N/A";
            }

            return resolveTopPlaceholder(parsedTop.currencyId(), parsedTop.position(), parsedTop.type());
        }

        // Keep backward compatibility for %orbiseconomy_richest_<position>_<type>% using default "coins" currency
        if (normalizedParams.startsWith("richest_"))
        {
            String richestParams = normalizedParams.substring("richest_".length());
            int separatorIndex = richestParams.indexOf('_');

            if (separatorIndex <= 0 || separatorIndex >= richestParams.length() - 1)
            {
                return "N/A";
            }

            String position = richestParams.substring(0, separatorIndex);
            String type = richestParams.substring(separatorIndex + 1);
            return resolveTopPlaceholder("coins", position, type);
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

    private ParsedTopPlaceholder parseTopPlaceholder(String topParams)
    {
        if (topParams == null || topParams.isBlank())
        {
            return null;
        }

        String type;
        String withoutType;

        if (topParams.endsWith("_balance_formatted"))
        {
            type = "balance_formatted";
            withoutType = topParams.substring(0, topParams.length() - "_balance_formatted".length());
        }
        else
        {
            int typeSeparatorIndex = topParams.lastIndexOf('_');

            if (typeSeparatorIndex <= 0 || typeSeparatorIndex >= topParams.length() - 1)
            {
                return null;
            }

            type = topParams.substring(typeSeparatorIndex + 1);
            withoutType = topParams.substring(0, typeSeparatorIndex);
        }

        int positionSeparatorIndex = withoutType.lastIndexOf('_');

        if (positionSeparatorIndex <= 0 || positionSeparatorIndex >= withoutType.length() - 1)
        {
            return null;
        }

        String currencyId = withoutType.substring(0, positionSeparatorIndex);
        String position = withoutType.substring(positionSeparatorIndex + 1);
        return new ParsedTopPlaceholder(currencyId, position, type);
    }

    private String resolvePlayerBalance(OfflinePlayer player, String currencyId, boolean formatted)
    {
        if (player == null || currencyId == null)
        {
            return "0";
        }

        String normalizedCurrencyId = OrbisEconomy.normalizeCurrencyId(currencyId);
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

        String normalizedCurrencyId = OrbisEconomy.normalizeCurrencyId(currencyId);
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
            return getTopPlaceholderNoneValue(type);
        }

        Map.Entry<UUID, BigDecimal> entry = topBalances.get(position - 1);
        String normalizedType = type.toLowerCase(java.util.Locale.ROOT);

        if (normalizedType.equals("name"))
        {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
            String name = offlinePlayer.getName();
            return name == null ? "N/A" : name;
        }

        if (normalizedType.equals("uuid"))
        {
            return entry.getKey().toString();
        }

        if (normalizedType.equals("balance"))
        {
            return entry.getValue().toPlainString();
        }

        if (normalizedType.equals("balance_formatted"))
        {
            return currency.formatAmount(entry.getValue());
        }

        // Legacy compatibility alias for %..._formatted% after exact matching.
        if (normalizedType.equals("formatted"))
        {
            return currency.formatAmount(entry.getValue());
        }

        if (normalizedType.equals("entry") || normalizedType.equals("entry_legacy"))
        {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
            String name = offlinePlayer.getName() == null ? "N/A" : offlinePlayer.getName();
            String formattedBalance = currency.formatAmount(entry.getValue());

            if (normalizedType.equals("entry_legacy"))
            {
                return "§6" + name + "§8: §6" + formattedBalance;
            }

            return "<gold>" + name + "</gold><dark_gray>: </dark_gray><gold>" + formattedBalance + "</gold>";
        }

        return getTopPlaceholderNoneValue(type);
    }

    private String getTopPlaceholderNoneValue(String type)
    {
        if (type == null)
        {
            return "N/A";
        }

        String normalizedType = type.toLowerCase(java.util.Locale.ROOT);

        if (normalizedType.equals("entry_legacy"))
        {
            return instance.getConfig().getString("settings.placeholders.balancetop-position-entry-legacy-none", "N/A");
        }

        if (normalizedType.equals("formatted"))
        {
            normalizedType = "balance_formatted";
        }

        return instance.getConfig().getString("settings.placeholders.balancetop-position-" + normalizedType + "-none", "N/A");
    }

    private record ParsedTopPlaceholder(String currencyId, String position, String type)
    {
    }

}
