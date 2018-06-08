package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.runner.commands.Json
import ch.loewenfels.depgraph.runner.commands.Man
import ch.tutteli.atrium.api.cc.en_UK.contains
import ch.tutteli.atrium.api.cc.en_UK.message
import ch.tutteli.atrium.api.cc.en_UK.toThrow
import ch.tutteli.atrium.expect
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

object ManSpec : Spek({
    describe("validation errors") {
        val man = Man(mapOf())

        it("throws an error if not enough arguments are supplied") {
            expect {
                dispatch(arrayOf(), errorHandler, listOf(man))
            }.toThrow<IllegalStateException> {
                message { contains("No arguments supplied") }
            }
        }

        it("throws an error if too many arguments are supplied") {
            expect {
                dispatch(arrayOf(man.name, "-command=json", "unexpectedAdditionalArg"), errorHandler, listOf(man))
            }.toThrow<IllegalStateException> {
                message { contains("Not enough or too many arguments supplied") }
            }
        }

        it("does not throw if two args are supplied") {
            dispatch(arrayOf(man.name, "-command=json"), errorHandler, listOf(man, Json))
        }
    }
})
