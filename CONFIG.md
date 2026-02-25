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


## Placeholder fallback behavior

- `%orbiseconomy_balance_<currencyId>%` and `%orbiseconomy_balance_formatted_<currencyId>%` return `0` when player/account/currency context is missing.
- `%orbiseconomy_top_<currencyId>_<position>_name%`, `%orbiseconomy_top_<currencyId>_<position>_uuid%`, `%orbiseconomy_top_<currencyId>_<position>_balance%`, and `%orbiseconomy_top_<currencyId>_<position>_balance_formatted%` return `N/A` when parsing fails (invalid currency/position/type).
- For valid top placeholders with an out-of-range position, fallback values come from `settings.placeholders.balancetop-position-<type>-none` (default `N/A`).

Legacy compatibility aliases remain available after exact-match parsing:

- `%orbiseconomy_richest_<position>_<type>%` (maps to `coins`).
- `%orbiseconomy_top_<currencyId>_<position>_formatted%` (maps to `_balance_formatted`).
