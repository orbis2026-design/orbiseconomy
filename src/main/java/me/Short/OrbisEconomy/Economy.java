package me.Short.OrbisEconomy;

import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class Economy implements net.milkbowl.vault.economy.Economy
{

    // EconomyResponse error messages
    private static final String ERROR_INSUFFICIENT_FUNDS = "Insufficient funds.";
    private static final String ERROR_WOULD_EXCEED_MAX_BALANCE = "Would exceed the configured maximum balance.";
    private static final String ERROR_TOO_MANY_DECIMAL_PLACES = "Too many decimal places.";
    private static final String ERROR_NOT_GREATER_THAN_ZERO = "Amount is not greater than zero.";
    private static final String ERROR_ACCOUNT_NOT_FOUND = "Player account is unavailable.";

    // Instance of "OrbisEconomy"
    private OrbisEconomy instance;

    // Constructor
    public Economy(OrbisEconomy instance)
    {
        this.instance = instance;
    }

    @Override
    public boolean isEnabled()
    {
        return instance.isEnabled();
    }

    @Override
    public String getName()
    {
        return instance.getName();
    }

    @Override
    public boolean hasBankSupport()
    {
        return false;
    }

    @Override
    public int fractionalDigits()
    {
        return instance.getConfig().getInt("settings.currencies.coins.decimal-places");
    }

    @Override
    public String format(double amount)
    {
        int fractionalDigits = fractionalDigits();

        // Determine decimal format based on the configured number of decimal places ("fractionalDigits")
        DecimalFormat df;
        if (fractionalDigits > 0 && amount % 1 != 0)
        {
            df = new DecimalFormat("#,##0." + StringUtils.repeat("0", fractionalDigits));
        }
        else
        {
            df = new DecimalFormat("#,##0");
        }

        // Return formatted output, applying decimal format to the amount
        return instance.getConfig().getString("settings.currencies.coins.format")
                .replace("<amount>", df.format(amount))
                .replace("<name>", amount == 1D ? currencyNameSingular() : currencyNamePlural());
    }

    @Override
    public String currencyNamePlural()
    {
        return instance.getConfig().getString("settings.currencies.coins.name-plural");
    }

    @Override
    public String currencyNameSingular()
    {
        return instance.getConfig().getString("settings.currencies.coins.name-singular");
    }

    @Override
    public boolean hasAccount(OfflinePlayer player)
    {
        UUID uuid = player == null ? null : player.getUniqueId();
        return getAccountOrNull(uuid) != null;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName)
    {
        return hasAccount(player);
    }

    @Override
    public double getBalance(OfflinePlayer player)
    {
        if (player == null)
        {
            return 0D;
        }

        UUID uuid = player.getUniqueId();
        PlayerAccount account = getAccountOrNull(uuid);
        if (account == null && createPlayerAccount(player))
        {
            account = getAccountOrNull(uuid);
        }

        if (account == null)
        {
            return 0D;
        }

        return account.getBalance().doubleValue();
    }

    @Override
    public double getBalance(OfflinePlayer player, String world)
    {
        return getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount)
    {
        if (player == null)
        {
            return false;
        }

        UUID uuid = player.getUniqueId();
        PlayerAccount account = getAccountOrNull(uuid);
        if (account == null && createPlayerAccount(player))
        {
            account = getAccountOrNull(uuid);
        }

        return account != null && account.getBalance().compareTo(BigDecimal.valueOf(amount)) >= 0;
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount)
    {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount)
    {
        FileConfiguration config = instance.getConfig();

        if (player == null)
        {
            return new EconomyResponse(0D, 0D, ResponseType.FAILURE, ERROR_ACCOUNT_NOT_FOUND);
        }

        UUID uuid = player.getUniqueId();
        String playerName = safePlayerName(player);

        PlayerAccount account = getAccountOrNull(uuid);
        if (account == null && createPlayerAccount(player))
        {
            account = getAccountOrNull(uuid);
        }

        if (account == null)
        {
            if (config.getBoolean("settings.logging.vault-withdraw-fail.log"))
            {
                instance.getLogger().log(Level.WARNING, config.getString("settings.logging.vault-withdraw-fail.message")
                        .replace("<player>", playerName)
                        .replace("<uuid>", uuid.toString())
                        .replace("<amount>", BigDecimal.valueOf(amount).toPlainString())
                        .replace("<error_message>", ERROR_ACCOUNT_NOT_FOUND));
            }

            return new EconomyResponse(0D, 0D, ResponseType.FAILURE, ERROR_ACCOUNT_NOT_FOUND);
        }

        BigDecimal currentBalance = account.getBalance();
        BigDecimal bdAmount = Util.round(BigDecimal.valueOf(amount), fractionalDigits(), RoundingMode.valueOf(config.getString("settings.currencies.coins.rounding-mode"))).stripTrailingZeros();
        double bdAmountDoubleValue = bdAmount.doubleValue();

        // If the amount is 0 or less, log error, and return failure economy response
        if (bdAmount.compareTo(BigDecimal.ZERO) <= 0)
        {
            // Log the failure to the console if config.yml says to do so
            if (config.getBoolean("settings.logging.vault-withdraw-fail.log"))
            {
                instance.getLogger().log(Level.WARNING, config.getString("settings.logging.vault-withdraw-fail.message")
                        .replace("<player>", playerName)
                        .replace("<uuid>", uuid.toString())
                        .replace("<amount>", bdAmount.toPlainString())
                        .replace("<error_message>", ERROR_NOT_GREATER_THAN_ZERO));
            }

            return new EconomyResponse(bdAmountDoubleValue, currentBalance.doubleValue(), ResponseType.FAILURE, ERROR_NOT_GREATER_THAN_ZERO);
        }

        // If the amount uses more decimal places than the configured amount, log error, and return failure economy response
        if (bdAmount.scale() > fractionalDigits())
        {
            // Log the failure to the console if config.yml says to do so
            if (config.getBoolean("settings.logging.vault-withdraw-fail.log"))
            {
                instance.getLogger().log(Level.WARNING, config.getString("settings.logging.vault-withdraw-fail.message")
                        .replace("<player>", playerName)
                        .replace("<uuid>", uuid.toString())
                        .replace("<amount>", bdAmount.toPlainString())
                        .replace("<error_message>", ERROR_TOO_MANY_DECIMAL_PLACES));
            }

            return new EconomyResponse(bdAmountDoubleValue, currentBalance.doubleValue(), ResponseType.FAILURE, ERROR_TOO_MANY_DECIMAL_PLACES);
        }

        // If the player does not have enough money, log error, and return failure economy response
        if (!has(player, bdAmountDoubleValue))
        {
            // Log the failure to the console if config.yml says to do so
            if (config.getBoolean("settings.logging.vault-withdraw-fail.log"))
            {
                instance.getLogger().log(Level.WARNING, config.getString("settings.logging.vault-withdraw-fail.message")
                        .replace("<player>", playerName)
                        .replace("<uuid>", uuid.toString())
                        .replace("<amount>", bdAmount.toPlainString())
                        .replace("<error_message>", ERROR_INSUFFICIENT_FUNDS));
            }

            return new EconomyResponse(bdAmountDoubleValue, currentBalance.doubleValue(), ResponseType.FAILURE, ERROR_INSUFFICIENT_FUNDS);
        }

        BigDecimal resultingBalance = currentBalance.subtract(bdAmount);

        // Update the player's balance
        account.setBalance(resultingBalance);

        // Mark for saving
        instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

        // Log the change to the console if config.yml says to do so
        if (config.getBoolean("settings.logging.vault-withdraw-success.log"))
        {
            instance.getLogger().log(Level.INFO, config.getString("settings.logging.vault-withdraw-success.message")
                    .replace("<player>", playerName)
                    .replace("<uuid>", uuid.toString())
                    .replace("<amount>", bdAmount.toPlainString())
                    .replace("<balance>", resultingBalance.toPlainString()));
        }

        return new EconomyResponse(bdAmountDoubleValue, resultingBalance.doubleValue(), ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player,	String worldName, double amount)
    {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount)
    {
        FileConfiguration config = instance.getConfig();

        if (player == null)
        {
            return new EconomyResponse(0D, 0D, ResponseType.FAILURE, ERROR_ACCOUNT_NOT_FOUND);
        }

        UUID uuid = player.getUniqueId();
        String playerName = safePlayerName(player);

        PlayerAccount account = getAccountOrNull(uuid);
        if (account == null && createPlayerAccount(player))
        {
            account = getAccountOrNull(uuid);
        }

        if (account == null)
        {
            if (config.getBoolean("settings.logging.vault-deposit-fail.log"))
            {
                instance.getLogger().log(Level.WARNING, config.getString("settings.logging.vault-deposit-fail.message")
                        .replace("<player>", playerName)
                        .replace("<uuid>", uuid.toString())
                        .replace("<amount>", BigDecimal.valueOf(amount).toPlainString())
                        .replace("<error_message>", ERROR_ACCOUNT_NOT_FOUND));
            }

            return new EconomyResponse(0D, 0D, ResponseType.FAILURE, ERROR_ACCOUNT_NOT_FOUND);
        }

        BigDecimal currentBalance = account.getBalance();
        BigDecimal bdAmount = Util.round(BigDecimal.valueOf(amount), fractionalDigits(), RoundingMode.valueOf(config.getString("settings.currencies.coins.rounding-mode"))).stripTrailingZeros();
        double bdAmountDoubleValue = bdAmount.doubleValue();

        // If the amount is 0 or less, log error, and return failure economy response
        if (bdAmount.compareTo(BigDecimal.ZERO) <= 0)
        {
            // Log the failure to the console if config.yml says to do so
            if (config.getBoolean("settings.logging.vault-deposit-fail.log"))
            {
                instance.getLogger().log(Level.WARNING, config.getString("settings.logging.vault-deposit-fail.message")
                        .replace("<player>", playerName)
                        .replace("<uuid>", uuid.toString())
                        .replace("<amount>", bdAmount.toPlainString())
                        .replace("<error_message>", ERROR_NOT_GREATER_THAN_ZERO));
            }

            return new EconomyResponse(bdAmountDoubleValue, currentBalance.doubleValue(), ResponseType.FAILURE, ERROR_NOT_GREATER_THAN_ZERO);
        }

        // If the amount uses more decimal places than the configured amount, log error, and return failure economy response
        if (bdAmount.scale() > fractionalDigits())
        {
            // Log the failure to the console if config.yml says to do so
            if (config.getBoolean("settings.logging.vault-deposit-fail.log"))
            {
                instance.getLogger().log(Level.WARNING, config.getString("settings.logging.vault-deposit-fail.message")
                        .replace("<player>", playerName)
                        .replace("<uuid>", uuid.toString())
                        .replace("<amount>", bdAmount.toPlainString())
                        .replace("<error_message>", ERROR_TOO_MANY_DECIMAL_PLACES));
            }

            return new EconomyResponse(bdAmountDoubleValue, currentBalance.doubleValue(), ResponseType.FAILURE, ERROR_TOO_MANY_DECIMAL_PLACES);
        }

        BigDecimal resultingBalance = currentBalance.add(bdAmount);

        // If the resulting balance is greater than the configured maximum balance, log error, and return failure economy response
        if (resultingBalance.compareTo(new BigDecimal(config.getString("settings.currencies.coins.max-balance"))) > 0)
        {
            // Log the failure to the console if config.yml says to do so
            if (config.getBoolean("settings.logging.vault-deposit-fail.log"))
            {
                instance.getLogger().log(Level.WARNING, config.getString("settings.logging.vault-deposit-fail.message")
                        .replace("<player>", playerName)
                        .replace("<uuid>", uuid.toString())
                        .replace("<amount>", bdAmount.toPlainString())
                        .replace("<error_message>", ERROR_WOULD_EXCEED_MAX_BALANCE));
            }

            return new EconomyResponse(bdAmountDoubleValue, currentBalance.doubleValue(), ResponseType.FAILURE, ERROR_WOULD_EXCEED_MAX_BALANCE);
        }

        // Update the player's balance
        account.setBalance(resultingBalance);

        // Mark for saving
        instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

        // Log the change to the console if config.yml says to do so
        if (config.getBoolean("settings.logging.vault-deposit-success.log"))
        {
            instance.getLogger().log(Level.INFO, config.getString("settings.logging.vault-deposit-success.message")
                    .replace("<player>", playerName)
                    .replace("<uuid>", uuid.toString())
                    .replace("<amount>", bdAmount.toPlainString())
                    .replace("<balance>", resultingBalance.toPlainString()));
        }

        return new EconomyResponse(bdAmountDoubleValue, resultingBalance.doubleValue(), ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount)
    {
        return depositPlayer(player, amount);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player)
    {
        if (player == null)
        {
            return false;
        }

        UUID uuid = player.getUniqueId();
        if (uuid == null)
        {
            return false;
        }

        if (getAccountOrNull(uuid) != null)
        {
            return true;
        }

        BigDecimal defaultBalance = new BigDecimal(instance.getConfig().getString("settings.currencies.coins.default-balance")).stripTrailingZeros();

        // If the default balance is less than 0, uses more decimal places than what is configured, or is greater than the configured maximum balance, return `false`, indicating that the account creation was unsuccessful
        if (defaultBalance.compareTo(BigDecimal.ZERO) < 0 || defaultBalance.scale() > fractionalDigits() || defaultBalance.compareTo(new BigDecimal(instance.getConfig().getString("settings.currencies.coins.max-balance"))) > 0)
        {
            return false;
        }

        // Create new player account with default balances for all configured currencies
        PlayerAccount account = instance.createDefaultAccount();

        // Add account to the cache
        instance.getPlayerAccounts().put(uuid, account);

        // Mark for saving
        instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName)
    {
        return createPlayerAccount(player);
    }

    @Override
    public EconomyResponse createBank(String name, String player)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse deleteBank(String name)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse bankBalance(String name)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse bankHas(String name, double amount)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player)
    {
        return new EconomyResponse(0D, 0D, ResponseType.NOT_IMPLEMENTED, null);
    }

    @Override
    public List<String> getBanks()
    {
        return null;
    }

    // ----- Getters for error messages -----
    public static String getErrorInsufficientFunds()
    {
        return ERROR_INSUFFICIENT_FUNDS;
    }

    public static String getErrorWouldExceedMaxBalance()
    {
        return ERROR_WOULD_EXCEED_MAX_BALANCE;
    }

    public static String getErrorTooManyDecimalPlaces()
    {
        return ERROR_TOO_MANY_DECIMAL_PLACES;
    }

    public static String getErrorNotGreaterThanZero()
    {
        return ERROR_NOT_GREATER_THAN_ZERO;
    }

    private PlayerAccount getAccountOrNull(UUID uuid)
    {
        if (uuid == null)
        {
            return null;
        }

        return instance.getPlayerAccounts().get(uuid);
    }

    private String safePlayerName(OfflinePlayer player)
    {
        if (player == null || player.getName() == null)
        {
            return "unknown";
        }

        return player.getName();
    }

    // ----- Deprecated Economy methods -----

    @SuppressWarnings("deprecation")
    @Override
    public boolean hasAccount(String playerName)
    {
        return hasAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean hasAccount(String playerName, String worldName)
    {
        return hasAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @SuppressWarnings("deprecation")
    @Override
    public double getBalance(String playerName)
    {
        return getBalance(Bukkit.getOfflinePlayer(playerName));
    }

    @SuppressWarnings("deprecation")
    @Override
    public double getBalance(String playerName, String world)
    {
        return getBalance(Bukkit.getOfflinePlayer(playerName));
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean has(String playerName, double amount)
    {
        return has(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean has(String playerName, String worldName, double amount)
    {
        return has(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @SuppressWarnings("deprecation")
    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount)
    {
        return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @SuppressWarnings("deprecation")
    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount)
    {
        return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @SuppressWarnings("deprecation")
    @Override
    public EconomyResponse depositPlayer(String playerName, double amount)
    {
        return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @SuppressWarnings("deprecation")
    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount)
    {
        return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean createPlayerAccount(String playerName)
    {
        return createPlayerAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean createPlayerAccount(String playerName, String worldName)
    {
        return createPlayerAccount(Bukkit.getOfflinePlayer(playerName));
    }

}
