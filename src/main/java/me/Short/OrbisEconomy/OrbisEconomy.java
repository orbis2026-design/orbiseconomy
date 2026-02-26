package me.Short.OrbisEconomy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.Short.OrbisEconomy.Commands.BalanceCommand;
import me.Short.OrbisEconomy.Commands.BalanceTopCommand;
import me.Short.OrbisEconomy.Commands.EconomyCommand;
import me.Short.OrbisEconomy.Commands.OrbsCommand;
import me.Short.OrbisEconomy.Commands.PayCommand;
import me.Short.OrbisEconomy.Commands.PayToggleCommand;
import me.Short.OrbisEconomy.Events.BalanceTopSortEvent;
import me.Short.OrbisEconomy.Listeners.PlayerJoinListener;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OrbisEconomy extends JavaPlugin
{

    // Map containing snapshots of player accounts to be saved to respective JSON files on a schedule
    private Map<UUID, PlayerAccountSnapshot> dirtyPlayerAccountSnapshots;

    // Scheduler for handling the repeated saving of dirty player accounts
    private ScheduledExecutorService saveDirtyPlayerAccountsScheduler;

    // File handler for the plugin's logger
    private FileHandler logFileHandler;

    // Instance of the Gson library
    private Gson gson;

    // Instance of the economy service implementation (Vault-backed when Vault API is present)
    private Economy economy;

    // Cache of all players' usernames, for the purpose of offline player tab completion
    private Map<UUID, String> offlinePlayerNames;

    // Cache of all player accounts
    private Map<UUID, PlayerAccount> playerAccounts;

    // Premium network currency cache hydrated from OrbisPaperAgent
    private Map<UUID, PremiumBalance> premiumBalances;

    // Bridge service for signed API calls via OrbisPaperAgent
    private OrbisPaperAgentBridge orbisPaperAgentBridge;

    // Top balances and combined total balance
    private BalanceTop balanceTop;

    // The top balances update task, so it can be cancelled in the event of a reload
    private BukkitTask updateBalanceTopTask;

    // Flag to determine whether a top balances update task is running - used to prevent tasks from being able to overlap
    private AtomicBoolean updateBalanceTopTaskRunning;

    // Instance of the MiniMessage API
    private MiniMessage miniMessage;

    // Instance of the LegacyComponentSerializer API
    private LegacyComponentSerializer legacyComponentSerializer;


    // Registry of all configured currencies, keyed by currency ID (e.g. "coins", "orbs")
    private Map<String, Currency> currencies;

    // Config options that may need to be retrieved in the "updateBalanceTop" method later
    private boolean balanceTopConsiderExcludePermission;
    private boolean balanceTopExcludePermanentlyBannedPlayers;
    private BigDecimal balanceTopMinBalance;

    @Override
    public void onEnable()
    {
        // Set up config.yml
        saveDefaultConfig();

        // Load currency registry from config
        currencies = loadCurrencies();

        // Log placeholder usage guidance, including available currency IDs
        logPlaceholderStartupGuidance();

        // Set "dirtyPlayerAccountSnapshots"
        dirtyPlayerAccountSnapshots = new ConcurrentHashMap<>();

        // Set "saveDirtyPlayerAccountsScheduler"
        saveDirtyPlayerAccountsScheduler = Executors.newSingleThreadScheduledExecutor();

        // Disable console logging if config.yml says to do so
        if (!getConfig().getBoolean("settings.logging.log-console"))
        {
            getLogger().setUseParentHandlers(false);
        }

        // Enable file logging to "logs.log" if config.yml says to do so
        if (getConfig().getBoolean("settings.logging.log-file"))
        {
            setupLogFileHandler();
        }

        // Create instance of Gson
        gson = new GsonBuilder().setPrettyPrinting().create();

        // Register OrbisEconomy as a Vault Economy provider
        if (!isClassPresent("net.milkbowl.vault.economy.Economy"))
        {
            getLogger().severe("Vault API classes are unavailable at runtime. Ensure Vault is installed and loads before OrbisEconomy.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economy = new Economy(this);
        registerVaultEconomyService();

        // Initial empty BalanceTop data
        balanceTop = new BalanceTop(new HashMap<>(), BigDecimal.ZERO);

        // Get instance of the MiniMessage API
        miniMessage = MiniMessage.miniMessage();

        // Get instance of the LegacyComponentSerializer API
        legacyComponentSerializer = LegacyComponentSerializer.legacySection();

        // Get config options from config.yml here, so they don't need to be retrieved in the async "updateBalanceTop" method later
        balanceTopConsiderExcludePermission = getConfig().getBoolean("settings.balancetop.consider-exclude-permission");
        balanceTopExcludePermanentlyBannedPlayers = getConfig().getBoolean("settings.balancetop.exclude-permanently-banned-players");
        balanceTopMinBalance = new BigDecimal(getConfig().getString("settings.balancetop.min-balance"));

        // Register events
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new PlayerJoinListener(this), this);

        // Register commands
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands ->
        {
            Commands registrar = commands.registrar();

            // Basic commands
            registrar.register(getConfig().getString("settings.commands.paytoggle.name"), getConfig().getString("settings.commands.paytoggle.description"), getConfig().getStringList("settings.commands.paytoggle.aliases"), new PayToggleCommand(this));

            // Non-basic commands
            registrar.register(BalanceCommand.createCommand(getConfig().getString("settings.commands.balance.name"), this), getConfig().getString("settings.commands.balance.description"), getConfig().getStringList("settings.commands.balance.aliases"));
            registrar.register(BalanceTopCommand.createCommand(getConfig().getString("settings.commands.balancetop.name"), this), getConfig().getString("settings.commands.balancetop.description"), getConfig().getStringList("settings.commands.balancetop.aliases"));
            registrar.register(PayCommand.createCommand(getConfig().getString("settings.commands.pay.name"), this), getConfig().getString("settings.commands.pay.description"), getConfig().getStringList("settings.commands.pay.aliases"));
            registrar.register(EconomyCommand.createCommand(getConfig().getString("settings.commands.economy.name"), this), getConfig().getString("settings.commands.economy.description"), getConfig().getStringList("settings.commands.economy.aliases"));
            registrar.register(OrbsCommand.createCommand(getConfig().getString("settings.commands.orbs.name"), this), getConfig().getString("settings.commands.orbs.description"), getConfig().getStringList("settings.commands.orbs.aliases"));
        });

        // Register PlaceholderAPI placeholders, if PlaceholderAPI is installed
        if (pluginManager.getPlugin("PlaceholderAPI") != null)
        {
            new PlaceholderAPI(this).register();
        }

        // Register EconomyBridge providers, if EconomyBridge runtime is available
        if (pluginManager.getPlugin("EconomyBridge") != null
                && isClassPresent("su.nightexpress.economybridge.EconomyBridge")
                && isClassPresent("su.nightexpress.economybridge.api.Currency"))
        {
            try
            {
                new EconomyBridgeIntegration(this).register();
                getLogger().info("Successfully hooked into EconomyBridge! Premium currencies registered.");
            }
            catch (Throwable throwable)
            {
                getLogger().log(Level.WARNING, "EconomyBridge hook failed. Continuing without bridge integration.", throwable);
            }
        }
        else
        {
            getLogger().warning("EconomyBridge was not found or API classes are unavailable. Orbs and Votepoints will not be bridged to shops.");
        }


        // bStats
        Metrics metrics = new Metrics(this, 13836);

        // Cache all offline player names
        offlinePlayerNames = cacheOfflinePlayerNames();

        // Cache all players' UUIDs and their account data
        playerAccounts = cachePlayerAccounts();

        // Premium cache starts empty and is hydrated during async pre-login
        premiumBalances = new ConcurrentHashMap<>();
        orbisPaperAgentBridge = new OrbisPaperAgentBridge();

        // Set "updateBalanceTopTaskRunning"
        updateBalanceTopTaskRunning = new AtomicBoolean(false);

        // Schedule repeating BalanceTop update task
        scheduleBalanceTopUpdateTask();

        // Schedule task to periodically save any dirty player accounts to JSON files
        runSaveDirtyPlayerAccountsLoop();
    }

    @Override
    public void onDisable()
    {
        // Shut down the save dirty player accounts scheduler, allowing the current task (if there is one running) to finish
        saveDirtyPlayerAccountsScheduler.shutdown();

        // Allow currently running task to finish, or force shutdown if it hasn't after 30 seconds (it should never take this long)
        try
        {
            if (!saveDirtyPlayerAccountsScheduler.awaitTermination(30, TimeUnit.SECONDS))
            {
                saveDirtyPlayerAccountsScheduler.shutdownNow();
            }
        }
        catch (InterruptedException ignored)
        {
            saveDirtyPlayerAccountsScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Ensure any remaining dirty player accounts are saved
        saveDirtyPlayerAccounts();
    }

    // Method to set up "logFileHandler" and set it to this plugin's logger
    private void setupLogFileHandler()
    {
        try
        {
            logFileHandler = new FileHandler(getDataFolder().getAbsolutePath() + "/logs.log", true);

            logFileHandler.setFormatter(new LogFormatter());

            getLogger().addHandler(logFileHandler);

        }
        catch (IOException exception)
        {
            throw new RuntimeException(exception);
        }
    }

    // Method to cache all offline player names
    private Map<UUID, String> cacheOfflinePlayerNames()
    {
        Map<UUID, String> offlinePlayerNames = new ConcurrentHashMap<>();

        for (OfflinePlayer player : Bukkit.getOfflinePlayers())
        {
            offlinePlayerNames.put(player.getUniqueId(), player.getName());
        }

        return offlinePlayerNames;
    }

    // Method to cache all player accounts from their respective JSON files in the "player-accounts" folder
    private Map<UUID, PlayerAccount> cachePlayerAccounts()
    {
        Map<UUID, PlayerAccount> playerAccounts = new ConcurrentHashMap<>();

        File playerAccountsFolder = new File(getDataFolder(), "player-accounts");

        // If a "player-accounts" folder doesn't exist, create one
        if (!playerAccountsFolder.exists())
        {
            playerAccountsFolder.mkdirs();
        }

        // Get a list of player account files
        File[] playerAccountFiles = playerAccountsFolder.listFiles();

        // If there are no files, return the unpopulated `playerAccounts` map
        if (playerAccountFiles == null)
        {
            return playerAccounts;
        }

        // Go through each file, read a `PlayerAccount` object from it, and put it in `PlayerAccounts`
        for (File file : playerAccountFiles)
        {
            try
            {
                UUID uuid = UUID.fromString(file.getName().replace(".json", ""));

                try (FileReader reader = new FileReader(file))
                {
                    PlayerAccount account = gson.fromJson(reader, PlayerAccount.class);
                    account.migrateFromLegacy();

                    // Normalize historical balance keys (e.g. "Coins", " coins ") to canonical IDs
                    Map<String, BigDecimal> balances = account.getBalances();
                    Map<String, BigDecimal> normalizedBalances = new ConcurrentHashMap<>();
                    boolean modified = false;

                    for (Map.Entry<String, BigDecimal> balanceEntry : balances.entrySet())
                    {
                        String normalizedCurrencyId = normalizeCurrencyId(balanceEntry.getKey());
                        BigDecimal existingAmount = normalizedBalances.putIfAbsent(normalizedCurrencyId, balanceEntry.getValue());

                        if (existingAmount != null)
                        {
                            normalizedBalances.put(normalizedCurrencyId, existingAmount.add(balanceEntry.getValue()));
                        }

                        if (!normalizedCurrencyId.equals(balanceEntry.getKey()))
                        {
                            modified = true;
                        }
                    }

                    if (normalizedBalances.size() != balances.size())
                    {
                        modified = true;
                    }

                    if (modified)
                    {
                        balances.clear();
                        balances.putAll(normalizedBalances);
                    }

                    // Add default balances for any currencies that are configured but missing from this account
                    // (e.g. when a new currency is added to config.yml after the account was first created)
                    for (Map.Entry<String, Currency> entry : currencies.entrySet())
                    {
                        if (!account.getBalances().containsKey(entry.getKey()))
                        {
                            account.setBalance(entry.getKey(), entry.getValue().getDefaultBalance());
                            modified = true;
                        }
                    }

                    playerAccounts.put(uuid, account);

                    if (modified)
                    {
                        dirtyPlayerAccountSnapshots.put(uuid, account.snapshot());
                    }
                }
            }
            catch (IllegalArgumentException exception)
            {
                getLogger().log(Level.WARNING, "Failed to load player account - invalid UUID in file name: " + file.getName());
                exception.printStackTrace();
            }
            catch (IOException | JsonIOException exception)
            {
                getLogger().log(Level.WARNING, "Failed to load player account - failed to read file: " + file.getName());
                exception.printStackTrace();
            }
        }

        return playerAccounts;
    }

    // Method to schedule a repeating BalanceTop update task
    private void scheduleBalanceTopUpdateTask()
    {
        updateBalanceTopTask = Bukkit.getScheduler().runTaskTimer(this, () ->
        {
            // Don't update BalanceTop if a task is already running
            if (!updateBalanceTopTaskRunning.compareAndSet(false, true))
            {
                return;
            }

            // Create BalanceTopSortEvent instance with an initial empty HashSet of excluded players' UUIDs
            BalanceTopSortEvent balanceTopSortEvent = new BalanceTopSortEvent(new HashSet<>());

            // Call the event
            Bukkit.getServer().getPluginManager().callEvent(balanceTopSortEvent);

            // If the event was cancelled, return
            if (balanceTopSortEvent.isCancelled())
            {
                updateBalanceTopTaskRunning.set(false);
                return;
            }

            // Create immutable snapshot candidates on the server thread
            List<BalanceTopCandidate> balanceTopCandidates = collectBalanceTopCandidates(balanceTopSortEvent.getExcludedPlayers());

            // Process snapshot data async, then apply results back on the server thread
            updateBalanceTop(balanceTopCandidates).whenComplete((updatedBalanceTop, throwable) -> Bukkit.getScheduler().runTask(this, () ->
            {
                if (throwable != null)
                {
                    getLogger().log(Level.WARNING, "Failed to update BalanceTop.", throwable);
                }
                else
                {
                    this.balanceTop = updatedBalanceTop;
                }

                updateBalanceTopTaskRunning.set(false);
            }));
        }, 0L, getConfig().getLong("settings.balancetop.update-task-frequency"));
    }

    // Method to collect immutable snapshot candidates for BalanceTop processing
    private List<BalanceTopCandidate> collectBalanceTopCandidates(Set<UUID> excludedPlayers)
    {
        List<BalanceTopCandidate> candidates = new ArrayList<>(playerAccounts.size());

        for (Map.Entry<UUID, PlayerAccount> entry : playerAccounts.entrySet())
        {
            UUID uuid = entry.getKey();
            PlayerAccount account = entry.getValue();

            if (account == null)
            {
                continue;
            }

            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

            Map<String, BigDecimal> balancesSnapshot = new HashMap<>();
            for (String currencyId : currencies.keySet())
            {
                balancesSnapshot.put(currencyId, account.getBalance(currencyId));
            }

            boolean excludedByPermission = balanceTopConsiderExcludePermission
                    && player.isOnline()
                    && player.getPlayer() != null
                    && player.getPlayer().hasPermission("orbiseconomy.balancetop.exclude");
            boolean excludedByBan = balanceTopExcludePermanentlyBannedPlayers && player.isBanned();

            candidates.add(new BalanceTopCandidate(
                    uuid,
                    Map.copyOf(balancesSnapshot),
                    excludedPlayers.contains(uuid),
                    excludedByPermission,
                    excludedByBan
            ));
        }

        return List.copyOf(candidates);
    }

    // Method to repeatedly call `saveDirtyPlayerAccounts()` async
    private void runSaveDirtyPlayerAccountsLoop()
    {
        saveDirtyPlayerAccountsScheduler.scheduleWithFixedDelay(this::saveDirtyPlayerAccounts, 0, getConfig().getLong("settings.misc.data-file-save-frequency"), TimeUnit.SECONDS);
    }

    // Method to save all player accounts in `dirtyPlayerAccountSnapshots` to respective JSON files, and remove the saved accounts from `dirtyPlayerAccountSnapshots` after
    private void saveDirtyPlayerAccounts()
    {
        // If there are no dirty player account snapshots, return
        if (dirtyPlayerAccountSnapshots.isEmpty())
        {
            return;
        }

        // Save each dirty player account to a JSON file inside the "player-accounts" folder
        for (Map.Entry<UUID, PlayerAccountSnapshot> entry : new HashMap<>(dirtyPlayerAccountSnapshots).entrySet())
        {
            UUID uuid = entry.getKey();
            PlayerAccountSnapshot playerAccountSnapshot = entry.getValue();

            Path target = getDataFolder().toPath()
                    .resolve("player-accounts")
                    .resolve(uuid + ".json");

            Path temp = target.resolveSibling(target.getFileName() + ".tmp");

            try
            {
                // Write the snapshot to a temporary file
                try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8))
                {
                    gson.toJson(playerAccountSnapshot, writer);
                }

                // Atomically move the temporary file to the real JSON file
                try
                {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
                catch (AtomicMoveNotSupportedException ignored)
                {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }

                // Remove the snapshot from `dirtyPlayerAccountSnapshots` - only remove if same instance
                dirtyPlayerAccountSnapshots.remove(uuid, playerAccountSnapshot);
            }
            catch (IOException exception)
            {
                getLogger().log(Level.WARNING, "Failed to save player account to file: " + target);
                exception.printStackTrace();
            }
        }
    }

    // Method to update BalanceTop async using immutable snapshot data
    private CompletableFuture<BalanceTop> updateBalanceTop(List<BalanceTopCandidate> candidates)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            Map<String, List<Map.Entry<UUID, BigDecimal>>> topBalancesByCurrency = new HashMap<>();
            BigDecimal total = BigDecimal.ZERO;

            for (String currencyId : currencies.keySet())
            {
                List<Map.Entry<UUID, BigDecimal>> balancesForCurrency = new ArrayList<>();

                for (BalanceTopCandidate candidate : candidates)
                {
                    if (!candidate.excludedByEvent() && !candidate.excludedByPermission() && !candidate.excludedByBan())
                    {
                        BigDecimal balance = candidate.balances().getOrDefault(currencyId, BigDecimal.ZERO);

                        if ("coins".equalsIgnoreCase(currencyId))
                        {
                            total = total.add(balance);
                        }

                        if (balance.compareTo(balanceTopMinBalance) >= 0)
                        {
                            balancesForCurrency.add(Map.entry(candidate.uuid(), balance));
                        }
                    }
                }

                List<Map.Entry<UUID, BigDecimal>> topBalances = balancesForCurrency.stream()
                        .sorted((first, second) -> second.getValue().compareTo(first.getValue()))
                        .limit(100)
                        .toList();

                topBalancesByCurrency.put(normalizeCurrencyId(currencyId), topBalances);
            }

            return new BalanceTop(topBalancesByCurrency, total);
        });
    }

    // Immutable snapshot candidate used by BalanceTop two-phase processing
    private record BalanceTopCandidate(UUID uuid, Map<String, BigDecimal> balances, boolean excludedByEvent, boolean excludedByPermission, boolean excludedByBan)
    {
    }

    // Method to reload the config and data files
    public void reload()
    {
        // Reload config.yml from disk
        reloadConfig();

        // Reload currency registry
        currencies = loadCurrencies();

        // Re-get values from config that were retrieved in "onEnable"
        balanceTopConsiderExcludePermission = getConfig().getBoolean("settings.balancetop.consider-exclude-permission");
        balanceTopExcludePermanentlyBannedPlayers = getConfig().getBoolean("settings.balancetop.exclude-permanently-banned-players");
        balanceTopMinBalance = new BigDecimal(getConfig().getString("settings.balancetop.min-balance"));

        // Set whether to send logs to console
        Logger logger = getLogger();
        if (getConfig().getBoolean("settings.logging.log-console"))
        {
            if (!logger.getUseParentHandlers())
            {
                logger.setUseParentHandlers(true);
            }
        }
        else
        {
            if (logger.getUseParentHandlers())
            {
                logger.setUseParentHandlers(false);
            }
        }

        // Set whether to send logs to the "logs.log" file
        if (getConfig().getBoolean("settings.logging.log-file"))
        {
            if (logFileHandler != null)
            {
                if (!Arrays.asList(logger.getHandlers()).contains(logFileHandler))
                {
                    logger.addHandler(logFileHandler);
                }
            }
            else
            {
                setupLogFileHandler();
            }
        }
        else
        {
            // Remove the FileHandler from the logger, if it exists
            if (logFileHandler != null)
            {
                for (Handler handler : logger.getHandlers())
                {
                    if (handler.equals(logFileHandler))
                    {
                        logger.removeHandler(logFileHandler);
                        break;
                    }
                }
            }
        }

        // Shut down the save dirty player accounts scheduler, allowing the current task (if there is one running) to finish
        saveDirtyPlayerAccountsScheduler.shutdown();

        // Allow currently running task to finish, or force shutdown if it hasn't after 30 seconds (it should never take this long)
        try
        {
            if (!saveDirtyPlayerAccountsScheduler.awaitTermination(30, TimeUnit.SECONDS))
            {
                saveDirtyPlayerAccountsScheduler.shutdownNow();
            }
        }
        catch (InterruptedException ignored)
        {
            saveDirtyPlayerAccountsScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Ensure any remaining dirty player accounts are saved
        saveDirtyPlayerAccounts();

        // Re-set "saveDirtyPlayerAccountsScheduler"
        saveDirtyPlayerAccountsScheduler = Executors.newSingleThreadScheduledExecutor();

        // Re-schedule repeating data save task
        runSaveDirtyPlayerAccountsLoop();

        // Re-cache player accounts
        playerAccounts = cachePlayerAccounts();

        // Cancel the current repeating BalanceTop update task
        updateBalanceTopTask.cancel();

        // Re-set "updateBalanceTopTaskRunning"
        updateBalanceTopTaskRunning.set(false);

        // Re-schedule repeating BalanceTop update task
        scheduleBalanceTopUpdateTask();
    }

    // Method to load the currency registry from config.yml
    private Map<String, Currency> loadCurrencies()
    {
        Map<String, Currency> result = new LinkedHashMap<>();
        Map<String, List<String>> collapsedCurrencyKeys = new LinkedHashMap<>();

        ConfigurationSection section = getConfig().getConfigurationSection("settings.currencies");

        if (section != null)
        {
            for (String id : section.getKeys(false))
            {
                String normalizedId = normalizeCurrencyId(id);
                String path = "settings.currencies." + id + ".";
                collapsedCurrencyKeys.computeIfAbsent(normalizedId, ignored -> new ArrayList<>()).add(id);

                try
                {
                    String nameSingular = getConfig().getString(path + "name-singular", id);
                    String namePlural = getConfig().getString(path + "name-plural", id);
                    BigDecimal defaultBalance = new BigDecimal(getConfig().getString(path + "default-balance", "0")).stripTrailingZeros();
                    BigDecimal maxBalance = new BigDecimal(getConfig().getString(path + "max-balance", "10000000000000"));
                    int decimalPlaces = getConfig().getInt(path + "decimal-places", 0);
                    String format = getConfig().getString(path + "format", "<amount> <name>");
                    RoundingMode roundingMode = RoundingMode.valueOf(getConfig().getString(path + "rounding-mode", "NONE"));

                    result.put(normalizedId, new Currency(normalizedId, nameSingular, namePlural, defaultBalance, maxBalance, decimalPlaces, format, roundingMode));
                }
                catch (IllegalArgumentException exception)
                {
                    getLogger().log(Level.WARNING, "Failed to load currency '" + id + "' from config: " + exception.getMessage());
                }
            }

            collapsedCurrencyKeys.forEach((normalizedKey, originalKeys) ->
            {
                if (originalKeys.size() > 1)
                {
                    getLogger().warning("Duplicate currency keys collapse to normalized ID '" + normalizedKey + "': " + String.join(", ", originalKeys));
                }
            });
        }

        return result;
    }

    // Method to log startup guidance for PlaceholderAPI usage
    private void logPlaceholderStartupGuidance()
    {
        List<String> currencyIds = new ArrayList<>(currencies.keySet());

        if (currencyIds.isEmpty())
        {
            getLogger().warning("No currencies are configured. Placeholder examples will use <currencyId>.");
            getLogger().info("Vault placeholders are default-currency compatible only (coins).");
            getLogger().info("Player-scoped placeholders (require a player context): %orbiseconomy_balance_<currencyId>%, %orbiseconomy_balance_formatted_<currencyId>%, %orbiseconomy_accepting_payments%");
            getLogger().info("Global placeholders (safe for holograms/leaderboards): %orbiseconomy_top_<currencyId>_<position>_<type>%, %orbiseconomy_richest_<position>_<type>%, %orbiseconomy_combined_total_balance%, %orbiseconomy_combined_total_balance_formatted%");
            getLogger().info("Explicit target placeholders (for non-player contexts): %orbiseconomy_balance_for_uuid_<currencyId>_<uuid>%, %orbiseconomy_balance_formatted_for_uuid_<currencyId>_<uuid>%");
            return;
        }

        String exampleCurrencyId = currencyIds.getFirst();

        getLogger().info("Available currency IDs: " + String.join(", ", currencyIds));
        getLogger().info("Vault placeholders are default-currency compatible only (coins).");
        getLogger().info("Player-scoped placeholders (require player context):");
        getLogger().info(" - %orbiseconomy_balance_" + exampleCurrencyId + "%");
        getLogger().info(" - %orbiseconomy_balance_formatted_" + exampleCurrencyId + "%");
        getLogger().info(" - %orbiseconomy_accepting_payments%");
        getLogger().info("Global placeholders (safe for holograms/leaderboards):");
        getLogger().info(" - %orbiseconomy_top_" + exampleCurrencyId + "_1_balance_formatted%");
        getLogger().info(" - %orbiseconomy_richest_1_balance_formatted%");
        getLogger().info(" - %orbiseconomy_combined_total_balance%");
        getLogger().info(" - %orbiseconomy_combined_total_balance_formatted%");
        getLogger().info("Explicit target placeholders (non-player contexts):");
        getLogger().info(" - %orbiseconomy_balance_for_uuid_" + exampleCurrencyId + "_<uuid>%");
        getLogger().info(" - %orbiseconomy_balance_formatted_for_uuid_" + exampleCurrencyId + "_<uuid>%");
        getLogger().info(" - %orbiseconomy_balance_for_name_" + exampleCurrencyId + "_<name>%");
        getLogger().info(" - %orbiseconomy_balance_formatted_for_name_" + exampleCurrencyId + "_<name>%");
    }

    // Method to create a new PlayerAccount with default balances for all configured currencies
    public PlayerAccount createDefaultAccount()
    {
        Map<String, BigDecimal> balances = new ConcurrentHashMap<>();

        for (Map.Entry<String, Currency> entry : currencies.entrySet())
        {
            balances.put(entry.getKey(), entry.getValue().getDefaultBalance());
        }

        return new PlayerAccount(balances, true);
    }


    private boolean isClassPresent(String className)
    {
        try
        {
            Class.forName(className, false, getClassLoader());
            return true;
        }
        catch (ClassNotFoundException ignored)
        {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerVaultEconomyService()
    {
        try
        {
            Class vaultEconomyClass = Class.forName("net.milkbowl.vault.economy.Economy", false, getClassLoader());
            Bukkit.getServicesManager().register(vaultEconomyClass, economy, this, ServicePriority.Highest);
        }
        catch (ClassNotFoundException exception)
        {
            throw new IllegalStateException("Vault API classes are unavailable while registering economy service.", exception);
        }
    }

    // ----- Getters -----

    // Getter for "dirtyPlayerAccountSnapshots"
    public Map<UUID, PlayerAccountSnapshot> getDirtyPlayerAccountSnapshots()
    {
        return dirtyPlayerAccountSnapshots;
    }

    // Getter for "economy"
    public Economy getEconomy()
    {
        return economy;
    }

    // Getter for ""
    public Map<UUID, String> getOfflinePlayerNames()
    {
        return offlinePlayerNames;
    }

    // Getter for "playerAccounts"
    public Map<UUID, PlayerAccount> getPlayerAccounts()
    {
        return playerAccounts;
    }

    // Getter for "balanceTop"
    public BalanceTop getBalanceTop()
    {
        return balanceTop;
    }

    // Getter for "miniMessage"
    public MiniMessage getMiniMessage()
    {
        return miniMessage;
    }

    // Getter for "legacyComponentSerializer"
    public LegacyComponentSerializer getLegacyComponentSerializer()
    {
        return legacyComponentSerializer;
    }

    // Getter for "currencies"
    public Map<String, Currency> getCurrencies()
    {
        return currencies;
    }

    public Map<UUID, PremiumBalance> getPremiumBalances()
    {
        return premiumBalances;
    }

    public OrbisPaperAgentBridge getOrbisPaperAgentBridge()
    {
        return orbisPaperAgentBridge;
    }

    public static String normalizeCurrencyId(String currencyId)
    {
        return currencyId == null ? "" : currencyId.trim().toLowerCase(Locale.ROOT);
    }

}
