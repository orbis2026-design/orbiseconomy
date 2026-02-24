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
import me.Short.OrbisEconomy.PlayerAccount;
import me.Short.OrbisEconomy.OrbisEconomy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.logging.Level;

@NullMarked
public class EconomyCommand
{

    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName, OrbisEconomy instance)
    {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(commandName)

                // Require permission
                .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.economy"))

                .executes(ctx ->
                {
                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.economy.help")));
                    return Command.SINGLE_SUCCESS;
                });

        // ----- "set" sub-command -----
        {
            LiteralArgumentBuilder<CommandSourceStack> setNode = Commands.literal("set")
                    .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.economy.set"))
                    .executes(ctx ->
                    {
                        ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                Placeholder.component("argument_usage", Component.text("set <player name> <amount>"))));
                        return Command.SINGLE_SUCCESS;
                    });

            // Dynamic currency literal nodes: /eco set <currencyId> <player> <amount>
            for (String currencyId : instance.getCurrencies().keySet())
            {
                final String fid = currencyId;
                setNode.then(Commands.literal(currencyId)
                        .executes(ctx ->
                        {
                            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                    Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                    Placeholder.component("argument_usage", Component.text("set " + fid + " <player name> <amount>"))));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
                                .executes(ctx ->
                                {
                                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                            Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                            Placeholder.component("argument_usage", Component.text("set " + fid + " <player name> <amount>"))));
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))
                                        .executes(ctx ->
                                        {
                                            executeSetLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), BigDecimal.valueOf(ctx.getArgument("amount", double.class)).stripTrailingZeros(), fid);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                );
            }

            // Default: /eco set <player> <amount> -> coins
            setNode.then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
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
                                executeSetLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), BigDecimal.valueOf(ctx.getArgument("amount", double.class)).stripTrailingZeros(), "coins");
                                return Command.SINGLE_SUCCESS;
                            })
                    )
            );

            root.then(setNode);
        }

        // ----- "give" sub-command -----
        {
            LiteralArgumentBuilder<CommandSourceStack> giveNode = Commands.literal("give")
                    .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.economy.give"))
                    .executes(ctx ->
                    {
                        ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                Placeholder.component("argument_usage", Component.text("give <player name> <amount>"))));
                        return Command.SINGLE_SUCCESS;
                    });

            // Dynamic currency literal nodes: /eco give <currencyId> <player> <amount>
            for (String currencyId : instance.getCurrencies().keySet())
            {
                final String fid = currencyId;
                giveNode.then(Commands.literal(currencyId)
                        .executes(ctx ->
                        {
                            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                    Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                    Placeholder.component("argument_usage", Component.text("give " + fid + " <player name> <amount>"))));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
                                .executes(ctx ->
                                {
                                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                            Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                            Placeholder.component("argument_usage", Component.text("give " + fid + " <player name> <amount>"))));
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))
                                        .executes(ctx ->
                                        {
                                            executeGiveLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), BigDecimal.valueOf(ctx.getArgument("amount", double.class)).stripTrailingZeros(), fid);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                );
            }

            // Default: /eco give <player> <amount> -> coins
            giveNode.then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
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
                                executeGiveLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), BigDecimal.valueOf(ctx.getArgument("amount", double.class)).stripTrailingZeros(), "coins");
                                return Command.SINGLE_SUCCESS;
                            })
                    )
            );

            root.then(giveNode);
        }

        // ----- "take" sub-command -----
        {
            LiteralArgumentBuilder<CommandSourceStack> takeNode = Commands.literal("take")
                    .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.economy.take"))
                    .executes(ctx ->
                    {
                        ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                Placeholder.component("argument_usage", Component.text("take <player name> <amount>"))));
                        return Command.SINGLE_SUCCESS;
                    });

            // Dynamic currency literal nodes: /eco take <currencyId> <player> <amount>
            for (String currencyId : instance.getCurrencies().keySet())
            {
                final String fid = currencyId;
                takeNode.then(Commands.literal(currencyId)
                        .executes(ctx ->
                        {
                            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                    Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                    Placeholder.component("argument_usage", Component.text("take " + fid + " <player name> <amount>"))));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
                                .executes(ctx ->
                                {
                                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                            Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                            Placeholder.component("argument_usage", Component.text("take " + fid + " <player name> <amount>"))));
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0D))
                                        .executes(ctx ->
                                        {
                                            executeTakeLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), BigDecimal.valueOf(ctx.getArgument("amount", double.class)).stripTrailingZeros(), fid);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                );
            }

            // Default: /eco take <player> <amount> -> coins
            takeNode.then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
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
                                executeTakeLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), BigDecimal.valueOf(ctx.getArgument("amount", double.class)).stripTrailingZeros(), "coins");
                                return Command.SINGLE_SUCCESS;
                            })
                    )
            );

            root.then(takeNode);
        }

        // ----- "reset" sub-command -----
        {
            LiteralArgumentBuilder<CommandSourceStack> resetNode = Commands.literal("reset")
                    .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.economy.reset"))
                    .executes(ctx ->
                    {
                        ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                Placeholder.component("argument_usage", Component.text("reset <player name>"))));
                        return Command.SINGLE_SUCCESS;
                    });

            // Dynamic currency literal nodes: /eco reset <currencyId> <player>
            for (String currencyId : instance.getCurrencies().keySet())
            {
                final String fid = currencyId;
                resetNode.then(Commands.literal(currencyId)
                        .executes(ctx ->
                        {
                            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                                    Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                                    Placeholder.component("argument_usage", Component.text("reset " + fid + " <player name>"))));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
                                .executes(ctx ->
                                {
                                    executeResetLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), fid);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                );
            }

            // Default: /eco reset <player> -> coins
            resetNode.then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
                    .executes(ctx ->
                    {
                        executeResetLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), "coins");
                        return Command.SINGLE_SUCCESS;
                    })
            );

            root.then(resetNode);
        }

        // ----- "reload" sub-command -----
        root.then(Commands.literal("reload")
                .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.economy.reload"))
                .executes(ctx ->
                {
                    executeReloadLogic(instance, ctx);
                    return Command.SINGLE_SUCCESS;
                })
        );

        return root.build();
    }

    // Method to execute the logic for the "set" sub-command
    private static void executeSetLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, BigDecimal amount, String currencyId)
    {
        if (!instance.getPlayerAccounts().containsKey(target.getUniqueId()))
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.no-account-other"),
                    Placeholder.component("target", Component.text(target.getName()))));
            return;
        }

        if (currencyId.equals("coins"))
        {
            executeSetCoinsLogic(instance, ctx, target, amount);
        }
        else
        {
            executeSetCurrencyLogic(instance, ctx, target, amount, currencyId);
        }
    }

    // Coins "set" routed through Vault for logging compatibility
    private static void executeSetCoinsLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, BigDecimal amount)
    {
        net.milkbowl.vault.economy.Economy economy = instance.getEconomy();
        int decimalPlaces = economy.fractionalDigits();

        if (amount.scale() > decimalPlaces)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                    Placeholder.component("amount", Component.text(amount.toPlainString())),
                    Placeholder.component("decimal_places", Component.text(decimalPlaces))));
            return;
        }

        FileConfiguration config = instance.getConfig();

        if (amount.compareTo(new BigDecimal(config.getString("settings.currencies.coins.max-balance"))) > 0)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(config.getString("messages.error.would-exceed-max-balance"),
                    Placeholder.component("target", Component.text(target.getName()))));
            return;
        }

        MiniMessage miniMessage = instance.getMiniMessage();
        UUID uuid = target.getUniqueId();
        PlayerAccount account = instance.getPlayerAccounts().get(uuid);

        account.setBalance("coins", amount);
        instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

        String amountFormatted = economy.format(amount.doubleValue());

        if (config.getBoolean("settings.logging.balance-set.log"))
        {
            instance.getLogger().log(Level.INFO, config.getString("settings.logging.balance-set.message")
                    .replace("<player>", target.getName())
                    .replace("<uuid>", uuid.toString())
                    .replace("<amount>", amount.toPlainString()));
        }

        if (target instanceof Player)
        {
            ((Player) target).sendMessage(miniMessage.deserialize(config.getString("messages.economy.set.balance-set-target"),
                    Placeholder.component("amount", Component.text(amountFormatted))));
        }

        ctx.getSource().getSender().sendMessage(miniMessage.deserialize(config.getString("messages.economy.set.balance-set-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("amount", Component.text(amountFormatted))));
    }

    // Non-coins "set" via direct balance manipulation
    private static void executeSetCurrencyLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, BigDecimal amount, String currencyId)
    {
        Currency currency = instance.getCurrencies().get(currencyId);

        if (currency == null)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize("<red>Unknown currency: " + currencyId + "</red>"));
            return;
        }

        if (amount.scale() > currency.getDecimalPlaces())
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                    Placeholder.component("amount", Component.text(amount.toPlainString())),
                    Placeholder.component("decimal_places", Component.text(currency.getDecimalPlaces()))));
            return;
        }

        if (amount.compareTo(currency.getMaxBalance()) > 0)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.would-exceed-max-balance"),
                    Placeholder.component("target", Component.text(target.getName()))));
            return;
        }

        UUID uuid = target.getUniqueId();
        PlayerAccount account = instance.getPlayerAccounts().get(uuid);

        account.setBalance(currencyId, amount);
        instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

        String amountFormatted = currency.formatAmount(amount);
        MiniMessage miniMessage = instance.getMiniMessage();
        FileConfiguration config = instance.getConfig();

        if (target instanceof Player)
        {
            ((Player) target).sendMessage(miniMessage.deserialize(config.getString("messages.economy.set.balance-set-target"),
                    Placeholder.component("amount", Component.text(amountFormatted))));
        }

        ctx.getSource().getSender().sendMessage(miniMessage.deserialize(config.getString("messages.economy.set.balance-set-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("amount", Component.text(amountFormatted))));
    }

    // Method to execute the logic for the "give" sub-command
    private static void executeGiveLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, BigDecimal amount, String currencyId)
    {
        if (!instance.getPlayerAccounts().containsKey(target.getUniqueId()))
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.no-account-other"),
                    Placeholder.component("target", Component.text(target.getName()))));
            return;
        }

        if (currencyId.equals("coins"))
        {
            executeGiveCoinsLogic(instance, ctx, target, amount);
        }
        else
        {
            executeGiveCurrencyLogic(instance, ctx, target, amount, currencyId);
        }
    }

    // Coins "give" routed through Vault for logging compatibility
    private static void executeGiveCoinsLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, BigDecimal amount)
    {
        net.milkbowl.vault.economy.Economy economy = instance.getEconomy();
        int decimalPlaces = economy.fractionalDigits();

        if (amount.scale() > decimalPlaces)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                    Placeholder.component("amount", Component.text(amount.toPlainString())),
                    Placeholder.component("decimal_places", Component.text(decimalPlaces))));
            return;
        }

        EconomyResponse depositPlayerResponse = economy.depositPlayer(target, amount.doubleValue());

        if (!depositPlayerResponse.transactionSuccess())
        {
            String errorMessage = depositPlayerResponse.errorMessage;

            if (errorMessage.equals(me.Short.OrbisEconomy.Economy.getErrorWouldExceedMaxBalance()))
            {
                ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.would-exceed-max-balance"),
                        Placeholder.component("target", Component.text(target.getName()))));
            }
            else if (errorMessage.equals(me.Short.OrbisEconomy.Economy.getErrorTooManyDecimalPlaces()))
            {
                ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                        Placeholder.component("amount", Component.text(amount.toPlainString())),
                        Placeholder.component("decimal_places", Component.text(economy.fractionalDigits()))));
            }
            else if (errorMessage.equals(me.Short.OrbisEconomy.Economy.getErrorNotGreaterThanZero()))
            {
                ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.not-greater-than-zero-amount")));
            }
            else
            {
                ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize("<red>An unknown error occurred when depositing money.</red>"));
            }

            return;
        }

        FileConfiguration config = instance.getConfig();
        MiniMessage miniMessage = instance.getMiniMessage();
        String amountFormatted = economy.format(depositPlayerResponse.amount);

        if (target instanceof Player)
        {
            ((Player) target).sendMessage(miniMessage.deserialize(config.getString("messages.economy.give.money-given-target"),
                    Placeholder.component("amount", Component.text(amountFormatted))));
        }

        ctx.getSource().getSender().sendMessage(miniMessage.deserialize(config.getString("messages.economy.give.money-given-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("amount", Component.text(amountFormatted))));
    }

    // Non-coins "give" via direct balance manipulation
    private static void executeGiveCurrencyLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, BigDecimal amount, String currencyId)
    {
        Currency currency = instance.getCurrencies().get(currencyId);

        if (currency == null)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize("<red>Unknown currency: " + currencyId + "</red>"));
            return;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.not-greater-than-zero-amount")));
            return;
        }

        if (amount.scale() > currency.getDecimalPlaces())
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                    Placeholder.component("amount", Component.text(amount.toPlainString())),
                    Placeholder.component("decimal_places", Component.text(currency.getDecimalPlaces()))));
            return;
        }

        UUID uuid = target.getUniqueId();
        PlayerAccount account = instance.getPlayerAccounts().get(uuid);
        BigDecimal newBalance = account.getBalance(currencyId).add(amount);

        if (newBalance.compareTo(currency.getMaxBalance()) > 0)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.would-exceed-max-balance"),
                    Placeholder.component("target", Component.text(target.getName()))));
            return;
        }

        account.setBalance(currencyId, newBalance);
        instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

        String amountFormatted = currency.formatAmount(amount);
        MiniMessage miniMessage = instance.getMiniMessage();
        FileConfiguration config = instance.getConfig();

        if (target instanceof Player)
        {
            ((Player) target).sendMessage(miniMessage.deserialize(config.getString("messages.economy.give.money-given-target"),
                    Placeholder.component("amount", Component.text(amountFormatted))));
        }

        ctx.getSource().getSender().sendMessage(miniMessage.deserialize(config.getString("messages.economy.give.money-given-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("amount", Component.text(amountFormatted))));
    }

    // Method to execute the logic for the "take" sub-command
    private static void executeTakeLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, BigDecimal amount, String currencyId)
    {
        if (!instance.getPlayerAccounts().containsKey(target.getUniqueId()))
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.no-account-other"),
                    Placeholder.component("target", Component.text(target.getName()))));
            return;
        }

        if (currencyId.equals("coins"))
        {
            executeTakeCoinsLogic(instance, ctx, target, amount);
        }
        else
        {
            executeTakeCurrencyLogic(instance, ctx, target, amount, currencyId);
        }
    }

    // Coins "take" routed through Vault for logging compatibility
    private static void executeTakeCoinsLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, BigDecimal amount)
    {
        net.milkbowl.vault.economy.Economy economy = instance.getEconomy();
        int decimalPlaces = economy.fractionalDigits();

        if (amount.scale() > decimalPlaces)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                    Placeholder.component("amount", Component.text(amount.toPlainString())),
                    Placeholder.component("decimal_places", Component.text(decimalPlaces))));
            return;
        }

        double amountAsDouble = amount.doubleValue();
        EconomyResponse withdrawPlayerResponse = economy.withdrawPlayer(target, amountAsDouble);

        if (!withdrawPlayerResponse.transactionSuccess())
        {
            String errorMessage = withdrawPlayerResponse.errorMessage;

            if (errorMessage.equals(me.Short.OrbisEconomy.Economy.getErrorInsufficientFunds()))
            {
                ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.insufficient-funds-other"),
                        Placeholder.component("target", Component.text(target.getName())),
                        Placeholder.component("amount", Component.text(economy.format(amountAsDouble)))));
            }
            else if (errorMessage.equals(me.Short.OrbisEconomy.Economy.getErrorTooManyDecimalPlaces()))
            {
                ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                        Placeholder.component("amount", Component.text(amount.toPlainString())),
                        Placeholder.component("decimal_places", Component.text(economy.fractionalDigits()))));
            }
            else if (errorMessage.equals(me.Short.OrbisEconomy.Economy.getErrorNotGreaterThanZero()))
            {
                ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.not-greater-than-zero-amount")));
            }
            else
            {
                ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize("<red>An unknown error occurred when withdrawing money.</red>"));
            }

            return;
        }

        FileConfiguration config = instance.getConfig();
        MiniMessage miniMessage = instance.getMiniMessage();
        String amountFormatted = economy.format(withdrawPlayerResponse.amount);

        if (target.isOnline())
        {
            target.getPlayer().sendMessage(miniMessage.deserialize(config.getString("messages.economy.take.money-taken-target"),
                    Placeholder.component("amount", Component.text(amountFormatted))));
        }

        ctx.getSource().getSender().sendMessage(miniMessage.deserialize(config.getString("messages.economy.take.money-taken-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("amount", Component.text(amountFormatted))));
    }

    // Non-coins "take" via direct balance manipulation
    private static void executeTakeCurrencyLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, BigDecimal amount, String currencyId)
    {
        Currency currency = instance.getCurrencies().get(currencyId);

        if (currency == null)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize("<red>Unknown currency: " + currencyId + "</red>"));
            return;
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.not-greater-than-zero-amount")));
            return;
        }

        if (amount.scale() > currency.getDecimalPlaces())
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                    Placeholder.component("amount", Component.text(amount.toPlainString())),
                    Placeholder.component("decimal_places", Component.text(currency.getDecimalPlaces()))));
            return;
        }

        UUID uuid = target.getUniqueId();
        PlayerAccount account = instance.getPlayerAccounts().get(uuid);
        BigDecimal currentBalance = account.getBalance(currencyId);

        if (currentBalance.compareTo(amount) < 0)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.insufficient-funds-other"),
                    Placeholder.component("target", Component.text(target.getName())),
                    Placeholder.component("amount", Component.text(currency.formatAmount(amount)))));
            return;
        }

        account.setBalance(currencyId, currentBalance.subtract(amount));
        instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

        String amountFormatted = currency.formatAmount(amount);
        MiniMessage miniMessage = instance.getMiniMessage();
        FileConfiguration config = instance.getConfig();

        if (target.isOnline())
        {
            target.getPlayer().sendMessage(miniMessage.deserialize(config.getString("messages.economy.take.money-taken-target"),
                    Placeholder.component("amount", Component.text(amountFormatted))));
        }

        ctx.getSource().getSender().sendMessage(miniMessage.deserialize(config.getString("messages.economy.take.money-taken-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("amount", Component.text(amountFormatted))));
    }

    // Method to execute the logic for the "reset" sub-command
    private static void executeResetLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, String currencyId)
    {
        if (!instance.getPlayerAccounts().containsKey(target.getUniqueId()))
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.no-account-other"),
                    Placeholder.component("target", Component.text(target.getName()))));
            return;
        }

        if (currencyId.equals("coins"))
        {
            executeResetCoinsLogic(instance, ctx, target);
        }
        else
        {
            executeResetCurrencyLogic(instance, ctx, target, currencyId);
        }
    }

    // Coins "reset" routed through Vault for logging compatibility
    private static void executeResetCoinsLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target)
    {
        net.milkbowl.vault.economy.Economy economy = instance.getEconomy();
        BigDecimal defaultBalance = new BigDecimal(instance.getConfig().getString("settings.currencies.coins.default-balance")).stripTrailingZeros();

        if (defaultBalance.compareTo(BigDecimal.ZERO) < 0)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.negative-default-balance"),
                    Placeholder.component("target", Component.text(target.getName()))));
            return;
        }

        if (defaultBalance.scale() > economy.fractionalDigits())
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.too-many-decimal-places-default-balance"),
                    Placeholder.component("target", Component.text(target.getName()))));
            return;
        }

        FileConfiguration config = instance.getConfig();

        if (defaultBalance.compareTo(new BigDecimal(config.getString("settings.currencies.coins.max-balance"))) > 0)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(config.getString("messages.error.default-balance-exceeds-max-balance"),
                    Placeholder.component("target", Component.text(target.getName()))));
            return;
        }

        MiniMessage miniMessage = instance.getMiniMessage();
        UUID uuid = target.getUniqueId();
        PlayerAccount account = instance.getPlayerAccounts().get(uuid);

        account.setBalance("coins", defaultBalance);
        instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

        String defaultBalanceFormatted = economy.format(defaultBalance.doubleValue());

        if (config.getBoolean("settings.logging.balance-reset.log"))
        {
            instance.getLogger().log(Level.INFO, config.getString("settings.logging.balance-reset.message")
                    .replace("<player>", target.getName())
                    .replace("<uuid>", uuid.toString())
                    .replace("<default_balance>", defaultBalance.toPlainString()));
        }

        if (target.isOnline())
        {
            target.getPlayer().sendMessage(miniMessage.deserialize(config.getString("messages.economy.reset.balance-reset-target"),
                    Placeholder.component("default_balance", Component.text(defaultBalanceFormatted))));
        }

        ctx.getSource().getSender().sendMessage(miniMessage.deserialize(config.getString("messages.economy.reset.balance-reset-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("default_balance", Component.text(defaultBalanceFormatted))));
    }

    // Non-coins "reset" to configured default balance
    private static void executeResetCurrencyLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, String currencyId)
    {
        Currency currency = instance.getCurrencies().get(currencyId);

        if (currency == null)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize("<red>Unknown currency: " + currencyId + "</red>"));
            return;
        }

        BigDecimal defaultBalance = currency.getDefaultBalance();

        if (defaultBalance.compareTo(currency.getMaxBalance()) > 0)
        {
            ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.default-balance-exceeds-max-balance"),
                    Placeholder.component("target", Component.text(target.getName()))));
            return;
        }

        UUID uuid = target.getUniqueId();
        PlayerAccount account = instance.getPlayerAccounts().get(uuid);

        account.setBalance(currencyId, defaultBalance);
        instance.getDirtyPlayerAccountSnapshots().put(uuid, account.snapshot());

        String defaultBalanceFormatted = currency.formatAmount(defaultBalance);
        MiniMessage miniMessage = instance.getMiniMessage();
        FileConfiguration config = instance.getConfig();

        if (target.isOnline())
        {
            target.getPlayer().sendMessage(miniMessage.deserialize(config.getString("messages.economy.reset.balance-reset-target"),
                    Placeholder.component("default_balance", Component.text(defaultBalanceFormatted))));
        }

        ctx.getSource().getSender().sendMessage(miniMessage.deserialize(config.getString("messages.economy.reset.balance-reset-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("default_balance", Component.text(defaultBalanceFormatted))));
    }

    // Method to execute the logic for the "reload" sub-command
    private static void executeReloadLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx)
    {
        instance.reload();
        ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.economy.reload")));
    }

}
