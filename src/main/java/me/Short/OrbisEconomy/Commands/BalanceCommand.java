package me.Short.OrbisEconomy.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.Short.OrbisEconomy.Currency;
import me.Short.OrbisEconomy.CustomCommandArguments.CachedOfflinePlayerArgument;
import me.Short.OrbisEconomy.OrbisEconomy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

@NullMarked
public class BalanceCommand
{

    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName, OrbisEconomy instance)
    {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(commandName)

                // Require permission
                .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.balance"))

                // /balance -> show own coins balance
                .executes(ctx ->
                {
                    executeCommandLogic(instance, ctx, null, "coins");
                    return Command.SINGLE_SUCCESS;
                });

        // Dynamic currency literal nodes: /balance <currencyId> [player]
        for (String currencyId : instance.getCurrencies().keySet())
        {
            final String fid = currencyId;
            root.then(Commands.literal(currencyId)

                    // /balance <currencyId> -> show own balance for that currency
                    .executes(ctx ->
                    {
                        executeCommandLogic(instance, ctx, null, fid);
                        return Command.SINGLE_SUCCESS;
                    })

                    // /balance <currencyId> <player> -> show another player's balance for that currency
                    .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))

                            .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.balance.others"))

                            .executes(ctx ->
                            {
                                executeCommandLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), fid);
                                return Command.SINGLE_SUCCESS;
                            })
                    )
            );
        }

        // Backward-compat: /balance <player> -> show that player's coins balance
        root.then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))

                .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.balance.others"))

                .executes(ctx ->
                {
                    executeCommandLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), "coins");
                    return Command.SINGLE_SUCCESS;
                })
        );

        return root.build();
    }

    // Method to execute the command logic
    private static void executeCommandLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, @Nullable OfflinePlayer target, String currencyId)
    {
        final CommandSender sender = ctx.getSource().getSender();
        String normalizedCurrencyId = OrbisEconomy.normalizeCurrencyId(currencyId);

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
            sender.sendMessage(instance.getMiniMessage().deserialize(
                    instance.getConfig().getString(target != sender ? "messages.error.no-account-other" : "messages.error.no-account"),
                    Placeholder.component("target", Component.text(target.getName()))));
            return;
        }

        BigDecimal balance = instance.getPlayerAccounts().get(target.getUniqueId()).getBalance(normalizedCurrencyId);
        Currency currency = instance.getCurrencies().get(normalizedCurrencyId);
        String formattedBalance = currency != null ? currency.formatAmount(balance) : balance.stripTrailingZeros().toPlainString();

        sender.sendMessage(instance.getMiniMessage().deserialize(
                instance.getConfig().getString(target != sender ? "messages.balance.their-balance" : "messages.balance.your-balance"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("balance", Component.text(formattedBalance))));
    }

}
