package me.Short.OrbisEconomy;

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

/**
 * EconomyBridge integration for OrbisLoot compatibility.
 *
 * <p>This class registers a separate EconomyBridge provider for each currency configured in
 * {@code settings.currencies} in {@code config.yml}. This allows plugins such as OrbisLoot to
 * natively reward any supported currency (e.g. "coins", "orbs", "votepoints") by name.
 *
 * <p><b>Setup instructions (to be completed when the EconomyBridge API is available):</b>
 * <ol>
 *   <li>Add the EconomyBridge API jar as a {@code compileOnly} dependency in {@code build.gradle.kts}.</li>
 *   <li>Add {@code EconomyBridge} to the optional server dependencies in {@code paper-plugin.yml}.</li>
 *   <li>Implement the {@code EconomyBridge} provider interface in the inner class below, replacing
 *       the TODO comments with the actual API calls.</li>
 * </ol>
 */
public class EconomyBridgeIntegration
{

    private final OrbisEconomy instance;

    public EconomyBridgeIntegration(OrbisEconomy instance)
    {
        this.instance = instance;
    }

    /**
     * Registers one EconomyBridge provider per configured currency.
     * Called from {@link OrbisEconomy#onEnable()} when the EconomyBridge plugin is detected.
     */
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

    /**
     * EconomyBridge {@link Currency} provider that delegates all balance operations to OrbisEconomy's
     * {@link PlayerAccount} map.
     */
    private static class OrbisEconomyBridgeProvider implements Currency
    {

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
            // EconomyBridge uses %amount% and %name% as placeholders in format strings
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

    }

}
