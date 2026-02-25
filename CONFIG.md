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
