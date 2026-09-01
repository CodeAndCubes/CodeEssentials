package com.mrleonardos.codeessentials.api.teleport;

/**
 * Что за блок стоит в клетке, глазами поиска безопасной точки.
 *
 * <p>
 * Четырёх ответов хватает на решение: пройдёт ли игрок сквозь блок, встанет ли на него, ранит ли он
 * и жидкость ли это. Настоящие типы блоков остаются в платформе, поэтому поиск точки живёт без
 * Minecraft, а чужой мод описывает свои блоки теми же четырьмя ответами.
 */
public final class BlockSample {

    /** Воздух: сквозь него проходят, встать на него нельзя. */
    public static final BlockSample AIR = new BlockSample(true, false, false, false);

    /** Твёрдый блок: на нём стоят. */
    public static final BlockSample SOLID = new BlockSample(false, true, false, false);

    /** Вода: проходима, но приземляться в неё разрешено только с {@code liquidOk}. */
    public static final BlockSample WATER = new BlockSample(true, false, false, true);

    /** Лава: проходима, ранит, жидкость. Ставить сюда игрока нельзя никогда. */
    public static final BlockSample LAVA = new BlockSample(true, false, true, true);

    /** Огонь: проходим и ранит. */
    public static final BlockSample FIRE = new BlockSample(true, false, true, false);

    private final boolean passable;
    private final boolean solid;
    private final boolean harmful;
    private final boolean liquid;

    private BlockSample(boolean passable, boolean solid, boolean harmful, boolean liquid) {
        this.passable = passable;
        this.solid = solid;
        this.harmful = harmful;
        this.liquid = liquid;
    }

    /**
     * Описать блок.
     *
     * @param passable проходит ли игрок сквозь блок
     * @param solid    встанет ли игрок на блок
     * @param harmful  ранит ли блок: лава, огонь, кактус и им подобные
     * @param liquid   жидкость ли это
     * @throws IllegalArgumentException если блок объявлен и проходимым, и твёрдым
     */
    public static BlockSample of(boolean passable, boolean solid, boolean harmful, boolean liquid) {
        if (passable && solid) {
            throw new IllegalArgumentException("A block is either passable or solid, not both");
        }
        return new BlockSample(passable, solid, harmful, liquid);
    }

    /** Проходит ли игрок сквозь блок. */
    public boolean passable() {
        return passable;
    }

    /** Встанет ли игрок на блок. */
    public boolean solid() {
        return solid;
    }

    /** Ранит ли блок. */
    public boolean harmful() {
        return harmful;
    }

    /** Жидкость ли это. */
    public boolean liquid() {
        return liquid;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BlockSample)) {
            return false;
        }
        BlockSample that = (BlockSample) other;
        return passable == that.passable && solid == that.solid && harmful == that.harmful && liquid == that.liquid;
    }

    @Override
    public int hashCode() {
        return ((Boolean.hashCode(passable) * 31 + Boolean.hashCode(solid)) * 31 + Boolean.hashCode(harmful)) * 31
            + Boolean.hashCode(liquid);
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(passable ? "passable" : "solid");
        if (harmful) {
            text.append(" harmful");
        }
        if (liquid) {
            text.append(" liquid");
        }
        return text.toString();
    }
}
