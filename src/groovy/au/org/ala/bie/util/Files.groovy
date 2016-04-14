package au.org.ala.bie.util

import com.github.davidmoten.rx.FileObservable
import rx.Observable
import rx.schedulers.Schedulers

import java.nio.file.WatchEvent

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY

class Files {
    /**
     * Return an Rx Observable that will report *all* changes to the given file path.  The observable is set to
     * subscribe on the Rx IO thread pool and subscribers also observe on the IO thread pool (as presumably the
     * subscriber will want to read the resulting file in some way).
     *
     * If the path doesn't exist when the WatchService is created, it is assumed to be a file and the watch will occur
     * on the parent path with a filter.
     *
     * Only watch events generated *after* the Observable is subscribed to are reported.  To properly close the
     * underlying WatchService the Observalbe must be unsubscribed from.
     *
     * @param path The path to watch
     * @returns An Rx Observable that provides a stream of WatchEvents.
     */
    static Observable<WatchEvent<?>> watch(String path) {
        final file = new File(path)
        return FileObservable.from(file, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE).observeOn(Schedulers.io()).subscribeOn(Schedulers.io())
    }
}
