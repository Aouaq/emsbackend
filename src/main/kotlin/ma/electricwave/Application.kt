package ma.electricwave

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Scheduling is on for [ma.electricwave.service.SiteMonitorService], which polls every
 * registered site and records threshold crossings. Turn the sweep itself off with
 * `app.monitor.enabled=false` rather than removing this - the tests disable it that way,
 * and so should any deployment that is not meant to be the one watching.
 */
@SpringBootApplication
@EnableScheduling
class Application

fun main(args: Array<String>) {
	runApplication<Application>(*args)
}
