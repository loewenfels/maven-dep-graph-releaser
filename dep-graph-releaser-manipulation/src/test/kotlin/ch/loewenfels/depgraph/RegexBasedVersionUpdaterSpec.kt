package ch.loewenfels.depgraph

import ch.loewenfels.depgraph.maven.getTestDirectory
import ch.tutteli.atrium.api.cc.en_UK.containsRegex
import ch.tutteli.atrium.api.cc.en_UK.toBe
import ch.tutteli.atrium.api.cc.en_UK.toThrow
import ch.tutteli.atrium.assert
import ch.tutteli.atrium.exampleA
import ch.tutteli.atrium.expect
import ch.tutteli.spek.extensions.TempFolder
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.context
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import java.io.File

object RegexBasedVersionUpdaterSpec : Spek({
    val tempFolder = TempFolder.perTest()
    registerListener(tempFolder)

    val testee = RegexBasedVersionUpdater()

    describe("error cases") {
        given("single project with third party dependency") {
            val pom = File(getTestDirectory("singleProject"), "pom.xml")
            context("dependency without version shall be updated") {
                it("throws an IllegalStateException, mentioning that at least one dependency should be updated") {
                    val tmpPom = tempFolder.newFile("pom.xml")
                    tmpPom.writeBytes(pom.readBytes())
                    expect {
                        testee.updateDependency(tmpPom, "com.google.code.gson", "gson", "4.4")
                    }.toThrow<IllegalStateException>()
                }
            }
        }
    }


    given("single project with third party dependency") {
        val pom = File(getTestDirectory("singleProject"), "pom.xml")

        context("dependency shall be updated, same version") {
            it("updates the dependency and file content is the same as before") {
                val tmpPom = tempFolder.newFile("pom.xml")
                tmpPom.writeBytes(pom.readBytes())
                testee.updateDependency(tmpPom, "junit", "junit", "4.12")
                assert(tmpPom.readText()).toBe(pom.readText())
            }
        }

        context("dependency shall be updated, new version") {
            it("updates the dependency") {
                val tmpPom = tempFolder.newFile("pom.xml")
                tmpPom.writeBytes(pom.readBytes())
                testee.updateDependency(tmpPom, "junit", "junit", "4.4")
                assert(tmpPom.readText()).containsRegex(
                    "<dependency>[\\S\\s]*" +
                        "<groupId>junit</groupId>[\\S\\s]*" +
                        "<artifactId>junit</artifactId>[\\S\\s]*" +
                        "<version>4.4</version>[\\S\\s]*" +
                        "</dependency>"
                )
            }
        }

        context("dependency occurs multiple times") {
            it("updates the dependency") {
                val tmpPom = tempFolder.newFile("pom.xml")
                tmpPom.writeBytes(pom.readBytes())
                testee.updateDependency(tmpPom, "test", "test", "2.0")
                assert(tmpPom.readText()).containsRegex(
                    "<dependency>[\\S\\s]*" +
                        "<groupId>test</groupId>[\\S\\s]*" +
                        "<artifactId>test</artifactId>[\\S\\s]*" +
                        "<version>2.0</version>[\\S\\s]*" +
                        "</dependency>",
                    "<dependency>[\\S\\s]*" +
                        "<groupId>test</groupId>[\\S\\s]*" +
                        "<version>2.0</version>[\\S\\s]*" +
                        "<artifactId>test</artifactId>[\\S\\s]*" +
                        "</dependency>",
                    "<dependency>[\\S\\s]*" +
                        "<artifactId>test</artifactId>[\\S\\s]*" +
                        "<groupId>test</groupId>[\\S\\s]*" +
                        "<version>2.0</version>[\\S\\s]*" +
                        "</dependency>",
                    "<dependency>[\\S\\s]*" +
                        "<artifactId>test</artifactId>[\\S\\s]*" +
                        "<version>2.0</version>[\\S\\s]*" +
                        "<groupId>test</groupId>[\\S\\s]*" +
                        "</dependency>"
                )
            }
        }

        context("dependency once without version") {
            it("updates the dependency with version") {
                val tmpPom = tempFolder.newFile("pom.xml")
                tmpPom.writeBytes(pom.readBytes())
                testee.updateDependency(tmpPom, "test", "onceWithoutVersion", "3.4")
                assert(tmpPom.readText()).containsRegex(
                    "<dependency>[\\S\\s]*" +
                        "<groupId>test</groupId>[\\S\\s]*" +
                        "<artifactId>onceWithoutVersion</artifactId>[\\S\\s]*" +
                        "<version>3.4</version>[\\S\\s]*" +
                        "</dependency>",
                    "<dependency>[\\S\\s]*" +
                        "<groupId>test</groupId>[\\S\\s]*" +
                        "<artifactId>onceWithoutVersion</artifactId>[\\S\\s]*" +
                        "</dependency>"
                )
            }
        }
    }


    given("project with dependency and version in dependency management") {
        val pom = File(getTestDirectory("managingVersions/viaDependencyManagement"), "b.pom")

        context("dependency shall be updated, same version") {
            it("updates the dependency and file content is the same as before") {
                val tmpPom = tempFolder.newFile("pom.xml")
                tmpPom.writeBytes(pom.readBytes())
                testee.updateDependency(tmpPom, exampleA.id.groupId, exampleA.id.artifactId, "1.0.0")
                assert(tmpPom.readText()).toBe(pom.readText())
            }
        }

        context("dependency shall be updated, new version") {
            it("updates the dependency") {
                val tmpPom = tempFolder.newFile("pom.xml")
                tmpPom.writeBytes(pom.readBytes())
                testee.updateDependency(tmpPom,exampleA.id.groupId, exampleA.id.artifactId, "1.1.0")
                assert(tmpPom.readText()).containsRegex(
                    "<dependency>[\\S\\s]*" +
                        "<groupId>${exampleA.id.groupId}</groupId>[\\S\\s]*" +
                        "<artifactId>${exampleA.id.artifactId}</artifactId>[\\S\\s]*" +
                        "<version>1.1.0</version>[\\S\\s]*" +
                        "</dependency>"
                )
            }
        }
    }
})
