package ma.electricwave

import com.github.tomakehurst.wiremock.WireMockServer
import com.jayway.jsonpath.JsonPath
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get as wiremockGet
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.http.Fault
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@MobileApiIntegrationTest
class GatewayIntegrationTest : ApiTestSupport() {

    @BeforeEach
    fun resetWireMock() {
        wireMock.resetAll()
    }

    @Test
    fun `proxies the feed list with the stored Emoncms key`() {
        val key = uniqueKey()
        val resource = uniqueResource()
        val tokens = clientWithClaimedKey(resource, key)
        wireMock.stubFor(
            wiremockGet(urlPathEqualTo("/feed/list.json"))
                .withQueryParam("apikey", equalTo(key))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""[{"id":1,"name":"power"}]""")
                )
        )

        mockMvc.perform(
            get("/api/gateway/$resource/feed/list.json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].name").value("power"))

        wireMock.verify(
            getRequestedFor(urlPathEqualTo("/feed/list.json"))
                .withQueryParam("apikey", equalTo(key))
        )
    }

    @Test
    fun `forwards whitelisted feed data parameters`() {
        val key = uniqueKey()
        val resource = uniqueResource()
        val tokens = clientWithClaimedKey(resource, key)
        wireMock.stubFor(
            wiremockGet(urlPathEqualTo("/feed/data.json"))
                .withQueryParam("id", equalTo("12"))
                .withQueryParam("start", equalTo("100"))
                .withQueryParam("end", equalTo("200"))
                .withQueryParam("interval", equalTo("60"))
                .withQueryParam("skipmissing", equalTo("1"))
                .withQueryParam("apikey", equalTo(key))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""[[100,1.5],[160,2.5]]""")
                )
        )

        mockMvc.perform(
            get("/api/gateway/$resource/feed/data.json")
                .param("id", "12")
                .param("start", "100")
                .param("end", "200")
                .param("interval", "60")
                .param("skipmissing", "1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$[1][1]").value(2.5))
    }

    @Test
    fun `proxies the latest value`() {
        val key = uniqueKey()
        val resource = uniqueResource()
        val tokens = clientWithClaimedKey(resource, key)
        wireMock.stubFor(
            wiremockGet(urlPathEqualTo("/feed/timevalue.json"))
                .withQueryParam("id", equalTo("12"))
                .withQueryParam("apikey", equalTo(key))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"id":12,"value":42}""")
                )
        )

        mockMvc.perform(
            get("/api/gateway/$resource/feed/timevalue.json")
                .param("id", "12")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.value").value(42))
    }

    @Test
    fun `rejects unknown parameters including client supplied apikey`() {
        val key = uniqueKey()
        val resource = uniqueResource()
        val tokens = clientWithClaimedKey(resource, key)

        mockMvc.perform(
            get("/api/gateway/$resource/feed/list.json")
                .param("apikey", "attacker-supplied")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))

        mockMvc.perform(
            get("/api/gateway/$resource/feed/list.json")
                .param("node", "1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))

        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/feed/list.json")))
    }

    @Test
    fun `rejects invalid feed data parameters`() {
        val key = uniqueKey()
        val resource = uniqueResource()
        val tokens = clientWithClaimedKey(resource, key)
        val auth = "Bearer ${tokens.accessToken}"

        mockMvc.perform(
            get("/api/gateway/$resource/feed/data.json")
                .param("start", "100").param("end", "200")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            get("/api/gateway/$resource/feed/data.json")
                .param("id", "1").param("start", "200").param("end", "200")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            get("/api/gateway/$resource/feed/data.json")
                .param("id", "1").param("start", "0").param("end", "${401L * 24 * 60 * 60}")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            get("/api/gateway/$resource/feed/data.json")
                .param("id", "1").param("start", "1").param("end", "2").param("interval", "0")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            get("/api/gateway/$resource/feed/data.json")
                .param("id", "1").param("start", "1").param("end", "2").param("skipmissing", "2")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isBadRequest)

        mockMvc.perform(
            get("/api/gateway/$resource/feed/timevalue.json")
                .param("id", "1").param("start", "1")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isBadRequest)

        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/feed/data.json")))
    }

    @Test
    fun `denies access without a link and after unlinking`() {
        val email = uniqueEmail("gateway-denied")
        registerAndVerify(email)
        val tokens = login(email)
        val resource = uniqueResource()

        mockMvc.perform(
            get("/api/gateway/$resource/feed/list.json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))

        val key = uniqueKey()
        val accessKeyId = createAccessKey(resource, key)
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/me/keys")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"key":"$key"}""")
        ).andExpect(status().isCreated)

        wireMock.stubFor(
            wiremockGet(urlPathEqualTo("/feed/list.json"))
                .willReturn(aResponse().withStatus(200).withBody("[]"))
        )
        mockMvc.perform(
            get("/api/gateway/$resource/feed/list.json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isOk)

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/me/keys/$accessKeyId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            get("/api/gateway/$resource/feed/list.json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `maps upstream failures`() {
        val key = uniqueKey()
        val resource = uniqueResource()
        val tokens = clientWithClaimedKey(resource, key)

        wireMock.stubFor(
            wiremockGet(urlPathEqualTo("/feed/list.json"))
                .willReturn(aResponse().withStatus(500).withBody("boom"))
        )
        mockMvc.perform(
            get("/api/gateway/$resource/feed/list.json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"))

        wireMock.resetAll()
        wireMock.stubFor(
            wiremockGet(urlPathEqualTo("/feed/timevalue.json"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
        )
        mockMvc.perform(
            get("/api/gateway/$resource/feed/timevalue.json")
                .param("id", "1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"))

        wireMock.resetAll()
        wireMock.stubFor(
            wiremockGet(urlPathEqualTo("/feed/list.json"))
                .willReturn(aResponse().withStatus(200).withBody("[]").withFixedDelay(11_000))
        )
        mockMvc.perform(
            get("/api/gateway/$resource/feed/list.json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isGatewayTimeout)
            .andExpect(jsonPath("$.code").value("UPSTREAM_TIMEOUT"))

        wireMock.resetAll()
        wireMock.stubFor(
            wiremockGet(urlPathEqualTo("/feed/list.json"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("x".repeat(5 * 1024 * 1024 + 1))
                )
        )
        mockMvc.perform(
            get("/api/gateway/$resource/feed/list.json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.code").value("UPSTREAM_ERROR"))
    }

    @Test
    fun `deactivation and key replacement evict gateway state`() {
        val key = uniqueKey()
        val resource = uniqueResource()
        val tokens = clientWithClaimedKey(resource, key)
        val accessKeyId = JsonPath.read<Number>(
            mockMvc.perform(
                get("/api/me/keys")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
            ).andExpect(status().isOk).andReturn().response.contentAsString,
            "$[0].id"
        ).toLong()
        val admin = adminLogin()
        val adminAuth = "Bearer ${admin.accessToken}"

        wireMock.stubFor(
            wiremockGet(urlPathEqualTo("/feed/list.json"))
                .withQueryParam("apikey", equalTo(key))
                .willReturn(aResponse().withStatus(200).withBody("[]"))
        )
        mockMvc.perform(
            get("/api/gateway/$resource/feed/list.json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isOk)

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/keys/$accessKeyId")
                .header(HttpHeaders.AUTHORIZATION, adminAuth)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"resource":"$resource","description":"Test","isActive":false}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/gateway/$resource/feed/list.json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/keys/$accessKeyId")
                .header(HttpHeaders.AUTHORIZATION, adminAuth)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"resource":"$resource","description":"Test","isActive":true}""")
        ).andExpect(status().isOk)

        val newKey = uniqueKey()
        wireMock.stubFor(
            wiremockGet(urlPathEqualTo("/feed/list.json"))
                .withQueryParam("apikey", equalTo(newKey))
                .willReturn(aResponse().withStatus(200).withBody("""[{"id":2}]"""))
        )
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/keys/$accessKeyId/emoncms-key")
                .header(HttpHeaders.AUTHORIZATION, adminAuth)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"emoncmsKey":"$newKey"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/api/gateway/$resource/feed/list.json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(2))

        wireMock.verify(
            getRequestedFor(urlPathEqualTo("/feed/list.json"))
                .withQueryParam("apikey", equalTo(newKey))
        )
    }

    @Test
    fun `caches identical requests for 750ms`() {
        val key = uniqueKey()
        val resource = uniqueResource()
        val tokens = clientWithClaimedKey(resource, key)
        wireMock.stubFor(
            wiremockGet(urlPathEqualTo("/feed/list.json"))
                .willReturn(aResponse().withStatus(200).withBody("[]"))
        )

        repeat(2) {
            mockMvc.perform(
                get("/api/gateway/$resource/feed/list.json")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
            ).andExpect(status().isOk)
        }
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/feed/list.json")))

        Thread.sleep(900)
        mockMvc.perform(
            get("/api/gateway/$resource/feed/list.json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
        ).andExpect(status().isOk)
        wireMock.verify(2, getRequestedFor(urlPathEqualTo("/feed/list.json")))
    }

    @Test
    fun `coalesces concurrent identical requests`() {
        val key = uniqueKey()
        val resource = uniqueResource()
        val tokens = clientWithClaimedKey(resource, key)
        wireMock.stubFor(
            wiremockGet(urlPathEqualTo("/feed/list.json"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody("[]")
                        .withFixedDelay(300)
                )
        )

        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val futures = (1..2).map {
                executor.submit<Int> {
                    start.await()
                    mockMvc.perform(
                        get("/api/gateway/$resource/feed/list.json")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
                    ).andReturn().response.status
                }
            }
            start.countDown()
            futures.forEach { assert(it.get(10, TimeUnit.SECONDS) == 200) }
        } finally {
            executor.shutdownNow()
        }

        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/feed/list.json")))
    }

    private fun clientWithClaimedKey(resource: String, key: String): TokenPair {
        val email = uniqueEmail("gateway")
        registerAndVerify(email)
        val tokens = login(email)
        createAccessKey(resource, key)
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/me/keys")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.accessToken}")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""{"key":"$key"}""")
        ).andExpect(status().isCreated)
        return tokens
    }

    private fun uniqueResource(): String =
        "g-${UUID.randomUUID().toString().substring(0, 8)}"

    private fun uniqueKey(): String =
        UUID.randomUUID().toString().replace("-", "")

    companion object {
        private val wireMock = WireMockServer(options().dynamicPort())

        @JvmStatic
        @DynamicPropertySource
        fun emoncmsProperties(registry: DynamicPropertyRegistry) {
            if (!wireMock.isRunning) {
                wireMock.start()
            }
            registry.add("app.emoncms.base-url") {
                "http://127.0.0.1:${wireMock.port()}"
            }
        }

        @JvmStatic
        @AfterAll
        fun stopWireMock() {
            wireMock.stop()
        }
    }
}
