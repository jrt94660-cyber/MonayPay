# MonayPay 1.5.0

Native Minecraft Dialog payment plugin for MonaySMP. Uses Vault's Economy service (intended for ExcellentEconomy + Vault).

## Features
- `/pay` opens a native dialog with recipient and amount inputs, plus a live snapshot of other online players.
- `/pay <player> <amount>` opens a native confirmation dialog.
- Confirms recipient and amount before transfer.
- Rechecks online status and balance at confirmation time.
- Blocks self-payments, invalid usernames, invalid amounts, and offline recipients.
- Uses Vault withdraw/deposit responses and attempts a refund if recipient deposit fails.

## Build
Requires JDK 21 and Maven. Run `mvn clean package`; output is `target/MonayPay-1.5.0.jar`.

Alternatively, push this project to a GitHub repository and run **Actions → Build MonayPay → Run workflow**. The JAR is published as a workflow artifact.

## Install
1. Ensure Paper 26.2, Vault, and ExcellentEconomy are installed and enabled.
2. Upload `MonayPay-1.0.0.jar` to the server's `plugins` folder.
3. Restart the server (do not use `/reload`).
4. Test `/pay` and `/pay <online-player> <amount>` with two test accounts first.

## Compatibility note
The Paper API artifact version is pinned in `pom.xml` to `26.2.build.141-beta` to match the requested Paper 26.2 target. If your host uses a different Paper 26.2 build, update `paper.version` to the exact API version printed in the server startup log, then rebuild. The JAR must be built against a resolvable Paper API artifact.
