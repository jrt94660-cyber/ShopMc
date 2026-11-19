# ShopMc — Recreated

A clean-room Paper shop plugin based on the behavior/configuration of the supplied SMPShop 26.3 jar. License activation, license validation, expiration checks, license-server communications, and premium access locks are intentionally not included.

## Features
- Configurable main category menu and category inventories.
- End, Nether, Gear, and Food shops with configurable prices, materials, display names, lore, glow, commands, and item quantities.
- Shop purchase confirmation inventory with amount controls for stackable goods.
- Vault economy; optional CoinsEngine command-based currency withdrawal; legacy PlayerPoints category configuration maps to the configured Shards currency.
- Message routing to chat, action bar, and title/subtitle using `basic-setup/messages.yml`.
- Sounds and money-format suffixes/decimals configurable.
- Shards category button runs the external `/shardshop` command (the ShardShop plugin must be installed).
- `/shop` and `/shop reload`.

## Build
Java 21, Gradle 8.14.3. Run `gradle clean build`. CI builds automatically on pushes and uploads the JAR as a workflow artifact.

## Commands and permissions
- `/shop` — opens shop; `smpshop.use` (default true)
- `/shop reload` — reloads; `smpshop.admin` (default OP)

## Shards / CoinsEngine
The default currency value is `shards`. Set `economy.coinsengine-withdraw-command` to the exact withdrawal command supported by your CoinsEngine version, using `%player%`, `%amount%`, and `%currency%` placeholders. The Shards main-menu icon runs `/shardshop` instead of opening an internal category.
