package ch.loewenfels.depgraph.jenkins

import ch.tutteli.atrium.api.cc.en_UK.contains
import ch.tutteli.atrium.api.cc.en_UK.message
import ch.tutteli.atrium.api.cc.en_UK.toThrow
import ch.tutteli.atrium.assert
import ch.tutteli.atrium.expect
import com.nhaarman.mockito_kotlin.*
import okhttp3.*
import okio.BufferedSource
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.*
import org.mockito.invocation.InvocationOnMock

object RemoteJenkinsM2ReleaserSpec : Spek({

    describe("misconfiguration") {
        fun TestContainer.testMisconfiguration(
            prop: String,
            cause: String,
            jenkinsBaseUrl: String,
            jenkinsUsername: String,
            jenkinsPassword: String,
            maxTriggerRetries: Int,
            pollExecutionEverySecond: Int,
            maxWaitForExecutionInSeconds: Int,
            pollReleaseEverySecond: Int,
            maxReleaseTimeInSeconds: Int
        ) {
            it("throws an IllegalArgumentException in case the $prop $cause") {
                expect {
                    RemoteJenkinsM2Releaser(
                        { OkHttpClient() },
                        jenkinsBaseUrl,
                        jenkinsUsername,
                        jenkinsPassword,
                        maxTriggerRetries,
                        pollExecutionEverySecond,
                        maxWaitForExecutionInSeconds,
                        pollReleaseEverySecond,
                        maxReleaseTimeInSeconds,
                        mapOf()
                    )
                }.toThrow<IllegalArgumentException> { message { contains(prop) } }
            }
        }

        testMisconfiguration(
            "jenkinsBaseUrl", "does not start with http",
            "ftp://asdf", "user", "password", 1, 1, 1, 1, 1
        )
        testMisconfiguration(
            "jenkinsUsername", "is blank",
            "http://asdf", "", "password", 1, 1, 1, 1, 1
        )
        testMisconfiguration(
            "jenkinsPassword", "is blank",
            "http://asdf", "user", "", 1, 1, 1, 1, 1
        )
        testMisconfiguration(
            "maxTriggerTries", "is less than 1",
            "http://asdf", "user", "password", 0, 1, 1, 1, 1
        )
        testMisconfiguration(
            "pollExecutionEverySecond", "is less than 1",
            "http://asdf", "user", "password", 1, 0, 1, 1, 1
        )
        testMisconfiguration(
            "maxWaitForExecutionInSeconds", "is less than 1",
            "http://asdf", "user", "password", 1, 1, 0, 1, 1
        )
        testMisconfiguration(
            "pollReleaseEverySecond", "is less than 1",
            "http://asdf", "user", "password", 1, 1, 1, 0, 1
        )
        testMisconfiguration(
            "maxReleaseTimeInSeconds", "is less than 1",
            "http://asdf", "user", "password", 1, 1, 1, 1, 0
        )
    }

    val jobName = "testJob"
    val releaseVersion = "1.0.0"
    val nextDevVersion = "1.2.0"
    val jobOverviewHtml = createHtml(jobName, 12)
    val queuedItemXml = createQueuedItemXml(releaseVersion, nextDevVersion)

    describe("error cases trigger") {
        given("response is always 500") {
            context("maxTriggerTries 1") {
                it("calls httpClient only once and throws an IllegalStateException") {
                    val httpClient = createFailingHttpClient()
                    val testee = createTestee(httpClient, 1)
                    expect {
                        testee.release(jobName, releaseVersion, nextDevVersion)
                    }.toThrow<IllegalStateException> {
                        message { contains(jobName, "1 attempts") }
                    }
                    verify(httpClient, times(1)).newCall(any())
                }
            }
            context("maxTriggerTries 2") {
                it("calls httpClient twice and throws an IllegalStateException") {
                    val httpClient = createFailingHttpClient()
                    val testee = createTestee(httpClient, 2)
                    expect {
                        testee.release(jobName, releaseVersion, nextDevVersion)
                    }.toThrow<IllegalStateException> {
                        message { contains(jobName, "2 attempts") }
                    }
                    verify(httpClient, times(2)).newCall(any())
                }
            }
        }

        given("response OK but...") {
            group("... fetching queued items fails") {
                context("maxTriggerTries 2") {
                    it("calls httpClient twice throws an IllegalStateException") {
                        val httpClient = createHttpClient(
                            Triple(200, "OK", null), //null as body should not matter for first request
                            Triple(404, "Not Found", null)
                        )
                        val testee = createTestee(httpClient, 2)
                        expect {
                            testee.release(jobName, releaseVersion, nextDevVersion)
                        }.toThrow<IllegalStateException> {
                            message { contains(jobName, "Cannot fetch, job-overview-api-xml response was not OK.") }
                        }
                        verify(httpClient, times(2)).newCall(any())
                    }
                }
            }
            group("... fetching queued items OK...") {
                group("... but body is null...") {
                    it("calls httpClient twice throws an IllegalStateException") {
                        val httpClient = createHttpClient(
                            Triple(200, "OK", createBody("<html></html>")),
                            Triple(200, "OK", null)
                        )
                        val testee = createTestee(httpClient, 2)
                        expect {
                            testee.release(jobName, releaseVersion, nextDevVersion)
                        }.toThrow<IllegalStateException> {
                            message { contains(jobName, "Body of the job-overview-api-xml response was null") }
                        }
                        verify(httpClient, times(2)).newCall(any())
                    }
                }
                group("... maxWaitForExecutionInSeconds is 1 second and cannot retrieve build number after 1 second... ") {
                    it("calls httpClient twice throws an IllegalStateException") {
                        val httpClient = createHttpClient(
                            Triple(200, "OK", createBody("<html></html>")),
                            Triple(200, "OK", queuedItemXml),
                            Triple(404, "404", null)
                        )
                        val testee = createTestee(httpClient, 2)
                        expect {
                            testee.release(jobName, releaseVersion, nextDevVersion)
                        }.toThrow<IllegalStateException> {
                            message { contains(jobName, "Waited at least 1 seconds", "still not queued") }
                        }
                        verify(httpClient, times(3)).newCall(any())
                    }
                }
                group("... no queued item found, extract from html but...") {
                    group("... requesting job overview fails") {
                        it("throws an IllegalStateException") {
                            val httpClient = createHttpClient(
                                Triple(200, "OK", createBody("<html><head></head><body></body></html>")),
                                Triple(200, "OK", createBody("<inQueue>false<inQueue>")),
                                Triple(500, "Internal Server Error", null)
                            )
                            val testee = createTestee(httpClient, 2)
                            expect {
                                testee.release(jobName, releaseVersion, nextDevVersion)
                            }.toThrow<IllegalStateException> {
                                message { contains(jobName, "Cannot fetch, job-overview-html response was not OK.") }
                            }
                            verify(httpClient, times(3)).newCall(any())
                        }
                    }
                    group("... requesting job overview OK but does not contain build number") {
                        it("throws an IllegalStateException") {
                            val response = "<html></html>"
                            val httpClient = createOkHttpClientWithBody(
                                createBody("<html><head></head><body></body></html>"),
                                createBody("<inQueue>false<inQueue>"),
                                createBody(response)
                            )
                            val testee = createTestee(httpClient, 2)
                            expect {
                                testee.release(jobName, releaseVersion, nextDevVersion)
                            }.toThrow<IllegalStateException> {
                                message {
                                    contains(
                                        jobName,
                                        "Could not find the build number",
                                        "<nothing found",
                                        response
                                    )
                                }
                            }
                            verify(httpClient, times(3)).newCall(any())
                        }
                    }
                }
            }

        }

        given("response OK but does not contain build number") {

        }
    }

    describe("error cases polling") {


        given("maxReleaseTimeInSeconds is 1 second and no success after 1 seconds") {
            it("throws an IllegalStateException, mentioning that timeout was reached") {
                val httpClient = createOkHttpClientWithBody(
                    jobOverviewHtml,
                    createBody("no queued item, build already running"),
                    jobOverviewHtml,
                    createBody("not yet available"),
                    createBody("<result>SUCCESS</result>")
                )
                val testee = createTestee(httpClient, 1)
                expect {
                    testee.release(jobName, releaseVersion, nextDevVersion)
                }.toThrow<IllegalStateException> { message { contains(jobName, "Waited at least 1 seconds") } }
            }
        }

        given("result is FAILURE") {
            it("throws an IllegalStateException, mentioning the status") {
                val httpClient = createOkHttpClientWithBody(
                    jobOverviewHtml,
                    createBody("no queued item, build already running"),
                    jobOverviewHtml,
                    createBody("<result>FAILURE</result>")
                )
                val testee = createTestee(httpClient, 1)
                expect {
                    testee.release(jobName, releaseVersion, nextDevVersion)
                }.toThrow<IllegalStateException> { message { contains(jobName, "not SUCCESS but FAILURE") } }
            }
        }
    }

    describe("happy case") {
        it("not yet running, polls queuedItem first and then pools with the returned buildNumber and does not throw if result is SUCCESS") {
            val buildNumber = 12
            val httpClient = createOkHttpClientWithBody(
                jobOverviewHtml,
                queuedItemXml,
                createBody("<number>$buildNumber</number>"),
                createBody("<result>SUCCESS</result>")
            )
            val testee = createTestee(httpClient, 1)
            testee.release(jobName, releaseVersion, nextDevVersion)
            val captor = argumentCaptor<Request>()
            verify(httpClient, times(4)).newCall(captor.capture())
            assert(captor.lastValue.toString()).contains("$HOST/job/$jobName/$buildNumber/api/xml?xpath=/*/result")
        }

        it("is already running, extracts from html and then pools with the returned buildNumber and does not throw if result is SUCCESS") {
            val buildNumber = 12
            val httpClient = createOkHttpClientWithBody(
                jobOverviewHtml,
                createBody("<inQueue>false</inQueue>"),
                jobOverviewHtml,
                createBody("<result>SUCCESS</result>")
            )
            val testee = createTestee(httpClient, 1)
            testee.release(jobName, releaseVersion, nextDevVersion)
            val captor = argumentCaptor<Request>()
            verify(httpClient, times(4)).newCall(captor.capture())
            assert(captor.lastValue.toString()).contains("$HOST/job/$jobName/$buildNumber/api/xml?xpath=/*/result")
        }
    }
})

