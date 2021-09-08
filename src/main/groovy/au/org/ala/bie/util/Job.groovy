package au.org.ala.bie.util

/**
 * An executing job.
 * <p>
 * Used to coordinate the asynchronous activities of the import controller
 *
 * @author Doug Palmer &lt;Doug.Palmer@csiro.au&gt;
 * @copyright Copyright &copy; 2017 Atlas of Living Australia
 */
class Job implements Comparable<Job> {
    /** The job identifier */
    String id
    /** The types of job that this job is doing */
    Set<String> types
    /** The job title */
    String title
    /** Any informational message */
    String message
    /** The job lifecycle */
    Lifecycle lifecycle
    /** The thread running the job */
    Thread thread
    /** The time of the last update */
    Date lastUpdated

    /**
     * Construct a job with a new ID and an empty type set
     */
    Job() {
        id = UUID.randomUUID().toString()
        lifecycle = Lifecycle.STARTING
        types = [] as Set
    }

    Lifecycle getLifecycle() {
        return lifecycle
    }

    /**
     * Set the current lifcycle and update the datestamp
     *
     * @param lifecycle The new lifecycle state
     */
    synchronized void setLifecycle(Lifecycle lifecycle) {
        this.lifecycle = lifecycle
        this.lastUpdated = new Date()
    }

    /**
     * Run an action in the job
     *
     * @param closure The action to perform
     *
     */
    def run(Closure closure) {
        thread = Thread.start {
            try {
                lifecycle = Lifecycle.RUNNING
                closure.call()
                if (lifecycle == Lifecycle.CANCELLING)
                    lifecycle = Lifecycle.CANCELLED
                if (lifecycle == Lifecycle.RUNNING)
                    lifecycle = Lifecycle.FINISHED
            } catch (Exception ex) {
                if (lifecycle == Lifecycle.CANCELLING)
                    lifecycle = Lifecycle.CANCELLED
                if (lifecycle.active) {
                    lifecycle = Lifecycle.ERROR
                    message = ex.message
                }
            }
            thread = null
        }
    }

    /**
     * Has this job been cancelled?
     *
     * @return True if
     */
    boolean isCancelled() {
        return lifecycle == Lifecycle.CANCELLING || lifecycle == Lifecycle.CANCELLED
    }

    /**
     * Cancel the job.
     * <p>
     * Note that a job's code needs to check to see whether the job has been cancelled.
     * Nuthin' we can do about that.
     * </p>
     *
     * @return True if the jobs was cancelled
     */
    synchronized boolean cancel() {
        if (lifecycle == Lifecycle.STARTING || lifecycle == Lifecycle.RUNNING) {
            lifecycle = Lifecycle.CANCELLING
            return true
        }
        return false
    }

    /**
     * Jobs compare on lifecycle and then last updated.
     *
     * @param o The other job
     *
     * @return The comparison
     */
    @Override
    int compareTo(Job o) {
        int o1 = lifecycle.order
        int o2 = o.lifecycle.order
        if (o1 != o2)
            return o1 - o2
        return o.lastUpdated.compareTo(lastUpdated)
    }

    /**
     * Get a status report for the job
     */
    def status() {
        return [
                id: id,
                active: lifecycle.active,
                success: lifecycle.success,
                completed: lifecycle.completed,
                lifecycle: lifecycle.name(),
                title: title,
                message: message,
                lastUpdated: lastUpdated
        ]
    }

    enum Lifecycle {
        STARTING(true, true, false, 10),
        RUNNING(true, true, false, 20),
        FINISHED(false, true, true, 30),
        CANCELLING(false, false, false, 40),
        CANCELLED(false, false, true, 50),
        ERROR(false, false, true, 0)

        int order
        boolean active
        boolean completed
        boolean success
        
        Lifecycle(boolean active, boolean success, boolean completed, int order) {
            this.active = active
            this.success = success
            this.completed = completed
            this.order = order
        }
     }
}
