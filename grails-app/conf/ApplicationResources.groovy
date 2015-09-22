modules = {
    sockets {
        dependsOn 'bootstrap'
        resource url: [dir: 'js', file: 'sockjs.js']
        resource url: [dir: 'js', file: 'stomp.js']
    }
}