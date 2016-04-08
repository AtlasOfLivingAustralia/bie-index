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

    final exec = Executors.newSingleThreadExecutor()
    final ws = FileSystems.getDefault().newWatchService()
    Future<?> wt

    final registeredPaths = new ConcurrentHashMap<Path, FileWatchRegistration>()

    @PostConstruct
    def init() {
        wt = exec.submit {
            try {
                log.info("Watch thread started")
                while (!Thread.interrupted()) {
                    log.trace("Watch thread spinning")
                    WatchKey key
                    try {
                        key = ws.poll(25, MILLISECONDS)
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
                        Thread.yield()
                    }
                }
            } catch (Exception e) {
                log.warn("Uncaught exception", e)
            } finally {
                log.info("Watch thread ending")
            }
        }
    }

    private void invokeCallback(FileWatchRegistration fileWatchRegistration, Path path, WatchEvent.Kind kind) {
        try {
            fileWatchRegistration?.cb?.call(path, kind)
        } catch(Exception e) {
            log.error("Uncaught exception invoking callback for ${fileWatchRegistration.wk.watchable()} with $path and $kind", e)
        }
    }

    @PreDestroy
    @Override
    void close() {
        wt.cancel(true)
        ws.close()
        exec.shutdownNow()
    }

    /**
     * Watch a file or directory for changes
     *
     * @param path The path to watch
     * @param cb The callback
     */
    void watch(String path, Closure<Void> cb) {
        log.info("watch $path")
        final f = new File(path)
        final p = f.isDirectory() ? f.toPath() : f.parentFile.toPath()
        final fp = f.toPath()
        final existing = registeredPaths[fp]
        if (existing != null) throw new IllegalArgumentException("$path is already watched")
        log.info("Watching $p")
        final wk = p.register(ws, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
        registeredPaths[fp] = new FileWatchRegistration(wk: wk, cb: cb)
        log.info("Registered ${wk.watchable()} and got validity ${wk.isValid()}")
    }

    /**
     * Stop receiving file watch callbacks for a path
     * @param path The path to stop watching
     */
    void unwatch(String path) {
        final r = registeredPaths.remove(Paths.get(path))
        if (r != null) {
            log.info("$path removed")
            if (registeredPaths.values().count { it.wk == r.wk } == 0) { r.wk.cancel() }
        } else {
            log.info("$path NOT removed")
        }
    }

    /**
     * Remove all registered watches that use the given WatchKey
     * @param wk The watch key to remove watches for
     */
    private void removeWatchKey(WatchKey wk) {
        final i = registeredPaths.entrySet().iterator()
        while (i.hasNext()) {
            final e = i.next()
            if (e.value.wk == wk) {
                log.warn("Automatically removing ${e.key} from file watches")
                i.remove()
            }
        }
    }

}

