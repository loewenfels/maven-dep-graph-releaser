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
            maxReleaseTimeInSeconds: Int,
            poolEverySecond: Int
        ) {
            it("throws an IllegalArgumentException in case the $prop $cause") {
                expect {
                    RemoteJenkinsM2Releaser(
                        { OkHttpClient() },
                        jenkinsBaseUrl,
                        jenkinsUsername,
                        jenkinsPassword,
                        maxTriggerRetries,
                        maxReleaseTimeInSeconds,
                        poolEverySecond,
                        mapOf()
                    )
                }.toThrow<IllegalArgumentException> { message { contains(prop) } }
            }
        }

        testMisconfiguration(
            "jenkinsBaseUrl", "does not start with http",
            "ftp://asdf", "user", "password", 1, 1, 1
        )
        testMisconfiguration(
            "jenkinsUsername", "is blank",
            "http://asdf", "", "password", 1, 1, 1
        )
        testMisconfiguration(
            "jenkinsPassword", "is blank",
            "http://asdf", "user", "", 1, 1, 1
        )
        testMisconfiguration(
            "maxTriggerTries", "is less than 1",
            "http://asdf", "user", "password", 0, 1, 1
        )
        testMisconfiguration(
            "maxReleaseTimeInSeconds", "is less than 1",
            "http://asdf", "user", "password", 1, 0, 1
        )
        testMisconfiguration(
            "pollEverySecond", "is less than 1",
            "http://asdf", "user", "password", 1, 1, 0
        )
    }

    val jobName = "testJob"
    describe("error cases trigger") {
        given("response is always 500") {
            context("maxTriggerTries 1") {
                it("calls httpClient only once and throws an IllegalStateException") {
                    val httpClient = createFailingHttpClient()
                    val testee = createTestee(httpClient, 1)
                    expect {
                        testee.release(jobName, "1.0.0", "1.2.0")
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
                        testee.release(jobName, "1.0.0", "1.2.0")
                    }.toThrow<IllegalStateException> {
                        message { contains(jobName, "2 attempts") }
                    }
                    verify(httpClient, times(2)).newCall(any())
                }
            }
        }

        given("response OK but body null") {
            context("maxTriggerTries 2") {
                it("calls httpClient only once throws an IllegalStateException") {
                    val httpClient = createOkHttpClientWithBody(null)
                    val testee = createTestee(httpClient, 2)
                    expect {
                        testee.release(jobName, "1.0.0", "1.2.0")
                    }.toThrow<IllegalStateException> {
                        message { contains(jobName, "Body of the response was null") }
                    }
                    verify(httpClient, times(1)).newCall(any())
                }
            }
        }

        given("response OK but does not contain build number") {
            it("throws an IllegalStateException") {
                val response = "<html></html>"
                val httpClient = createOkHttpClientWithBody(createBody(response))
                val testee = createTestee(httpClient, 2)
                expect {
                    testee.release(jobName, "1.0.0", "1.2.0")
                }.toThrow<IllegalStateException> {
                    message { contains(jobName, "Could not find the build number", "<nothing found", response) }
                }
            }
        }
    }

    describe("error cases polling") {
        given("maxReleaseTimeInSeconds is 1 second and no success after 1 seconds") {
            it("throws an IllegalStateException, mentioning that timeout was reached") {
                val httpClient = createOkHttpClientWithBody(
                    createBody(createHtml(jobName, 12)),
                    createBody("not yet available"),
                    createBody("<result>SUCCESS</result>")
                )
                val testee = createTestee(httpClient, 1)
                expect {
                    testee.release(jobName, "1.0.0", "1.2.0")
                }.toThrow<IllegalStateException> { message { contains(jobName, "Waited at least 1 seconds") } }
            }
        }

        given("result is FAILURE") {
            it("throws an IllegalStateException, mentioning the status") {
                val httpClient = createOkHttpClientWithBody(
                    createBody(createHtml(jobName, 12)),
                    createBody("<result>FAILURE</result>")
                )
                val testee = createTestee(httpClient, 1)
                expect {
                    testee.release(jobName, "1.0.0", "1.2.0")
                }.toThrow<IllegalStateException> { message { contains(jobName, "not SUCCESS but FAILURE") } }
            }
        }
    }

    describe("happy case") {
        it("pools with the returned buildNumber and does not throw if result is SUCCESS") {
            val buildNumber = 12
            val response = createHtml(jobName, buildNumber)
            val httpClient = createOkHttpClientWithBody(createBody(response), createBody("<result>SUCCESS</result>"))
            val testee = createTestee(httpClient, 1)
            testee.release(jobName, "1.0.0", "1.2.0")
            val captor = argumentCaptor<Request>()
            verify(httpClient, times(2)).newCall(captor.capture())
            assert(captor.secondValue.toString()).contains("$HOST/job/$jobName/$buildNumber/api/xml?xpath=/*/result")
        }
    }
})

private fun createHtml(jobName: String, buildNumber: Int): String {
    return "<div id=\"buildHistoryPage\"><tr><td class=\"build-row-cell\">" +
        "<a href=\"jenkins/job/$jobName/$buildNumber/console\"><img src=\"asdf/plugin/m2release\">" +
        "</td><tr>"
}

private fun createBody(response: String): ResponseBody {
    return mock {
        val bufferedSource = mock<BufferedSource> {
            on { readString(any()) }.thenReturn(response)
        }
        on { source() }.thenReturn(bufferedSource)
    }
}

private fun createFailingHttpClient() = createHttpClient(500, "Internal Server Error", null)

private fun createOkHttpClientWithBody(body: ResponseBody?) = createHttpClient(200, "OK", body)
private fun createOkHttpClientWithBody(vararg bodies: ResponseBody): OkHttpClient {
    return mock {
        val itr = bodies.iterator()
        var stub = on { newCall(any()) }
        do {
            val body = itr.next()
            stub = stub.then {
                createCall(it, 200, "OK", body)
            }
        } while (itr.hasNext())
    }
}

private fun createHttpClient(statusCode: Int, message: String, body: ResponseBody?): OkHttpClient {
    return mock {
        on { newCall(any()) }.then {
            createCall(it, statusCode, message, body)
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
    val call = mock<Call> {
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
    return call
}

private fun createTestee(httpClient: OkHttpClient, maxTriggerTries: Int) =
    RemoteJenkinsM2Releaser({ httpClient }, HOST, "user", "password", maxTriggerTries, 1, 1, mapOf())

private const val HOST = "http://asdf"
