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
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        for (Map.Entry<String, me.Short.OrbisEconomy.Currency> entry : instance.getCurrencies().entrySet())
        {
            String currencyId = entry.getKey();
            me.Short.OrbisEconomy.Currency currency = entry.getValue();

            String canonicalId = idPrefix + currency.getId();

            EconomyBridge.registerCurrency(new OrbisEconomyBridgeProvider(instance, currency, canonicalId));
            registeredIds.add(canonicalId);

            instance.getLogger().info("[EconomyBridge] Registered provider for currency: " + currencyId
                    + " (" + currency.getNamePlural() + ") as id '" + canonicalId + "'");
        }

        instance.getLogger().info("[EconomyBridge] Registered EconomyBridge IDs: " + String.join(", ", registeredIds));
        instance.getLogger().info("[EconomyBridge] Use these exact IDs in NightCore/SunLight/ExcellentShop/EconomyBridge-backed plugin configs.");
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
