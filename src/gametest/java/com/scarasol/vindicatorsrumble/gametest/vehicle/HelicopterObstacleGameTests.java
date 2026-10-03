package com.scarasol.vindicatorsrumble.gametest.vehicle;

import com.scarasol.vindicatorsrumble.VindicatorsRumbleMod;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(VindicatorsRumbleMod.MODID)
@PrefixGameTestTemplate(false)
public final class HelicopterObstacleGameTests {
    private HelicopterObstacleGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 42_000)
    public static void fullGoalObstacleCorpus(GameTestHelper helper) {
        new HelicopterObstacleCorpus(helper).start();
    }
}
