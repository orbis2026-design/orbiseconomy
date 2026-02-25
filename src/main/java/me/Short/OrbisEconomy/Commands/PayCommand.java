package me.Short.OrbisEconomy.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.Short.OrbisEconomy.Currency;
import me.Short.OrbisEconomy.CustomCommandArguments.CachedOfflinePlayerArgument;
import me.Short.OrbisEconomy.Events.PlayerPayPlayerEvent;
import me.Short.OrbisEconomy.OrbisEconomy;
import me.Short.OrbisEconomy.PlayerAccount;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@NullMarked
public class PayCommand
{

    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName, OrbisEconomy instance)
    {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(commandName)
                .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.pay"))
                .executes(ctx ->
                {
                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                            Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                            Placeholder.component("argument_usage", Component.text("<player name> <amount>"))));
                    return Command.SINGLE_SUCCESS;
                });

        for (String currencyId : instance.getCurrencies().keySet())
        {
            final String fid = currencyId;
            root.then(Commands.literal(currencyId)
                    .executes(ctx ->
                    {
                        ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                Placeholder.component("argument_usage", Component.text(fid + " <player name> <amount>"))));
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
                            .suggests((ctx, builder) -> CompletableFuture.supplyAsync(() ->
                            {
                                if (ctx.getSource().getSender() instanceof Player senderPlayer)
                                {
                                    instance.getOfflinePlayerNames().values().stream()
                                            .filter(name -> !name.equals(senderPlayer.getName()) && name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
                                            .forEach(builder::suggest);
                                }
                                else
                                {
                                    instance.getOfflinePlayerNames().values().stream()
                                            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
                                            .forEach(builder::suggest);
                                }
                                return builder.build();
                            }))
                            .executes(ctx ->
                            {
                                ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                        Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                        Placeholder.component("argument_usage", Component.text(fid + " <player name> <amount>"))));
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))
                                    .executes(ctx ->
                                    {
                                        executeCommandLogic(instance, ctx,
                                                ctx.getArgument("target player", OfflinePlayer.class),
                                                BigDecimal.valueOf(ctx.getArgument("amount", double.class)).stripTrailingZeros(),
                                                fid);
                                        return Command.SINGLE_SUCCESS;
                                    }))));
        }

        root.then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
                .suggests((ctx, builder) -> CompletableFuture.supplyAsync(() ->
                {
                    if (ctx.getSource().getSender() instanceof Player senderPlayer)
                    {
                        instance.getOfflinePlayerNames().values().stream()
                                .filter(name -> !name.equals(senderPlayer.getName()) && name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
                                .forEach(builder::suggest);
                    }
                    else
                    {
                        instance.getOfflinePlayerNames().values().stream()
                                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase()))
                                .forEach(builder::suggest);
                    }
                    return builder.build();
                }))
                .executes(ctx ->
                {
                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                            Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                            Placeholder.component("argument_usage", Component.text("<player name> <amount>"))));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))
                        .executes(ctx ->
                        {
                            executeCommandLogic(instance, ctx,
                                    ctx.getArgument("target player", OfflinePlayer.class),
                                    BigDecimal.valueOf(ctx.getArgument("amount", double.class)).stripTrailingZeros(),
                                    "coins");
                            return Command.SINGLE_SUCCESS;
                        })));

        return root.build();
    }

    private static void executeCommandLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, BigDecimal amount, String currencyId)
    {
        final CommandSender sender = ctx.getSource().getSender();

        if (!(sender instanceof Player senderPlayer))
        {
            sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.console-cannot-use")));
            return;
        }

        if (target.getUniqueId().equals(senderPlayer.getUniqueId()))
        {
            sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.cannot-pay-yourself")));
            return;
        }

        Map<UUID, PlayerAccount> playerAccounts = instance.getPlayerAccounts();
        PlayerAccount senderAccount = CurrencyCommandService.requireAccount(instance, senderPlayer, senderPlayer, true);
        PlayerAccount targetAccount = CurrencyCommandService.requireAccount(instance, senderPlayer, target, false);
        if (senderAccount == null || targetAccount == null)
        {
            return;
        }

        if (!targetAccount.getAcceptingPayments())
        {
            senderPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.not-accepting-payments"),
                    Placeholder.component("target", Component.text(target.getName()))));
            return;
        }

        PlayerPayPlayerEvent playerPayPlayerEvent = new PlayerPayPlayerEvent(senderPlayer, target, amount);
        Bukkit.getServer().getPluginManager().callEvent(playerPayPlayerEvent);
        if (playerPayPlayerEvent.isCancelled())
        {
            return;
        }

        senderPlayer = playerPayPlayerEvent.getSender();
        target = playerPayPlayerEvent.getRecipient();
        amount = playerPayPlayerEvent.getAmount();

        Currency currency = CurrencyCommandService.resolveCurrency(instance, senderPlayer, currencyId);
        if (currency == null
                || !CurrencyCommandService.validateDecimalPlaces(instance, senderPlayer, amount, currency)
                || !CurrencyCommandService.validatePositive(instance, senderPlayer, amount))
        {
            return;
        }

        senderAccount = playerAccounts.get(senderPlayer.getUniqueId());
        targetAccount = playerAccounts.get(target.getUniqueId());
        if (senderAccount == null || targetAccount == null)
        {
            senderPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.no-account")));
            return;
        }

        BigDecimal senderBalance = senderAccount.getBalance(currency.getId());
        if (senderBalance.compareTo(amount) < 0)
        {
            senderPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.insufficient-funds"),
                    Placeholder.component("amount", Component.text(currency.formatAmount(amount)))));
            return;
        }

        BigDecimal newTargetBalance = targetAccount.getBalance(currency.getId()).add(amount);
        if (!CurrencyCommandService.validateMaxBalance(instance, senderPlayer, target, currency, newTargetBalance))
        {
            return;
        }

        senderAccount.setBalance(currency.getId(), senderBalance.subtract(amount));
        targetAccount.setBalance(currency.getId(), newTargetBalance);

        UUID senderUuid = senderPlayer.getUniqueId();
        UUID targetUuid = target.getUniqueId();
        CurrencyCommandService.markDirty(instance, senderUuid, senderAccount);
        CurrencyCommandService.markDirty(instance, targetUuid, targetAccount);

        FileConfiguration config = instance.getConfig();
        MiniMessage miniMessage = instance.getMiniMessage();
        String amountFormatted = currency.formatAmount(amount);

        if (target instanceof Player targetPlayer)
        {
            targetPlayer.sendMessage(miniMessage.deserialize(config.getString("messages.pay.paid-target"),
                    Placeholder.component("player", Component.text(senderPlayer.getName())),
                    Placeholder.component("amount", Component.text(amountFormatted))));
        }

        senderPlayer.sendMessage(miniMessage.deserialize(config.getString("messages.pay.paid-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("amount", Component.text(amountFormatted))));
    }
}
