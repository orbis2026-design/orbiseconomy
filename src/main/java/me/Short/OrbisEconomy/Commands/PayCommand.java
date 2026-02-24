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

                // Require permission
                .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.pay"))

                .executes(ctx ->
                {
                    ctx.getSource().getSender().sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.incorrect-usage"),
                            Placeholder.component("command", Component.text("/" + ctx.getInput().split("\\s+")[0])),
                            Placeholder.component("argument_usage", Component.text("<player name> <amount>"))));
                    return Command.SINGLE_SUCCESS;
                });

        // Dynamic currency literal nodes: /pay <currencyId> <player> <amount>
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
                                    })
                            )
                    )
            );
        }

        // Default: /pay <player> <amount> -> coins
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
                        })
                )
        );

        return root.build();
    }

    // Method to execute the command logic for any currency
    private static void executeCommandLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, OfflinePlayer target, BigDecimal amount, String currencyId)
    {
        final CommandSender sender = ctx.getSource().getSender();

        // Only players can pay money
        if (!(sender instanceof Player senderPlayer))
        {
            sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.console-cannot-use")));
            return;
        }

        // Players cannot pay themselves
        if (target.getUniqueId().equals(senderPlayer.getUniqueId()))
        {
            sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.cannot-pay-yourself")));
            return;
        }

        Map<UUID, PlayerAccount> playerAccounts = instance.getPlayerAccounts();

        // Sender must have an account
        if (!playerAccounts.containsKey(senderPlayer.getUniqueId()))
        {
            senderPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.no-account")));
            return;
        }

        // Target must have an account
        if (!playerAccounts.containsKey(target.getUniqueId()))
        {
            senderPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.no-account-other"),
                    Placeholder.component("target", Component.text(target.getName()))));
            return;
        }

        // Target must be accepting payments
        if (!playerAccounts.get(target.getUniqueId()).getAcceptingPayments())
        {
            senderPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.not-accepting-payments"),
                    Placeholder.component("target", Component.text(target.getName()))));
            return;
        }

        // Fire PlayerPayPlayerEvent
        PlayerPayPlayerEvent playerPayPlayerEvent = new PlayerPayPlayerEvent(senderPlayer, target, amount);
        Bukkit.getServer().getPluginManager().callEvent(playerPayPlayerEvent);

        if (playerPayPlayerEvent.isCancelled())
        {
            return;
        }

        // Re-assign variables in case a plugin modified them via the event
        senderPlayer = playerPayPlayerEvent.getSender();
        target = playerPayPlayerEvent.getRecipient();
        amount = playerPayPlayerEvent.getAmount();

        Currency currency = instance.getCurrencies().get(currencyId);

        if (currency == null)
        {
            senderPlayer.sendMessage(instance.getMiniMessage().deserialize("<red>Unknown currency: " + currencyId + "</red>"));
            return;
        }

        // Validate amount precision
        if (amount.scale() > currency.getDecimalPlaces())
        {
            senderPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.too-many-decimal-places-amount"),
                    Placeholder.component("amount", Component.text(amount.toPlainString())),
                    Placeholder.component("decimal_places", Component.text(currency.getDecimalPlaces()))));
            return;
        }

        // Amount must be greater than zero
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
        {
            senderPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.not-greater-than-zero-amount")));
            return;
        }

        PlayerAccount senderAccount = playerAccounts.get(senderPlayer.getUniqueId());
        BigDecimal senderBalance = senderAccount.getBalance(currencyId);

        // Sender must have enough funds
        if (senderBalance.compareTo(amount) < 0)
        {
            senderPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.insufficient-funds"),
                    Placeholder.component("amount", Component.text(currency.formatAmount(amount)))));
            return;
        }

        PlayerAccount targetAccount = playerAccounts.get(target.getUniqueId());
        BigDecimal targetBalance = targetAccount.getBalance(currencyId);
        BigDecimal newTargetBalance = targetBalance.add(amount);

        // Target balance must not exceed max
        if (newTargetBalance.compareTo(currency.getMaxBalance()) > 0)
        {
            senderPlayer.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.would-exceed-max-balance"),
                    Placeholder.component("target", Component.text(target.getName()))));
            return;
        }

        // Perform the transfer
        senderAccount.setBalance(currencyId, senderBalance.subtract(amount));
        targetAccount.setBalance(currencyId, newTargetBalance);

        // Mark both accounts for saving
        UUID senderUuid = senderPlayer.getUniqueId();
        UUID targetUuid = target.getUniqueId();
        instance.getDirtyPlayerAccountSnapshots().put(senderUuid, senderAccount.snapshot());
        instance.getDirtyPlayerAccountSnapshots().put(targetUuid, targetAccount.snapshot());

        FileConfiguration config = instance.getConfig();
        MiniMessage miniMessage = instance.getMiniMessage();
        String amountFormatted = currency.formatAmount(amount);

        // Notify the target player if online
        if (target instanceof Player)
        {
            ((Player) target).sendMessage(miniMessage.deserialize(config.getString("messages.pay.paid-target"),
                    Placeholder.component("player", Component.text(senderPlayer.getName())),
                    Placeholder.component("amount", Component.text(amountFormatted))));
        }

        // Notify the sender
        senderPlayer.sendMessage(miniMessage.deserialize(config.getString("messages.pay.paid-sender"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("amount", Component.text(amountFormatted))));
    }

}
