package me.Short.OrbisEconomy.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.Short.OrbisEconomy.CustomCommandArguments.CachedOfflinePlayerArgument;
import me.Short.OrbisEconomy.OrbisEconomy;
import me.Short.OrbisEconomy.PlayerAccount;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;

@NullMarked
public class OrbsCommand
{

    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName, OrbisEconomy instance)
    {
        return Commands.literal(commandName)

                // Require permission
                .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.orbs"))

                .executes(ctx ->
                {
                    // Send the sender the help message
                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.orbs.help")));

                    return Command.SINGLE_SUCCESS;
                })

                .then(Commands.literal("balance")

                        // Require permission
                        .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.orbs.balance"))

                        .executes(ctx ->
                        {
                            executeBalanceLogic(instance, ctx, null);

                            return Command.SINGLE_SUCCESS;
                        })

                        .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))

                                // Require permission
                                .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.orbs.balance.others"))

                                .executes(ctx ->
                                {
                                    executeBalanceLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class));

                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )

                .then(Commands.literal("give")

                        // Require permission
                        .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.orbs.give"))

                        .executes(ctx ->
                        {
                            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                    Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                    Placeholder.component("argument_usage", Component.text("give <player name> <amount>"))));

                            return Command.SINGLE_SUCCESS;
                        })

                        .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))

                                .executes(ctx ->
                                {
                                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                            Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                            Placeholder.component("argument_usage", Component.text("give <player name> <amount>"))));

                                    return Command.SINGLE_SUCCESS;
                                })

                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))

                                        .executes(ctx ->
                                        {
                                            executeGiveLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), BigDecimal.valueOf(ctx.getArgument("amount", double.class)).stripTrailingZeros());

                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )

                .then(Commands.literal("take")

                        // Require permission
                        .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.orbs.take"))

                        .executes(ctx ->
                        {
                            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                    Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                    Placeholder.component("argument_usage", Component.text("take <player name> <amount>"))));

                            return Command.SINGLE_SUCCESS;
                        })

                        .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))

                                .executes(ctx ->
                                {
                                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                            Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                            Placeholder.component("argument_usage", Component.text("take <player name> <amount>"))));

                                    return Command.SINGLE_SUCCESS;
                                })

                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))

                                        .executes(ctx ->
                                        {
                                            executeTakeLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), BigDecimal.valueOf(ctx.getArgument("amount", double.class)).stripTrailingZeros());

                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )

                .then(Commands.literal("set")

                        // Require permission
                        .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.orbs.set"))

                        .executes(ctx ->
                        {
                            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                    Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                    Placeholder.component("argument_usage", Component.text("set <player name> <amount>"))));

                            return Command.SINGLE_SUCCESS;
                        })

                        .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))

                                .executes(ctx ->
                                {
                                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                            Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                            Placeholder.component("argument_usage", Component.text("set <player name> <amount>"))));

                                    return Command.SINGLE_SUCCESS;
                                })

                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))

                                        .executes(ctx ->
                                        {
                                            executeSetLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), BigDecimal.valueOf(ctx.getArgument("amount", double.class)).stripTrailingZeros());

                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                ).build();
    }

    // Method to execute the balance sub-command logic
    private static void executeBalanceLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, @Nullable OfflinePlayer target)
    {
        final CommandSender sender = ctx.getSource().getSender();

        if (target == null)
        {
            if (!(sender instanceof Player))
            {
                sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.console-cannot-use")));

                return;
            }

            target = (Player) sender;
        }

        // If the target player does not have an account, return
        if (!instance.getPlayerAccounts().containsKey(target.getUniqueId()))
        {
            sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString(target != sender ? "messages.error.no-account-other" : "messages.error.no-account"),
                    Placeholder.component("target", Component.text(target.getName()))));

            return;
        }

        BigDecimal orbsBalance = instance.getPlayerAccounts().get(target.getUniqueId()).getOrbsBalance();
        String balanceFormatted = formatOrbs(instance, orbsBalance);

        sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString(target != sender ? "messages.orbs.their-balance" : "messages.orbs.your-balance"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("balance", Component.text(balanceFormatted))));
    }

    // Method to execute the give sub-command logic
    private static void executeGiveLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, BigDecimal amount)
    {
        // If the target player does not have an account, return
        if (!instance.getPlayerAccounts().containsKey(target.getUniqueId()))
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.no-account-other"),
                    Placeholder.component("target", Component.text(target.getName()))));

            return;
        }

        // Amount must be greater than zero
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.not-greater-than-zero-amount")));

            return;
        }

        UUID uuid = target.getUniqueId();
        PlayerAccount account = instance.getPlayerAccounts().get(uuid);

        BigDecimal newBalance = account.getOrbsBalance().add(amount);

        // Check max balance
        BigDecimal maxBalance = new BigDecimal(instance.getConfig().getString("settings.currencies.orbs.max-balance"));
        if (newBalance.compareTo(maxBalance) > 0)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.would-exceed-max-balance"),
                    Placeholder.component("target", Component.text(target.getName()))));

            return;
        }

        account.setOrbsBalance(newBalance);
        instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

        FileConfiguration config = instance.getConfig();
        MiniMessage miniMessage = instance.getMiniMessage();
        String amountFormatted = formatOrbs(instance, amount);

        // Send message to the target player, if online
        if (target instanceof Player)
        {
            ((Player) target).sendMessage(miniMessage.deserialize(config.getString("messages.orbs.give.orbs-given-target"),
                    Placeholder.component("amount", Component.text(amountFormatted))));
        }

        ctx.getSource().getSender().sendMessage(miniMessage.deserialize(config.getString("messages.orbs.give.orbs-given-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("amount", Component.text(amountFormatted))));
    }

    // Method to execute the take sub-command logic
    private static void executeTakeLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, BigDecimal amount)
    {
        // If the target player does not have an account, return
        if (!instance.getPlayerAccounts().containsKey(target.getUniqueId()))
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.no-account-other"),
                    Placeholder.component("target", Component.text(target.getName()))));

            return;
        }

        // Amount must be greater than zero
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.not-greater-than-zero-amount")));

            return;
        }

        UUID uuid = target.getUniqueId();
        PlayerAccount account = instance.getPlayerAccounts().get(uuid);

        BigDecimal currentOrbsBalance = account.getOrbsBalance();

        // Check sufficient funds
        if (currentOrbsBalance.compareTo(amount) < 0)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.insufficient-funds-other"),
                    Placeholder.component("target", Component.text(target.getName())),
                    Placeholder.component("amount", Component.text(formatOrbs(instance, amount)))));

            return;
        }

        account.setOrbsBalance(currentOrbsBalance.subtract(amount));
        instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

        FileConfiguration config = instance.getConfig();
        MiniMessage miniMessage = instance.getMiniMessage();
        String amountFormatted = formatOrbs(instance, amount);

        // Send message to the target player, if online
        if (target.isOnline())
        {
            target.getPlayer().sendMessage(miniMessage.deserialize(config.getString("messages.orbs.take.orbs-taken-target"),
                    Placeholder.component("amount", Component.text(amountFormatted))));
        }

        ctx.getSource().getSender().sendMessage(miniMessage.deserialize(config.getString("messages.orbs.take.orbs-taken-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("amount", Component.text(amountFormatted))));
    }

    // Method to execute the set sub-command logic
    private static void executeSetLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, BigDecimal amount)
    {
        // If the target player does not have an account, return
        if (!instance.getPlayerAccounts().containsKey(target.getUniqueId()))
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.no-account-other"),
                    Placeholder.component("target", Component.text(target.getName()))));

            return;
        }

        // Amount must be non-negative
        if (amount.compareTo(BigDecimal.ZERO) < 0)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.negative-amount")));

            return;
        }

        // Check max balance
        BigDecimal maxBalance = new BigDecimal(instance.getConfig().getString("settings.currencies.orbs.max-balance"));
        if (amount.compareTo(maxBalance) > 0)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.would-exceed-max-balance"),
                    Placeholder.component("target", Component.text(target.getName()))));

            return;
        }

        UUID uuid = target.getUniqueId();
        PlayerAccount account = instance.getPlayerAccounts().get(uuid);

        account.setOrbsBalance(amount);
        instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

        FileConfiguration config = instance.getConfig();
        MiniMessage miniMessage = instance.getMiniMessage();
        String amountFormatted = formatOrbs(instance, amount);

        // Send message to the target player, if online
        if (target instanceof Player)
        {
            ((Player) target).sendMessage(miniMessage.deserialize(config.getString("messages.orbs.set.orbs-set-target"),
                    Placeholder.component("amount", Component.text(amountFormatted))));
        }

        ctx.getSource().getSender().sendMessage(miniMessage.deserialize(config.getString("messages.orbs.set.orbs-set-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("amount", Component.text(amountFormatted))));
    }

    // Helper method to format an Orbs amount using the configured format
    private static String formatOrbs(OrbisEconomy instance, BigDecimal amount)
    {
        String nameSingular = instance.getConfig().getString("settings.currencies.orbs.name-singular");
        String namePlural = instance.getConfig().getString("settings.currencies.orbs.name-plural");
        String format = instance.getConfig().getString("settings.currencies.orbs.format");

        String name = amount.compareTo(BigDecimal.ONE) == 0 ? nameSingular : namePlural;

        return format
                .replace("<amount>", amount.stripTrailingZeros().toPlainString())
                .replace("<name>", name);
    }

}
