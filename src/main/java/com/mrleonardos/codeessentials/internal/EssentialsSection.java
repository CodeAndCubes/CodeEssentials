package com.mrleonardos.codeessentials.internal;

import com.mrleonardos.codecore.api.config.Comment;
import com.mrleonardos.codecore.api.config.SectionSpec;

@Comment("Перемещения. Файлы лежат в config/code/essentials/.")
public final class EssentialsSection {

    public static final String NAME = "essentials";

    public static final int DEFAULT_HOMES = 3;
    public static final int DEFAULT_WARMUP_SECONDS = 3;

    @Comment("Сколько домов у игрока без личного лимита из меты.")
    public int homes = DEFAULT_HOMES;

    @Comment("Сколько секунд игрок стоит на месте перед переносом.")
    public int warmupSeconds = DEFAULT_WARMUP_SECONDS;

    public Cooldowns cooldowns = new Cooldowns();

    public static SectionSpec<EssentialsSection> spec() {
        return SectionSpec.of(NAME, EssentialsSection.class)
            .defaults(EssentialsSection::new)
            .validator(EssentialsSection::heal)
            .build();
    }

    private static void heal(EssentialsSection section) {
        if (section.cooldowns == null) {
            section.cooldowns = new Cooldowns();
        }
    }

    @Comment("Пауза после переноса, по причине. Ноль снимает.")
    public static final class Cooldowns {

        public int home;
        public int spawn;
        public int warp;
        public int back;
        public int tpa;
        public int random;
    }
}
