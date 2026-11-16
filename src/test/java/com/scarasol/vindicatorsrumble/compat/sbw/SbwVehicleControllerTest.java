package com.scarasol.vindicatorsrumble.compat.sbw;

import com.google.gson.JsonParser;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponFireMode;
import com.scarasol.vindicatorsrumble.api.vehicle.WeaponPayloadType;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.struct.MemberInfo;
import org.spongepowered.asm.mixin.refmap.ReferenceMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SbwVehicleControllerTest {
    @Test
    void initialCombatTimeUsesTheApprovedPayloadBudgets() {
        assertEquals(90.0D, SbwVehicleController.initialCombatSeconds(WeaponPayloadType.BULLET));
        assertEquals(67.5D, SbwVehicleController.initialCombatSeconds(WeaponPayloadType.HITSCAN));
        assertEquals(45.0D, SbwVehicleController.initialCombatSeconds(WeaponPayloadType.SHELL));
        assertEquals(30.0D, SbwVehicleController.initialCombatSeconds(WeaponPayloadType.ROCKET));
        assertEquals(60.0D, SbwVehicleController.initialCombatSeconds(WeaponPayloadType.MISSILE));
        assertEquals(45.0D, SbwVehicleController.initialCombatSeconds(WeaponPayloadType.UNKNOWN));
        assertEquals(0.0D, SbwVehicleController.initialCombatSeconds(WeaponPayloadType.UTILITY));
    }

    @Test
    void directFeedAmmoRoundsFractionalShotCountsUp() {
        assertEquals(6, SbwVehicleController.initialAmmoUnits(
                1.0D, 65.0D, WeaponFireMode.AUTOMATIC, 0.0D, 0, 3, 0));
        assertEquals(188, SbwVehicleController.initialAmmoUnits(
                45.0D, 250.0D, WeaponFireMode.AUTOMATIC, 0.0D, 0, 1, 0));
    }

    @Test
    void actionCycleAndSemiAutomaticCadenceLimitAmmoBudget() {
        assertEquals(80, SbwVehicleController.initialAmmoUnits(
                10.0D, 1200.0D, WeaponFireMode.AUTOMATIC, 5.0D, 0, 2, 0));
        assertEquals(50, SbwVehicleController.initialAmmoUnits(
                10.0D, 600.0D, WeaponFireMode.SEMI_AUTOMATIC, 0.0D, 0, 1, 0));
        assertEquals(100, SbwVehicleController.initialAmmoUnits(
                10.0D, 600.0D, WeaponFireMode.UNKNOWN, 0.0D, 0, 1, 0));
    }

    @Test
    void magazineWeaponsIncludeEmptyReloadTime() {
        assertEquals(8, SbwVehicleController.initialAmmoUnits(
                60.0D, 600.0D, WeaponFireMode.SEMI_AUTOMATIC, 0.0D, 1, 1, 150));
        assertEquals(70, SbwVehicleController.initialAmmoUnits(
                30.0D, 450.0D, WeaponFireMode.AUTOMATIC, 0.0D, 14, 1, 100));
    }

    @Test
    void zeroDurationOrInvalidAmmoCostProvidesNoAmmo() {
        assertEquals(0, SbwVehicleController.initialAmmoUnits(
                0.0D, 600.0D, WeaponFireMode.AUTOMATIC, 0.0D, 30, 1, 40));
        assertEquals(0, SbwVehicleController.initialAmmoUnits(
                30.0D, 600.0D, WeaponFireMode.AUTOMATIC, 0.0D, 30, 0, 40));
    }

    @Test
    void projectileFriendlyFilterBindsToTheCurrentCollisionPath() throws IOException {
        ClassNode projectile = readClass("com/atsuishio/superbwarfare/entity/projectile/ProjectileEntity");
        MethodNode collision = projectile.methods.stream()
                .filter(method -> method.name.equals("getHitResult")).findFirst().orElseThrow();
        ClassNode mixin = readClass("com/scarasol/vindicatorsrumble/mixin/sbw/ProjectileFriendlyFireMixin");
        assertEquals(Set.of(collision.name + collision.desc), injectionTargets(mixin));

        ClassNode detection = readClass("com/atsuishio/superbwarfare/entity/projectile/IAdvancedHitDetection$DefaultImpls");
        MethodNode search = detection.methods.stream()
                .filter(method -> method.name.equals("findEntitiesOnPath")).findFirst().orElseThrow();
        boolean callsCollision = false;
        for (var instruction : search.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals(collision.name)
                    && call.desc.equals(collision.desc)) {
                callsCollision = true;
            }
        }
        assertTrue(callsCollision, "The active projectile search must use the filtered collision entry point");
    }

    @Test
    void aiFireGuardsCoverEveryCurrentVehicleShootingOverload() throws IOException {
        ClassNode vehicle = readClass("com/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity");
        Set<String> shootingMethods = vehicle.methods.stream()
                .filter(method -> method.name.equals("vehicleShoot"))
                .map(method -> method.name + method.desc).collect(Collectors.toSet());
        assertFalse(shootingMethods.isEmpty());
        ClassNode mixin = readClass("com/scarasol/vindicatorsrumble/mixin/sbw/VehicleAutoFireMixin");
        assertEquals(shootingMethods, injectionTargets(mixin));
    }

    @Test
    void crushFriendlyFilterWrapsOnlyAndAllNativeCrushDamageCalls() throws IOException {
        ClassNode motion = readClass("com/atsuishio/superbwarfare/entity/vehicle/utils/VehicleMotionUtils");
        MethodNode crush = motion.methods.stream()
                .filter(method -> method.name.equals("crushEntities")).findFirst().orElseThrow();
        Set<String> damageCalls = new HashSet<>();
        for (var instruction : crush.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && (call.name.equals("hurt") || call.name.equals("forceHurt"))) {
                damageCalls.add("L" + call.owner + ";" + call.name + call.desc);
            }
        }
        assertEquals(3, damageCalls.size(), "Review every native crush-damage entry point");
        Set<String> wrappedCalls = new HashSet<>();
        ClassNode mixin = readClass("com/scarasol/vindicatorsrumble/mixin/sbw/VehicleCrushFriendlyFireMixin");
        for (MethodNode handler : mixin.methods) {
            if (handler.visibleAnnotations == null) continue;
            for (AnnotationNode injection : handler.visibleAnnotations) {
                if (!injection.desc.equals("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;")) continue;
                assertEquals(List.of(crush.name + crush.desc), annotationValue(injection, "method", List.of()));
                Object at = annotationValue(injection, "at", List.of());
                List<?> points = at instanceof AnnotationNode ? List.of(at) : (List<?>) at;
                for (Object point : points) {
                    wrappedCalls.add((String) annotationValue((AnnotationNode) point, "target", ""));
                }
            }
        }
        assertEquals(damageCalls, wrappedCalls);
    }

    @Test
    void releaseMixinSelectorsResolveAgainstPublishedSbw() throws IOException {
        try (JarFile release = new JarFile(System.getProperty("vindicatorsRumble.releaseJar"));
             JarFile sbw = new JarFile(System.getProperty("vindicatorsRumble.sbwReleaseJar"));
             var configReader = new InputStreamReader(release.getInputStream(
                     release.getJarEntry("vindicators_rumble.mixins.json")), StandardCharsets.UTF_8)) {
            var config = JsonParser.parseReader(configReader).getAsJsonObject();
            ReferenceMapper refmap;
            try (var reader = new InputStreamReader(release.getInputStream(
                    release.getJarEntry(config.get("refmap").getAsString())), StandardCharsets.UTF_8)) {
                refmap = ReferenceMapper.read(reader, config.get("refmap").getAsString());
            }
            assertFalse(refmap.isDefault(), "The release must contain a readable reference map");
            int checked = 0;
            for (var mixinName : config.getAsJsonArray("mixins")) {
                ClassNode mixin = readClass(release,
                        config.get("package").getAsString().replace('.', '/') + "/"
                                + mixinName.getAsString().replace('.', '/'));
                for (AnnotationNode annotation : mixin.invisibleAnnotations) {
                    if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
                        continue;
                    }
                    for (Object value : (List<?>) annotationValue(annotation, "value", List.of())) {
                        String owner = ((Type) value).getInternalName();
                        if (!owner.startsWith("com/atsuishio/superbwarfare/")) {
                            continue;
                        }
                        ClassNode target = readClass(sbw, owner);
                        for (MethodNode handler : mixin.methods) {
                            if (handler.visibleAnnotations == null) {
                                continue;
                            }
                            for (AnnotationNode injection : handler.visibleAnnotations) {
                                if (!injection.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")
                                        && !injection.desc.equals("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;")) {
                                    continue;
                                }
                                for (Object selector : (List<?>) annotationValue(injection, "method", List.of())) {
                                    MemberInfo method = MemberInfo.parse(refmap.remap(mixin.name, (String) selector), null);
                                    List<MethodNode> matches = target.methods.stream()
                                            .filter(node -> node.name.equals(method.getName())
                                                    && (method.getDesc() == null || node.desc.equals(method.getDesc())))
                                            .toList();
                                    String context = mixin.name + " -> " + selector + " -> " + method;
                                    assertFalse(matches.isEmpty(), context);
                                    Object atValue = annotationValue(injection, "at", List.of());
                                    List<?> points = atValue instanceof AnnotationNode ? List.of(atValue) : (List<?>) atValue;
                                    for (Object point : points) {
                                        String reference = (String) annotationValue((AnnotationNode) point, "target", "");
                                        if (reference.isEmpty()) {
                                            continue;
                                        }
                                        MemberInfo callTarget = MemberInfo.parse(refmap.remap(mixin.name, reference), null);
                                        boolean found = false;
                                        for (MethodNode match : matches) {
                                            for (var instruction : match.instructions) {
                                                if (instruction instanceof MethodInsnNode call
                                                        && call.owner.equals(callTarget.getOwner())
                                                        && call.name.equals(callTarget.getName())
                                                        && call.desc.equals(callTarget.getDesc())) {
                                                    found = true;
                                                }
                                            }
                                        }
                                        assertTrue(found, context + " must invoke " + callTarget);
                                    }
                                    checked++;
                                }
                            }
                        }
                    }
                }
            }
            assertTrue(checked > 0, "No production SBW injection selectors were checked");
        }
    }

    private static Object annotationValue(AnnotationNode annotation, String key, Object fallback) {
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if (annotation.values.get(index).equals(key)) {
                return annotation.values.get(index + 1);
            }
        }
        return fallback;
    }

    private static ClassNode readClass(JarFile jar, String name) throws IOException {
        var entry = jar.getJarEntry(name + ".class");
        assertNotNull(entry, name);
        try (InputStream input = jar.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static ClassNode readClass(String name) throws IOException {
        try (InputStream input = SbwVehicleControllerTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static Set<String> injectionTargets(ClassNode mixin) {
        Set<String> targets = new HashSet<>();
        for (MethodNode method : mixin.methods) {
            if (method.visibleAnnotations == null) {
                continue;
            }
            for (AnnotationNode annotation : method.visibleAnnotations) {
                if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) {
                    continue;
                }
                for (int index = 0; index < annotation.values.size(); index += 2) {
                    if (annotation.values.get(index).equals("method")) {
                        for (Object selector : (java.util.List<?>) annotation.values.get(index + 1)) {
                            targets.add((String) selector);
                        }
                    }
                }
            }
        }
        return targets;
    }
}
