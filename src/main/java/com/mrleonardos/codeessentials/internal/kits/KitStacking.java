package com.mrleonardos.codeessentials.internal.kits;

import com.mrleonardos.codeessentials.api.model.KitItem;

public interface KitStacking {

    /** Сколько предметов одного вида лежит в одной стопке у этого предмета. */
    int limit(KitItem item);

    /** Подливается ли предмет в существующую стопку: кроме вида, решает и сама игра. */
    boolean merges(KitItem held, KitItem added);

    /** Ложится ли предмет в этот слот: в броню только то, что надевается. */
    boolean accepts(int slot, KitItem item);
}
