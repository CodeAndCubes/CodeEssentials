package com.mrleonardos.codeessentials.platform;

import java.util.Optional;
import java.util.function.Supplier;

import com.mrleonardos.codecore.api.CodeApi;
import com.mrleonardos.codecore.api.service.ServicePriority;
import com.mrleonardos.codeessentials.api.EssentialsApi;
import com.mrleonardos.codeessentials.api.event.EssentialsEvents;

final class ServiceBridge {

    static final String NOBODY = "nobody";

    private ServiceBridge() {}

    static void install(EssentialsEvents events) {
        EssentialsApi.install(new EssentialsApi.Lookup() {

            @Override
            public <T> Optional<T> find(Class<T> type) {
                return CodeApi.services()
                    .find(type);
            }
        }, events);
    }

    static <T> void register(Class<T> type, T implementation, ServicePriority priority) {
        CodeApi.services()
            .register(type, implementation, priority);
    }

    static <T> Supplier<T> holder(Class<T> type, T fallback) {
        return () -> CodeApi.services()
            .find(type)
            .orElse(fallback);
    }

    static String owner(Class<?> type) {
        return CodeApi.services()
            .find(type)
            .map(
                held -> held.getClass()
                    .getName())
            .orElse(NOBODY);
    }
}
