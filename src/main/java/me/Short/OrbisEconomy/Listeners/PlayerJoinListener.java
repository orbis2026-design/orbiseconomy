package me.Short.OrbisEconomy.Listeners;

import com.google.gson.JsonObject;
import me.Short.OrbisEconomy.Economy;
import me.Short.OrbisEconomy.OrbisEconomy;
import me.Short.OrbisEconomy.PremiumBalance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerJoinListener implements Listener
{

    private final OrbisEconomy instance;

    public PlayerJoinListener(OrbisEconomy instance)
    {
        this.instance = instance;
    }

    @EventHandler
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event)
    {
        UUID uuid = event.getUniqueId();
        PremiumBalance premiumBalance = new PremiumBalance();

        if (!instance.getOrbisPaperAgentBridge().isAgentOnline())
        {
            instance.getPremiumBalances().put(uuid, premiumBalance);
            return;
        }

        try
        {
            JsonObject data = instance.getOrbisPaperAgentBridge().makeSignedApiCall("GET", "/api/economy/" + uuid, null).join();

            if (data.has("orbs"))
            {
                premiumBalance.setOrbs(data.get("orbs").getAsLong());
            }

            if (data.has("votepoints"))
            {
                premiumBalance.setVotepoints(data.get("votepoints").getAsLong());
            }

            premiumBalance.setAvailable(true);
        }
        catch (Exception exception)
        {
            instance.getLogger().warning("Failed to load premium economy for " + uuid + ". Entering read-only ghost state. " + exception.getMessage());
        }

        instance.getPremiumBalances().put(uuid, premiumBalance);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        instance.getOfflinePlayerNames().put(uuid, playerName);

        Economy economy = instance.getEconomy();

        if (economy.hasAccount(player))
        {
            return;
        }

        FileConfiguration config = instance.getConfig();

        if (economy.createPlayerAccount(player))
        {
            if (config.getBoolean("settings.logging.account-creation-success.log"))
            {
                instance.getLogger().log(Level.INFO, config.getString("settings.logging.account-creation-success.message")
                        .replace("<player>", playerName)
                        .replace("<uuid>", uuid.toString())
                        .replace("<default_balance>", new BigDecimal(config.getString("settings.currencies.coins.default-balance")).stripTrailingZeros().toPlainString()));
            }
        }
        else
        {
            if (config.getBoolean("settings.logging.account-creation-fail.log"))
            {
                instance.getLogger().log(Level.WARNING, config.getString("settings.logging.account-creation-fail.message")
                        .replace("<player>", playerName)
                        .replace("<uuid>", uuid.toString()));
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        instance.getPremiumBalances().remove(event.getPlayer().getUniqueId());
    }
}
