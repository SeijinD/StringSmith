package com.seijind.stringsmith.extract

import org.junit.Assert.assertEquals
import org.junit.Test

class ResourceSystemTest {

    @Test
    fun androidResPath() {
        assertEquals(
            ResourceSystem.ANDROID,
            ResourceSystem.of("/proj/app/src/main/res/values/strings.xml")
        )
    }

    @Test
    fun composeResourcesPath() {
        assertEquals(
            ResourceSystem.COMPOSE_MULTIPLATFORM,
            ResourceSystem.of("/proj/shared/src/commonMain/composeResources/values/strings.xml")
        )
    }

    @Test
    fun composeResourcesLocaleVariant() {
        assertEquals(
            ResourceSystem.COMPOSE_MULTIPLATFORM,
            ResourceSystem.of("/proj/shared/src/commonMain/composeResources/values-de/strings.xml")
        )
    }

    @Test
    fun windowsSeparatorsNormalized() {
        assertEquals(
            ResourceSystem.COMPOSE_MULTIPLATFORM,
            ResourceSystem.of("C:\\proj\\shared\\src\\commonMain\\composeResources\\values\\strings.xml")
        )
    }

    @Test
    fun mixedSeparatorsStillDetected() {
        // Mixed `\` and `/` (can occur from VirtualFile path quirks) must still be recognized.
        assertEquals(
            ResourceSystem.COMPOSE_MULTIPLATFORM,
            ResourceSystem.of("C:\\proj\\shared/composeResources\\values/strings.xml")
        )
    }

    @Test
    fun markerAsSubstringNotMistakenForSegment() {
        // A directory merely containing the marker text (not as a full path segment) is Android.
        assertEquals(
            ResourceSystem.ANDROID,
            ResourceSystem.of("/proj/mycomposeResourcesX/values/strings.xml")
        )
    }
}
