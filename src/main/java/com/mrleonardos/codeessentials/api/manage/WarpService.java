package com.mrleonardos.codeessentials.api.manage;

import java.util.Map;
import java.util.Optional;

import com.mrleonardos.codeessentials.api.model.WarpRecord;
import com.mrleonardos.codeessentials.api.store.StoreResult;

/**
 * Варпы сервера.
 *
 * <p>
 * Варпы лежат в файле настроек и правятся человеком, поэтому сервис ничего не знает про право
 * доступа: доступ решает нода {@code codeessentials.warp.go.<имя>}, и список варпов игроку
 * фильтрует команда. Свежий варп по этой причине закрыт для всех, пока администратор не выдаст
 * ноду.
 */
public interface WarpService {

    /** Варп по имени. */
    Optional<WarpRecord> warp(String name);

    /** Все варпы по именам, порядок алфавитный. */
    Map<String, WarpRecord> warps();

    /**
     * Записать варп.
     *
     * @param safeSpot проверять ли точку на безопасность; выключается флагом {@code --force}
     * @param actor    кто правит, для аудита
     * @return отказ {@code UNSAFE_SPOT}, если в точке негде встать, {@code LIMIT_REACHED} при
     *         исчерпанном потолке варпов
     */
    StoreResult setWarp(WarpRecord warp, boolean safeSpot, String actor);

    /**
     * Убрать варп.
     *
     * @return отказ {@code NOT_FOUND}, если варпа с таким именем нет
     */
    StoreResult deleteWarp(String name, String actor);
}
