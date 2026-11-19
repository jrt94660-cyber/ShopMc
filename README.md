# ShopMc recreation

An independent Paper shop plugin recreation based on the behavior and configuration exposed by the provided SMPShop 26.3 jar. License/activation/expiration and premium-lock logic are omitted.

Features: category menus, pagination, confirmation purchase menus, configurable item prices/commands/lore, Vault economy, optional PlayerPoints, configurable CoinsEngine withdrawal command, messages/sounds, and reload.

Build with Java 21 and Gradle 8.14.3: `gradle build`. The GitHub Actions workflow builds and uploads the jar as a workflow artifact.

Commands: `/shop`; `/shop reload`.
Permissions: `smpshop.use` (default true), `smpshop.admin` (default OP).
