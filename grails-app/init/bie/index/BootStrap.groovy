package bie.index

import grails.util.Holders
import org.apache.commons.lang3.time.DateUtils
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

class BootStrap {
    def messageSource
    ThreadPoolTaskScheduler threadPoolTaskScheduler
    def importService

    def init = { servletContext ->
        messageSource.setBasenames(
                "file:///var/opt/atlas/i18n/bie-index/messages",
                "file:///opt/atlas/i18n/bie-index/messages",
                "WEB-INF/grails-app/i18n/messages",
                "classpath:messages"
        )

        Date weeklyStart = new Date(hours: Integer.parseInt(Holders.config.import.dailyRunHour as String))
        while(weeklyStart.day != Integer.parseInt(Holders.config.import.weeklyRunDay as String) || weeklyStart.before(new Date())) {
            weeklyStart = DateUtils.addDays(weeklyStart, 1)
        }

        threadPoolTaskScheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            void run() {
                importService.importAll(importService.importWeeklySequence)
            }
        }, weeklyStart, 7*24*60*60*1000)

        Date dailyStart = new Date(hours: Integer.parseInt(Holders.config.import.dailyRunHour as String))
        while(dailyStart.before(new Date())) {
            dailyStart = DateUtils.addDays(dailyStart, 1)
        }

        threadPoolTaskScheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            void run() {
                importService.importAll(importService.importDailySequence)
            }
        }, dailyStart, 24*60*60*1000)
    }
    def destroy = {
    }
}
