package com.scarasol.vindicatorsrumble.mixin;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModIsolationTest {
    private static final String PLUGIN = "com.scarasol.vindicatorsrumble.mixin.plugin.MixinPlugin";

    @Test
    void optionalMixinsAreSkippedWhenTheirModsAreAbsent() throws Exception {
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("com.atsuishio.superbwarfare.")
                        || name.startsWith("com.talhanation.recruits.")
                        || name.startsWith("tallestegg.guardvillagers.")
                        || name.startsWith("com.tacz.guns.")
                        || name.startsWith("com.scarasol.pillagers_gun.")) {
                    throw new ClassNotFoundException(name);
                }
                if (!name.equals(PLUGIN)) {
                    return super.loadClass(name, resolve);
                }
                try (InputStream input = getResourceAsStream(name.replace('.', '/') + ".class")) {
                    assertNotNull(input);
                    byte[] bytes = input.readAllBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (java.io.IOException exception) {
                    throw new ClassNotFoundException(name, exception);
                }
            }
        };
        IMixinConfigPlugin plugin = (IMixinConfigPlugin) loader.loadClass(PLUGIN).getConstructor().newInstance();
        for (String optional : List.of("sbw.VehicleAutoFireMixin", "sbw.VehiclePassengerRendererMixin",
                "sbw.VehicleInaccuracyMixin",
                "sbw.pillagersgun.VehicleInaccuracyMixin",
                "recruits.RecruitMovementGoalMixin",
                "recruits.RecruitVehicleNodeEvaluatorMixin",
                "guardvillagers.GuardMixin", "tacz.GunMixin")) {
            assertFalse(plugin.shouldApplyMixin("unused", "com.scarasol.vindicatorsrumble.mixin." + optional));
        }
        assertTrue(plugin.shouldApplyMixin("net.minecraft.world.level.pathfinder.NodeEvaluator",
                "com.scarasol.vindicatorsrumble.mixin.VehicleNodeEvaluatorMixin"));
        assertTrue(plugin.shouldApplyMixin("net.minecraft.world.level.pathfinder.WalkNodeEvaluator",
                "com.scarasol.vindicatorsrumble.mixin.VehicleWalkNodeEvaluatorMixin"));
    }

    @Test
    void vehicleAccuracyRequiresBothOptionalMods() throws Exception {
        String sbw = "com.atsuishio.superbwarfare.Mod";
        String pg = "com.scarasol.pillagers_gun.PillagersGunMod";
        for (Set<String> present : List.of(Set.<String>of(), Set.of(sbw), Set.of(pg), Set.of(sbw, pg))) {
            ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
                @Override
                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (name.equals(sbw) || name.equals(pg)) {
                        if (!present.contains(name)) throw new ClassNotFoundException(name);
                        Class<?> loaded = findLoadedClass(name);
                        if (loaded != null) return loaded;
                        ClassWriter writer = new ClassWriter(0);
                        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name.replace('.', '/'),
                                null, "java/lang/Object", null);
                        writer.visitEnd();
                        byte[] bytes = writer.toByteArray();
                        return defineClass(name, bytes, 0, bytes.length);
                    }
                    if (!name.equals(PLUGIN)) return super.loadClass(name, resolve);
                    try (InputStream input = getResourceAsStream(name.replace('.', '/') + ".class")) {
                        assertNotNull(input);
                        byte[] bytes = input.readAllBytes();
                        return defineClass(name, bytes, 0, bytes.length);
                    } catch (java.io.IOException exception) {
                        throw new ClassNotFoundException(name, exception);
                    }
                }
            };
            IMixinConfigPlugin plugin = (IMixinConfigPlugin) loader.loadClass(PLUGIN).getConstructor().newInstance();
            org.junit.jupiter.api.Assertions.assertEquals(present.size() == 2, plugin.shouldApplyMixin("unused",
                    "com.scarasol.vindicatorsrumble.mixin.sbw.pillagersgun.VehicleInaccuracyMixin"),
                    () -> "Incorrect optional accuracy gate with mods " + present);
            org.junit.jupiter.api.Assertions.assertEquals(present.contains(sbw), plugin.shouldApplyMixin("unused",
                    "com.scarasol.vindicatorsrumble.mixin.sbw.VehicleInaccuracyMixin"),
                    () -> "Base accuracy must only require SBW, present mods " + present);
        }
    }

    @Test
    void releaseDoesNotBundleOrRequirePillagersGun() throws Exception {
        try (JarFile release = new JarFile(System.getProperty("vindicatorsRumble.releaseJar"));
             InputStream metadata = release.getInputStream(release.getJarEntry("META-INF/mods.toml"))) {
            assertTrue(release.stream().noneMatch(entry -> entry.getName().startsWith("com/scarasol/pillagers_gun/")));
            assertFalse(new String(metadata.readAllBytes(), StandardCharsets.UTF_8).contains("modId=\"pillagers_gun\""));
        }
    }

    @Test
    void releaseDoesNotBundleOrRequireLimitlessVehicle() throws Exception {
        try (JarFile release = new JarFile(System.getProperty("vindicatorsRumble.releaseJar"));
             InputStream metadata = release.getInputStream(release.getJarEntry("META-INF/mods.toml"))) {
            assertFalse(release.stream().anyMatch(entry -> entry.getName().startsWith("org/ywzj/vehicle/")));
            assertFalse(new String(metadata.readAllBytes(), StandardCharsets.UTF_8).contains("modId=\"ywzj_vehicle\""));
        }
    }

    @Test
    void vehicleSizedPathfindingIsRegisteredAsACommonMixin() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/vindicators_rumble.mixins.json")) {
            assertNotNull(input);
            var mixins = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("mixins");
            List<String> names = new java.util.ArrayList<>();
            mixins.forEach(name -> names.add(name.getAsString()));
            assertTrue(names.contains("VehicleNodeEvaluatorMixin"));
            assertTrue(names.contains("VehicleWalkNodeEvaluatorMixin"));
            assertFalse(names.contains("sbw.VehicleNodeEvaluatorMixin"));
        }
    }

    @Test
    void sbwPassengerHidingIsClientOnlyAndMappedForRelease() throws Exception {
        try (JarFile release = new JarFile(System.getProperty("vindicatorsRumble.releaseJar"));
             InputStream configInput = release.getInputStream(release.getJarEntry("vindicators_rumble.mixins.json"));
             InputStream refmapInput = release.getInputStream(release.getJarEntry("vindicators_rumble.refmap.json"))) {
            var config = JsonParser.parseReader(new InputStreamReader(configInput, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            var name = new com.google.gson.JsonPrimitive("sbw.VehiclePassengerRendererMixin");
            assertTrue(config.getAsJsonArray("client").contains(name));
            assertFalse(config.getAsJsonArray("mixins").contains(name));

            var hooks = JsonParser.parseReader(new InputStreamReader(refmapInput, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("mappings")
                    .getAsJsonObject("com/scarasol/vindicatorsrumble/mixin/sbw/VehiclePassengerRendererMixin");
            assertNotNull(hooks, "Passenger rendering hook must be mapped in the release");
            String selector = "render(Lnet/minecraft/world/entity/LivingEntity;FF"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
            assertTrue(hooks.has(selector));
            assertTrue(hooks.get(selector).getAsString().contains(";m_"),
                    "Passenger rendering hook must use the mapped vanilla method");
        }
    }

    @Test
    void recruitNarrowPathHooksAreMappedForRelease() throws Exception {
        try (JarFile release = new JarFile(System.getProperty("vindicatorsRumble.releaseJar"));
             InputStream input = release.getInputStream(release.getJarEntry("vindicators_rumble.refmap.json"))) {
            var hooks = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("mappings")
                    .getAsJsonObject("com/scarasol/vindicatorsrumble/mixin/recruits/RecruitVehicleNodeEvaluatorMixin");
            assertNotNull(hooks, "Recruit vehicle hooks were not included in the release refmap");
            for (String inherited : List.of("prepare", "done", "getStart", "getNeighbors")) {
                assertTrue(hooks.has(inherited), () -> "Missing inherited mapping: " + inherited);
                assertTrue(hooks.get(inherited).getAsString().contains(";m_"),
                        () -> "Unmapped inherited hook: " + inherited);
            }
            assertFalse(hooks.has("getStartNode"), "Recruit-only methods must retain their original names");
            assertFalse(hooks.has("getBlockPathTypes"), "Recruit-only methods must retain their original names");
        }
    }

    @Test
    void releaseArtifactExcludesGameTestFixturesAndProfiling() throws Exception {
        try (JarFile release = new JarFile(System.getProperty("vindicatorsRumble.releaseJar"))) {
            List<String> leaked = release.stream().map(entry -> entry.getName())
                    .filter(name -> name.toLowerCase(Locale.ROOT).contains("gametest")
                            || name.startsWith("data/vindicators_rumble_benchmark/")
                            || name.startsWith("data/vindicators_rumble_isolation/")
                            || name.startsWith("data/vindicators_rumble_limitless/")
                            || name.startsWith("data/vindicators_rumble_smoke/"))
                    .toList();
            assertTrue(leaked.isEmpty(), () -> "Test-only release entries: " + leaked);
        }
    }
}
