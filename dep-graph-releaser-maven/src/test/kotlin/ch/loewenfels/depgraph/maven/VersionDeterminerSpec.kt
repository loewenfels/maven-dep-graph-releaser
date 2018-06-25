package ch.loewenfels.depgraph.maven

import ch.tutteli.atrium.api.cc.en_GB.toBe
import ch.tutteli.atrium.assert
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe

object VersionDeterminerSpec : Spek({
    val testee = VersionDeterminer()

    describe("fun ${testee::releaseVersion.name}") {
        mapOf(
            "2.0.1-10-SNAPSHOT" to "2.0.1-10",
            "2.0.1-SNAPSHOT" to "2.0.1",
            "2.x.1-SNAPSHOT" to "2.x.1",
            "2.0-SNAPSHOT" to "2.0",
            "2.x-SNAPSHOT" to "2.x",
            "2-SNAPSHOT" to "2",
            "2.0.3" to "2.0.4",
            "2.0.49" to "2.0.50",
            "2.0.99" to "2.0.100",
            "2.19" to "2.20",
            "9" to "10",
            "10" to "11",
            "2.0.3final" to "2.0.4",
            "2.5" to "2.6",
            "2.x" to "3",
            "2" to "3",
            "lovelyVersion" to "lovelyVersion.2"
        ).forEach { version, expected ->
            test("$version turns into $expected") {
                val result = testee.releaseVersion(version)
                assert(result).toBe(expected)
            }
            test("v$version turns into v$expected") {
                val result = testee.releaseVersion("v$version")
                assert(result).toBe("v$expected")
            }
        }
    }

    describe("fun ${testee::nextDevVersion.name}") {
        mapOf(
            "2.0.1-10-SNAPSHOT" to "2.0.2-SNAPSHOT",
            "2.0.1-SNAPSHOT" to "2.0.2-SNAPSHOT",
            "2.x.1-SNAPSHOT" to "2.x.2-SNAPSHOT",
            "2.0-SNAPSHOT" to "2.1-SNAPSHOT",
            "2.x-SNAPSHOT" to "3-SNAPSHOT",
            "2-SNAPSHOT" to "3-SNAPSHOT",
            "2.0.3" to "2.0.5-SNAPSHOT",
            "2.0.3final" to "2.0.5-SNAPSHOT",
            "2.5" to "2.7-SNAPSHOT",
            "2.x" to "4-SNAPSHOT",
            "2" to "4-SNAPSHOT",
            "2.0.48" to "2.0.50-SNAPSHOT",
            "2.0.49" to "2.0.51-SNAPSHOT",
            "2.0.98" to "2.0.100-SNAPSHOT",
            "2.0.99" to "2.0.101-SNAPSHOT",
            "2.18" to "2.20-SNAPSHOT",
            "2.19" to "2.21-SNAPSHOT",
            "8" to "10-SNAPSHOT",
            "9" to "11-SNAPSHOT",
            "10" to "12-SNAPSHOT",
            "lovelyVersion" to "lovelyVersion.3-SNAPSHOT"
        ).forEach { version, expected ->
            test("$version turns into $expected") {
                val result = testee.nextDevVersion(version)
                assert(result).toBe(expected)
            }
            test("v$version turns into v$expected") {
                val result = testee.nextDevVersion("v$version")
                assert(result).toBe("v$expected")
            }
        }
    }
})
