package ch.loewenfels.depgraph.serialization

import ch.loewenfels.depgraph.ConfigKey
import ch.loewenfels.depgraph.data.Command
import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.Project
import ch.loewenfels.depgraph.data.ReleasePlan
import ch.tutteli.atrium.api.cc.en_UK.toBe
import ch.tutteli.atrium.api.cc.en_UK.toThrow
import ch.tutteli.atrium.assert
import ch.tutteli.atrium.expect
import com.squareup.moshi.JsonEncodingException
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it

object SerializerSpec : Spek({
    val testee = Serializer()

    fun createReleasePlan(project: Project): ReleasePlan {
        return ReleasePlan("publishId", project.id, mapOf(project.id to project), mapOf(), mapOf())
    }

    fun createReleasePlan(state: CommandState): ReleasePlan {
        val rootProjectId = DummyProjectId("x")
        val project = Project(rootProjectId, false, "8.2", "9.0.0", 0, listOf(DummyCommand(state)), "")
        return createReleasePlan(project)
    }

    describe("serialize and deserialize") {
        val aId = DummyProjectId("a")
        val projectWithoutCommandsAndDependents = Project(aId, false,"5.0", "5.1", 1, listOf(), "")
        val projectWithCommandsWithoutDependents = Project(DummyProjectId("b"), false, "1.2", "2.0", 2, listOf(DummyCommand(CommandState.Failed)), "")
        val projectWithoutCommandsButDependents = Project(DummyProjectId("c"), false,"1.5", "3.0", 1, listOf(), "")
        val releasePlanWithoutCommandsButDependents = ReleasePlan(
            "publishId",
            projectWithoutCommandsButDependents.id,
            mapOf(
                projectWithoutCommandsButDependents.id to projectWithoutCommandsButDependents,
                projectWithCommandsWithoutDependents.id to projectWithCommandsWithoutDependents
            ),
            mapOf(),
            mapOf(
                projectWithoutCommandsButDependents.id to setOf(projectWithCommandsWithoutDependents.id)
            )
        )
        val projectWithCommandsAndDependents = Project(DummyProjectId("d"), false, "1.5", "3.0", 0, listOf(DummyCommand(CommandState.Waiting(setOf(aId)))), "")
        val releasePlanWithCommandsAndDependents = ReleasePlan(
            "publishId",
            projectWithCommandsAndDependents.id,
            mapOf(
                projectWithCommandsAndDependents.id to projectWithCommandsAndDependents,
                projectWithoutCommandsAndDependents.id to projectWithoutCommandsAndDependents,
                projectWithoutCommandsButDependents.id to projectWithoutCommandsButDependents,
                projectWithCommandsWithoutDependents.id to projectWithCommandsWithoutDependents
            ),
            mapOf(),
            mapOf(
                projectWithCommandsAndDependents.id to setOf(projectWithoutCommandsButDependents.id, projectWithoutCommandsAndDependents.id),
                projectWithoutCommandsButDependents.id to setOf(projectWithCommandsWithoutDependents.id)
            ),
            listOf("warning 1"),
            listOf("info 1", "info2"),
            mapOf(ConfigKey.REMOTE_JOB to "value 1", ConfigKey.REGEX_PARAMS to "value 2")
        )

        val commands = Project::commands.name

        val projects = mapOf(
            "a Project without $commands and dependents" to createReleasePlan(projectWithoutCommandsAndDependents),
            "a Project with $commands but without dependents" to createReleasePlan(projectWithCommandsWithoutDependents),
            "a Project without $commands but dependents" to releasePlanWithoutCommandsButDependents,
            "a Project with $commands and dependents" to releasePlanWithCommandsAndDependents
        )
        val states = listOf(
            CommandState.Waiting(setOf(aId, DummyProjectId("x"), DummyProjectId("z"))),
            CommandState.Ready,
            CommandState.InProgress,
            CommandState.Succeeded,
            CommandState.Failed,
            CommandState.Deactivated(CommandState.Waiting(setOf(DummyProjectId("x"), DummyProjectId("z"))))
        ).associateBy(
            { "a Project with a single command in state ${it::class.java.simpleName}" },
            { createReleasePlan(it) }
        )

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

    describe("malformed JSON") {
        given("dangling }") {
            it("throws a JsonEncodingException") {
                val json = testee.serialize(createReleasePlan(CommandState.Ready))
                expect {
                    testee.deserialize("$json}")
                }.toThrow<JsonEncodingException>()
            }
        }
        given("comment at the beginning") {
            it("throws a JsonEncodingException") {
                val json = testee.serialize(createReleasePlan(CommandState.Ready))
                expect {
                    testee.deserialize("<!-- my lovely JSON --> $json")
                }.toThrow<JsonEncodingException>()
            }
        }
    }
})


data class DummyCommand(override val state: CommandState) : Command {
    override fun asNewState(newState: CommandState) = DummyCommand(newState)
}
