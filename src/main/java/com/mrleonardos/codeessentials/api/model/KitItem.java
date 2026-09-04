package com.mrleonardos.codeessentials.api.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Один предмет кита: имя, число, урон и NBT.
 *
 * <p>
 * Идентичность предмета это все четыре поля сразу. Два хлеба с разным уроном или разным NBT друг в
 * друга не сливаются при выдаче, поэтому {@link #sameKind(KitItem)} сравнивает всё, кроме числа.
 *
 * <p>
 * Строковая форма {@code id [count] [damage] [{nbt}]} едина для файла китов и для печати: NBT всегда
 * в конце и всегда в фигурных скобках, поэтому разбор не путает пробелы внутри тегов с разделителями.
 * Число больше стака не ошибка: выдача развернёт его в несколько стопок.
 */
public final class KitItem {

    /** Наименьшее число предметов в записи. */
    public static final int MIN_COUNT = 1;

    /** Наибольшее число предметов в одной записи: остаток уходит в буфер, а не в мегабайт файла. */
    public static final int MAX_COUNT = 4096;

    /** Наибольшее значение урона, совпадает с шириной поля в стеке предмета. */
    public static final int MAX_DAMAGE = 32767;

    /** Предел длины NBT: сложные предметы модов помещаются, рукописный мусор не помещается. */
    public static final int MAX_NBT_LENGTH = 8192;

    private static final String DEFAULT_NAMESPACE = "minecraft";
    private static final Pattern ID_SHAPE = Pattern.compile("([a-z0-9_.-]+):([a-z0-9_./-]+)");
    private static final Pattern BARE_SHAPE = Pattern.compile("[a-z0-9_.-]+");

    private static final String NBT_OPEN = "{";
    private static final String NBT_CLOSE = "}";

    private final String id;
    private final int count;
    private final int damage;
    private final String nbt;

    private KitItem(String id, int count, int damage, String nbt) {
        this.id = id;
        this.count = count;
        this.damage = damage;
        this.nbt = nbt;
    }

    /**
     * Собрать предмет.
     *
     * @param id     имя с пространством имён или без него, приводится к нижнему регистру
     * @param count  число предметов, от {@link #MIN_COUNT} до {@link #MAX_COUNT}
     * @param damage урон или метаданные, 0..{@link #MAX_DAMAGE}
     * @param nbt    строка SNBT в фигурных скобках или пустая строка
     * @throws IllegalArgumentException если любое поле не проходит свои границы
     */
    public static KitItem of(String id, int count, int damage, String nbt) {
        String normalized = normalizedId(id);
        if (count < MIN_COUNT || count > MAX_COUNT) {
            throw new IllegalArgumentException(
                "Item count must be within " + MIN_COUNT + ".." + MAX_COUNT + ": " + count);
        }
        if (damage < 0 || damage > MAX_DAMAGE) {
            throw new IllegalArgumentException("Item damage must be within 0.." + MAX_DAMAGE + ": " + damage);
        }
        String tags = normalizedNbt(nbt);
        return new KitItem(normalized, count, damage, tags);
    }

    /** Предмет без урона и без NBT, самая частая запись файла. */
    public static KitItem of(String id, int count) {
        return of(id, count, 0, "");
    }

    /**
     * Разобрать строку предмета.
     *
     * @return пустой ответ, если строка пустая, токенов больше трёх или число вне границ; NBT при
     *         этом не проверяется на грамматику, это дело игры
     */
    public static Optional<KitItem> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String line = text.trim();
        if (line.isEmpty()) {
            return Optional.empty();
        }
        String tags = "";
        int brace = line.indexOf(NBT_OPEN);
        if (brace >= 0) {
            tags = line.substring(brace)
                .trim();
            if (!tags.startsWith(NBT_OPEN) || !tags.endsWith(NBT_CLOSE) || tags.length() > MAX_NBT_LENGTH) {
                return Optional.empty();
            }
            line = line.substring(0, brace)
                .trim();
            if (line.isEmpty()) {
                return Optional.empty();
            }
        }
        String[] tokens = line.split("\\s+");
        if (tokens.length > 3) {
            return Optional.empty();
        }
        try {
            int count = tokens.length > 1 ? Integer.parseInt(tokens[1]) : MIN_COUNT;
            int damage = tokens.length > 2 ? Integer.parseInt(tokens[2]) : 0;
            return Optional.of(of(tokens[0], count, damage, tags));
        } catch (RuntimeException malformed) {
            return Optional.empty();
        }
    }

    /** Имя предмета с пространством имён, в нижнем регистре. */
    public String id() {
        return id;
    }

    /** Число предметов в записи. */
    public int count() {
        return count;
    }

    /** Урон или метаданные. */
    public int damage() {
        return damage;
    }

    /** Строка NBT в фигурных скобках или пустая строка. */
    public String nbt() {
        return nbt;
    }

    /** Тот же предмет с другим числом: так выдача режет запись на стопки. */
    public KitItem withCount(int newCount) {
        if (newCount == count) {
            return this;
        }
        return of(id, newCount, damage, nbt);
    }

    /** Совпадают ли имя, урон и NBT, то есть можно ли подлить предмет в стопку. */
    public boolean sameKind(KitItem other) {
        return other != null && damage == other.damage && nbt.equals(other.nbt) && id.equals(other.id);
    }

    /** Строковая форма для файла и для лога. */
    public String print() {
        StringBuilder text = new StringBuilder(id).append(' ')
            .append(count);
        if (damage != 0) {
            text.append(' ')
                .append(damage);
        }
        if (!nbt.isEmpty()) {
            text.append(' ')
                .append(nbt);
        }
        return text.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof KitItem)) {
            return false;
        }
        KitItem that = (KitItem) other;
        return count == that.count && damage == that.damage && id.equals(that.id) && nbt.equals(that.nbt);
    }

    @Override
    public int hashCode() {
        return (id.hashCode() * 31 + count) * 31 + (damage * 31 + nbt.hashCode());
    }

    @Override
    public String toString() {
        return print();
    }

    private static String normalizedId(String raw) {
        Objects.requireNonNull(raw, "id");
        String id = raw.trim()
            .toLowerCase(Locale.ROOT);
        if (!ID_SHAPE.matcher(id)
            .matches()) {
            if (BARE_SHAPE.matcher(id)
                .matches()) {
                id = DEFAULT_NAMESPACE + ":" + id;
            } else {
                throw new IllegalArgumentException("Item id must be a registry name: " + raw);
            }
        }
        return id;
    }

    private static String normalizedNbt(String raw) {
        if (raw == null || raw.trim()
            .isEmpty()) {
            return "";
        }
        String tags = raw.trim();
        if (!tags.startsWith(NBT_OPEN) || !tags.endsWith(NBT_CLOSE)) {
            throw new IllegalArgumentException("Item nbt must be a compound in braces: " + raw);
        }
        if (tags.length() > MAX_NBT_LENGTH) {
            throw new IllegalArgumentException(
                "Item nbt is longer than " + MAX_NBT_LENGTH + " characters: " + tags.length());
        }
        return tags;
    }
}
