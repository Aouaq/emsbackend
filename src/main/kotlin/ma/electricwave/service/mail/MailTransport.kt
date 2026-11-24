package ma.electricwave.service.mail

/**
 * How a message leaves this process.
 *
 * [ma.electricwave.service.MailService] owns what an email says and whether sending is
 * attempted at all; this owns only the wire. The split exists because the two have
 * different availability: SMTP works on a developer machine and is blocked outbound on
 * the deployed platform, where only HTTPS leaves the box. Keeping the boundary this
 * narrow means a hosting provider's egress policy never reaches the code that decides
 * what a verification email contains.
 *
 * Implementations throw on failure rather than returning a status. `MailService.send`
 * already catches and maps to [ma.electricwave.service.MailDelivery.FAILED], so the
 * caller's contract is identical whichever transport is active.
 */
interface MailTransport {
    fun send(from: String, to: String, subject: String, text: String)
}

/**
 * A send the provider actively rejected, as opposed to one that never reached it.
 *
 * Deliberately carries no stack trace. The throw site is one of two transports and the
 * message already names the provider's own error, so the frames add nothing: the
 * 160-frame trace a blocked SMTP connection logged for every registration buried the one
 * line that said why. Faults below the application - a connect timeout, DNS failure, a
 * TLS handshake error - are left to propagate as themselves with their traces intact,
 * because those are the ones where the frames carry the information.
 */
class MailTransportException(message: String) : RuntimeException(message, null, false, false)
