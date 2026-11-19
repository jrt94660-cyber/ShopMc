# ShopMc 1.0.4

Shop GUI plugin for Paper 1.21+ (Java 21). Configurable categories, item pricing, purchase confirmation, and economy routing.

## Commands
- `/shop` opens the main category menu.
- `/shop food`, `/shop blocks`, `/shop farm`, `/shop mob`, `/shop gear`, `/shop red`, `/shop premium`, `/shop shards` opens the selected category directly.
- `/shop reload` reloads configuration.

Tab completion suggests available category names and filters by the typed prefix; `reload` is suggested to users with the admin permission.

## Permissions
- `smpshop.use` — use the shop (default: true).
- `smpshop.admin` — reload the shop (default: OP).

## Features
- Main menu and configurable category menus.
- Shards is an internal shop category; its button opens the category GUI, not an external `/shardshop` command.
- Purchase confirmation with stack amount controls.
- Configurable material, name, lore, prices, enchantments, item quantity, and purchase commands.
- Vault, PlayerPoints, and command-based CoinsEngine withdrawals.
- Chat/action-bar/title message routing and sound settings.
- Supports command placeholders `%player%`, `{player}`, `%amount%`, `{amount}`, `%price%`, `{price}`, `%currency%`, and `{currency}`.

## Config files
- `config.yml`
- `shop/main-shop-gui.yml`
- `shop/<category>.yml`
- `basic-setup/messages.yml`, `sounds.yml`, `money-format.yml`
- `economy-confirm-gui/*.yml`

## Build
Java 21 and Gradle 8.14.3:
```
gradle clean build
``
CI builds on pushes to main and uploads the JAR as a workflow artifact.
