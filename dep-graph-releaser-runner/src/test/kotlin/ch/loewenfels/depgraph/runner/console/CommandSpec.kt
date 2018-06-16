package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.runner.Main
import ch.loewenfels.depgraph.runner.commands.ConsoleCommand
import ch.tutteli.atrium.api.cc.en_GB.contains
import ch.tutteli.atrium.api.cc.en_GB.message
import ch.tutteli.atrium.api.cc.en_GB.toBe
import ch.tutteli.atrium.api.cc.en_GB.toThrow
import ch.tutteli.atrium.assert
import ch.tutteli.atrium.expect
import ch.tutteli.spek.extensions.TempFolder
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import java.io.File

abstract class CommandSpec(
    testee: ConsoleCommand,
    notEnoughArgs: (TempFolder) -> Array<out String>,
    tooManyArgs: (TempFolder) -> Array<out String>,
    allowedNumberOfArgs: IntRange
) : Spek({
    val tempFolder = TempFolder.perTest()
    registerListener(tempFolder)

    Main.fileVerifier = object : FileVerifier {
        override fun file(path: String, fileDescription: String) = File(path)
    }

    describe("validation errors") {

        it("throws an error if not enough arguments are supplied") {
            expect {
                dispatch(notEnoughArgs(tempFolder), errorHandler, listOf(testee))
            }.toThrow<IllegalStateException> {
                message { contains("Not enough or too many arguments supplied") }
            }
        }

        it("throws an error if too many arguments are supplied") {
            expect {
                dispatch(tooManyArgs(tempFolder), errorHandler, listOf(testee))
            }.toThrow<IllegalStateException> {
                message { contains("Not enough or too many arguments supplied") }
            }
        }

        describe("fun ${testee::numOfArgsNotOk.name}") {
            group("returns false for a correct number of args") {
                allowedNumberOfArgs.forEach { number ->
                    it("$number") {
                        assert(testee.numOfArgsNotOk(number)).toBe(false)
                    }
                }
            }
            group("returns true for an incorrect number of args") {
                listOf(allowedNumberOfArgs.start - 1, allowedNumberOfArgs.endInclusive + 1).forEach { number ->
                    it("$number") {
                        assert(testee.numOfArgsNotOk(number)).toBe(true)
                    }
                }
            }
        }
    }
})
