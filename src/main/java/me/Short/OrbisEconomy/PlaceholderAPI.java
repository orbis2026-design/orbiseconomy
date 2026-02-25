package me.Short.OrbisEconomy;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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

        String normalizedParams = params.toLowerCase(Locale.ROOT);

        // %orbiseconomy_balance_for_uuid_<currencyId>_<uuid>%
        // %orbiseconomy_balance_formatted_for_uuid_<currencyId>_<uuid>%
        if (normalizedParams.startsWith("balance_for_uuid_") || normalizedParams.startsWith("balance_formatted_for_uuid_"))
        {
            return resolveExplicitTargetBalance(params, "for_uuid");
        }

        // %orbiseconomy_balance_for_name_<currencyId>_<name>%
        // %orbiseconomy_balance_formatted_for_name_<currencyId>_<name>%
        if (normalizedParams.startsWith("balance_for_name_") || normalizedParams.startsWith("balance_formatted_for_name_"))
        {
            return resolveExplicitTargetBalance(params, "for_name");
        }

        // %orbiseconomy_balance_<currencyId>%
        if (normalizedParams.startsWith("balance_"))
        {
            String currencyId = normalizedParams.substring("balance_".length());

            if (currencyId.startsWith("formatted_"))
            {
                return resolvePlayerBalance(player, currencyId.substring("formatted_".length()), true);
            }

            return resolvePlayerBalance(player, currencyId, false);
        }

        // %orbiseconomy_top_<currencyId>_<position>_<type>%
        if (normalizedParams.startsWith("top_"))
        {
            Optional<TopPlaceholderInput> parsedTop = parseTopPlaceholderInput(normalizedParams.substring("top_".length()), true);

            if (parsedTop.isEmpty())
            {
                return "N/A";
            }

            TopPlaceholderInput placeholderInput = parsedTop.get();
            return resolveTopPlaceholder(placeholderInput.currencyId(), placeholderInput.position(), placeholderInput.type());
        }

        // Keep backward compatibility for %orbiseconomy_richest_<position>_<type>% using default "coins" currency
        if (normalizedParams.startsWith("richest_"))
        {
            Optional<TopPlaceholderInput> parsedTop = parseTopPlaceholderInput(normalizedParams.substring("richest_".length()), false);

            if (parsedTop.isEmpty())
            {
                return "N/A";
            }

            TopPlaceholderInput placeholderInput = parsedTop.get();
            return resolveTopPlaceholder("coins", placeholderInput.position(), placeholderInput.type());
        }

        // %orbiseconomy_accepting_payments%
        if (normalizedParams.equals("accepting_payments"))
        {
            if (player == null)
            {
                return getPlayerRequiredFallback();
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

    private Optional<TopPlaceholderInput> parseTopPlaceholderInput(String input, boolean includeCurrency)
    {
        if (input == null || input.isBlank())
        {
            return Optional.empty();
        }

        String matchedType = null;

        for (String supportedType : List.of("balance_formatted", "entry_legacy", "formatted", "balance", "entry", "name", "uuid"))
        {
            String typeSuffix = "_" + supportedType;

            if (input.endsWith(typeSuffix))
            {
                matchedType = supportedType;
                input = input.substring(0, input.length() - typeSuffix.length());
                break;
            }
        }

        if (matchedType == null)
        {
            return Optional.empty();
        }

        int lastSeparatorIndex = input.lastIndexOf('_');

        if (lastSeparatorIndex <= 0)
        {
            return Optional.empty();
        }

        String position = input.substring(lastSeparatorIndex + 1);

        if (includeCurrency)
        {
            String currencyId = input.substring(0, lastSeparatorIndex);

            if (currencyId.isBlank())
            {
                return Optional.empty();
            }

            return Optional.of(new TopPlaceholderInput(currencyId, position, matchedType));
        }

        return Optional.of(new TopPlaceholderInput("coins", position, matchedType));
    }

    private String resolvePlayerBalance(OfflinePlayer player, String currencyId, boolean formatted)
    {
        if (player == null || currencyId == null)
        {
            return getPlayerRequiredFallback();
        }

        return resolveBalanceByUuid(player.getUniqueId(), currencyId, formatted);
    }

    private String resolveExplicitTargetBalance(String input, String targetType)
    {
        if (input == null || targetType == null)
        {
            return "0";
        }

        String normalizedInput = input.toLowerCase(Locale.ROOT);

        boolean formatted = normalizedInput.startsWith("balance_formatted_");
        String prefix = formatted ? "balance_formatted_" + targetType + "_" : "balance_" + targetType + "_";

        if (!normalizedInput.startsWith(prefix))
        {
            return "0";
        }

        String payload = input.substring(prefix.length());
        int firstSeparator = payload.indexOf('_');

        if (firstSeparator <= 0 || firstSeparator >= payload.length() - 1)
        {
            return "0";
        }

        String currencyId = payload.substring(0, firstSeparator);
        String target = payload.substring(firstSeparator + 1);

        if (targetType.equals("for_uuid"))
        {
            try
            {
                return resolveBalanceByUuid(UUID.fromString(target), currencyId, formatted);
            }
            catch (IllegalArgumentException ignored)
            {
                return "0";
            }
        }

        if (targetType.equals("for_name"))
        {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayerIfCached(target);

            if (offlinePlayer == null)
            {
                return "0";
            }

            return resolveBalanceByUuid(offlinePlayer.getUniqueId(), currencyId, formatted);
        }

        return "0";
    }

    private String resolveBalanceByUuid(UUID playerUuid, String currencyId, boolean formatted)
    {
        if (playerUuid == null || currencyId == null)
        {
            return "0";
        }

        String normalizedCurrencyId = OrbisEconomy.normalizeCurrencyId(currencyId);
        Currency currency = instance.getCurrencies().get(normalizedCurrencyId);

        if (currency == null)
        {
            return "0";
        }

        PlayerAccount account = instance.getPlayerAccounts().get(playerUuid);

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
        String normalizedType = type.toLowerCase(Locale.ROOT);

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

        String normalizedType = type.toLowerCase(Locale.ROOT);

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

    private String getPlayerRequiredFallback()
    {
        return instance.getConfig().getString("settings.placeholders.player-required-fallback", "PLAYER_REQUIRED");
    }

    private record TopPlaceholderInput(String currencyId, String position, String type)
    {
    }

}
