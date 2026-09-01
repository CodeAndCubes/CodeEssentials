package com.mrleonardos.codeessentials.api.teleport;

import java.util.Objects;
import java.util.Optional;

import com.mrleonardos.codeessentials.api.model.Point;

/**
 * Чем закончился поиск безопасной точки.
 *
 * <p>
 * Поправку нельзя объявить самому: {@link #found} сравнивает найденное с просьбой и сам решает,
 * точное это место или сдвинутое. Поиск, сообщивший «точно», подвинув игрока на десять блоков,
 * здесь невозможен, а игроку важно знать, что он встал не там, где просил.
 *
 * <p>
 * Отказ несёт готовую причину для работы переноса, поэтому движку не приходится переводить один
 * перечень в другой.
 */
public final class SafeSpotResult {

    /** Чем закончился поиск. */
    public enum Outcome {

        /** Место безопасно ровно там, где просили. */
        EXACT,

        /** Место нашлось рядом, точку пришлось поправить. */
        CORRECTED,

        /** Безопасного места нет во всех разрешённых границах. */
        UNSAFE,

        /** Чанк цели не загружен, а генерация выключена. */
        CHUNK_MISSING
    }

    private final Outcome outcome;
    private final Point spot;
    private final int probes;

    private SafeSpotResult(Outcome outcome, Point spot, int probes) {
        this.outcome = outcome;
        this.spot = spot;
        this.probes = probes;
    }

    /**
     * Место найдено.
     *
     * @param hint   куда просили
     * @param spot   куда игрок встанет на самом деле
     * @param probes сколько клеток проверено
     */
    public static SafeSpotResult found(Point hint, Point spot, int probes) {
        Objects.requireNonNull(hint, "hint");
        Objects.requireNonNull(spot, "spot");
        return new SafeSpotResult(hint.equals(spot) ? Outcome.EXACT : Outcome.CORRECTED, spot, checked(probes));
    }

    /**
     * Безопасного места нет.
     *
     * @param probes сколько клеток проверено
     */
    public static SafeSpotResult unsafe(int probes) {
        return new SafeSpotResult(Outcome.UNSAFE, null, checked(probes));
    }

    /** Чанк цели не загружен, поиск не начинался. */
    public static SafeSpotResult chunkMissing() {
        return new SafeSpotResult(Outcome.CHUNK_MISSING, null, 0);
    }

    /** Чем закончился поиск. */
    public Outcome outcome() {
        return outcome;
    }

    /** Правда ли место нашлось. */
    public boolean found() {
        return spot != null;
    }

    /** Правда ли точку пришлось поправить. */
    public boolean corrected() {
        return outcome == Outcome.CORRECTED;
    }

    /** Найденное место или пустой ответ. */
    public Optional<Point> spot() {
        return Optional.ofNullable(spot);
    }

    /** Сколько клеток проверено: по этому числу видно, во что обходится поиск. */
    public int probes() {
        return probes;
    }

    /** Причина для работы переноса или пустой ответ, если место нашлось. */
    public Optional<CancelReason> failure() {
        if (outcome == Outcome.UNSAFE) {
            return Optional.of(CancelReason.UNSAFE);
        }
        if (outcome == Outcome.CHUNK_MISSING) {
            return Optional.of(CancelReason.CHUNK_MISSING);
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SafeSpotResult)) {
            return false;
        }
        SafeSpotResult that = (SafeSpotResult) other;
        return outcome == that.outcome && probes == that.probes && Objects.equals(spot, that.spot);
    }

    @Override
    public int hashCode() {
        return (outcome.hashCode() * 31 + Objects.hashCode(spot)) * 31 + probes;
    }

    @Override
    public String toString() {
        return outcome + (spot == null ? "" : " " + spot.print()) + " after " + probes + " probe(s)";
    }

    private static int checked(int probes) {
        if (probes < 0) {
            throw new IllegalArgumentException("Probe count must not be negative: " + probes);
        }
        return probes;
    }
}
