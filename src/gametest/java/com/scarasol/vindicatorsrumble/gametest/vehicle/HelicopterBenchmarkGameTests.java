package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.scarasol.vindicatorsrumble.gametest.profile.HelicopterSearchProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("vindicators_rumble_benchmark")
@PrefixGameTestTemplate(false)
public final class HelicopterBenchmarkGameTests {
    private HelicopterBenchmarkGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 42_000)
    public static void profiledFullGoalObstacleCorpus(GameTestHelper helper) {
        HelicopterSearchProfile.start();
        new HelicopterObstacleCorpus(helper).start();
    }
}
