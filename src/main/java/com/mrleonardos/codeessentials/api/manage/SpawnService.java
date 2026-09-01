package com.mrleonardos.codeessentials.api.manage;

import java.util.Optional;

import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.model.SpawnTable;
import com.mrleonardos.codeessentials.api.store.StoreResult;

/**
 * Точки спавна сервера.
 *
 * <p>
 * Выбор идёт от частного к общему: точка текущего измерения, затем общая. Пустой ответ означает,
 * что мод в выбор не вмешивается и работает ванильная точка мира. Подмена места возрождения
 * работает только при заданной точке и только когда у игрока нет кровати: кровать всегда сильнее.
 */
public interface SpawnService {

    /** Таблица точек целиком. */
    SpawnTable table();

    /** Точка для измерения по политике выбора или пустой ответ. */
    Optional<Point> spawnFor(int dimension);

    /**
     * Записать общую точку спавна.
     *
     * @param actor кто правит, для аудита
     */
    StoreResult setGlobalSpawn(Point point, String actor);

    /** Записать точку спавна для измерения этой точки. */
    StoreResult setDimensionSpawn(Point point, String actor);
}
