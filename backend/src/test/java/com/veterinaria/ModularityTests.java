package com.veterinaria;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifica que la estructura modular se respete: sin dependencias ciclicas,
 * sin acceso a tipos internos de otros modulos y sin violaciones de API.
 */
class ModularityTests {

    static final ApplicationModules MODULES = ApplicationModules.of(VeterinariaApplication.class);

    @Test
    void verifiesModuleStructure() {
        MODULES.verify();
    }
}