package com.mrleonardos.codeessentials.internal.kits;

import java.util.Optional;
import java.util.UUID;

import com.mrleonardos.codeessentials.api.model.KitItem;

public interface KitHands {

    /**
     * Слоты игрока в том же порядке, что у кита: 0..35 инвентарь, 36..39 броня.
     *
     * @return пустой ответ, когда игрока нет на сервере: брать его слоты не с чего
     */
    Optional<KitItem[]> worn(UUID player);

    /** Положить слоты игроку. Игрока нет на сервере: ложь, вызывающий отложит всё в буфер. */
    boolean dress(UUID player, KitItem[] slots);

    /** Правила стопок и надеваемости того клиента игры, что стоит на сервере. */
    KitStacking stacking();
}
