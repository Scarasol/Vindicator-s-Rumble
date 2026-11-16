# Optional Pillager's Gun Accuracy Tests

This source tree is compiled only by `accuracy.init.gradle` against a local
Pillager's Gun checkout. It is not part of normal compilation or release jars.

Build the current vehicle classes, then run the ten focused tests:

```powershell
$env:GRADLE_USER_HOME = 'D:/Library/.gradle'
Set-Location 'D:/Workspace/VindicatorsRumble-forge-1.20.1'
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=D:/Workspace/VindicatorsRumble-forge-1.20.1/build'
./gradlew.bat --offline --no-daemon compileJava processResources

Set-Location 'D:/Workspace/Pillager-s-Gun'
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=D:/Workspace/Pillager-s-Gun/build'
./gradlew.bat --offline --no-daemon -I D:/Workspace/VindicatorsRumble-forge-1.20.1/src/gametest/pillagersgun/accuracy.init.gradle -PvehicleProject=D:/Workspace/VindicatorsRumble-forge-1.20.1 runGameTestServer
```

The test-only mixin observes parameters received by native SBW shooting without
cancelling the shot. Contract cases check event propagation, dynamic settings,
scaled-spread precision, locked targets, persistent weapon data, and exclusions.
Two real-tick scenarios acquire villagers naturally and fire from tank/pickup
weapon seats. These are accuracy integration tests, not driving or performance
benchmarks.

Production uses `Vehicle AI` / `Vehicle Weapon Inaccuracy Multiplier` to scale
each weapon's native spread. The default 1.3 increases native spread by 30%.
A value of 1 preserves native spread, 0.5 halves it, 2 doubles it, and zero removes
random spread.
Pillager's Gun's existing accuracy event runs after this multiplication.
Handheld weapon-category overrides are not applied to vehicle weapons.
Interpretation of the event, including the gun mod's held-item sniper check,
remains owned by Pillager's Gun.
