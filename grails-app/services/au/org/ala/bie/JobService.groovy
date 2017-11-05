package au.org.ala.bie

import au.org.ala.bie.util.Job
import grails.transaction.Transactional

class JobService {
    static scope = "singleton"

    def grailsApplication

    Map<String, Job> jobs = [:]

    /**
     * Get a list of jobs, sorted by lifecycle and update time
     */
    List<Job> list() {
        cleanUp()
        def list = new ArrayList<Job>(jobs.values())
        list.sort()
        return list
    }

    /**
     * Get a particular job
     *
     * @param id The job ID
     *
     * @return The job status or null for no found
     */
    def get(String id) {
        return jobs[id]
    }

    /**
     * Cancel a job
     *
     * @param id The job id
     *
     * @return True if the job was cancelled
     */
    synchronized def cancel(String id) {
        Job job = jobs[id]
        if (!job)
            return false
        return job.cancel()
    }

    /**
     * Remove a job
     *
     * @param id The job id
     *
     * @return True if the job was removed
     */
    synchronized def remove(String id) {
        Job job = jobs[id]
        if (job.lifecycle.active)
            return false
        return jobs.remove(id) != null
    }

    /**
     * Check for an existing job that matches the job types
     *
     * @param types The types to check for
     *
     * @return An existing job or null for not found
     */
    Job existing(Set<String> types) {
        cleanUp()
        return jobs.values().find { !it.lifecycle.completed && !types.disjoint(it.types) }
    }

    /**
     * Get the job associated with the current thread.
     *
     * @return The matching job or null for no matching job
     */
    def getCurrent() {
        return jobs.values().find { it.thread == Thread.currentThread() }
    }

    /**
     * Create a new job
     *
     * @param types The job types
     * @param title The job title
     * @param action The action to perform
     *
     * @return
     */
    synchronized Job create(Set<String> types, String title, Closure action) {
        cleanUp()
        Job job = new Job()
        job.types = types
        job.title = title
        jobs[job.id] = job
        job.run(action)
        return job
    }

    /**
     * Check for expired jobs
     */
    synchronized def cleanUp() {
        int cleanupInterval = grailsApplication.config.getProperty("jobs.cleanupInterval", Integer.class, 3600)
        Calendar ec = Calendar.getInstance()
        ec.add(Calendar.SECOND, - cleanupInterval)
        Date expiry = ec.time
        def remove = jobs.values().findAll({ it.lifecycle.completed && it.lastUpdated.before(expiry) && (!it.thread || !it.thread.alive) }).collect({ it.id })
        if (remove) {
            log.debug "Remove expired jobs ${remove}"
            remove.each { jobs.remove(it) }
        }
    }
}
