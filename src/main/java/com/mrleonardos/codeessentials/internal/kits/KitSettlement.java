package com.mrleonardos.codeessentials.internal.kits;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.mrleonardos.codeessentials.api.model.KitItem;

/**
 * Чем кончился обмен между сундуком редактора и рюкзаком администратора.
 *
 * <p>
 * Редактор показывает кит настоящими предметами, поэтому админ, положивший туда своё, остаётся без
 * него, а вынувший оттуда получает предмет из ниоткуда. Разница двух наборов, снятого при открытии и
 * оставшегося при закрытии, и есть то, что нужно вернуть в рюкзак и что забрать из него.
 *
 * <p>
 * Наборы сравниваются по видам, а не по слотам: перекладывание предмета из слота в слот не долг и не
 * возврат. Записи длиннее {@link KitItem#MAX_COUNT} режутся на несколько, потому что столько в одну
 * запись не помещается.
 */
public final class KitSettlement {

    private final List<KitItem> back;
    private final List<KitItem> take;

    private KitSettlement(List<KitItem> back, List<KitItem> take) {
        this.back = back;
        this.take = take;
    }

    /**
     * Сравнить два набора.
     *
     * @param opened что редактор показал при открытии
     * @param closed что осталось в редакторе при закрытии
     */
    public static KitSettlement between(List<KitItem> opened, List<KitItem> closed) {
        Objects.requireNonNull(opened, "opened");
        Objects.requireNonNull(closed, "closed");
        Map<KitItem, Integer> balance = new LinkedHashMap<>();
        for (KitItem item : opened) {
            count(balance, item, -item.count());
        }
        for (KitItem item : closed) {
            count(balance, item, item.count());
        }
        List<KitItem> back = new ArrayList<>();
        List<KitItem> take = new ArrayList<>();
        for (Map.Entry<KitItem, Integer> entry : balance.entrySet()) {
            int total = entry.getValue()
                .intValue();
            if (total > 0) {
                split(back, entry.getKey(), total);
            } else if (total < 0) {
                split(take, entry.getKey(), -total);
            }
        }
        return new KitSettlement(back, take);
    }

    /** Что вернуть администратору: это он принёс в редактор из своего рюкзака. */
    public List<KitItem> back() {
        return back;
    }

    /** Что забрать у администратора: это он вынул из картинки кита. */
    public List<KitItem> take() {
        return take;
    }

    private static void count(Map<KitItem, Integer> balance, KitItem item, int change) {
        KitItem kind = item.withCount(KitItem.MIN_COUNT);
        Integer held = balance.get(kind);
        balance.put(kind, Integer.valueOf(held == null ? change : held.intValue() + change));
    }

    private static void split(List<KitItem> into, KitItem kind, int total) {
        int left = total;
        while (left > 0) {
            int part = Math.min(left, KitItem.MAX_COUNT);
            into.add(kind.withCount(part));
            left -= part;
        }
    }
}
