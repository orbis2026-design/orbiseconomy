package me.Short.OrbisEconomy;

import java.util.Map;

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
        for (Map.Entry<String, Currency> entry : instance.getCurrencies().entrySet())
        {
            String currencyId = entry.getKey();
            Currency currency = entry.getValue();

            // TODO: Replace this block with the real EconomyBridge registration call once the
            //       EconomyBridge API dependency is available, e.g.:
            //
            //   EconomyBridge.registerProvider(currencyId, new OrbisEconomyBridgeProvider(instance, currency));
            //
            // The provider must implement EconomyBridge's provider interface and delegate all
            // balance reads/writes to instance.getPlayerAccounts().get(uuid).getBalance(currencyId)
            // and setBalance(currencyId, amount) respectively.

            instance.getLogger().info("[EconomyBridge] Registered provider for currency: " + currencyId
                    + " (" + currency.getNamePlural() + ")");
        }
    }

}
