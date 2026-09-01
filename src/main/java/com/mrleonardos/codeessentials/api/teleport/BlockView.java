package com.mrleonardos.codeessentials.api.teleport;

/**
 * Взгляд на мир для поиска безопасной точки.
 *
 * <p>
 * Через этот интерфейс поиск смотрит на блоки, не зная ни про {@code World}, ни про чанки. В игре
 * его реализует платформа, в тестах фейковый мир из нескольких колонок.
 *
 * <p>
 * Правила безопасного слота лежат здесь, а не во встроенном поиске, потому что чужая
 * {@link SafeSpotPolicy} обязана понимать «безопасно» так же: игрок, провалившийся в лаву через
 * чужую политику, для сервера ничем не отличается от игрока, провалившегося через нашу.
 */
public interface BlockView {

    /** Идентификатор измерения, на которое смотрим. */
    int dimension();

    /**
     * Загружен ли чанк с этой колонкой.
     *
     * <p>
     * {@link #sample} спрашивают только про загруженные чанки: незагруженный чанк отдал бы воздух и
     * увёл бы игрока под мир.
     */
    boolean chunkLoaded(int blockX, int blockZ);

    /** Что стоит в клетке. */
    BlockSample sample(int blockX, int blockY, int blockZ);

    /** Высота мира в блоках: 256 в 1.7.10. */
    int height();

    /**
     * Помещается ли игрок в клетку целиком: ноги и голова проходимы и ничего не ранит.
     *
     * @param liquidOk разрешено ли приземляться ногами в жидкость
     */
    default boolean passableSpot(int blockX, int blockY, int blockZ, boolean liquidOk) {
        if (blockY < 0 || blockY + 1 >= height()) {
            return false;
        }
        BlockSample feet = sample(blockX, blockY, blockZ);
        BlockSample head = sample(blockX, blockY + 1, blockZ);
        if (!feet.passable() || !head.passable() || feet.harmful() || head.harmful()) {
            return false;
        }
        return liquidOk || !feet.liquid();
    }

    /** Есть ли под клеткой твёрдая земля, на которой не больно стоять. */
    default boolean solidFloorUnder(int blockX, int blockY, int blockZ) {
        if (blockY <= 0 || blockY > height()) {
            return false;
        }
        BlockSample floor = sample(blockX, blockY - 1, blockZ);
        return floor.solid() && !floor.harmful();
    }
}
