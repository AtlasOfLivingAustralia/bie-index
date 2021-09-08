package bie.index

class BootStrap {
    def messageSource

    def init = { servletContext ->
        messageSource.setBasenames(
                "file:///var/opt/atlas/i18n/bie-index/messages",
                "file:///opt/atlas/i18n/bie-index/messages",
                "WEB-INF/grails-app/i18n/messages",
                "classpath:messages"
        )
    }
    def destroy = {
    }
}
