package me.Short.OrbisEconomy.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.Short.OrbisEconomy.BalanceTop;
import me.Short.OrbisEconomy.Currency;
import me.Short.OrbisEconomy.OrbisEconomy;
import me.Short.OrbisEconomy.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@NullMarked
public class BalanceTopCommand
{

    public static LiteralCommandNode<CommandSourceStack> createCommand(final String commandName, OrbisEconomy instance)
    {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(commandName)

                // Require permission
                .requires(sender -> sender.getSender().hasPermission("orbiseconomy.command.balancetop"))

                // Command logic if no page number was specified
                .executes(ctx ->
                {
                    // Execute command logic if no page number was specified
                    executeCommandLogic(instance, ctx, "coins", 1);

                    return Command.SINGLE_SUCCESS;
                });

        root.then(Commands.argument("currency ID", StringArgumentType.word())

                // Suggest configured currency IDs
                .suggests((ctx, builder) ->
                {
                    for (String currencyId : instance.getCurrencies().keySet())
                    {
                        if (currencyId.startsWith(builder.getRemainingLowerCase()))
                        {
                            builder.suggest(currencyId);
                        }
                    }

                    return builder.buildFuture();
                })

                // /balancetop <currencyId>
                .executes(ctx ->
                {
                    String currencyOrPage = StringArgumentType.getString(ctx, "currency ID");

                    if (isInteger(currencyOrPage))
                    {
                        executeCommandLogic(instance, ctx, "coins", Integer.parseInt(currencyOrPage));
                    }
                    else
                    {
                        executeCommandLogic(instance, ctx, currencyOrPage, 1);
                    }

                    return Command.SINGLE_SUCCESS;
                })

                // /balancetop <currencyId> <page number>
                .then(Commands.argument("page number", IntegerArgumentType.integer())
                        .suggests((ctx, builder) -> CompletableFuture.supplyAsync(() ->
                        {
                            String currencyId = StringArgumentType.getString(ctx, "currency ID");

                            for (int i = 1; i <= calculateBalanceTopPages(instance, currencyId); i++)
                            {
                                if (Integer.toString(i).startsWith(builder.getRemaining()))
                                {
                                    builder.suggest(i);
                                }
                            }

                            return builder.build();
                        }))
                        .executes(ctx ->
                        {
                            executeCommandLogic(instance, ctx, StringArgumentType.getString(ctx, "currency ID"), IntegerArgumentType.getInteger(ctx, "page number"));
                            return Command.SINGLE_SUCCESS;
                        })
                )
        );

        // Backward compatibility: /balancetop <page number> defaults to coins
        root.then(Commands.argument("page number", IntegerArgumentType.integer())

                        // Send all valid page numbers as suggestions
                        .suggests((ctx, builder) -> CompletableFuture.supplyAsync(() ->
                        {
                            for (int i = 1; i <= calculateBalanceTopPages(instance, "coins"); i++)
                            {
                                if (Integer.toString(i).startsWith(builder.getRemaining()))
                                {
                                    builder.suggest(i);
                                }
                            }

                            return builder.build();
                        }))

                        // Command logic if a page number was specified
                        .executes(ctx ->
                        {
                            // Execute command logic if a page number was specified
                            executeCommandLogic(instance, ctx, "coins", IntegerArgumentType.getInteger(ctx, "page number"));

                            return Command.SINGLE_SUCCESS;
                        })
                ));

