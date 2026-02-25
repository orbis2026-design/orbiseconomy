package me.Short.OrbisEconomy;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.Arrays;
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
        if (splitParams.length >= 4 && splitParams[0].equals("top"))
        {
            String type = String.join("_", Arrays.copyOfRange(splitParams, 3, splitParams.length));
            return resolveTopPlaceholder(splitParams[1], splitParams[2], type);
        }

        // Keep backward compatibility for %orbiseconomy_richest_<position>_<type>% using default "coins" currency
        if (splitParams.length >= 3 && splitParams[0].equals("richest"))
        {
            String type = String.join("_", Arrays.copyOfRange(splitParams, 2, splitParams.length));
            return resolveTopPlaceholder("coins", splitParams[1], type);
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

        if (normalizedType.equals("formatted"))
        {
            normalizedType = "balance_formatted";
        }

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

        if (normalizedType.equals("formatted"))
        {
            normalizedType = "balance_formatted";
        }

        if (normalizedType.equals("entry_legacy"))
        {
            return instance.getConfig().getString("settings.placeholders.balancetop-position-entry-legacy-none", "N/A");
        }

        return instance.getConfig().getString("settings.placeholders.balancetop-position-" + normalizedType + "-none", "N/A");
    }

}
