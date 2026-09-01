package com.mrleonardos.codeessentials.api.manage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codeessentials.api.model.BackPoint;
import com.mrleonardos.codeessentials.api.store.StoreResult;

/**
 * Стек возврата игрока.
 *
 * <p>
 * Точка пишется в момент фактического переноса, а не подачи команды, поэтому {@code /back} ведёт
 * туда, где игрок стоял перед самым исчезновением. Отказ переноса запись не выталкивает: игрок
 * повторяет попытку, когда получит нужную ноду или когда в точке станет безопасно.
 */
public interface BackService {

    /** Верхняя запись стека или пустой ответ. */
    Optional<BackPoint> peek(UUID player);

    /** Стек от свежей записи к старой. */
    List<BackPoint> stack(UUID player);

    /** Какая глубина стека разрешена игроку сейчас: мета {@code codeessentials.backdepth}. */
    int depthFor(UUID player);

    /** Положить точку на вершину стека, вытеснив лишнее с хвоста. */
    StoreResult record(UUID player, BackPoint point);

    /**
     * Снять верхнюю запись после состоявшегося возврата.
     *
     * @return правда, если было что снимать
     */
    boolean pop(UUID player);
}
