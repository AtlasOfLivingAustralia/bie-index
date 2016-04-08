package au.org.ala.bie.util

import groovy.transform.Canonical

import java.nio.file.WatchKey

@Canonical class FileWatchRegistration<T> {
    WatchKey wk
    Closure<T> cb
}
