package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.runner.commands.ConsoleCommand
import ch.tutteli.atrium.api.cc.en_GB.contains
import ch.tutteli.atrium.api.cc.en_GB.message
import ch.tutteli.atrium.api.cc.en_GB.toThrow
import ch.tutteli.atrium.expect
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

object DispatchSpec : Spek({

    describe("error cases") {
        it("errors in case no commands are specified") {
            expect {
                dispatch(arrayOf("html"), errorHandler, listOf())
            }.toThrow<IllegalStateException> { message { contains("Main is misconfigured") } }
        }

        it("errors in case the args are empty") {
            expect {
                dispatch(arrayOf(), errorHandler, listOf(DummyCommand()))
            }.toThrow<IllegalStateException> { message { contains("No arguments supplied.") } }
        }

        it("errors in case the first arg is null") {
            expect {
                dispatch(arrayOf<String?>(null), errorHandler, listOf(DummyCommand()))
            }.toThrow<IllegalStateException> { message { contains("The first argument needs to be specified") } }
        }
        it("errors in case the first arg is empty") {
            expect {
                dispatch(arrayOf(""), errorHandler, listOf(DummyCommand()))
            }.toThrow<IllegalStateException> { message { contains("The first argument needs to be specified") } }
        }
        it("errors in case the first arg is blank") {
            expect {
                dispatch(arrayOf(" "), errorHandler, listOf(DummyCommand()))
            }.toThrow<IllegalStateException> { message { contains("The first argument needs to be specified") } }
        }

        it("errors in case there are not enough args for the command") {
            expect {
                dispatch(arrayOf("dummy"), errorHandler, listOf(DummyCommand()))
            }.toThrow<IllegalStateException> { message { contains("Not enough or too many arguments supplied") } }
        }

        it("errors in case there are too many args for the command") {
            expect {
                dispatch(arrayOf("dummy", "oneArgument", "tooMuch"), errorHandler, listOf(DummyCommand()))
            }.toThrow<IllegalStateException> { message { contains("Not enough or too many arguments supplied") } }
        }
    }
}){
    private class DummyCommand(
        override val name: String,
        override val description: String,
        override val arguments: String,
        override val example: String
    ) : ConsoleCommand {
        constructor(): this("dummy", "descr", "args", "example")
        override fun numOfArgsNotOk(number: Int) = number != 2

        override fun execute(args: Array<out String>, errorHandler: ErrorHandler) {
            //nothing to do
        }
    }
}
