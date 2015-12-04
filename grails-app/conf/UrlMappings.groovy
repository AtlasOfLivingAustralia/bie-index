class UrlMappings {

	static mappings = {


        "/species"(controller: "search", action: "taxon")
        "/species/"(controller: "search", action: "taxon")
        "/species/$id"(controller: "search", action: "taxon")
        "/species/$id(.json)?"(controller: "search", action: "taxon")
        "/species/shortProfile/$id(.$format)?"(controller: "search", action: "shortProfile")
        "/taxon/$id"(controller: "search", action: "taxon")
        "/guid/$name"(controller: "search", action: "guid")
        "/auto"(controller: "search", action: "auto")
        "/search/auto(.$format)?"(controller: "search", action: "auto")
        "/search(.$format)?"(controller: "search", action: "search")
        "/classification/"(controller: "search", action: "classification")
        "/classification/$id"(controller: "search", action: "classification")
        "/childConcepts/$id"(controller: "search", action: "childConcepts")
        "/imageSearch/$id"(controller: "search", action: "imageSearch")
        "/imageSearch/"(controller: "search", action: "imageSearch")
        "/imageSearch"(controller: "search", action: "imageSearch")
        "/species/guids/bulklookup(.$format)?"(controller: "search", action: "bulkGuidLookup")
        "/download"(controller: "search", action: "download")
        "/habitats"(controller: "search", action: "habitats")
        "/habitats/tree"(controller: "search", action: "habitatTree")
        "/habitat/$guid"(controller: "search", action: "getHabitat")
        "/habitat/ids/$guid"(controller: "search", action: "getHabitatIDs")

        "/admin"(controller: "admin")
        "/admin/"(controller: "admin")

        "/admin/import/$action?/$id?(.$format)?"(controller: "import")

        "/"(view:"/index")
        "500"(view:'/error')
	}
}
