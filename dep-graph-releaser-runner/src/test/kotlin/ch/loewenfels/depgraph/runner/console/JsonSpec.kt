package ch.loewenfels.depgraph.runner.console

import ch.loewenfels.depgraph.maven.getTestDirectory
import ch.loewenfels.depgraph.runner.Main
import ch.loewenfels.depgraph.runner.commands.Json
import ch.tutteli.atrium.api.cc.en_GB.messageContains
import ch.tutteli.atrium.api.cc.en_GB.toThrow
import ch.tutteli.atrium.expect
import ch.tutteli.niok.absolutePathAsString
import ch.tutteli.spek.extensions.MemoizedTempFolder
import ch.tutteli.spek.extensions.memoizedTempFolder
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

import java.lang.IllegalStateException
import java.nio.file.Paths
import java.util.regex.PatternSyntaxException

class JsonSpec : Spek({
    include(JsonCommandSpec)

    val tempFolder by memoizedTempFolder()

    Main.pathVerifier = object : PathVerifier {
        override fun path(path: String, fileDescription: String) = Paths.get(path)
    }

    fun createArgs(tempFolder: MemoizedTempFolder, mvnIds: String, regex: String, jobMapping: String): Array<String> {
        val jsonFile = tempFolder.tmpDir.resolve("test.json")
        return arrayOf(
            Json.name, mvnIds,
            getTestDirectory("managingVersions/inDependency").absolutePathAsString,
            jsonFile.absolutePathAsString,
            "dgr-updater",
            "dgr-dry-run",
            "^$#https://none.com",
            "[^/]+/[^/]+/.+",
            "^(.*)/\$",
            "https://github.com/$1",
            "${Json.REGEX_PARAMS_ARG}.*#branch.name=master",
            "${Json.DISABLE_RELEASE_FOR}ch.loewenfels.*",
            "${Json.JOB_MAPPING_ARG}$jobMapping",
            "${Json.COMMIT_PREFIX_ARG}[TEST]",
            "${Json.BUILD_WITH_PARAM_JOBS_ARG}$regex"
        )
    }

    fun createArgs(tempFolder: MemoizedTempFolder, mvnIds: String, regex: String): Array<String> =
        createArgs(tempFolder, mvnIds, regex, "com.example:project=ownJobName\n" +
            "com.example:anotherProject=another-project")

    fun createArgs(tempFolder: MemoizedTempFolder, regex: String): Array<String> =
        createArgs(tempFolder, "com.example:a;com.example:b", regex, "com.example:project=ownJobName\n" +
            "com.example:anotherProject=another-project")

    describe("general validation errors"){

        describe("empty mvnIds") {
            it("throws IllegalStateException mentioning at least one id required") {
                val args = createArgs(tempFolder, "", ".#http://")
                expect {
                    dispatch(args, errorHandler, listOf(Json))
                }.toThrow<IllegalStateException> { messageContains("You need to specify at least one mvn project") }
            }
        }

        describe("blank mvnIds") {
            it("throws IllegalStateException mentioning at least one id required") {
                val args = createArgs(tempFolder, " ", ".#http://")
                expect {
                    dispatch(args, errorHandler, listOf(Json))
                }.toThrow<IllegalStateException> { messageContains("You need to specify at least one mvn project") }
            }
        }

        describe("one mvnId without groupId") {
            it("throws IllegalStateException mentioning at least one id required") {
                val args = createArgs(tempFolder, "com:a;com-b", ".#http://")
                expect {
                    dispatch(args, errorHandler, listOf(Json))
                }.toThrow<IllegalStateException> { messageContains("At least one maven project did not have a groupId") }
            }
        }


        describe("one mvnId with two colon") {
            it("throws IllegalStateException mentioning at least one id required") {
                val args = createArgs(tempFolder, "com:a;com:b;com:c:d", ".#http://")
                expect {
                    dispatch(args, errorHandler, listOf(Json))
                }.toThrow<IllegalStateException> { messageContains("At least one maven project was invalid, had more than one : in its identifier") }
            }
        }


        describe("non-existing directory") {
            it("throws IllegalStateException mentioning that the directory does not exist") {
                val args = createArgs(tempFolder, "com:a;com:b", ".#http://")
                args[2] = args[2].replace("inDependency", "nonExistingFolder")
                expect {
                    dispatch(args, errorHandler, listOf(Json))
                }.toThrow<IllegalStateException> { messageContains("The given directory to analyse does not exist.") }
            }
        }
    }

    describe(Json.JOB_MAPPING_ARG) {

        describe("happy case") {

            describe("empty param") {
                it("does not throw IllegalArgumentException") {
                    val args = createArgs(tempFolder, "com.example:a;com.example:b", ".*#maven#rel;nextDev;add", "")
                    dispatch(args, errorHandler, listOf(Json))
                }
            }

            describe("empty line") {
                it("does not throw IllegalArgumentException") {
                    val args = createArgs(tempFolder, "com.example:a;com.example:b", ".*#maven#rel;nextDev;add", "com:job=job2\n\ncom:job2=job3")
                    dispatch(args, errorHandler, listOf(Json))
                }
            }
        }

        describe("validation errors") {

            describe("missing :") {
                it("throws IllegalArgumentException") {
                    val args = createArgs(tempFolder, "com.example:a;com.example:b", ".*#maven#rel;nextDev;add", "job=job2")
                    expect {
                        dispatch(args, errorHandler, listOf(Json))
                    }.toThrow<IllegalArgumentException> { messageContains("At least one groupId and artifactId is erroneous, does not contain a `:`.") }
                }
            }

            describe("missing =") {
                it("throws IllegalArgumentException") {
                    val args = createArgs(tempFolder, "com.example:a;com.example:b", ".*#maven#rel;nextDev;add", "com:job,job2")
                    expect {
                        dispatch(args, errorHandler, listOf(Json))
                    }.toThrow<IllegalArgumentException> { messageContains("At least one mapping has no groupId and artifactId defined.") }
                }
            }

            describe("missing = in second line") {
                it("throws IllegalArgumentException") {
                    val args = createArgs(tempFolder, "com.example:a;com.example:b", ".*#maven#rel;nextDev;add", "com:job=job2\ncom:job2,job3")
                    expect {
                        dispatch(args, errorHandler, listOf(Json))
                    }.toThrow<IllegalArgumentException> { messageContains("At least one mapping has no groupId and artifactId defined.") }
                }
            }

            describe("missing = in third line") {
                it("throws IllegalArgumentException") {
                    val args = createArgs(tempFolder, "com.example:a;com.example:b", ".*#maven#rel;nextDev;add", "com:job=job2\n\ncom:job2,job3")
                    expect {
                        dispatch(args, errorHandler, listOf(Json))
                    }.toThrow<IllegalArgumentException> { messageContains("At least one mapping has no groupId and artifactId defined.") }
                }
            }

            describe("no job name defined") {
                it("throws IllegalArgumentException") {
                    val args = createArgs(tempFolder, "com.example:a;com.example:b", ".*#maven#rel;nextDev;add", "com:job=job1\ncom:job2=")
                    expect {
                        dispatch(args, errorHandler, listOf(Json))
                    }.toThrow<IllegalArgumentException> { messageContains("At least one groupId and artifactId is erroneous, has no job name defined.") }
                }
            }

            describe("job duplicate") {
                it("throws IllegalArgumentException") {
                    val args = createArgs(tempFolder, "com.example:a;com.example:b", ".*#maven#rel;nextDev;add", "com:job=job1\ncom:job=job2")
                    expect {
                        dispatch(args, errorHandler, listOf(Json))
                    }.toThrow<IllegalArgumentException> { messageContains("At least one jobMapping is a duplicate.") }
                }
            }
        }
    }

    describe(Json.BUILD_WITH_PARAM_JOBS_ARG) {

        describe("validation errors") {

            describe("invalid regex") {
                it("throws PatternSyntaxException") {
                    val args = createArgs(tempFolder, "(shouldCloseParenthesis#query#rel;next")
                    expect {
                        dispatch(args, errorHandler, listOf(Json))
                    }.toThrow<PatternSyntaxException> {}
                }
            }

            describe("invalid format") {
                it("throws IllegalArgumentException mentioning allowed formats") {
                    val args = createArgs(tempFolder, ".*#noFormat#rel;next")
                    expect {
                        dispatch(args, errorHandler, listOf(Json))
                    }.toThrow<IllegalArgumentException> {
                        messageContains("Illegal format `noFormat`", "query", "maven")
                    }
                }
            }

            describe("nextDev param name not provided") {
                it("throws IllegalArgumentException mentioning not enough names") {
                    val args = createArgs(tempFolder, ".*#query#rel")
                    expect {
                        dispatch(args, errorHandler, listOf(Json))
                    }.toThrow<IllegalArgumentException> {
                        messageContains("requires 2", "1 given")
                    }
                }
            }

            describe("too many params provided for format query") {
                it("throws IllegalArgumentException mentioning not enough names") {
                    val args = createArgs(tempFolder, ".*#query#rel;nextDev;")
                    expect {
                        dispatch(args, errorHandler, listOf(Json))
                    }.toThrow<IllegalArgumentException> {
                        messageContains("requires 2", "3 given")
                    }
                }
            }

            describe("additional param name not provided for format maven") {
                it("throws IllegalArgumentException mentioning not enough names") {
                    val args = createArgs(tempFolder, ".*#maven#rel;nextDev")
                    expect {
                        dispatch(args, errorHandler, listOf(Json))
                    }.toThrow<IllegalArgumentException> {
                        messageContains("requires 3", "2 given")
                    }
                }
            }

            describe("too many params provided for format maven") {
                it("throws IllegalArgumentException mentioning not enough names") {
                    val args = createArgs(tempFolder, ".*#maven#rel;nextDev;add;somethingElse")
                    expect {
                        dispatch(args, errorHandler, listOf(Json))
                    }.toThrow<IllegalArgumentException> {
                        messageContains("requires 3", "4 given")
                    }
                }
            }
        }
    }
}) {
    object JsonCommandSpec : CommandSpec(
        Json,
        ::getNotEnoughArgs,
        ::getTooManyArgs,
        10..15
    )

    companion object {
        fun getNotEnoughArgs(tempFolder: MemoizedTempFolder): Array<out String> {
            val jsonFile = tempFolder.tmpDir.resolve("test.json")
            return arrayOf(
                Json.name, "com.example:a;com:b;com:c;com:d",
                getTestDirectory("managingVersions/inDependency").absolutePathAsString,
                jsonFile.absolutePathAsString,
                "dgr-updater",
                "dgr-dry-run",
                "^$#none",
                "[^/]+/[^/]+/.+",
                "^(.*)/\$"
                //the RELATIVE_PATH_TO_GIT_REPO_REPLACEMENT is required as well
                //"https://github.com/$1"
            )
        }

        fun getTooManyArgs(tempFolder: MemoizedTempFolder): Array<out String> {
            val jsonFile = tempFolder.tmpDir.resolve("test.json")
            return arrayOf(
                Json.name, "com.example:a;com:b;com:c",
                getTestDirectory("managingVersions/inDependency").absolutePathAsString,
                jsonFile.absolutePathAsString,
                "dgr-updater",
                "dgr-dry-run",
                "^$#none",
                "[^/]+/[^/]+/.+",
                "^(.*)/\$",
                "https://github.com/$1",
                "${Json.REGEX_PARAMS_ARG}.*#branch.name=master",
                "${Json.DISABLE_RELEASE_FOR}ch.loewenfels.*",
                "${Json.JOB_MAPPING_ARG}com.example.project=ownJobName|com.example.anotherProject=another-project",
                "${Json.COMMIT_PREFIX_ARG}[TEST]",
                "${Json.BUILD_WITH_PARAM_JOBS_ARG}test",
                "unexpectedAdditionalArg"
            )
        }
    }
}
