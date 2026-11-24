package ma.electricwave

import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * A site's name, location and photo belong to the user looking at it, not to the site.
 *
 * That is why they live in their own table keyed on user *and* access key rather than as
 * columns on the key: a site is shared by everyone linked to it, and one technician's
 * "Atelier nord" is another's "Kammah 3". The test that earns its place here is the one
 * asserting a rename stays invisible to the other user - the rest is plumbing around it.
 *
 * Unset fields come back as JSON null rather than absent, because nothing configures
 * Jackson to drop them, so "not set" is asserted with isEmpty() rather than
 * doesNotExist().
 */
@MobileApiIntegrationTest
class SitePreferenceIntegrationTest : ApiTestSupport() {

    @Test
    fun `a rename comes back on the next listing`() {
        val resource = uniqueResource()
        val key = uniqueKey()
        val id = createAccessKey(resource, key)
        val client = clientHolding(key, "rename")

        patchSite(client, id, """{"name":"Atelier nord"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.resource").value(resource))
            .andExpect(jsonPath("$.name").value("Atelier nord"))

        listKeys(client)
            .andExpect(jsonPath("$[0].resource").value(resource))
            .andExpect(jsonPath("$[0].name").value("Atelier nord"))
    }

    @Test
    fun `one user's rename is invisible to another on the same site`() {
        val resource = uniqueResource()
        val key = uniqueKey()
        val id = createAccessKey(resource, key)

        // Both users link the same site: claimKey's conflict check is per-user, so this
        // is a supported state and exactly the one the per-user table exists for.
        val first = clientHolding(key, "first")
        val second = clientHolding(key, "second")

        patchSite(first, id, """{"name":"Kammah 3","addressDetail":"Targa"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Kammah 3"))

        // The second user is looking at the same installation and must see none of it.
        listKeys(second)
            .andExpect(jsonPath("$[0].resource").value(resource))
            .andExpect(jsonPath("$[0].name").isEmpty)
            .andExpect(jsonPath("$[0].addressDetail").isEmpty)
    }

    @Test
    fun `a blank value clears a field and an absent one is left alone`() {
        val key = uniqueKey()
        val id = createAccessKey(uniqueResource(), key)
        val client = clientHolding(key, "clear")

        patchSite(client, id, """{"name":"Temporary","addressDetail":"Targa"}""")
            .andExpect(status().isOk)

        // Only the name is sent, and blank. The address is not mentioned and survives.
        patchSite(client, id, """{"name":"  "}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").isEmpty)
            .andExpect(jsonPath("$.addressDetail").value("Targa"))
    }

    @Test
    fun `a site the caller has not linked is not found`() {
        val id = createAccessKey(uniqueResource(), uniqueKey())

        val email = uniqueEmail("stranger")
        registerAndVerify(email)
        val stranger = login(email).accessToken

        // 404 rather than 403: whether a site exists is not something to leak to someone
        // who was never given it.
        patchSite(stranger, id, """{"name":"Not mine"}""")
            .andExpect(status().isNotFound)
    }

    @Test
    fun `region, detail and pin are stored together`() {
        val key = uniqueKey()
        val id = createAccessKey(uniqueResource(), key)
        val client = clientHolding(key, "location")

        patchSite(
            client,
            id,
            """{"region":"Marrakech,Marrakech-Safi,Morocco","addressDetail":"Targa",""" +
                """"latitude":31.6295,"longitude":-7.9811}"""
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.region").value("Marrakech,Marrakech-Safi,Morocco"))
            .andExpect(jsonPath("$.addressDetail").value("Targa"))
            .andExpect(jsonPath("$.latitude").value(31.6295))
            .andExpect(jsonPath("$.longitude").value(-7.9811))

        listKeys(client)
            .andExpect(jsonPath("$[0].latitude").value(31.6295))
    }

    @Test
    fun `a pin outside the world is rejected`() {
        val key = uniqueKey()
        val id = createAccessKey(uniqueResource(), key)
        val client = clientHolding(key, "badpin")

        patchSite(client, id, """{"latitude":91.0,"longitude":0.0}""")
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `an admin can name a site they are not linked to`() {
        val resource = uniqueResource()
        val key = uniqueKey()
        val id = createAccessKey(resource, key)
        val client = clientHolding(key, "adminside")
        val admin = adminLogin().accessToken

        // Admins are deliberately unlinked from the sites they manage, so before this
        // every one of these was a 404 and the feature was unusable from the account
        // that sees the whole fleet.
        patchSite(admin, id, """{"name":"Fleet label","addressDetail":"Targa"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Fleet label"))

        // Still personal: the client linked to the same site inherits none of it.
        listKeys(client)
            .andExpect(jsonPath("$[0].resource").value(resource))
            .andExpect(jsonPath("$[0].name").isEmpty)
    }

    @Test
    fun `the admin listing carries the admin's own name, not a client's`() {
        val resource = uniqueResource()
        val key = uniqueKey()
        val id = createAccessKey(resource, key)
        val client = clientHolding(key, "adminlist")
        val admin = adminLogin().accessToken

        patchSite(client, id, """{"name":"Client label"}""").andExpect(status().isOk)
        patchSite(admin, id, """{"name":"Fleet label"}""").andExpect(status().isOk)

        // The admin listing is the only place an admin's own labels can appear, since
        // /api/me/keys returns nothing for an account with no links.
        mockMvc.perform(
            get("/api/admin/keys").header(HttpHeaders.AUTHORIZATION, "Bearer $admin")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.resource=='$resource')].name").value(hasItem("Fleet label")))
            .andExpect(jsonPath("$[?(@.resource=='$resource')].addressDetail").value(hasItem(nullValue())))
    }

    // --- helpers ------------------------------------------------------------------

    /** A verified client that has linked [key], returning its access token. */
    private fun clientHolding(key: String, prefix: String): String {
        val email = uniqueEmail(prefix)
        registerAndVerify(email)
        val token = login(email).accessToken
        mockMvc.perform(
            post("/api/me/keys")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"key":"$key"}""")
        ).andExpect(status().isCreated)
        return token
    }

    private fun patchSite(token: String, accessKeyId: Long, body: String): ResultActions =
        mockMvc.perform(
            patch("/api/me/sites/$accessKeyId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )

    private fun listKeys(token: String): ResultActions =
        mockMvc.perform(
            get("/api/me/keys").header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        ).andExpect(status().isOk)

    private fun uniqueResource(): String =
        "site-${UUID.randomUUID().toString().substring(0, 8)}"

    private fun uniqueKey(): String = UUID.randomUUID().toString().replace("-", "")
}
