# OrbisEconomy

## PlaceholderAPI placeholders

> [!IMPORTANT]
> Vault placeholder compatibility is **default-currency only**. Any Vault/Economy placeholders
> exposed through other plugins read from the configured `coins` currency.
>
> For multi-currency usage, use OrbisEconomy's explicit currency-aware placeholders:
>
> - `%orbiseconomy_balance_<currencyId>%` (player-scoped)
> - `%orbiseconomy_balance_formatted_<currencyId>%` (player-scoped)
> - `%orbiseconomy_accepting_payments%` (player-scoped)
> - `%orbiseconomy_top_<currencyId>_<position>_<type>%` (global)
> - `%orbiseconomy_combined_total_balance%` (global)
> - `%orbiseconomy_combined_total_balance_formatted%` (global)

Top placeholders support both currency-aware and legacy richest formats:

- `%orbiseconomy_top_<currencyId>_<position>_<type>%`
- `%orbiseconomy_richest_<position>_<type>%` (uses `coins` as the default currency)

Supported `<type>` values:

- `name`
- `uuid`
- `balance`
- `balance_formatted` (alias: `formatted`)
- `entry`
- `entry_legacy`

`entry` and `entry_legacy` return a combined `player name + formatted amount` string. The
legacy variant uses section-sign (`§`) formatting for legacy text consumers.

When the requested position does not exist, each type uses its matching fallback from
`settings.placeholders` in `config.yml`.


Player-scoped placeholders require PlaceholderAPI to pass a player UUID/context. If a plugin requests
one of these placeholders without a player, OrbisEconomy returns `settings.placeholders.player-required-fallback`
(default: `PLAYER_REQUIRED`) so configuration issues are visible.

For non-player contexts that still need a player's balance, use explicit target placeholders:

- `%orbiseconomy_balance_for_uuid_<currencyId>_<uuid>%`
- `%orbiseconomy_balance_formatted_for_uuid_<currencyId>_<uuid>%`
- `%orbiseconomy_balance_for_name_<currencyId>_<name>%`
- `%orbiseconomy_balance_formatted_for_name_<currencyId>_<name>%`


## EconomyBridge currency IDs (NightCore / SunLight / ExcellentShop)

OrbisEconomy registers one EconomyBridge currency per configured `settings.currencies` entry.

- By default, IDs are the raw currency IDs from `config.yml` (for example: `coins`, `orbs`, `votepoints`).
- `getInternalId()` and `getOriginalId()` now resolve to the same canonical ID.
- Optional namespacing can be enabled with `settings.economybridge.id-prefix`.
  - Example: `id-prefix: "orbiseconomy_"` makes `orbs` register as `orbiseconomy_orbs`.
- Shops and all EconomyBridge-backed plugins must reference these **exact canonical IDs**.

At startup, OrbisEconomy logs a sync summary in this exact format:

- `EconomyBridge sync complete: [coins, orbs, votepoints]`

The IDs inside that summary already include your configured `settings.economybridge.id-prefix`.
If the EconomyBridge verification API is unavailable, OrbisEconomy logs deterministic expected IDs and instructs you to run `/economybridge reload`.

Example IDs in EconomyBridge-backed plugin configs:

```yaml
# Example: ExcellentShop / NightCore / SunLight style config snippets
Price:
  Currency: "orbs"
  Amount: 25

Reward:
  Currency: "coins"
  Amount: 100
```

If you set `settings.economybridge.id-prefix: "orbiseconomy_"`, use:

```yaml
Currency: "orbiseconomy_orbs"
Currency: "orbiseconomy_coins"
```

