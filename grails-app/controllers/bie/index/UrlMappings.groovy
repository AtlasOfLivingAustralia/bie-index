package bie.index

class UrlMappings {

    static mappings = {


        "/species"(controller: "search", action: "taxon")
        "/species/"(controller: "search", action: "taxon")
        "/species/$id**"(controller: "search", action: "taxon")
        "/species/$id**.json"(controller: "search", action: "taxon")
        // Needs to be more specific than the general species URL
        "/species/image/thumbnail/$id**"(controller: "search", action: "imageLinkSearch") { imageType = "thumbnail" }
        "/species/image/small/$id**"(controller: "search", action: "imageLinkSearch") { imageType = "small" }
        "/species/image/large/$id**"(controller: "search", action: "imageLinkSearch") { imageType = "large" }
        "/species/image/bulk"(controller: "search", action: "bulkImageLookup")
        "/species/lookup/bulk(.$format)?"(controller: "search", action: "speciesLookupBulk")
        "/species/shortProfile/$id**"(controller: "search", action: "shortProfile")
        "/species/shortProfile/$id**.json"(controller: "search", action: "shortProfile")
        "/taxon/$id**"(controller: "search", action: "taxon")
        "/guid/$name"(controller: "search", action: "guid")
        "/guid/batch(.$format)?"(controller: "search", action: "getSpeciesForNames")
        "/auto"(controller: "search", action: "auto")
        "/search/auto(.$format)?"(controller: "search", action: "auto")
        "/search(.$format)?"(controller: "search", action: "search")
        "/classification/"(controller: "search", action: "classification")
        "/classification/$id**"(controller: "search", action: "classification")
        "/childConcepts/$id**"(controller: "search", action: "childConcepts")
        "/imageSearch/$id**"(controller: "search", action: "imageSearch")
        "/imageSearch/"(controller: "search", action: "imageSearch")
        "/imageSearch"(controller: "search", action: "imageSearch")
        "/species/guids/bulklookup(.$format)?"(controller: "search", action: "bulkGuidLookup")
        "/download"(controller: "search", action: "download")
        "/habitats"(controller: "search", action: "habitats")
        "/habitats/tree"(controller: "search", action: "habitatTree")
        "/habitat/ids/$guid**"(controller: "search", action: "getHabitatIDs")
        "/habitat/$guid**"(controller: "search", action: "getHabitat")

        "/ranks"(controller: "misc", action: "ranks")

        "/admin"(controller: "admin")
        "/admin/"(controller: "admin")
        "/admin/indexFields(.$format)?"(controller: "misc", action: "indexFields")
        "/indexFields(.$format)?"(controller: "misc", action: "indexFields")

        "/admin/import/$action?/$id?(.$format)?"(controller: "import")

        "/admin/services/$action/$id?"(controller: "importServices")

        "/admin/job(.$format)?"(controller: "job", action: "index")
        "/admin/job/$id(.$format)?"(controller: "job", action: "status")
        "/admin/job/$id/$action(.$format)?"(controller: "job")

        "/ws/subgroups(.json)?"(controller: 'misc', action: 'speciesGroups')
        "/subgroups.json"(controller: 'misc', action: 'speciesGroups')
        "/subgroups"(controller: 'misc', action: 'speciesGroups')
        "/updateImages" (controller: 'misc', action: 'updateImages')

        "/logout/logout" (controller: 'misc', action: 'logout')

        "/"(view: "/index")
        "500"(view: '/error')
    }
}
