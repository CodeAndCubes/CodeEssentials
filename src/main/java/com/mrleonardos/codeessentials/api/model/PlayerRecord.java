package com.mrleonardos.codeessentials.api.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import com.mrleonardos.codeessentials.api.EssentialsLimits;

/**
 * Состояние игрока в мире: дома, стек возврата, буфер китов и отметки одноразовых китов.
 *
 * <p>
 * Запись неизменяема, правка отдаёт новую: снимок за volatile меняется целиком, и читатель из
 * чужого потока никогда не видит половину правки. Кулдауны сюда не входят, они живут отдельным
 * файлом со своим сроком годности.
 *
 * <p>
 * Дома отсортированы по имени, стек возврата идёт от свежей записи к старой, буфер китов по имени
 * кита. Буфер это долг перед игроком: не влезшие при выдаче предметы ждут здесь и доезжают позже,
 * поэтому его никто не чистит, даже когда сам кит удалён.
 */
public final class PlayerRecord {

    private final UUID uuid;
    private final String name;
    private final Map<String, HomeRecord> homes;
    private final List<BackPoint> back;
    private final Map<String, List<KitItem>> kits;
    private final Set<String> claims;

    private PlayerRecord(UUID uuid, String name, Map<String, HomeRecord> homes, List<BackPoint> back,
        Map<String, List<KitItem>> kits, Set<String> claims) {
        this.uuid = uuid;
        this.name = name;
        this.homes = homes;
        this.back = back;
        this.kits = kits;
        this.claims = claims;
    }

    /**
     * Собрать запись.
     *
     * @param name  последнее известное имя игрока, допускается пустой ответ
     * @param homes дома, повтор имени оставляет последний
     * @param back  стек возврата от свежей записи к старой
     */
    public static PlayerRecord of(UUID uuid, String name, Collection<HomeRecord> homes, List<BackPoint> back) {
        return of(
            uuid,
            name,
            homes,
            back,
            Collections.<String, List<KitItem>>emptyMap(),
            Collections.<String>emptySet());
    }

