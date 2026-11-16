package com.scarasol.vindicatorsrumble.entity.goal.vehicle.drive;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriveArchitectureTest {
    private static final String ROOT = "com/scarasol/vindicatorsrumble/";

    @Test
    void strategiesNeverBuildOrSubmitVehicleInputs() throws Exception {
        for (String strategy : List.of("DriveVehicleGoal", "ground/GroundDriveStrategy", "helicopter/HelicopterDriveStrategy")) {
            Set<String> dependencies = dependencies(ROOT + "entity/goal/vehicle/drive/" + strategy);
            for (String forbidden : List.of("api/vehicle/VehicleInput", "api/vehicle/VehicleFlightInput",
                    "entity/goal/vehicle/drive/ground/GroundSteering",
                    "entity/goal/vehicle/drive/ground/GroundObstacleRadar",
                    "util/VehiclePathContext")) {
                assertFalse(dependencies.stream().anyMatch(value -> value.contains(ROOT + forbidden)),
                        () -> strategy + " depends on execution detail " + forbidden);
            }
            for (String method : List.of("drive", "controlFlight", "stopDriving", "fire", "reload", "selectWeapon")) {
                assertFalse(dependencies.contains(ROOT + "api/vehicle/VehicleController#" + method),
                        () -> strategy + " executes " + method);
            }
            assertFalse(dependencies.contains("net/minecraft/world/entity/ai/navigation/PathNavigation#createPath"),
                    () -> strategy + " performs path execution");
            assertFalse(dependencies.stream().anyMatch(value -> value.startsWith("com/atsuishio/")),
                    () -> strategy + " depends on a concrete vehicle mod");
        }
    }

    @Test
    void groundExecutionCannotSelectCombatTactics() throws Exception {
        Set<String> dependencies = dependencies(ROOT + "entity/goal/vehicle/drive/ground/GroundDriveControl");
        for (String forbidden : List.of("util/VehicleAiMemory", "util/VehicleSmokeDefense", "api/vehicle/CurrentWeaponSnapshot",
                "api/vehicle/WeaponStatus", "api/vehicle/WeaponHandle",
                "entity/goal/vehicle/drive/ground/GroundDriveStrategy")) {
            assertFalse(dependencies.stream().anyMatch(value -> value.contains(ROOT + forbidden)),
                    () -> "Ground execution depends on tactical owner " + forbidden);
        }
        assertFalse(dependencies.contains("net/minecraft/world/entity/Mob#getTarget"));
        assertFalse(dependencies.contains(ROOT + "api/vehicle/VehicleController#hasAnyWeaponAmmo"));
        assertTrue(dependencies.contains(ROOT + "api/vehicle/VehicleController#drive"));
    }

    @Test
    void flightExecutionCannotReadCombatGoalMemory() throws Exception {
        for (String execution : List.of("HelicopterFlightControl", "HelicopterFlightPlanner")) {
            Set<String> dependencies = dependencies(ROOT + "entity/goal/vehicle/drive/helicopter/" + execution);
            for (String forbidden : List.of("util/VehicleAiMemory", "util/VehicleSmokeDefense", "api/vehicle/CurrentWeaponSnapshot",
                    "api/vehicle/WeaponStatus", "api/vehicle/WeaponHandle")) {
                assertFalse(dependencies.stream().anyMatch(value -> value.contains(ROOT + forbidden)),
                        () -> execution + " depends on tactical owner " + forbidden);
            }
            if (execution.equals("HelicopterFlightControl")) {
                assertTrue(dependencies.contains(ROOT + "api/vehicle/VehicleController#controlFlight"));
            }
        }
    }

    @Test
    void adapterCannotChooseDefensiveActionsDuringInputTranslation() throws Exception {
        Set<String> dependencies = dependencies(ROOT + "compat/sbw/SbwVehicleController");
        for (String method : List.of("shouldDeploy", "requestDeployment", "applyInput")) {
            assertFalse(dependencies.contains(ROOT + "util/VehicleSmokeDefense#" + method),
                    () -> "Adapter selects defensive action through " + method);
        }
    }

    @Test
    void dispatcherCannotChooseNavigationDestinations() throws Exception {
        Set<String> dependencies = dependencies(ROOT + "entity/goal/vehicle/drive/DriveVehicleGoal");
        assertFalse(dependencies.contains(ROOT + "util/VehicleAiMemory#setNavigationDriveRequest"));
        assertFalse(dependencies.contains(ROOT + "api/vehicle/DriveRequest#moveTo"));
    }

    private Set<String> dependencies(String className) throws Exception {
        Set<String> dependencies = new HashSet<>();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(className + ".class")) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    dependencies.add(descriptor);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitTypeInsn(int opcode, String type) {
                            dependencies.add(type);
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name,
                                                    String descriptor, boolean isInterface) {
                            dependencies.add(owner);
                            dependencies.add(owner + "#" + name);
                            dependencies.add(descriptor);
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                            dependencies.add(owner);
                            dependencies.add(descriptor);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return dependencies;
    }
}
