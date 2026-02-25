package me.Short.OrbisEconomy.Commands;

import me.Short.OrbisEconomy.Currency;
import me.Short.OrbisEconomy.OrbisEconomy;
import me.Short.OrbisEconomy.PlayerAccount;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.UUID;

final class CurrencyCommandService
{

    private CurrencyCommandService()
    {
    }

    static Currency resolveCurrency(OrbisEconomy instance, CommandSender sender, String currencyId)
    {
        String normalizedCurrencyId = OrbisEconomy.normalizeCurrencyId(currencyId);
        Currency currency = instance.getCurrencies().get(normalizedCurrencyId);

        if (currency == null)
        {
            sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.unknown-currency"),
                    Placeholder.component("currency", Component.text(currencyId))));
            return null;
        }

        return currency;
    }

    static PlayerAccount requireAccount(OrbisEconomy instance, CommandSender sender, OfflinePlayer target, boolean self)
    {
        PlayerAccount account = instance.getPlayerAccounts().get(target.getUniqueId());

        if (account == null)
        {
            sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString(self ? "messages.error.no-account" : "messages.error.no-account-other"),
                    Placeholder.component("target", Component.text(target.getName()))));
            return null;
        }

        return account;
    }

    static boolean validateDecimalPlaces(OrbisEconomy instance, CommandSender sender, BigDecimal amount, Currency currency)
    {
        if (amount.scale() > currency.getDecimalPlaces())
        {
            sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                    Placeholder.component("amount", Component.text(amount.toPlainString())),
                    Placeholder.component("decimal_places", Component.text(currency.getDecimalPlaces()))));
            return false;
        }

        return true;
    }

    static boolean validatePositive(OrbisEconomy instance, CommandSender sender, BigDecimal amount)
    {
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
        {
            sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.not-greater-than-zero-amount")));
            return false;
        }

        return true;
    }

    static boolean validateNonNegative(OrbisEconomy instance, CommandSender sender, BigDecimal amount)
    {
        if (amount.compareTo(BigDecimal.ZERO) < 0)
        {
            sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.negative-amount")));
            return false;
        }

        return true;
    }

    static boolean validateMaxBalance(OrbisEconomy instance, CommandSender sender, OfflinePlayer target, Currency currency, BigDecimal amount)
    {
        if (amount.compareTo(currency.getMaxBalance()) > 0)
        {
            sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.would-exceed-max-balance"),
                    Placeholder.component("target", Component.text(target.getName()))));
            return false;
        }

        return true;
    }

    static void markDirty(OrbisEconomy instance, UUID uuid, PlayerAccount account)
    {
        instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());
    }

    static void sendMutationMessages(OrbisEconomy instance,
                                     CommandSender sender,
                                     OfflinePlayer target,
                                     String targetMessagePath,
                                     String senderMessagePath,
                                     String amountPlaceholder,
                                     String amountFormatted)
    {
        if (target instanceof Player targetPlayer)
        {
            targetPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString(targetMessagePath),
                    Placeholder.component(amountPlaceholder, Component.text(amountFormatted))));
        }

        sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString(senderMessagePath),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component(amountPlaceholder, Component.text(amountFormatted))));
    }
}
