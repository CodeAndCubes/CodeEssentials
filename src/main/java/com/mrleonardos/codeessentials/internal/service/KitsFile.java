package com.mrleonardos.codeessentials.internal.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.mrleonardos.codecore.api.config.Comment;
import com.mrleonardos.codecore.api.config.ConfigRoles;
import com.mrleonardos.codecore.api.config.ConfigScope;
import com.mrleonardos.codecore.api.config.ConfigSpec;
import com.mrleonardos.codeessentials.internal.EssentialsSettings;

@Comment("Киты сервера. Файл правят руками, командами /kit save и /kit edit, и удаляют командой /kit delete.")
public final class KitsFile {

    public static final int VERSION = 1;

    public static final String FILE = "kits";
    public static final String FILE_NAME = "essentials-kits.toml";

    @Comment("Запись на каждый кит, ключ это имя в нижнем регистре. Пустой кит не записывается ни одной дорогой.")
    public Map<String, Kit> kits = new LinkedHashMap<>();

    public static ConfigSpec<KitsFile> spec() {
        return ConfigSpec.of(EssentialsSettings.MODID, FILE, KitsFile.class)
            .role(ConfigRoles.ESSENTIALS)
            .scope(ConfigScope.SETTINGS)
            .schemaVersion(VERSION)
            .defaults(KitsFile::new)
            .validator(KitsFile::heal)
            .build();
    }

    static void heal(KitsFile file) {
        if (file.kits == null) {
            file.kits = new LinkedHashMap<>();
            return;
        }
        Map<String, Kit> keyed = new LinkedHashMap<>();
        for (Map.Entry<String, Kit> entry : file.kits.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String name = entry.getKey()
                .trim()
                .toLowerCase(Locale.ROOT);
            if (!keyed.containsKey(name)) {
                keyed.put(name, entry.getValue());
            }
        }
        file.kits = keyed;
    }

    public static final class Kit {

        @Comment({ "Одноразовый кит: получен навсегда, отметка хранится у игрока в мире.",
            "Пауза и одноразовость независимы, любая комбинация работает." })
        public boolean once;

        @Comment({ "Пауза между получениями в секундах, отсчёт по реальному времени.",
            "Ноль значит без кулдауна, и тогда метка прошлого получения никого не держит." })
        public int cooldownSeconds;

        @Comment({ "Предметы по слотам. Ключ это номер слота: 0..8 хотбар, 9..35 рюкзак,",
            "36 сапоги, 37 штаны, 38 нагрудник, 39 шлем. Запись предмета:",
            "\"имя [число] [урон] {nbt}\", например \"minecraft:bread 16\" или \"minecraft:iron_pickaxe 1 120\".",
            "Имя без пространства имён читается как minecraft. NBT пишется в фигурных скобках в конце." })
        public Map<String, String> slots = new LinkedHashMap<>();

        public Kit() {}

        public Kit(boolean once, int cooldownSeconds, Map<String, String> slots) {
            this.once = once;
            this.cooldownSeconds = cooldownSeconds;
            this.slots = slots == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<>(slots);
        }
    }
}
