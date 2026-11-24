package ma.electricwave

import com.jayway.jsonpath.JsonPath
import ma.electricwave.domain.Alarm
import ma.electricwave.domain.AlarmKind
import ma.electricwave.repository.AlarmRepository
import ma.electricwave.repository.AlarmThresholdRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@MobileApiIntegrationTest
class AdminAlarmIntegrationTest : ApiTestSupport() {

    @Autowired
    private lateinit var alarmRepository: AlarmRepository

    @Autowired
    private lateinit var alarmThresholdRepository: AlarmThresholdRepository

    @Test
    fun `lists and filters alarms across sites`() {
        val admin = adminLogin()
        val auth = "Bearer ${admin.accessToken}"
        val resourceA = uniqueResource()
        val resourceB = uniqueResource()
        val keyA = createAccessKey(resourceA, uniqueKey())
        val keyB = createAccessKey(resourceB, uniqueKey())

        val now = Instant.now()
        // Two alarms on A in different states, one on B that must not leak into A's page.
        seedAlarm(keyA, AlarmKind.OVER_POWER, now.minusSeconds(10), value = 258_027.0, threshold = 142_000.0)
        seedAlarm(
            keyA,
            AlarmKind.UNDER_VOLTAGE,
            now.minusSeconds(20),
            clearedAt = now.minusSeconds(5),
            value = 210.0,
            threshold = 230.0
        )
        seedAlarm(keyB, AlarmKind.UNDER_VOLTAGE, now.minusSeconds(30), value = 215.0, threshold = 230.0)

        mockMvc.perform(
            get("/api/admin/alarms")
                .param("resource", resourceA)
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].resource").value(resourceA))
            .andExpect(jsonPath("$.content[0].kind").value("OVER_POWER"))
            .andExpect(jsonPath("$.content[0].severity").value("FAULT"))
            .andExpect(jsonPath("$.content[0].active").value(true))
            .andExpect(jsonPath("$.content[0].value").value(258_027.0))
            .andExpect(jsonPath("$.content[0].threshold").value(142_000.0))
            // Newest first: the cleared under-voltage was raised before the open over-power.
            .andExpect(jsonPath("$.content[1].kind").value("UNDER_VOLTAGE"))
            .andExpect(jsonPath("$.content[1].severity").value("WARNING"))
            .andExpect(jsonPath("$.content[1].active").value(false))
            .andExpect(jsonPath("$.content[1].clearedAt").isNotEmpty)

        mockMvc.perform(
            get("/api/admin/alarms")
                .param("resource", resourceA)
                .param("active", "true")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].kind").value("OVER_POWER"))

        mockMvc.perform(
            get("/api/admin/alarms")
                .param("resource", resourceA)
                .param("active", "false")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].kind").value("UNDER_VOLTAGE"))

        mockMvc.perform(
            get("/api/admin/alarms")
                .param("resource", resourceA)
                .param("kind", "UNDER_VOLTAGE")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].kind").value("UNDER_VOLTAGE"))

        // The filter arrives from a query string, so it must not be case-sensitive.
        mockMvc.perform(
            get("/api/admin/alarms")
                .param("resource", resourceA.uppercase())
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(2))

        // An unknown resource is an empty page, not a 404.
        mockMvc.perform(
            get("/api/admin/alarms")
                .param("resource", "no-such-${UUID.randomUUID().toString().substring(0, 8)}")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.totalPages").value(0))

        mockMvc.perform(
            get("/api/admin/alarms")
                .param("size", "500")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.size").value(100))
    }

    @Test
    fun `summarises alarms and reports that the monitor is off`() {
        val admin = adminLogin()
        val auth = "Bearer ${admin.accessToken}"
        val beforeOpen = alarmRepository.countByClearedAtIsNull()
        val beforeTotal = alarmRepository.count()
        val beforeOverPower = alarmRepository.countByClearedAtIsNullAndKind(AlarmKind.OVER_POWER)
        val beforeUnderVoltage = alarmRepository.countByClearedAtIsNullAndKind(AlarmKind.UNDER_VOLTAGE)

        val key = createAccessKey(uniqueResource(), uniqueKey())
        val now = Instant.now()
        seedAlarm(key, AlarmKind.OVER_POWER, now)
        seedAlarm(key, AlarmKind.UNDER_VOLTAGE, now)
        seedAlarm(
            key,
            AlarmKind.OVER_POWER,
            now.minusSeconds(60),
            clearedAt = now.minusSeconds(30)
        )

        val body = mockMvc.perform(
            get("/api/admin/alarms/summary").header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andReturn().response.contentAsString

        val summary = JsonPath.read<Map<String, Any?>>(body, "$")
        assertEquals(beforeOpen + 2, (summary["open"] as Number).toLong())
        assertEquals(beforeOverPower + 1, (summary["overPower"] as Number).toLong())
        assertEquals(beforeUnderVoltage + 1, (summary["underVoltage"] as Number).toLong())
        assertEquals(beforeTotal + 3, (summary["total"] as Number).toLong())
        // Tests run with app.monitor.enabled=false on purpose.
        assertEquals(false, summary["monitoringEnabled"])
        assertTrue(summary["lastSweepAt"] == null, "no sweep should have run")
    }

    @Test
    fun `thresholds start at the defaults and can be changed and reset`() {
        val admin = adminLogin()
        val auth = "Bearer ${admin.accessToken}"
        val resource = uniqueResource()
        val id = createAccessKey(resource, uniqueKey())

        val initial = thresholdFor(resource, auth)
        assertEquals(142_000.0, (initial["maxPower"] as Number).toDouble())
        assertEquals(230.0, (initial["minVoltage"] as Number).toDouble())
        assertEquals(true, initial["usingDefaults"])
        assertEquals(true, initial["isActive"])

        val updated = mockMvc.perform(
            put("/api/admin/alarms/thresholds/$id")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"maxPower":150000,"minVoltage":null}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.accessKeyId").value(id))
            .andExpect(jsonPath("$.resource").value(resource))
            .andExpect(jsonPath("$.maxPower").value(150_000.0))
            .andExpect(jsonPath("$.usingDefaults").value(false))
            .andReturn().response.contentAsString

        // Null means "disabled", so it must survive as null rather than become the default.
        assertNull(JsonPath.read<Map<String, Any?>>(updated, "$")["minVoltage"])
        val row = alarmThresholdRepository.findById(id).orElseThrow()
        assertEquals(150_000.0, row.maxPower)
        assertNull(row.minVoltage)

        val afterSave = thresholdFor(resource, auth)
        assertEquals(false, afterSave["usingDefaults"])
        assertNull(afterSave["minVoltage"])

        mockMvc.perform(
            delete("/api/admin/alarms/thresholds/$id")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isNoContent)
        assertTrue(alarmThresholdRepository.findById(id).isEmpty)

        val afterReset = thresholdFor(resource, auth)
        assertEquals(true, afterReset["usingDefaults"])
        assertEquals(142_000.0, (afterReset["maxPower"] as Number).toDouble())
        assertEquals(230.0, (afterReset["minVoltage"] as Number).toDouble())

        // Resetting a site that already uses the defaults is a no-op, not an error.
        mockMvc.perform(
            delete("/api/admin/alarms/thresholds/$id")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isNoContent)

        // Inactive sites still appear: an administrator tunes before handing a site over.
        val inactiveResource = uniqueResource()
        createAccessKey(inactiveResource, uniqueKey(), active = false)
        assertEquals(false, thresholdFor(inactiveResource, auth)["isActive"])

        mockMvc.perform(
            put("/api/admin/alarms/thresholds/999999")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"maxPower":150000}""")
        ).andExpect(status().isNotFound)

        mockMvc.perform(
            delete("/api/admin/alarms/thresholds/999999")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isNotFound)

        mockMvc.perform(
            put("/api/admin/alarms/thresholds/$id")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"maxPower":-1}""")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `audits threshold changes`() {
        val admin = adminLogin()
        val auth = "Bearer ${admin.accessToken}"
        val resource = uniqueResource()
        val id = createAccessKey(resource, uniqueKey())

        mockMvc.perform(
            put("/api/admin/alarms/thresholds/$id")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"maxPower":150000,"minVoltage":null}""")
        ).andExpect(status().isOk)

        val updated = auditEvents("ALARM_THRESHOLDS_UPDATED", auth, resource)
        assertEquals(1, updated.size)
        assertEquals("admin@electricwave.local", updated.single()["actorEmail"])
        val details = updated.single()["details"] as Map<*, *>
        assertEquals(150_000.0, (details["maxPower"] as Number).toDouble())
        assertNull(details["minVoltage"])

        mockMvc.perform(
            delete("/api/admin/alarms/thresholds/$id")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isNoContent)

        assertEquals(1, auditEvents("ALARM_THRESHOLDS_RESET", auth, resource).size)

        // Nothing was stored, so the second reset has nothing to record.
        mockMvc.perform(
            delete("/api/admin/alarms/thresholds/$id")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isNoContent)
        assertEquals(1, auditEvents("ALARM_THRESHOLDS_RESET", auth, resource).size)
    }

    @Test
    fun `admin alarm endpoints are admin only`() {
        val admin = adminLogin()
        val auth = "Bearer ${admin.accessToken}"
        val id = createAccessKey(uniqueResource(), uniqueKey())

        mockMvc.perform(get("/api/admin/alarms")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/admin/alarms/summary")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/admin/alarms/thresholds")).andExpect(status().isUnauthorized)

        val email = uniqueEmail("alarm-client")
        registerAndVerify(email)
        val clientAuth = "Bearer ${login(email).accessToken}"

        mockMvc.perform(
            get("/api/admin/alarms").header(HttpHeaders.AUTHORIZATION, clientAuth)
        ).andExpect(status().isForbidden)
        mockMvc.perform(
            get("/api/admin/alarms/summary").header(HttpHeaders.AUTHORIZATION, clientAuth)
        ).andExpect(status().isForbidden)
        mockMvc.perform(
            get("/api/admin/alarms/thresholds").header(HttpHeaders.AUTHORIZATION, clientAuth)
        ).andExpect(status().isForbidden)
        mockMvc.perform(
            put("/api/admin/alarms/thresholds/$id")
                .header(HttpHeaders.AUTHORIZATION, clientAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"maxPower":1}""")
        ).andExpect(status().isForbidden)
        mockMvc.perform(
            delete("/api/admin/alarms/thresholds/$id")
                .header(HttpHeaders.AUTHORIZATION, clientAuth)
        ).andExpect(status().isForbidden)

        // The refused writes changed nothing.
        assertTrue(alarmThresholdRepository.findById(id).isEmpty)
    }

    private fun thresholdFor(resource: String, auth: String): Map<String, Any?> {
        val body = mockMvc.perform(
            get("/api/admin/alarms/thresholds").header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andReturn().response.contentAsString
        return JsonPath.read<List<Map<String, Any?>>>(body, "$")
            .single { it["resource"] == resource }
    }

    private fun auditEvents(
        eventType: String,
        auth: String,
        resource: String
    ): List<Map<String, Any?>> {
        val body = mockMvc.perform(
            get("/api/admin/audit")
                .param("eventType", eventType)
                .param("size", "100")
                .header(HttpHeaders.AUTHORIZATION, auth)
        ).andExpect(status().isOk)
            .andReturn().response.contentAsString
        return JsonPath.read<List<Map<String, Any?>>>(body, "$.content")
            .filter { it["resource"] == resource }
    }

    private fun seedAlarm(
        accessKeyId: Long,
        kind: AlarmKind,
        raisedAt: Instant,
        clearedAt: Instant? = null,
        value: Double = 1.0,
        threshold: Double = 1.0
    ): Alarm = alarmRepository.save(
        Alarm(
            accessKeyId = accessKeyId,
            kind = kind,
            raisedAt = raisedAt,
            clearedAt = clearedAt,
            value = value,
            threshold = threshold
        )
    )

    private fun uniqueResource(): String =
        "alarm-${UUID.randomUUID().toString().substring(0, 8)}"

    private fun uniqueKey(): String =
        UUID.randomUUID().toString().replace("-", "")
}
