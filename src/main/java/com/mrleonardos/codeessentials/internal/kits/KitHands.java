package com.mrleonardos.codeessentials.internal.kits;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mrleonardos.codeessentials.api.model.KitItem;

public interface KitHands {

    /**
     * Слепок слотов игрока в том же порядке, что у кита: 0..35 инвентарь, 36..39 броня. Предмет,
     * которого запись кита не выражает, помечается невыразимым, а не пустым.
     *
     * @return пустой ответ, когда игрока нет на сервере: брать его слоты не с чего
     */
    Optional<WornSlots> worn(UUID player);

    /**
     * Положить слоты игроку. Слоты из {@code untouched} не трогаются: там лежит предмет, который
     * запись кита не выражает, и он остаётся у игрока. Игрока нет на сервере: ложь, вызывающий
     * отложит всё в буфер.
     */
    boolean dress(UUID player, KitItem[] slots, Set<Integer> untouched);

    /** Правила стопок и надеваемости того клиента игры, что стоит на сервере. */
    KitStacking stacking();
}
