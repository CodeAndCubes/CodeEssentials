package com.mrleonardos.codeessentials.internal.engine;

import java.util.Optional;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;

import com.mrleonardos.codeessentials.api.model.Point;
import com.mrleonardos.codeessentials.api.teleport.BlockView;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotLimits;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotPolicy;
import com.mrleonardos.codeessentials.api.teleport.SafeSpotResult;

/**
 * Выбор политики поиска безопасной точки по имени из настроек.
 *
 * <p>
 * Имя выбирается один раз и держится до перечитывания настроек: смена {@code safeSpot.policy}
 * применяется после {@code /essentials reload}. Незнакомое имя не откатывается на встроенный поиск, а
 * выключает его: перенос идёт по прямым координатам, и об этом уходит строка в журнал.
 */
public final class PolicyChoice {

    /** Откуда искать политику по имени. Реестр {@code EssentialsApi} за швом ради тестов. */
    public interface Lookup {

        Optional<SafeSpotPolicy> policy(String id);
    }

    private final Supplier<String> wanted;
    private final Lookup lookup;
    private final Logger log;

    private volatile SafeSpotPolicy named;
    private boolean unknownTold;

    public PolicyChoice(Supplier<String> wanted, Lookup lookup, Logger log) {
        this.wanted = wanted;
        this.lookup = lookup;
        this.log = log;
    }

    /** Выбранная политика. */
    public SafeSpotPolicy named() {
        SafeSpotPolicy known = named;
        if (known != null) {
            return known;
        }
        String id = wanted.get();
        SafeSpotPolicy found = lookup.policy(id)
            .orElse(null);
        if (found == null) {
            if (!unknownTold) {
                unknownTold = true;
                log.warn(
                    "Safe spot seam safeSpot.policy holds the unregistered name {}, "
                        + "the search is off and teleports go to the exact coordinates",
                    id);
            }
            found = new OffPolicy(id);
        }
        named = found;
        return found;
    }

    /** Забыть выбранную политику: настройки перечитаны, имя могло стать другим или зарегистрированным. */
    public void reset() {
        named = null;
        unknownTold = false;
    }

    /** Политика выключенного поиска: точка считается годной как есть, в границах высоты мира. */
    static final class OffPolicy implements SafeSpotPolicy {

        private final String id;

        OffPolicy(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public SafeSpotResult find(BlockView view, Point hint, SafeSpotLimits limits) {
            return SafeSpotResult.found(hint, SafeSpotFinder.forced(hint, view.height()), 0);
        }
    }
}
