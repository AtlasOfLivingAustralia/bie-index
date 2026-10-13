package bie.index

import grails.util.Holders
import groovy.util.logging.Slf4j
import org.apache.commons.lang3.time.DateUtils
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Slf4j
class BootStrap {
    def messageSource
    ThreadPoolTaskScheduler threadPoolTaskScheduler
    def importService
    def jobService

    def init = { servletContext ->
        messageSource.setBasenames(
                "file:///var/opt/atlas/i18n/bie-index/messages",
                "file:///opt/atlas/i18n/bie-index/messages",
                "WEB-INF/grails-app/i18n/messages",
                "classpath:messages"
        )

        if (Holders.config.import.enableTasks) {
            Date dailyStart = new Date(hours: Integer.parseInt(Holders.config.import.dailyRunHour as String))
            while (dailyStart.before(new Date())) {
                dailyStart = DateUtils.addDays(dailyStart, 1)
            }

            threadPoolTaskScheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                void run() {
                    boolean isWeeklyDay = new Date().day == Integer.parseInt(Holders.config.import.weeklyRunDay as String)
                    String[] sequence
                    String title
                    if (isWeeklyDay) {
                        def weeklySteps = importService.importWeeklySequence?.findAll { it != 'swap' } ?: []
                        def dailySteps = importService.importDailySequence?.findAll { it != 'swap' } ?: []
                        def updateSteps = (weeklySteps + dailySteps).unique()
                        def combined = new ArrayList(updateSteps)
                        if (importService.importWeeklySequence?.contains('swap')) {
                            combined << 'swap'
                            // repeat weekly updates after swap to ensure updates are applied to both indexes. no swap this time.
                            combined.addAll(weeklySteps)
                        } else if (importService.importDailySequence?.contains('swap')) {
                            combined << 'swap'
                        }
                        sequence = combined as String[]
                        title = "Scheduled Weekly & Daily Import"
                    } else {
                        sequence = importService.importDailySequence
                        title = "Scheduled Daily Import"
                    }
                    // marker type so any scheduled run blocks the next, whichever sequence it used
                    def types = (sequence as Set) + 'scheduled-import'
                    def running = jobService.existing(types)
                    if (running) {
                        log.warn("Skipping ${title}: a previous job is still running: ${running.title}")
                        return
                    }
                    jobService.create(types, title) {
                        importService.importAll(sequence, false)
                    }
                }
            }, dailyStart, 24 * 60 * 60 * 1000)
        }
    }
    def destroy = {
    }
}
