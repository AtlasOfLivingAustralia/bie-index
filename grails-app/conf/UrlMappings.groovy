class UrlMappings {

	static mappings = {

        "/taxon/$id"(controller: "search", action: "taxon")
        "/species"(controller: "search", action: "taxon")
        "/species/"(controller: "search", action: "taxon")
        "/species/$id"(controller: "search", action: "taxon")
        "/species/$id(.json)?"(controller: "search", action: "taxon")
        "/search(.$format)?"(controller: "search", action: "search")
        "/classification/"(controller: "search", action: "classification")
        "/classification/$id"(controller: "search", action: "classification")
        "/childConcepts/$id"(controller: "search", action: "childConcepts")
        "/imageSearch/$id"(controller: "search", action: "imageSearch")
        "/imageSearch/"(controller: "search", action: "imageSearch")
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
