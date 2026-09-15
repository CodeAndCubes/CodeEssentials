package com.mrleonardos.codeessentials.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codecore.api.config.Comment;
import com.mrleonardos.codecore.api.config.SectionSpec;
import com.mrleonardos.codeessentials.internal.engine.EngineRules;

class EssentialsSectionTest {

    @Test
    void theSectionIsDeclaredByThisModUnderItsOwnName() {
        SectionSpec<EssentialsSection> spec = EssentialsSection.spec();

        assertEquals("essentials", spec.name());
        assertEquals(EssentialsSection.class, spec.type());
    }

    @Test
    void theDefaultsSupplierMakesAFreshSectionEveryTime() {
        EssentialsSection first = EssentialsSection.spec()
            .defaults()
            .get();
        EssentialsSection second = EssentialsSection.spec()
            .defaults()
            .get();

        first.homes = 9;

        assertNotSame(first, second);
        assertEquals(EssentialsSection.DEFAULT_HOMES, second.homes);
        assertEquals(EngineRules.DEFAULT_WARMUP_SECONDS, second.warmupSeconds);
    }

    @Test
    void aSectionEditedIntoNullCooldownsIsHealedBeforeUse() {
        EssentialsSection section = new EssentialsSection();
        section.cooldowns = null;

        EssentialsSection.spec()
            .validator()
            .accept(section);

        assertNotNull(section.cooldowns);
        assertEquals(0, section.cooldowns.home);
    }

    @Test
    void theSectionExplainsItselfInTheMainFile() throws Exception {
        assertArrayEquals(
            new String[] { "Перемещения. Файлы лежат в config/code/essentials/." },
            EssentialsSection.class.getAnnotation(Comment.class)
                .value());
        assertNotNull(field("homes").getAnnotation(Comment.class));
        assertNotNull(field("warmupSeconds").getAnnotation(Comment.class));
        assertNotNull(EssentialsSection.Cooldowns.class.getAnnotation(Comment.class));
    }

    private static Field field(String name) throws NoSuchFieldException {
        return EssentialsSection.class.getField(name);
    }
}