private fun createQueuedItemXml(releaseVersion: String, nextDevVersion: String): ResponseBody {
    return createBody(
        "<queueItem>\n" +
            "<blocked>false</blocked>\n" +
            "<buildable>true</buildable>\n" +
            "<id>37736</id>\n" +
            "<inQueueSince>1525260858242</inQueueSince>\n" +
            "<params>\n" +
            "branch.name=master MVN_RELEASE_VERSION=$releaseVersion MVN_DEV_VERSION=$nextDevVersion MVN_ISDRYRUN=false\n" +
            "</params>\n" +
            "..." +
            "</queueItem>"
    )
}

private fun createHtml(jobName: String, buildNumber: Int): ResponseBody {
    return createBody("<div id=\"buildHistoryPage\"><tr><td class=\"build-row-cell\">" +
        "<a href=\"jenkins/job/$jobName/$buildNumber/console\"><img src=\"asdf/plugin/m2release\">" +
        "</td><tr>")
}

private fun createBody(response: String): ResponseBody {
    return mock {
        val bufferedSource = mock<BufferedSource> {
            on { readString(any()) }.thenReturn(response)
        }
        on { source() }.thenReturn(bufferedSource)
    }
}

private fun createFailingHttpClient() = createHttpClient(Triple(500, "Internal Server Error", null))

private fun createOkHttpClientWithBody(vararg bodies: ResponseBody?): OkHttpClient {
    return createHttpClient(*bodies.map { Triple(200, "OK", it) }.toTypedArray())
}

private fun createHttpClient(vararg triple: Triple<Int, String, ResponseBody?>): OkHttpClient {
    return mock {
        var stub = on { newCall(any()) }
        triple.forEach { (statusCode, message, body) ->
            stub = stub.then { createCall(it, statusCode, message, body) }
        }
    }
}

private fun createCall(
    it: InvocationOnMock,
    statusCode: Int,
    message: String,
    body: ResponseBody?
): Call {
    val request = it.arguments[0] as Request
    return mock {
        val builder = Response.Builder()
        val response = builder
            .request(request)
            .protocol(Protocol.HTTP_1_0)
            .code(statusCode)
            .message(message)
            .body(body)
            .build()
        on { execute() }.thenReturn(response)
    }
}

private fun createTestee(httpClient: OkHttpClient, maxTriggerTries: Int) =
    RemoteJenkinsM2Releaser(
        { httpClient },
        HOST,
        "user",
        "password",
        maxTriggerTries,
        1,
        1,
        1,
        1,
        mapOf()
    )

private const val HOST = "http://asdf"
