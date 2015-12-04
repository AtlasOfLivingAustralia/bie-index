//includeTargets << grailsScript("_GrailsInit")
//
target(parseHabitatIDs: "The description of the script goes here!") {
//    // TODO: Implement script here
}
//
setDefaultTarget(parseHabitatIDs)

new File("/Users/mar759/Desktop/habitatIDs.txt").readLines().each {
    if(it.trim().length()==1){
        //no parent
        println(it + "\t")
    } else if (!it.trim().contains(".")){
        println(it + "\t" + it.replaceAll("[0-9]{1,}", ""))
    } else if(it.trim().length()>0){
        parts = it.split("\\.")
        if(parts[1].length() > 1){
            println(it + "\t" + it.substring(0, it.length()-1).replaceAll("#", "").replaceAll("x", ""))
        } else {
            println(it + "\t" + parts[0])
        }
    } else {
        println("\t")
    }
}