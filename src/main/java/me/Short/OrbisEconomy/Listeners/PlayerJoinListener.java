package me.Short.OrbisEconomy.Listeners;

import me.Short.OrbisEconomy.OrbisEconomy;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerJoinListener implements Listener
{

    // Instance of "OrbisEconomy"
    private OrbisEconomy instance;

    // Constructor
    public PlayerJoinListener(OrbisEconomy instance)
    {
        this.instance = instance;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        // Cache the player's username
        instance.getOfflinePlayerNames().put(uuid, playerName);

        Economy economy = instance.getEconomy();

        // If the player already has an account, return
        if (economy.hasAccount(player))
        {
            return;
        }

        FileConfiguration config = instance.getConfig();

        // Try to create an account for the player
        if (economy.createPlayerAccount(player))
        {
            // Log the successful account creation to console if config.yml says to do so
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
            // Log the failed account creation to console if config.yml says to do so
            if (config.getBoolean("settings.logging.account-creation-fail.log"))
            {
                instance.getLogger().log(Level.WARNING, config.getString("settings.logging.account-creation-fail.message")
                        .replace("<player>", playerName)
                        .replace("<uuid>", uuid.toString()));
            }
        }
    }

}