package ma.electricwave.service

import ma.electricwave.domain.Role
import ma.electricwave.dto.AlarmResponse
import ma.electricwave.repository.AccessKeyRepository
import ma.electricwave.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Pushes alarm changes to connected clients over Server-Sent Events.
 *
 * SSE rather than WebSocket because the traffic only goes one way: the server tells the
 * phone something happened. A socket would add a handshake, a framing protocol and a
 * second failure mode to carry messages that are already a plain HTTP response.
 *
 * Connections are indexed by user, so a client receives only alarms for the sites it has
 * claimed. Administrators are the exception: they manage the fleet rather than owning
 * sites, and promotion deliberately clears their links, so they receive every crossing.
 * Nothing is replayed on connect - the feed endpoint is the source of history, and a
 * client that has been away reads it there rather than through a backlog here.
 */
@Service
class AlarmStreamService(
    private val accessKeys: AccessKeyRepository,
    private val users: UserRepository
) {

    private val log = LoggerFactory.getLogger(AlarmStreamService::class.java)

    private val streams = ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>()

    /**
     * Registers a connection.
     *
     * The timeout is deliberately long: a client holding this open is the normal state,
     * not a stuck request. It is not infinite, because a connection the server has lost
     * track of should eventually be reaped rather than held forever.
     */
    fun open(userId: Long): SseEmitter {
        val emitter = SseEmitter(STREAM_TIMEOUT_MS)
        val forUser = streams.computeIfAbsent(userId) { CopyOnWriteArrayList() }
        forUser.add(emitter)

        val drop = Runnable { forUser.remove(emitter) }
        emitter.onCompletion(drop)
        emitter.onTimeout(drop)
        emitter.onError { drop.run() }

        // Something immediately, so a client knows it is connected rather than waiting
        // for the first alarm to find out - which on a healthy fleet could be never.
        try {
            emitter.send(SseEmitter.event().name("ready").data("ok"))
        } catch (ex: IOException) {
            forUser.remove(emitter)
        }
        return emitter
    }

    @EventListener
    fun onAlarmChanged(event: AlarmChangedEvent) {
        val alarm = event.alarm
        val payload = AlarmResponse(
            id = alarm.id,
            resource = event.resource,
            kind = alarm.kind.name,
            severity = AlarmService.severityOf(alarm.kind),
            raisedAt = alarm.raisedAt,
            clearedAt = alarm.clearedAt,
            value = alarm.value,
            threshold = alarm.threshold
        )

        // Linked clients, plus every enabled administrator. The two scopes are the same
        // ones the list endpoints use: `/api/me/alarms` joins `user_access_keys`, while
        // `/api/admin/alarms` answers for the whole fleet.
        for (userId in recipientsFor(alarm.accessKeyId)) {
            send(userId, if (event.raised) "alarm" else "cleared", payload)
        }
    }

    /**
     * Who should hear about an alarm on [accessKeyId].
     *
     * Administrators are included because they are not linked to the sites they manage -
     * promotion clears their links on purpose - so a link-only fan-out leaves the
     * fleet alarm view permanently deaf. The set matters: an admin who has claimed a
     * site since being promoted would otherwise receive the same event twice.
     *
     * Internal rather than private so the audience can be asserted without an emitter.
     * Sending to one needs a live handler, and the rule here is worth testing on its own.
     */
    internal fun recipientsFor(accessKeyId: Long): Set<Long> {
        val linked = accessKeys.findLinkedClients(accessKeyId).map { it.getUserId() }
        val admins = users.findAllByRoleOrderById(Role.ADMIN)
            .filter { it.disabledAt == null }
            .map { it.id }
        return (linked + admins).toSet()
    }

    /**
     * Keeps connections alive through proxies.
     *
     * Render's proxy closes idle connections, and an SSE stream on a quiet fleet is
     * indistinguishable from idle - the healthier the sites, the sooner the stream would
     * be dropped. A comment line costs nothing and is ignored by every SSE client.
     */
    @Scheduled(fixedDelay = HEARTBEAT_MS)
    fun heartbeat() {
        for ((userId, emitters) in streams) {
            for (emitter in emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"))
                } catch (ex: Exception) {
                    emitters.remove(emitter)
                    log.debug("Dropped stale stream for user {}", userId)
                }
            }
        }
    }

    private fun send(userId: Long, name: String, payload: AlarmResponse) {
        val emitters = streams[userId] ?: return
        for (emitter in emitters) {
            try {
                emitter.send(SseEmitter.event().name(name).data(payload))
            } catch (ex: Exception) {
                emitters.remove(emitter)
            }
        }
    }

    private companion object {
        const val STREAM_TIMEOUT_MS = 30L * 60 * 1000
        const val HEARTBEAT_MS = 20L * 1000
    }
}
