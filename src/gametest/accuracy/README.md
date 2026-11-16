# Vehicle Base Accuracy Tests

Run without Pillager's Gun from the vehicle project:

```powershell
$env:GRADLE_USER_HOME = 'D:/Library/.gradle'
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=D:/Workspace/VindicatorsRumble-forge-1.20.1/build'
Set-Location 'D:/Workspace/VindicatorsRumble-forge-1.20.1'
./gradlew.bat --offline --no-daemon -I src/gametest/accuracy/accuracy.init.gradle '-PgameTestNamespaces=vindicators_rumble,vindicators_rumble_isolation' runGameTestServer
```

Four native shooting contract tests check proportional native spread across tank
and machine-gun weapons, zero spread, live setting changes, identity-multiplier
precision, unchanged weapon data, and player/handheld/untagged exclusions.
Four existing navigation ownership tests also run as a server-loading regression.

The shared capture mixin observes native shot parameters without cancelling
shooting. These contract tests do not claim movement or statistical hit-rate
coverage. The optional tests in `src/gametest/pillagersgun` also verify dynamic
event composition and actual Goal-driven tank/pickup fire.

In `VindicatorsRumble-common.toml`, the setting belongs to `Vehicle AI`:

```toml
["Vehicle AI"]
    "Vehicle Weapon Inaccuracy Multiplier" = 1.3
```

Each weapon's native spread is multiplied by this nonnegative coefficient.
The default 1.3 increases native spread by 30%. A value of 1 retains native
spread; 0.5 halves it, and 2 doubles it. Zero removes random spread, not aiming,
projectile physics, or obstructions. The setting is not a hit probability and
has no effect on players or handheld guns.
This replaces the former absolute `Vehicle Weapon Inaccuracy` setting; its old
value is not interpreted as a multiplier.
