package ch.loewenfels.depgraph.serialization

import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.Project
import ch.tutteli.atrium.api.cc.en_UK.contains
import ch.tutteli.atrium.api.cc.en_UK.message
import ch.tutteli.atrium.api.cc.en_UK.toBe
import ch.tutteli.atrium.api.cc.en_UK.toThrow
import ch.tutteli.atrium.assert
import ch.tutteli.atrium.expect
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.JsonReader
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it

object SerializerSpec : Spek({
    val testee = Serializer()

    fun createProject(state: CommandState)
        = Project(DummyProjectId("x"), "8.2", "9.0.0", listOf(DummyCommand(state)), listOf())

    describe("serialize and deserialize") {

        val aId = DummyProjectId("a")
        val projectWithoutCommandsAndDependents = Project(aId, "5.0", "5.1", listOf(), listOf())
        val projectWithCommandsWithoutDependents = Project(DummyProjectId("b"), "1.2", "2.0", listOf(DummyCommand(CommandState.Failed("oh no"))), listOf())
        val projectWithoutCommandsButDependents = Project(DummyProjectId("c"), "1.5", "3.0", listOf(), listOf(projectWithCommandsWithoutDependents))
        val projectWitCommandsAndDependents = Project(DummyProjectId("d"), "1.5", "3.0", listOf(DummyCommand(CommandState.Waiting(setOf(aId)))), listOf(projectWithoutCommandsButDependents, projectWithCommandsWithoutDependents))

        val commands = Project::commands.name
        val dependents = Project::dependents.name

        val projects = mapOf(
            "a Project without $commands and $dependents" to projectWithoutCommandsAndDependents,
            "a Project with $commands but without $dependents" to projectWithCommandsWithoutDependents,
            "a Project without $commands but $dependents" to projectWithoutCommandsButDependents,
            "a Project with $commands and $dependents" to projectWitCommandsAndDependents
        )
        val states = listOf(
            CommandState.Waiting(setOf(aId, DummyProjectId("x"), DummyProjectId("z"))),
            CommandState.Ready,
            CommandState.InProgress,
            CommandState.Succeeded,
            CommandState.Failed("error"),
            CommandState.Deactivated
        ).associateBy({ "a Project with a single command in state ${it::class.java.simpleName}" }, { createProject(it) })

        (states.asSequence() + projects.asSequence()).forEach { (description, project) ->
            action(description) {
                val json = testee.serialize(project)
                val result = testee.deserialize(json)
                it("is an equal project") {
                    assert(result).toBe(project)
                }
                it("is the same JSON if it is serialized again") {
                    val jsonResult = testee.serialize(result)
                    assert(jsonResult).toBe(json)
                }
            }
        }
    }

    describe("malformed JSON"){
        given("dangling }") {
            it("throws a JsonEncodingException"){
                val json = testee.serialize(createProject(CommandState.Ready))
                expect {
                    testee.deserialize("$json}")
                }.toThrow<JsonEncodingException>()
            }
        }
        given("comment at the beginning") {
            it("throws a JsonEncodingException"){
                val json = testee.serialize(createProject(CommandState.Ready))
                expect {
                    testee.deserialize("<!-- my lovely JSON --> $json")
                }.toThrow<JsonEncodingException>()
            }
        }
    }
})

data class DummyCommand(override val state: CommandState) : Command
