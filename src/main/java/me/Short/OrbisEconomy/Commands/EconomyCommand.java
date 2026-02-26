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
import me.Short.OrbisEconomy.OrbisEconomy;
import me.Short.OrbisEconomy.PlayerAccount;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.util.UUID;

@NullMarked
public class EconomyCommand
{

    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName, OrbisEconomy instance)
    {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(commandName)
                .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.economy"))
                .executes(ctx ->
                {
                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.economy.help")));
                    return Command.SINGLE_SUCCESS;
                });

        root.then(buildMutationNode(instance, "set", "orbiseconomy.command.economy.set", true,
                "messages.economy.set.balance-set-target", "messages.economy.set.balance-set-sender"));
        root.then(buildMutationNode(instance, "give", "orbiseconomy.command.economy.give", false,
                "messages.economy.give.money-given-target", "messages.economy.give.money-given-sender"));
        root.then(buildTakeNode(instance));
        root.then(buildResetNode(instance));

        root.then(Commands.literal("reload")
                .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.economy.reload"))
                .executes(ctx ->
                {
                    instance.reload();
                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.economy.reload")));
                    return Command.SINGLE_SUCCESS;
                }));

        root.then(Commands.literal("bridge-sync")
                .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.economy.reload"))
                .executes(ctx ->
                {
                    instance.attemptEconomyBridgeRegistration("manual /" + commandName + " bridge-sync");
                    ctx.getSource().getSender().sendMessage(Component.text("Triggered EconomyBridge provider sync. Check console for registered IDs."));
                    return Command.SINGLE_SUCCESS;
                }));

        return root.build();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildMutationNode(OrbisEconomy instance,
                                                                                 String literal,
                                                                                 String permission,
                                                                                 boolean nonNegative,
                                                                                 String targetPath,
                                                                                 String senderPath)
    {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(literal)
                .requires(sender -> sender.getSender().hasPermission(permission))
                .executes(ctx -> incorrectUsage(instance, ctx, literal + " <player name> <amount>"));

        for (String currencyId : instance.getCurrencies().keySet())
        {
            String usage = literal + " " + currencyId + " <player name> <amount>";
            node.then(Commands.literal(currencyId)
                    .executes(ctx -> incorrectUsage(instance, ctx, usage))
                    .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
                            .executes(ctx -> incorrectUsage(instance, ctx, usage))
                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))
                                    .executes(ctx ->
                                    {
                                        mutate(instance, ctx.getSource().getSender(),
                                                ctx.getArgument("target player", OfflinePlayer.class),
                                                BigDecimal.valueOf(ctx.getArgument("amount", double.class)).stripTrailingZeros(),
                                                currencyId,
                                                literal,
                                                nonNegative,
                                                targetPath,
                                                senderPath);
                                        return Command.SINGLE_SUCCESS;
                                    }))));
        }

        node.then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
                .executes(ctx -> incorrectUsage(instance, ctx, literal + " <player name> <amount>"))
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))
                        .executes(ctx ->
                        {
                            mutate(instance, ctx.getSource().getSender(),
                                    ctx.getArgument("target player", OfflinePlayer.class),
                                    BigDecimal.valueOf(ctx.getArgument("amount", double.class)).stripTrailingZeros(),
                                    "coins",
                                    literal,
                                    nonNegative,
                                    targetPath,
                                    senderPath);
                            return Command.SINGLE_SUCCESS;
                        })));

        return node;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTakeNode(OrbisEconomy instance)
    {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("take")
                .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.economy.take"))
                .executes(ctx -> incorrectUsage(instance, ctx, "take <player name> <amount>"));

        for (String currencyId : instance.getCurrencies().keySet())
        {
            String usage = "take " + currencyId + " <player name> <amount>";
            node.then(Commands.literal(currencyId)
                    .executes(ctx -> incorrectUsage(instance, ctx, usage))
                    .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
                            .executes(ctx -> incorrectUsage(instance, ctx, usage))
                            .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))
                                    .executes(ctx ->
                                    {
                                        take(instance, ctx.getSource().getSender(),
                                                ctx.getArgument("target player", OfflinePlayer.class),
                                                BigDecimal.valueOf(ctx.getArgument("amount", double.class)).stripTrailingZeros(),
                                                currencyId);
                                        return Command.SINGLE_SUCCESS;
                                    }))));
        }

        node.then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
                .executes(ctx -> incorrectUsage(instance, ctx, "take <player name> <amount>"))
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))
                        .executes(ctx ->
                        {
                            take(instance, ctx.getSource().getSender(),
                                    ctx.getArgument("target player", OfflinePlayer.class),
                                    BigDecimal.valueOf(ctx.getArgument("amount", double.class)).stripTrailingZeros(),
                                    "coins");
                            return Command.SINGLE_SUCCESS;
                        })));

        return node;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildResetNode(OrbisEconomy instance)
    {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("reset")
                .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.economy.reset"))
                .executes(ctx -> incorrectUsage(instance, ctx, "reset <player name>"));

        for (String currencyId : instance.getCurrencies().keySet())
        {
            node.then(Commands.literal(currencyId)
                    .executes(ctx -> incorrectUsage(instance, ctx, "reset " + currencyId + " <player name>"))
                    .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
                            .executes(ctx ->
                            {
                                reset(instance, ctx.getSource().getSender(), ctx.getArgument("target player", OfflinePlayer.class), currencyId);
                                return Command.SINGLE_SUCCESS;
                            })));
        }

        node.then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
                .executes(ctx ->
                {
                    reset(instance, ctx.getSource().getSender(), ctx.getArgument("target player", OfflinePlayer.class), "coins");
                    return Command.SINGLE_SUCCESS;
                }));

        return node;
    }

    private static int incorrectUsage(OrbisEconomy instance, CommandContext<CommandSourceStack> ctx, String argumentUsage)
    {
        ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                Placeholder.component("argument_usage", Component.text(argumentUsage))));
        return Command.SINGLE_SUCCESS;
    }

    private static void mutate(OrbisEconomy instance, CommandSender sender, OfflinePlayer target, BigDecimal amount, String currencyId,
                               String mode, boolean nonNegative, String targetPath, String senderPath)
    {
        Currency currency = CurrencyCommandService.resolveCurrency(instance, sender, currencyId);
        if (currency == null)
        {
            return;
        }

        PlayerAccount account = CurrencyCommandService.requireAccount(instance, sender, target, false);
        if (account == null || !CurrencyCommandService.validateDecimalPlaces(instance, sender, amount, currency))
        {
            return;
        }

        boolean valid = nonNegative
                ? CurrencyCommandService.validateNonNegative(instance, sender, amount)
                : CurrencyCommandService.validatePositive(instance, sender, amount);
        if (!valid)
        {
            return;
        }

        BigDecimal newBalance = mode.equals("set") ? amount : account.getBalance(currency.getId()).add(amount);
        if (!CurrencyCommandService.validateMaxBalance(instance, sender, target, currency, newBalance))
        {
            return;
        }

        account.setBalance(currency.getId(), newBalance);
        CurrencyCommandService.markDirty(instance, target.getUniqueId(), account);

        CurrencyCommandService.sendMutationMessages(instance, sender, target, targetPath, senderPath, "amount", currency.formatAmount(amount));
    }

    private static void take(OrbisEconomy instance, CommandSender sender, OfflinePlayer target, BigDecimal amount, String currencyId)
    {
        Currency currency = CurrencyCommandService.resolveCurrency(instance, sender, currencyId);
        if (currency == null)
        {
            return;
        }

        PlayerAccount account = CurrencyCommandService.requireAccount(instance, sender, target, false);
        if (account == null || !CurrencyCommandService.validateDecimalPlaces(instance, sender, amount, currency)
                || !CurrencyCommandService.validatePositive(instance, sender, amount))
        {
            return;
        }

        BigDecimal currentBalance = account.getBalance(currency.getId());
        if (currentBalance.compareTo(amount) < 0)
        {
            sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.insufficient-funds-other"),
                    Placeholder.component("target", Component.text(target.getName())),
                    Placeholder.component("amount", Component.text(currency.formatAmount(amount)))));
            return;
        }

        account.setBalance(currency.getId(), currentBalance.subtract(amount));
        CurrencyCommandService.markDirty(instance, target.getUniqueId(), account);

        CurrencyCommandService.sendMutationMessages(instance, sender, target,
                "messages.economy.take.money-taken-target",
                "messages.economy.take.money-taken-sender",
                "amount",
                currency.formatAmount(amount));
    }

    private static void reset(OrbisEconomy instance, CommandSender sender, OfflinePlayer target, String currencyId)
    {
        Currency currency = CurrencyCommandService.resolveCurrency(instance, sender, currencyId);
        if (currency == null)
        {
            return;
        }

        PlayerAccount account = CurrencyCommandService.requireAccount(instance, sender, target, false);
        if (account == null)
        {
            return;
        }

        BigDecimal defaultBalance = currency.getDefaultBalance();
        if (!CurrencyCommandService.validateNonNegative(instance, sender, defaultBalance)
                || !CurrencyCommandService.validateDecimalPlaces(instance, sender, defaultBalance, currency)
                || !CurrencyCommandService.validateMaxBalance(instance, sender, target, currency, defaultBalance))
        {
            return;
        }

        account.setBalance(currency.getId(), defaultBalance);
        UUID uuid = target.getUniqueId();
        CurrencyCommandService.markDirty(instance, uuid, account);

        String defaultBalanceFormatted = currency.formatAmount(defaultBalance);
        if (target instanceof org.bukkit.entity.Player targetPlayer)
        {
            targetPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.economy.reset.balance-reset-target"),
                    Placeholder.component("default_balance", Component.text(defaultBalanceFormatted))));
        }

        sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.economy.reset.balance-reset-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("default_balance", Component.text(defaultBalanceFormatted))));
    }
}
