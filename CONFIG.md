# CONFIG.md

## Placeholder compatibility

Vault placeholder compatibility is **default-currency only**.

- Placeholders coming from Vault integrations read from the default `coins` currency.
- This is for compatibility with single-currency expectations in Vault-based plugins.

## Multi-currency placeholders

To target a specific configured currency ID, use OrbisEconomy's currency-aware placeholders:

- `%orbiseconomy_balance_<currencyId>%`
- `%orbiseconomy_balance_formatted_<currencyId>%`
- `%orbiseconomy_top_<currencyId>_<position>_<type>%`

Replace `<currencyId>` with an ID from `settings.currencies` in `config.yml` (for example: `coins`, `gems`, `tokens`).


## Placeholder context and fallbacks

Player-scoped placeholders:

- `%orbiseconomy_balance_<currencyId>%`
- `%orbiseconomy_balance_formatted_<currencyId>%`
- `%orbiseconomy_accepting_payments%`

When these are requested without a player context, the plugin returns
`settings.placeholders.player-required-fallback` (default: `PLAYER_REQUIRED`).

Global placeholders (safe for holograms/leaderboards):

- `%orbiseconomy_top_<currencyId>_<position>_<type>%`
- `%orbiseconomy_richest_<position>_<type>%`
- `%orbiseconomy_combined_total_balance%`
- `%orbiseconomy_combined_total_balance_formatted%`

Explicit player-target placeholders for non-player contexts:

- `%orbiseconomy_balance_for_uuid_<currencyId>_<uuid>%`
- `%orbiseconomy_balance_formatted_for_uuid_<currencyId>_<uuid>%`
- `%orbiseconomy_balance_for_name_<currencyId>_<name>%`
- `%orbiseconomy_balance_formatted_for_name_<currencyId>_<name>%`


## EconomyBridge IDs (`settings.economybridge.id-prefix`)

EconomyBridge currency IDs are built from:

- `<id-prefix><currency-id>`
- `id-prefix` comes from `settings.economybridge.id-prefix` in `config.yml`.
- `currency-id` is the normalized key from `settings.currencies` (for example: `coins`, `orbs`, `votepoints`).

Examples:

- `settings.economybridge.id-prefix: ""` -> `coins`, `orbs`, `votepoints`
- `settings.economybridge.id-prefix: "orbiseconomy_"` -> `orbiseconomy_coins`, `orbiseconomy_orbs`, `orbiseconomy_votepoints`

Use these exact IDs in shop/reward configs (ExcellentShop, NightCore, SunLight, and other EconomyBridge consumers).
If IDs do not resolve after config changes, run `/economybridge reload` and re-check the startup sync summary log.