        return root.build();
    }

    private static boolean isInteger(String input)
    {
        try
        {
            Integer.parseInt(input);
            return true;
        }
        catch (NumberFormatException exception)
        {
            return false;
        }
    }

    // Method to execute the command logic
    private static void executeCommandLogic(OrbisEconomy instance, final CommandContext<CommandSourceStack> ctx, String currencyId, int pageNumber)
    {
        final CommandSender sender = ctx.getSource().getSender();

        FileConfiguration config = instance.getConfig();
        MiniMessage miniMessage = instance.getMiniMessage();
        String normalizedCurrencyId = OrbisEconomy.normalizeCurrencyId(currencyId);

        Currency currency = instance.getCurrencies().get(normalizedCurrencyId);
        if (currency == null)
        {
            sender.sendMessage(miniMessage.deserialize(config.getString("messages.error.unknown-currency"),
                    Placeholder.component("currency", Component.text(currencyId))));
            return;
        }

        String formattedCurrency = Objects.requireNonNullElse(currency.getNamePlural(), normalizedCurrencyId);

        BalanceTop balanceTop = instance.getBalanceTop();
        List<Map.Entry<UUID, BigDecimal>> topBalances = balanceTop.getTopBalances(normalizedCurrencyId);
        BigDecimal totalBalance = topBalances.stream().map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);

        // If the top balances list is empty, tell the command sender, and return
        if (topBalances.isEmpty())
        {
            sender.sendMessage(miniMessage.deserialize(config.getString("messages.error.no-balancetop-entries"),
                    Placeholder.component("currency", Component.text(formattedCurrency)),
                    Placeholder.component("total", Component.text(currency.formatAmount(totalBalance)))));

            return;
        }

        int pageLength = config.getInt("settings.balancetop.page-length");
        int pages = calculateBalanceTopPages(instance, normalizedCurrencyId);

        // Make sure the specified page isn't below 1, and doesn't exceed the number of pages
        if (pageNumber < 1)
        {
            pageNumber = 1;
        }
        else if (pageNumber > pages)
        {
            pageNumber = pages;
        }

        // Initial output (header)
        Component output = miniMessage.deserialize(config.getString("messages.balancetop.header"),
                Placeholder.component("currency", Component.text(formattedCurrency)),
                Placeholder.component("page", Component.text(pageNumber)),
                Placeholder.component("pages", Component.text(pages)),
                Placeholder.component("total", Component.text(currency.formatAmount(totalBalance))));

        int startPoint = (pageNumber - 1) * pageLength;

        // Append entries to the output
        List<Map.Entry<UUID, BigDecimal>> entries = new ArrayList<>(topBalances);
        for (int i = startPoint; i < startPoint + pageLength && i < entries.size(); i++)
        {
            Map.Entry<UUID, BigDecimal> entry = entries.get(i);

            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());

            String balanceTopEntry = config.getString(player == sender ? "messages.balancetop.entry-you" : "messages.balancetop.entry");

            if (balanceTopEntry.contains("<dots>"))
            {
                output = output.appendNewline().append(miniMessage.deserialize(balanceTopEntry,
                        Placeholder.component("position", Component.text(i + 1)),
                        Placeholder.component("player", Component.text(player.getName())),
                        Placeholder.component("currency", Component.text(formattedCurrency)),
                        Placeholder.component("balance", Component.text(currency.formatAmount(entry.getValue()))),
                        Placeholder.component("dots", Component.text(new String(new char[Util.getNumberOfDotsToAlign(PlainTextComponentSerializer.plainText().serialize(miniMessage.deserialize(balanceTopEntry,
                                Placeholder.component("position", Component.text(i + 1)),
                                Placeholder.component("player", Component.text(player.getName())),
                                Placeholder.component("currency", Component.text(formattedCurrency)),
                                Placeholder.component("balance", Component.text(currency.formatAmount(entry.getValue()))))), sender instanceof Player)]).replace("\0", ".")))));
            }
            else
            {
                output = output.appendNewline().append(miniMessage.deserialize(balanceTopEntry,
                        Placeholder.component("position", Component.text(i + 1)),
                        Placeholder.component("player", Component.text(player.getName())),
                        Placeholder.component("currency", Component.text(formattedCurrency)),
                        Placeholder.component("balance", Component.text(currency.formatAmount(entry.getValue())))));
            }
        }

        // Send output
        sender.sendMessage(output);
    }

    // Method to calculate the number of pages for the top balances map
    private static int calculateBalanceTopPages(OrbisEconomy instance, String currencyId)
    {
        return Math.max(1, (int) Math.ceil((double) instance.getBalanceTop().getTopBalances(currencyId).size() / (double) instance.getConfig().getInt("settings.balancetop.page-length")));
    }

}
