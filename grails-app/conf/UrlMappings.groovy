class UrlMappings {

	static mappings = {

        "/taxon/$id"(controller: "search", action: "taxon")
        "/species/$id(.$format)?"(controller: "search", action: "taxon")
        "/search(.$format)?"(controller: "search", action: "search")
        "/classification/$id"(controller: "search", action: "classification")
        "/childConcepts/$id"(controller: "search", action: "childConcepts")
        "/imageSearch"(controller: "search", action: "imageSearch")

        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }

        "/"(view:"/index")
        "500"(view:'/error')
	}
}
