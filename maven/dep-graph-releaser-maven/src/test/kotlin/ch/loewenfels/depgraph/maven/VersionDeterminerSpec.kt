package ch.loewenfels.depgraph.maven

import ch.tutteli.atrium.api.cc.en_UK.toBe
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
            "lovelyVersion" to "lovelyVersion.3-SNAPSHOT"
        ).forEach { version, expected ->
            test("$version turns into $expected") {
                val result = testee.nextDevVersion(version)
                assert(result).toBe(expected)
            }
        }
    }
})
