package com.mrleonardos.codeessentials;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeessentials.api.EssentialsApi;
import com.mrleonardos.codesides.gate.PackageGate;

class ImportGateTest {

    private static final String[] GATED = { "com/mrleonardos/codeessentials/api",
        "com/mrleonardos/codeessentials/internal" };

    @Test
    void apiAndInternalHoldNoPlatformTypes() throws IOException {
        List<String> violations = gate().violations(GATED);

        assertTrue(
            violations.isEmpty(),
            () -> "типы Minecraft и Forge живут только в platform, чужие ссылки:\n" + String.join("\n", violations));
    }

    @Test
    void gateNoticesAForbiddenReference() throws IOException {
        List<String> found = gate().scan(PackageGate.foreignSample());

        assertFalse(found.isEmpty(), "гейт обязан ловить ссылку на тип Minecraft");
    }

    private static PackageGate gate() throws IOException {
        return PackageGate.of(EssentialsApi.class);
    }
}
