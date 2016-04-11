package au.org.ala.bie

import au.org.ala.bie.util.FileWatchRegistration

import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import static java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * Simplification around the Java 7 WatchService that supports individual files as well as directories
 */
class FileWatchService implements AutoCloseable {

    static transactional = false

    final executor = Executors.newSingleThreadExecutor()
    final watchService = FileSystems.getDefault().newWatchService()

    private Future<?> watchThread
    private final registeredPaths = new ConcurrentHashMap<Path, FileWatchRegistration>()

    @PostConstruct
    def init() {
        watchThread = executor.submit {
            try {
                log.info("File watch thread started")
                while (!Thread.interrupted()) {
                    log.trace("Watch thread spinning")
                    WatchKey key
                    try {
                        key = watchService.poll(25, MILLISECONDS)
                    } catch (InterruptedException e) {
                        log.info("WatchService.poll interrupted")
                        break
                    }

                    if (key != null) {
                        final w = key.watchable()
                        log.debug("File changed: $w")
                        for (e in key.pollEvents()) {
                            final ctx = e.context()
                            final kind = e.kind()
                            if (kind == StandardWatchEventKinds.OVERFLOW) { continue }
                            log.debug("$w ${File.separator} $ctx ($kind)")
                            if (ctx instanceof Path && w instanceof Path) {
                                final path = w.resolve(ctx)
                                log.debug("Resolved path is $path")
                                invokeCallback(registeredPaths[path], path, kind)
                                // The directory for the path may also be registered
                                invokeCallback(registeredPaths[w], path, kind)
                            } else {
                                log.error("$w OR $ctx IS NOT A PATH!")
                            }
                        }
                        final valid = key.reset()
                        if (!valid) {
                            log.warn("WatchKey for $w is no longer valid")
                            removeWatchKey(key)
                        }
                    }
                    Thread.yield()
                }
            } catch (Exception e) {
                log.warn("Uncaught exception", e)
            } finally {
                cancelAllWatches()
                log.info("Watch thread ending")
            }
        }
    }

    private void invokeCallback(FileWatchRegistration fileWatchRegistration, Path path, WatchEvent.Kind kind) {
        try {
            fileWatchRegistration?.callback?.call(path, kind)
        } catch(Exception e) {
            log.error("Uncaught exception invoking callback for ${fileWatchRegistration.watchKey.watchable()} with $path and $kind", e)
        }
    }

    @PreDestroy
    @Override
    void close() {
        watchThread.cancel(true)
        watchService.close()
        executor.shutdownNow()
    }

    /**
     * Watch a file or directory for changes
     *
     * @param path The path to watch
     * @param callback The callback
     */
    void watch(String path, Closure<Void> callback) {
        final file = new File(path)
        final dirPath = file.isDirectory() ? file.toPath().normalize() : file.parentFile.toPath().normalize()
        final filePath = file.toPath().normalize()
        final existing = registeredPaths[filePath]
        if (existing != null) { throw new IllegalArgumentException("$dirPath is already watched") }
        log.debug("Watching $dirPath")
        final watchKey = dirPath.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
        registeredPaths[filePath] = new FileWatchRegistration(watchKey: watchKey, callback: callback)
        log.info("Registered ${watchKey.watchable()} for watching and got validity ${watchKey.isValid()}")
    }

    /**
     * Stop receiving file watch callbacks for a path
     * @param path The path to stop watching
     */
    void unwatch(String path) {
        final r = registeredPaths.remove(Paths.get(path).normalize())
        if (r != null) {
            log.info("Watch for $path removed")
            if (registeredPaths.values().count { it.watchKey == r.watchKey } == 0) {
                log.debug("No other registered paths use the watch key for $path, cancelling the watch key for ${r.watchKey.watchable()}")
                r.watchKey.cancel()
            }
        } else {
            log.warn("Watch for $path NOT removed")
        }
    }

    /**
     * Remove all registered watches that use the given WatchKey
     * @param watchKey The watch key to remove watches for
     */
    private void removeWatchKey(WatchKey watchKey) {
        final i = registeredPaths.entrySet().iterator()
        while (i.hasNext()) {
            final e = i.next()
            if (e.value.watchKey == watchKey) {
                log.warn("Automatically removing ${e.key} from file watches")
                i.remove()
            }
        }
    }

    private void cancelAllWatches() {
        final i = registeredPaths.entrySet().iterator()
        while (i.hasNext()) {
            final e = i.next()
            e.value.watchKey.cancel() // no-op if watch key already cancelled
            i.remove()
        }
    }

}

