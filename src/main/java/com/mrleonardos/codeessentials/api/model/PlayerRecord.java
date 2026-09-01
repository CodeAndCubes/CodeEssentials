package com.mrleonardos.codeessentials.api.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import com.mrleonardos.codeessentials.api.EssentialsLimits;

/**
 * Состояние игрока в мире: дома и стек возврата.
 *
 * <p>
 * Запись неизменяема, правка отдаёт новую: снимок за volatile меняется целиком, и читатель из
 * чужого потока никогда не видит половину правки. Кулдауны сюда не входят, они живут отдельным
 * файлом со своим сроком годности.
 *
 * <p>
 * Дома отсортированы по имени, стек возврата идёт от свежей записи к старой.
 */
public final class PlayerRecord {

    private final UUID uuid;
    private final String name;
    private final Map<String, HomeRecord> homes;
    private final List<BackPoint> back;

    private PlayerRecord(UUID uuid, String name, Map<String, HomeRecord> homes, List<BackPoint> back) {
        this.uuid = uuid;
        this.name = name;
        this.homes = homes;
        this.back = back;
    }

    /**
     * Собрать запись.
     *
     * @param name  последнее известное имя игрока, допускается пустой ответ
     * @param homes дома, повтор имени оставляет последний
     * @param back  стек возврата от свежей записи к старой
     */
    public static PlayerRecord of(UUID uuid, String name, Collection<HomeRecord> homes, List<BackPoint> back) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(homes, "homes");
        Objects.requireNonNull(back, "back");
        Map<String, HomeRecord> byName = new TreeMap<>();
        for (HomeRecord home : homes) {
            byName.put(home.name(), home);
        }
        return new PlayerRecord(
            uuid,
            name,
            Collections.unmodifiableMap(byName),
            Collections.unmodifiableList(new ArrayList<>(back)));
    }

    /** Игрок без домов и без записей возврата. */
    public static PlayerRecord empty(UUID uuid, String name) {
        return of(uuid, name, Collections.<HomeRecord>emptyList(), Collections.<BackPoint>emptyList());
    }

    /** Идентификатор игрока. */
    public UUID uuid() {
        return uuid;
    }

    /** Последнее известное имя или пустой ответ. */
    public String name() {
        return name;
    }

    /** Дома по именам, порядок алфавитный. */
    public Map<String, HomeRecord> homes() {
        return homes;
    }

    /** Дом по имени. */
    public Optional<HomeRecord> home(String homeName) {
        return Optional.ofNullable(homes.get(homeName));
    }

    /** Стек возврата от свежей записи к старой. */
    public List<BackPoint> back() {
        return back;
    }

    /** Верхняя запись стека возврата. */
    public Optional<BackPoint> topBack() {
        return back.isEmpty() ? Optional.<BackPoint>empty() : Optional.of(back.get(0));
    }

    /** Та же запись с обновлённым именем. */
    public PlayerRecord withName(String newName) {
        return new PlayerRecord(uuid, newName, homes, back);
    }

    /** Та же запись с записанным домом: имя уже занято, значит дом переезжает на новую точку. */
    public PlayerRecord withHome(HomeRecord home) {
        Objects.requireNonNull(home, "home");
        Map<String, HomeRecord> updated = new TreeMap<>(homes);
        updated.put(home.name(), home);
        return new PlayerRecord(uuid, name, Collections.unmodifiableMap(updated), back);
    }

    /** Та же запись без указанного дома. Дома с таким именем нет, значит запись остаётся прежней. */
    public PlayerRecord withoutHome(String homeName) {
        if (!homes.containsKey(homeName)) {
            return this;
        }
        Map<String, HomeRecord> updated = new TreeMap<>(homes);
        updated.remove(homeName);
        return new PlayerRecord(uuid, name, Collections.unmodifiableMap(updated), back);
    }

    /**
     * Та же запись с новой точкой на вершине стека возврата.
     *
     * @param depth глубина стека, значение вне {@link EssentialsLimits} зажимается; лишние записи
     *              уходят с хвоста
     */
    public PlayerRecord pushBack(BackPoint point, int depth) {
        Objects.requireNonNull(point, "point");
        int kept = EssentialsLimits.defaults()
            .clampBackDepth(depth);
        LinkedList<BackPoint> updated = new LinkedList<>(back);
        updated.addFirst(point);
        while (updated.size() > kept) {
            updated.removeLast();
        }
        return new PlayerRecord(uuid, name, homes, Collections.unmodifiableList(new ArrayList<>(updated)));
    }

    /** Та же запись без верхней точки стека. Стек пуст, значит запись остаётся прежней. */
    public PlayerRecord popBack() {
        if (back.isEmpty()) {
            return this;
        }
        return new PlayerRecord(
            uuid,
            name,
            homes,
            Collections.unmodifiableList(new ArrayList<>(back.subList(1, back.size()))));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlayerRecord)) {
            return false;
        }
        PlayerRecord that = (PlayerRecord) other;
        return uuid.equals(that.uuid) && Objects.equals(name, that.name)
            && homes.equals(that.homes)
            && back.equals(that.back);
    }

    @Override
    public int hashCode() {
        return ((uuid.hashCode() * 31 + Objects.hashCode(name)) * 31 + homes.hashCode()) * 31 + back.hashCode();
    }

    @Override
    public String toString() {
        return (name == null ? uuid.toString() : name) + ": " + homes.size() + " home(s), " + back.size() + " back";
    }
}
