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
import me.Short.OrbisEconomy.PlayerAccount;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class BalanceCommand
{

    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName, OrbisEconomy instance)
    {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(commandName)
                .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.balance"))
                .executes(ctx ->
                {
                    executeCommandLogic(instance, ctx, null, "coins");
                    return Command.SINGLE_SUCCESS;
                });

        for (String currencyId : instance.getCurrencies().keySet())
        {
            final String fid = currencyId;
            root.then(Commands.literal(currencyId)
                    .executes(ctx ->
                    {
                        executeCommandLogic(instance, ctx, null, fid);
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
                            .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.balance.others"))
                            .executes(ctx ->
                            {
                                executeCommandLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), fid);
                                return Command.SINGLE_SUCCESS;
                            })));
        }

        root.then(Commands.argument("target player", new CachedOfflinePlayerArgument(instance))
                .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.balance.others"))
                .executes(ctx ->
                {
                    executeCommandLogic(instance, ctx, ctx.getArgument("target player", OfflinePlayer.class), "coins");
                    return Command.SINGLE_SUCCESS;
                }));

        return root.build();
    }

    private static void executeCommandLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, @Nullable OfflinePlayer target, String currencyId)
    {
        final CommandSender sender = ctx.getSource().getSender();

        if (target == null)
        {
            if (!(sender instanceof Player playerSender))
            {
                sender.sendMessage(instance.getMiniMessage().deserialize(instance.getConfig().getString("messages.error.console-cannot-use")));
                return;
            }
            target = playerSender;
        }

        Currency currency = CurrencyCommandService.resolveCurrency(instance, sender, currencyId);
        if (currency == null)
        {
            return;
        }

        PlayerAccount account = CurrencyCommandService.requireAccount(instance, sender, target, target == sender);
        if (account == null)
        {
            return;
        }

        sender.sendMessage(instance.getMiniMessage().deserialize(
                instance.getConfig().getString(target == sender ? "messages.balance.your-balance" : "messages.balance.their-balance"),
                Placeholder.component("target", Component.text(target.getName())),
                Placeholder.component("balance", Component.text(currency.formatAmount(account.getBalance(currency.getId()))))));
    }
}
