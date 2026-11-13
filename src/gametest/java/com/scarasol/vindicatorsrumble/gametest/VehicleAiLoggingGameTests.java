package com.scarasol.vindicatorsrumble.gametest;

import com.scarasol.vindicatorsrumble.config.CommonConfig;
import com.scarasol.vindicatorsrumble.util.VehicleAiLogger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder("vindicators_rumble")
@PrefixGameTestTemplate(false)
public final class VehicleAiLoggingGameTests {
    @GameTest(batch = "vehicle_logging", template = "empty", timeoutTicks = 20)
    public static void periodicLogsDeduplicateWithoutLosingEventsOrChangedState(GameTestHelper helper) {
        boolean previousDebug = CommonConfig.VEHICLE_AI_DEBUG.get();
        Mob first = helper.spawn(EntityType.COW, new BlockPos(1, 1, 1));
        Mob second = helper.spawn(EntityType.COW, new BlockPos(2, 1, 1));
        first.setNoAi(true);
        second.setNoAi(true);
        List<LogEvent> events = new ArrayList<>();
        org.apache.logging.log4j.core.Logger root =
                (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
        AbstractAppender observer = new AbstractAppender("vehicle_logging_contract", null, null, false) {
            @Override
            public void append(LogEvent event) {
                if (event.getMessage().getFormattedMessage().contains("Vehicle logging contract ")) {
                    events.add(event.toImmutable());
                }
            }
        };
        observer.start();
        root.addAppender(observer);
        Runnable close = () -> {
            root.removeAppender(observer);
            observer.stop();
            CommonConfig.VEHICLE_AI_DEBUG.set(previousDebug);
            first.discard();
            second.discard();
        };
        try {
            CommonConfig.VEHICLE_AI_DEBUG.set(false);
            VehicleAiLogger.debug("Vehicle logging contract disabled");
            VehicleAiLogger.debug(first, "Vehicle logging contract disabled");
            VehicleAiLogger.debugEvery(first, 1, "Vehicle logging contract disabled");
            helper.assertTrue(events.isEmpty(), "Disabled diagnostics emitted messages");
            helper.assertTrue(!VehicleAiLogger.shouldLog(first, 1), "Disabled sampling gate was open");

            CommonConfig.VEHICLE_AI_DEBUG.set(true);
            VehicleAiLogger.debugEvery(first, 1, "Vehicle logging contract snapshot weapon={} state={}", 1, "ready");
            VehicleAiLogger.debugEvery(first, 1, "Vehicle logging contract snapshot weapon={} state={}", 1, "ready");
            helper.assertTrue(events.size() == 1, "Identical same-tick snapshots were not deduplicated");
            VehicleAiLogger.debugEvery(first, 1, "Vehicle logging contract snapshot weapon={} state={}", 1, "waiting");
            VehicleAiLogger.debugEvery(first, 1, "Vehicle logging contract snapshot weapon={} state={}", 2, "ready");
            VehicleAiLogger.debugEvery(second, 1, "Vehicle logging contract snapshot weapon={} state={}", 1, "ready");
            helper.assertTrue(events.size() == 4, "Changed state, weapon or entity was suppressed");

            VehicleAiLogger.debug(first, "Vehicle logging contract event");
            VehicleAiLogger.debug(first, "Vehicle logging contract event");
            helper.assertTrue(events.size() == 6, "Repeated events must not be deduplicated");

            StringBuilder detail = new StringBuilder("before");
            VehicleAiLogger.debugEvery(first, 1, "Vehicle logging contract mutable {}", detail);
            detail.replace(0, detail.length(), "after");
            VehicleAiLogger.debugEvery(first, 1, "Vehicle logging contract mutable {}", detail);
            helper.assertTrue(events.size() == 8, "Changed mutable diagnostic argument was suppressed");

            RuntimeException failure = new RuntimeException("Vehicle logging contract expected exception");
            VehicleAiLogger.debugEvery(first, 1, "Vehicle logging contract failure", failure);
            VehicleAiLogger.debugEvery(first, 1, "Vehicle logging contract failure", failure);
            helper.assertTrue(events.size() == 10
                            && events.stream().filter(event -> event.getThrown() == failure).count() == 2,
                    "Exception diagnostics lost occurrences or their stack trace");
            int initialTick = first.tickCount;
            helper.runAfterDelay(2, () -> {
                try {
                    helper.assertTrue(first.tickCount > initialTick, "Fixture did not advance through server ticks");
                    VehicleAiLogger.debugEvery(first, 1,
                            "Vehicle logging contract snapshot weapon={} state={}", 1, "ready");
                    helper.assertTrue(events.size() == 11, "Snapshot was suppressed after the entity tick advanced");
                    CommonConfig.VEHICLE_AI_DEBUG.set(false);
                    VehicleAiLogger.debugEvery(first, 1, "Vehicle logging contract disabled again");
                    helper.assertTrue(events.size() == 11, "Disabling diagnostics did not stop output");
                    helper.succeed();
                } finally {
                    close.run();
                }
            });
        } catch (RuntimeException | Error exception) {
            close.run();
            throw exception;
        }
    }
}
