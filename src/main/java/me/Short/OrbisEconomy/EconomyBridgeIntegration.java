package me.Short.OrbisEconomy;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.economybridge.EconomyBridge;
import su.nightexpress.economybridge.api.Currency;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class EconomyBridgeIntegration
{

    private final OrbisEconomy instance;

    public EconomyBridgeIntegration(OrbisEconomy instance)
    {
        this.instance = instance;
    }

    public void register()
    {
        String idPrefix = instance.getConfig().getString("settings.economybridge.id-prefix", "");
        if (idPrefix == null)
        {
            idPrefix = "";
        }

        List<String> registeredIds = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (Map.Entry<String, me.Short.OrbisEconomy.Currency> entry : instance.getCurrencies().entrySet())
        {
            String currencyId = entry.getKey();
            me.Short.OrbisEconomy.Currency currency = entry.getValue();

            String canonicalId = idPrefix + currency.getId();

            if (!seenIds.add(canonicalId))
            {
                instance.getLogger().warning("[EconomyBridge] Skipping duplicate canonical ID from config/currency normalization: '" + canonicalId + "'.");
                continue;
            }

            unregisterIfSupported(canonicalId);

            EconomyBridge.registerCurrency(new OrbisEconomyBridgeProvider(instance, currency, canonicalId));
            registeredIds.add(canonicalId);

            instance.getLogger().info("[EconomyBridge] Registered provider for currency: " + currencyId
                    + " (" + currency.getNamePlural() + ") as id '" + canonicalId + "'");
        }

        instance.getLogger().info("EconomyBridge sync complete: [" + String.join(", ", registeredIds) + "]");
        verifyRegisteredIds(registeredIds);
        instance.getLogger().info("[EconomyBridge] Use these exact IDs in NightCore/SunLight/ExcellentShop/EconomyBridge-backed plugin configs.");
    }

    private void verifyRegisteredIds(List<String> expectedIds)
    {
        Set<String> discoveredIds = lookupRegisteredIds();

        if (discoveredIds == null)
        {
            instance.getLogger().warning("[EconomyBridge] Verification API unavailable. Expected IDs: [" + String.join(", ", expectedIds) + "]. Run EconomyBridge sync (for example: /economybridge reload) after config changes.");
            return;
        }

        List<String> missingIds = expectedIds.stream().filter(id -> !discoveredIds.contains(id)).toList();
        if (missingIds.isEmpty())
        {
            instance.getLogger().info("[EconomyBridge] Verification passed. Found all expected IDs in EconomyBridge registry.");
            return;
        }

        instance.getLogger().warning("[EconomyBridge] Verification failed. Missing IDs in EconomyBridge registry: [" + String.join(", ", missingIds) + "]");
        instance.getLogger().warning("[EconomyBridge] Expected IDs: [" + String.join(", ", expectedIds) + "]. Run EconomyBridge sync (for example: /economybridge reload) and verify dependent plugin configs.");
    }

    @SuppressWarnings("unchecked")
    private Set<String> lookupRegisteredIds()
    {
        try
        {
            Object currenciesById = EconomyBridge.class.getMethod("getCurrenciesMap").invoke(null);
            if (currenciesById instanceof Map<?, ?> currenciesMap)
            {
                Set<String> discovered = new HashSet<>();
                currenciesMap.keySet().stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .forEach(discovered::add);
                return discovered;
            }
        }
        catch (NoSuchMethodException ignored)
        {
            // Continue trying alternative API methods.
        }
        catch (Exception exception)
        {
            instance.getLogger().log(Level.WARNING, "[EconomyBridge] Failed to query getCurrenciesMap() for verification.", exception);
            return null;
        }

        try
        {
            Object currencies = EconomyBridge.class.getMethod("getCurrencies").invoke(null);
            if (currencies instanceof Collection<?> collection)
            {
                Set<String> discovered = new HashSet<>();
                for (Object entry : collection)
                {
                    if (entry instanceof Currency currency)
                    {
                        discovered.add(currency.getInternalId());
                    }
                }
                return discovered;
            }
        }
        catch (NoSuchMethodException ignored)
        {
            // Continue trying alternative API methods.
        }
        catch (Exception exception)
        {
            instance.getLogger().log(Level.WARNING, "[EconomyBridge] Failed to query getCurrencies() for verification.", exception);
            return null;
        }

        try
        {
            Set<String> discovered = new HashSet<>();
            for (String currencyId : instance.getCurrencies().keySet())
            {
                String canonicalId = buildCanonicalId(currencyId);
                Object lookup = EconomyBridge.class.getMethod("getCurrency", String.class).invoke(null, canonicalId);
                if (lookup != null)
                {
                    discovered.add(canonicalId);
                }
            }

            return discovered;
        }
        catch (NoSuchMethodException ignored)
        {
            return null;
        }
        catch (Exception exception)
        {
            instance.getLogger().log(Level.WARNING, "[EconomyBridge] Failed to query getCurrency(String) for verification.", exception);
            return null;
        }
    }

    private String buildCanonicalId(String rawCurrencyId)
    {
        String idPrefix = Objects.requireNonNullElse(instance.getConfig().getString("settings.economybridge.id-prefix", ""), "");
        String normalizedCurrencyId = OrbisEconomy.normalizeCurrencyId(rawCurrencyId);
        return idPrefix + normalizedCurrencyId;
    }

    private void unregisterIfSupported(String canonicalId)
    {
        try
        {
            EconomyBridge.class.getMethod("unregisterCurrency", String.class).invoke(null, canonicalId);
            instance.getLogger().info("[EconomyBridge] Replacing existing provider for ID '" + canonicalId + "'.");
            return;
        }
        catch (NoSuchMethodException ignored)
        {
            // Fallback below for legacy API variants.
        }
        catch (Exception exception)
        {
            instance.getLogger().log(Level.WARNING, "[EconomyBridge] Failed to unregister currency ID '" + canonicalId + "' before re-registering.", exception);
            return;
        }

        try
        {
            EconomyBridge.class.getMethod("removeCurrency", String.class).invoke(null, canonicalId);
            instance.getLogger().info("[EconomyBridge] Replacing existing provider for ID '" + canonicalId + "'.");
        }
        catch (NoSuchMethodException ignored)
        {
            // API does not expose an explicit unregister/remove call; registerCurrency semantics will apply.
        }
        catch (Exception exception)
        {
            instance.getLogger().log(Level.WARNING, "[EconomyBridge] Failed to remove currency ID '" + canonicalId + "' before re-registering.", exception);
        }
    }

    private static class OrbisEconomyBridgeProvider implements Currency
    {

        private static final List<String> PREMIUM_CURRENCIES = List.of("orbs", "votepoints");

        private final OrbisEconomy instance;
        private final me.Short.OrbisEconomy.Currency currency;
        private final String canonicalId;

        OrbisEconomyBridgeProvider(OrbisEconomy instance, me.Short.OrbisEconomy.Currency currency, String canonicalId)
        {
            this.instance = instance;
            this.currency = currency;
            this.canonicalId = canonicalId;
        }

        @Override
        @NotNull
        public String getInternalId()
        {
            return canonicalId;
        }

        @Override
        @NotNull
        public String getOriginalId()
        {
            return canonicalId;
        }

        @Override
        @NotNull
        public String getName()
        {
            return currency.getNamePlural();
        }

        @Override
        @NotNull
        public String getDefaultName()
        {
            return currency.getNamePlural();
        }

        @Override
        @NotNull
        public String getFormat()
        {
            return currency.getFormat()
                    .replace("<amount>", "%amount%")
                    .replace("<name>", "%name%");
        }

        @Override
        @NotNull
        public ItemStack getIcon()
        {
            return new ItemStack(Material.GOLD_NUGGET);
        }

        @Override
        @NotNull
        public ItemStack getDefaultIcon()
        {
            return new ItemStack(Material.GOLD_NUGGET);
        }

        @Override
        public boolean canHandleDecimals()
        {
            return currency.getDecimalPlaces() > 0;
        }

        @Override
        public boolean canHandleOffline()
        {
            return true;
        }

        @Override
        public double getBalance(@NotNull Player player)
        {
            return getBalance(player.getUniqueId());
        }

        @Override
        public double getBalance(@NotNull UUID playerId)
        {
            String curId = currency.getId().toLowerCase();
            if (PREMIUM_CURRENCIES.contains(curId))
            {
                PremiumBalance cached = instance.getPremiumBalances().get(playerId);
                if (cached == null || !cached.isAvailable())
                {
                    return 0.0;
                }
                return cached.getBalance(curId);
            }

            PlayerAccount account = instance.getPlayerAccounts().get(playerId);
            if (account == null)
            {
                return 0;
            }
            return account.getBalance(currency.getId()).doubleValue();
        }

        @Override
        public void give(@NotNull Player player, double amount)
        {
            give(player.getUniqueId(), amount);
        }

        @Override
        public void give(@NotNull UUID playerId, double amount)
        {
            String curId = currency.getId().toLowerCase();
            if (PREMIUM_CURRENCIES.contains(curId))
            {
                processPremiumTransaction(playerId, curId, Math.round(amount), "Deposit via EconomyBridge");
                return;
            }

            PlayerAccount account = instance.getPlayerAccounts().get(playerId);
            if (account == null)
            {
                return;
            }
            BigDecimal newBalance = account.getBalance(currency.getId()).add(BigDecimal.valueOf(amount));
            if (newBalance.compareTo(currency.getMaxBalance()) > 0)
            {
                newBalance = currency.getMaxBalance();
            }
            account.setBalance(currency.getId(), newBalance);
            instance.getDirtyPlayerAccountSnapshots().put(playerId, account.snapshot());
        }

        @Override
        public void take(@NotNull Player player, double amount)
        {
            take(player.getUniqueId(), amount);
        }

        @Override
        public void take(@NotNull UUID playerId, double amount)
        {
            String curId = currency.getId().toLowerCase();
            if (PREMIUM_CURRENCIES.contains(curId))
            {
                processPremiumTransaction(playerId, curId, -Math.round(amount), "Withdrawal via EconomyBridge");
                return;
            }

            PlayerAccount account = instance.getPlayerAccounts().get(playerId);
            if (account == null)
            {
                return;
            }
            BigDecimal newBalance = account.getBalance(currency.getId()).subtract(BigDecimal.valueOf(amount));
            if (newBalance.compareTo(BigDecimal.ZERO) < 0)
            {
                newBalance = BigDecimal.ZERO;
            }
            account.setBalance(currency.getId(), newBalance);
            instance.getDirtyPlayerAccountSnapshots().put(playerId, account.snapshot());
        }

        private void processPremiumTransaction(UUID playerId, String currencyId, long amountDelta, String reason)
        {
            if (Bukkit.isPrimaryThread())
            {
                throw new RuntimeException("Premium transactions must execute asynchronously; blocking I/O is forbidden on the server thread.");
            }

            PremiumBalance cached = instance.getPremiumBalances().get(playerId);
            if (cached == null || !cached.isAvailable())
            {
                throw new RuntimeException("Premium economy API is unavailable for " + playerId + ".");
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("transaction_id", UUID.randomUUID().toString());
            payload.addProperty("uuid", playerId.toString());
            payload.addProperty("currency", currencyId);
            payload.addProperty("amount", amountDelta);
            payload.addProperty("reason", reason);

            instance.getOrbisPaperAgentBridge().makeSignedApiCall("POST", "/api/economy/transaction", payload).join();
            cached.applyDelta(currencyId, amountDelta);
        }
    }
}
