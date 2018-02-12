package ch.loewenfels.depgraph.maven

import ch.loewenfels.depgraph.data.maven.MavenProjectId
import ch.tutteli.atrium.api.cc.en_UK.toBe
import ch.tutteli.atrium.assert
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe

object VersionDeterminerSpec : Spek({
    val testee = VersionDeterminer()
    describe(testee::determineNextVersion.name) {
        mapOf(
            "1.0-SNAPSHOT" to "1.0",
            "1.0.1-SNAPSHOT" to "1.0.1",
            "1.x-SNAPSHOT" to "1.x",
            "1.0.1" to "1.0.2",
            "1.0.1final" to "1.0.2",
            "lovelyVersion" to "lovelyVersion.2"
        ).forEach { version, expected ->
            test("$version turns into $expected") {
                val result = testee.determineNextVersion(MavenProjectId("com", "example", version))
                assert(result).toBe(expected)
            }
        }

    }
})
