package com.mrleonardos.codeessentials.api.manage;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codeessentials.api.model.HomeRecord;
import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.store.StoreResult;

/**
 * Дома игроков.
 *
 * <p>
 * Лимит читается на момент установки из меты {@code codeessentials.maxhomes} через права ядра, а
 * при её отсутствии из настройки {@code homes.defaultMax}. Падение лимита не отбирает дома:
 * запрещён только переход через лимит, то есть новое имя. Перезапись существующего дома лимит не
 * тратит, поэтому игрок с полным набором домов не остаётся без возможности переставить любой из
 * них.
 */
public interface HomeService {

    /** Дом игрока по имени. */
    Optional<HomeRecord> home(UUID player, String name);

    /** Дома игрока по именам, порядок алфавитный. */
    Map<String, HomeRecord> homes(UUID player);

    /** Сколько домов игроку разрешено сейчас: мета или значение из настроек. */
    int homeLimit(UUID player);

    /**
     * Поставить или переставить дом.
     *
     * @param actor кто правит, для аудита
     * @return отказ {@code LIMIT_REACHED} на новом имени сверх лимита, {@code INVALID_VALUE} на
     *         имени не по шаблону, {@code VETOED} при вето слушателя
     */
    StoreResult setHome(UUID player, String name, Point point, String actor);

    /**
     * Убрать дом.
     *
     * @return отказ {@code NOT_FOUND}, если дома с таким именем нет
     */
    StoreResult deleteHome(UUID player, String name, String actor);
}
