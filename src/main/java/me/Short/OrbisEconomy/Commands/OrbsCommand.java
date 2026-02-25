package me.Short.OrbisEconomy.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

@NullMarked
public class OrbsCommand
{
    private static final String ORBS_ID = "orbs";

    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName, OrbisEconomy instance)
    {
        return Commands.literal(commandName)
                .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.orbs"))
                .executes(ctx ->
                {
                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.orbs.help")));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("balance")
                        .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.orbs.balance"))
                        .executes(ctx ->
                        {
                            executeBalance(instance, ctx, null);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
                                .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.orbs.balance.others"))
                                .executes(ctx ->
                                {
                                    executeBalance(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(buildMutation(instance, "give", "orbiseconomy.command.orbs.give",
                        "messages.orbs.give.orbs-given-target", "messages.orbs.give.orbs-given-sender", false))
                .then(buildMutation(instance, "take", "orbiseconomy.command.orbs.take",
                        "messages.orbs.take.orbs-taken-target", "messages.orbs.take.orbs-taken-sender", false))
                .then(buildMutation(instance, "set", "orbiseconomy.command.orbs.set",
                        "messages.orbs.set.orbs-set-target", "messages.orbs.set.orbs-set-sender", true))
                .build();
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildMutation(
            OrbisEconomy instance, String literal, String permission, String targetPath, String senderPath, boolean nonNegative)
    {
        return Commands.literal(literal)
                .requires(sender -> sender.getSender().hasPermission(permission))
                .executes(ctx -> incorrectUsage(instance, ctx, literal + " <player name> <amount>"))
                .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
                        .executes(ctx -> incorrectUsage(instance, ctx, literal + " <player name> <amount>"))
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))
                                .executes(ctx ->
                                {
                                    executeMutation(instance, ctx.getSource().getSender(),
                                            ctx.getArgument("target player", OfflinePlayer.class),
                                            BigDecimal.valueOf(ctx.getArgument("amount", double.class)).stripTrailingZeros(),
                                            literal,
                                            targetPath,
                                            senderPath,
                                            nonNegative);
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    private static int incorrectUsage(OrbisEconomy instance, CommandContext<CommandSourceStack> ctx, String usage)
    {
        ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                Placeholder.component("argument_usage", Component.text(usage))));
        return Command.SINGLE_SUCCESS;
    }

    private static void executeBalance(OrbisEconomy instance, CommandContext<CommandSourceStack> ctx, @Nullable OfflinePlayer target)
    {
        CommandSender sender = ctx.getSource().getSender();
        if (target == null)
        {
            if (!(sender instanceof Player playerSender))
            {
                sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.console-cannot-use")));
                return;
            }
            target = playerSender;
        }

        Currency currency = CurrencyCommandService.resolveCurrency(instance, sender, ORBS_ID);
        if (currency == null)
        {
            return;
        }

        PlayerAccount account = CurrencyCommandService.requireAccount(instance, sender, target, target == sender);
        if (account == null)
        {
            return;
        }

        String formattedBalance = currency.formatAmount(account.getBalance(currency.getId()));
        sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString(target == sender ? "messages.orbs.your-balance" : "messages.orbs.their-balance"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("balance", Component.text(formattedBalance))));
    }

    private static void executeMutation(OrbisEconomy instance,
                                        CommandSender sender,
                                        OfflinePlayer target,
                                        BigDecimal amount,
                                        String mode,
                                        String targetPath,
                                        String senderPath,
                                        boolean nonNegative)
    {
        Currency currency = CurrencyCommandService.resolveCurrency(instance, sender, ORBS_ID);
        if (currency == null)
        {
            return;
        }

        PlayerAccount account = CurrencyCommandService.requireAccount(instance, sender, target, false);
        if (account == null || !CurrencyCommandService.validateDecimalPlaces(instance, sender, amount, currency))
        {
            return;
        }

        boolean validAmount = nonNegative
                ? CurrencyCommandService.validateNonNegative(instance, sender, amount)
                : CurrencyCommandService.validatePositive(instance, sender, amount);
        if (!validAmount)
        {
            return;
        }

        BigDecimal current = account.getBalance(currency.getId());
        BigDecimal newBalance;
        if (mode.equals("give"))
        {
            newBalance = current.add(amount);
        }
        else if (mode.equals("take"))
        {
            if (current.compareTo(amount) < 0)
            {
                sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.insufficient-funds-other"),
                        Placeholder.component("target", Component.text(target.getName())),
                        Placeholder.component("amount", Component.text(currency.formatAmount(amount)))));
                return;
            }
            newBalance = current.subtract(amount);
        }
        else
        {
            newBalance = amount;
        }

        if (!CurrencyCommandService.validateMaxBalance(instance, sender, target, currency, newBalance))
        {
            return;
        }

        account.setBalance(currency.getId(), newBalance);
        CurrencyCommandService.markDirty(instance, target.getUniqueId(), account);

        CurrencyCommandService.sendMutationMessages(instance, sender, target, targetPath, senderPath, "amount", currency.formatAmount(amount));
    }
}
