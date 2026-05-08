package net.dmgct.velocityclusterstats;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildConstantsTest {
    @Test
    void exposesSinglePluginVersionConstant() {
        assertEquals("0.1.0-SNAPSHOT", BuildConstants.VERSION);
    }

    @Test
    void constructorIsPrivateUtilityConstructor() throws Exception {
        Constructor<BuildConstants> constructor = BuildConstants.class.getDeclaredConstructor();

        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
    }
}
