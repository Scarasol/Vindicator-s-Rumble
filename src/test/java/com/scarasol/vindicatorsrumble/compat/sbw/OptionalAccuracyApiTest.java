package com.scarasol.vindicatorsrumble.compat.sbw;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OptionalAccuracyApiTest {
    @Test
    void missingEventApiKeepsNativeSpread() throws Exception {
        assertNativeFallback(false);
    }

    @Test
    void incompatibleEventApiKeepsNativeSpread() throws Exception {
        assertNativeFallback(true);
    }

    private void assertNativeFallback(boolean factoryPresent) throws Exception {
        String bridge = PillagersGunAccuracy.class.getName();
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("com.scarasol.pillagers_gun.event.EventFactory")) {
                    if (!factoryPresent) throw new ClassNotFoundException(name);
                    ClassWriter writer = new ClassWriter(0);
                    writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name.replace('.', '/'),
                            null, "java/lang/Object", null);
                    writer.visitEnd();
                    byte[] bytes = writer.toByteArray();
                    return defineClass(name, bytes, 0, bytes.length);
                }
                if (!name.equals(bridge)) return super.loadClass(name, resolve);
                try (InputStream input = getResourceAsStream(name.replace('.', '/') + ".class")) {
                    assertNotNull(input);
                    byte[] bytes = input.readAllBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (IOException exception) {
                    throw new ClassNotFoundException(name, exception);
                }
            }
        };
        var modify = loader.loadClass(bridge).getMethod("modifySpread", double.class, Mob.class, Entity.class);
        assertEquals(1.234567890123D, (double) modify.invoke(null, 1.234567890123D, null, null));
        assertEquals(0.0D, (double) modify.invoke(null, 0.0D, null, null));
    }
}
