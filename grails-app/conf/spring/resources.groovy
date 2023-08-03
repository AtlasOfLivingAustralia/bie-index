import au.org.ala.bie.solr.SolrClientBean
import au.org.ala.bie.util.ConservationListsSource
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

// Place your Spring DSL code here
beans = {
    liveSolrClient(SolrClientBean,
            application.config.solr.live.type,
            application.config.solr.live.connection,
            application.config.solr.live.queueSize as Integer,
            application.config.solr.live.threadCount as Integer,
            application.config.solr.live.timeout as Integer
    )
    offlineSolrClient(SolrClientBean,
            application.config.solr.offline.type,
            application.config.solr.offline.connection,
            application.config.solr.offline.queueSize as Integer,
            application.config.solr.offline.threadCount as Integer,
            application.config.solr.offline.timeout as Integer
    )
    updatingLiveSolrClient(SolrClientBean,
            application.config.solr.updatingLive.type,
            application.config.solr.updatingLive.connection,
            application.config.solr.updatingLive.queueSize as Integer,
            application.config.solr.updatingLive.threadCount as Integer,
            application.config.solr.updatingLive.timeout as Integer
    )
    adminSolrClient(SolrClientBean,
            application.config.solr.admin.type,
            application.config.solr.admin.connection,
            application.config.solr.admin.queueSize as Integer,
            application.config.solr.admin.threadCount as Integer,
            application.config.solr.admin.timeout as Integer
    )
    conservationListsSource(ConservationListsSource, application.config.conservationListsUrl)

    threadPoolTaskScheduler(ThreadPoolTaskScheduler)
}
