package ch.loewenfels.depgraph.serialization

import ch.loewenfels.depgraph.data.CommandState
import ch.loewenfels.depgraph.data.ProjectId
import ch.loewenfels.depgraph.data.serialization.CommandStateJson
import ch.loewenfels.depgraph.serialization.PolymorphicAdapterFactory.Companion.PAYLOAD
import ch.loewenfels.depgraph.serialization.PolymorphicAdapterFactory.Companion.TYPE
import ch.tutteli.atrium.api.cc.en_GB.*
import ch.tutteli.atrium.assert
import ch.tutteli.atrium.expect
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.KotlinJsonAdapterFactory
import com.squareup.moshi.Moshi
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.*
import java.io.EOFException

object PolymorphicAdapterFactorySpec : Spek({

    val testee = PolymorphicAdapterFactory(ProjectId::class.java)
    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    @Suppress("UNCHECKED_CAST")
    val adapter = testee.create(ProjectId::class.java, mutableSetOf(), moshi) as NonNullJsonAdapter<ProjectId>
    val notType = "notType"
    val typeDummy = """"$TYPE":"${DummyProjectId::class.java.name}""""

    describe("Adapter creation") {

        describe("validation errors") {
            given("not an interface nor an abstract class passed into the constructor") {
                listOf(
                    PolymorphicAdapterFactorySpec::class.java,
                    CommandState.Ready::class.java,
                    CommandStateJson::class.java,
                    CommandStateJson.State::class.java
                ).forEach { clazz ->
                    context(clazz.name) {
                        it("throws an IllegalArgumentException containing the wrong type") {
                            expect {
                                PolymorphicAdapterFactory(clazz)
                            }.toThrow<IllegalArgumentException> {
                                message { contains(clazz.name) }
                            }
                        }
                    }
                }
            }
        }

        given("another type") {
            it("returns `null`") {
                val result = testee.create(PolymorphicAdapterFactorySpec::class.java, mutableSetOf(), moshi)
                assert(result).toBe(null)
            }
        }

        given("the correct type") {
            it("returns an adapter") {
                val result = testee.create(ProjectId::class.java, mutableSetOf(), moshi)
                assert(result).notToBeNull {}
            }
        }
    }

    describe("serialization") {
        describe("validation errors") {

            given("anonymous class") {
                it("throws an IllegalArgumentException") {
                    val value = object : ProjectId {
                        override val identifier = "test"
                    }
                    expect {
                        adapter.toJson(value)
                    }.toThrow<IllegalArgumentException> {
                        message { contains(value::class.java.name) }
                    }
                }
            }
        }

        on("serialize ${DummyProjectId::class.java.simpleName}") {
            val result = adapter.toJson(DummyProjectId("test"))
            it("contains the full name of the type") {
                assert(result).contains(typeDummy)
            }
            it("contains ${DummyProjectId::identifier.name}") {
                assert(result).contains(""""identifier":"test"""")
            }
        }
    }

    describe("deserialization") {
        describe("validation errors") {

            given("empty string") {
                it("throws an EOFException") {
                    expect {
                        adapter.fromJson("")
                    }.toThrow<EOFException>{}
                }
            }

            given("json with top-level array instead of object") {
                it("throws an EOFException") {
                    expect {
                        adapter.fromJson("""[{$typeDummy, "$PAYLOAD": "asdf"}]""")
                    }.toThrow<JsonDataException> {
                        message { contains(JsonReader.Token.BEGIN_OBJECT.name) }
                    }
                }
            }

            listOf(PAYLOAD, notType).forEach { wrongType ->
                given("json with field `$wrongType` as first field instead of `$TYPE`") {
                    it("throws an IllegalArgumentException containing expected and given field name") {
                        expect {
                            adapter.fromJson("""{"$wrongType": 1, "$PAYLOAD": "bla"}""")
                        }.toThrow<IllegalArgumentException> {
                            message { contains("Expected: $TYPE", "Given: $wrongType") }
                        }
                    }
                }
            }

            listOf(TYPE, notType).forEach { wrongType ->
                given("json with field `$wrongType` as second field instead of `$PAYLOAD`") {
                    it("throws an IllegalArgumentException containing expected and given field name") {
                        expect {
                            adapter.fromJson("""{$typeDummy, "$wrongType": 1}""")
                        }.toThrow<IllegalArgumentException> {
                            message { contains("Expected: $PAYLOAD", "Given: $wrongType") }
                        }
                    }
                }
            }

            given("json with unknown class as $TYPE") {
                it("throws an ClassNotFoundException") {
                    expect {
                        adapter.fromJson("""{"$TYPE": "com.example.AnUnknownType"}""")
                    }.toThrow<ClassNotFoundException>{}
                }
            }

            given("json with another type than the one we want to deserialize") {
                it("throws an IllegalArgumentException, containing both, given type and expected abstract type") {
                    expect {
                        adapter.fromJson("""{"$TYPE": "${PolymorphicAdapterFactorySpec::class.java.name}"}""")
                    }.toThrow<IllegalArgumentException> {
                        message {
                            contains(
                                "Expected: ${ProjectId::class.java.name}",
                                "Given: ${PolymorphicAdapterFactorySpec::class.java.name}"
                            )
                        }
                    }
                }
            }

            given("json with empty $PAYLOAD") {
                it("throws a JsonDataException") {
                    expect {
                        adapter.fromJson("""{$typeDummy, "$PAYLOAD":{}}""")
                    }.toThrow<JsonDataException> {
                        message { contains("Required value 'identifier' missing at \$.$PAYLOAD") }
                    }
                }
            }

            given("json with incomplete type (not all required fields are set)") {
                it("throws a JsonDataException") {
                    expect {
                        adapter.fromJson("""{$typeDummy, "$PAYLOAD":{"version":"1.0"}}""")
                    }.toThrow<JsonDataException> {
                        message { contains("Required value 'identifier' missing") }
                    }
                }
            }
        }

        val identifier = """com.example:example"""
        val version = "2.0"
        mapOf(
            "expected order" to """"identifier":"$identifier", "version": "$version"""",
            "flipped order" to """"version": "$version", "identifier":"$identifier""""
        ).forEach { (order, fields) ->
            on("deserialize ${DummyProjectId::class.java.simpleName} fields in $order") {

                val result = adapter.fromJson("""{$typeDummy, "$PAYLOAD": {$fields}}""")

                it("is not `null` and identifier is set") {
                    assert(result).notToBeNull {
                        property(subject::identifier).toBe(identifier)
                    }
                }
            }
        }
    }

    describe("serialize and deserialize (round-trip)") {
        val original = DummyProjectId("test")
        val json = adapter.toJson(original)
        val result = adapter.fromJson(json)
        on("serialize and deserialize") {
            it("it is an equal object") {
                assert(result).notToBeNull { toBe(original) }
            }
            it("it is twice the same JSON if serialized again") {
                val jsonResult = adapter.toJson(result)
                assert(jsonResult).toBe(json)
            }
        }
    }
})