    /**
     * Собрать запись со всем состоянием китов.
     *
     * @param kits   буфер китов: имя кита в предметы по порядку доставки, пустые списки не хранятся
     * @param claims имена взятых одноразовых китов
     */
    public static PlayerRecord of(UUID uuid, String name, Collection<HomeRecord> homes, List<BackPoint> back,
        Map<String, List<KitItem>> kits, Set<String> claims) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(homes, "homes");
        Objects.requireNonNull(back, "back");
        Objects.requireNonNull(kits, "kits");
        Objects.requireNonNull(claims, "claims");
        Map<String, HomeRecord> byName = new TreeMap<>();
        for (HomeRecord home : homes) {
            byName.put(home.name(), home);
        }
        Map<String, List<KitItem>> buffered = new TreeMap<>();
        for (Map.Entry<String, List<KitItem>> entry : kits.entrySet()) {
            List<KitItem> items = entry.getValue() == null ? null : entry.getValue();
            if (items != null && !items.isEmpty()) {
                buffered.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(items)));
            }
        }
        return new PlayerRecord(
            uuid,
            name,
            Collections.unmodifiableMap(byName),
            Collections.unmodifiableList(new ArrayList<>(back)),
            Collections.unmodifiableMap(buffered),
            Collections.unmodifiableSet(new TreeSet<>(claims)));
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

    /** Буфер китов: имя кита в ждущие предметы, порядок алфавитный, пустых записей нет. */
    public Map<String, List<KitItem>> kitBuffer() {
        return kits;
    }

    /** Ждущие предметы одного кита или пустой список. */
    public List<KitItem> kitBuffer(String kitName) {
        List<KitItem> held = kits.get(kitName);
        return held == null ? Collections.<KitItem>emptyList() : held;
    }

    /** Сколько предметов ждёт в буфере всех китов. */
    public int kitBufferCount() {
        int count = 0;
        for (List<KitItem> items : kits.values()) {
            for (KitItem item : items) {
                count += item.count();
            }
        }
        return count;
    }

    /** Взят ли одноразовый кит с таким именем. */
    public boolean hasKitClaim(String kitName) {
        return claims.contains(kitName);
    }

    /** Имена взятых одноразовых китов, порядок алфавитный. */
    public Set<String> kitClaims() {
        return claims;
    }

    /** Та же запись с обновлённым именем. */
    public PlayerRecord withName(String newName) {
        return new PlayerRecord(uuid, newName, homes, back, kits, claims);
    }

    /** Та же запись с записанным домом: имя уже занято, значит дом переезжает на новую точку. */
    public PlayerRecord withHome(HomeRecord home) {
        Objects.requireNonNull(home, "home");
        Map<String, HomeRecord> updated = new TreeMap<>(homes);
        updated.put(home.name(), home);
        return new PlayerRecord(uuid, name, Collections.unmodifiableMap(updated), back, kits, claims);
    }

    /** Та же запись без указанного дома. Дома с таким именем нет, значит запись остаётся прежней. */
    public PlayerRecord withoutHome(String homeName) {
        if (!homes.containsKey(homeName)) {
            return this;
        }
        Map<String, HomeRecord> updated = new TreeMap<>(homes);
        updated.remove(homeName);
        return new PlayerRecord(uuid, name, Collections.unmodifiableMap(updated), back, kits, claims);
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
        return new PlayerRecord(
            uuid,
            name,
            homes,
            Collections.unmodifiableList(new ArrayList<>(updated)),
            kits,
            claims);
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
            Collections.unmodifiableList(new ArrayList<>(back.subList(1, back.size()))),
            kits,
            claims);
    }

    /**
     * Та же запись с отмеченным одноразовым китом. Отметка не снимается никаким методом: снятое
     * разрешение вернуло бы игроку второй кит.
     *
     * @param kitName имя кита в нижнем регистре
     */
    public PlayerRecord withKitClaim(String kitName) {
        Objects.requireNonNull(kitName, "kitName");
        if (claims.contains(kitName)) {
            return this;
        }
        Set<String> updated = new TreeSet<>(claims);
        updated.add(kitName);
        return new PlayerRecord(uuid, name, homes, back, kits, Collections.unmodifiableSet(updated));
    }

    /**
     * Та же запись с новым буфером одного кита.
     *
     * @param kitName имя кита в нижнем регистре
     * @param items   ждущие предметы по порядку доставки; пустой список убирает запись
     */
    public PlayerRecord withKitBuffer(String kitName, List<KitItem> items) {
        Objects.requireNonNull(kitName, "kitName");
        Objects.requireNonNull(items, "items");
        Map<String, List<KitItem>> updated = new TreeMap<>(kits);
        if (items.isEmpty()) {
            if (!updated.containsKey(kitName)) {
                return this;
            }
            updated.remove(kitName);
        } else {
            List<KitItem> held = updated.get(kitName);
            if (held != null && held.equals(items)) {
                return this;
            }
            updated.put(kitName, Collections.unmodifiableList(new ArrayList<>(items)));
        }
        return new PlayerRecord(uuid, name, homes, back, Collections.unmodifiableMap(updated), claims);
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
            && back.equals(that.back)
            && kits.equals(that.kits)
            && claims.equals(that.claims);
    }

    @Override
    public int hashCode() {
        return (((uuid.hashCode() * 31 + Objects.hashCode(name)) * 31 + homes.hashCode()) * 31 + back.hashCode()) * 31
            + (kits.hashCode() * 31 + claims.hashCode());
    }

    @Override
    public String toString() {
        return (name == null ? uuid.toString() : name) + ": "
            + homes.size()
            + " home(s), "
            + back.size()
            + " back, "
            + kits.size()
            + " kit buffer(s), "
            + claims.size()
            + " kit claim(s)";
    }
}
