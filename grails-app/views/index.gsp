<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="${grailsApplication.config.skin.layout}"/>
    <title>BIE Web services | ${grailsApplication.config.skin.orgNameLong}</title>
</head>
<body>
<div id="page-body" role="main">
    <h1><g:message code="homepage.heading1" /></h1>
    <p class="lead">
        <g:message code="homepage.lead.content" />
    </p>
    <table class="table">
        <tr>
            <td>Taxon details</td>
            <td>GET JSON</td>
            <td><bie:createWsLink>${g.createLink(controller: 'search', action: 'taxon')}/{GUID}</bie:createWsLink></td>
        </tr>
        <tr>
            <td>Taxon details - simple</td>
            <td>GET JSON</td>
            <td><bie:createWsLink>${g.createLink(uri:'/species/shortProfile')}/{GUID}</bie:createWsLink></td>
        </tr>
        <tr>
            <td>Search</td>
            <td>GET JSON</td>
            <td><bie:createWsLink>${g.createLink(controller: 'search', action: 'search')}?q={search terms}</bie:createWsLink></td>
        </tr>
        <tr>
            <td>Autocomplete</td>
            <td>GET JSON</td>
            <td><bie:createWsLink>${g.createLink(controller: 'search', action: 'auto')}?q={search terms}</bie:createWsLink></td>
        </tr>
        <tr>
            <td>Classification</td>
            <td>GET JSON</td>
            <td><bie:createWsLink>${g.createLink(controller: 'search', action: 'classification')}/{GUID}</bie:createWsLink></td>
        </tr>
        <tr>
            <td>Child concepts</td>
            <td>GET JSON</td>
            <td><bie:createWsLink>${g.createLink(uri: '/childConcepts')}/{GUID}</bie:createWsLink></td>
        </tr>
        <tr>
            <td>Image search</td>
            <td>GET JSON</td>
            <td><bie:createWsLink>${g.createLink(controller: 'search', action: 'imageSearch')}/{GUID}</bie:createWsLink></td>
        </tr>
        <tr>
            <td>Image link</td>
            <td>GET IMG</td>
            <td><bie:createWsLink>${g.createLink(uri:"/species/image/[thumbnail|large|small]/")}{GUID}</bie:createWsLink></td>
        </tr>
        <tr>
            <td>Update Image</td>
            <td>POST IMG</td>
            <td><bie:createWsLink>${g.createLink(uri: '/import/updateImages')} { "guidImageList": [ {"guid": GUID, "image": "63c06642-ab33-48b9-b04b-2f321d24f104"} ] }</bie:createWsLink></td>
        </tr>
        <tr>
            <td>Bulk lookup by GUID</td>
            <td>POST JSON</td>
            <td><bie:createWsLink>${g.createLink(controller: 'search', action: 'bulkGuidLookup')} [ "guid", ...]</bie:createWsLink></td>
        </tr>
        <tr>
            <td>Bulk lookup by name</td>
            <td>POST JSON</td>
            <td><bie:createWsLink>${g.createLink(controller: 'search', action: 'speciesLookupBulk')}  { names: [ "name", ... ], "vernacular": false }</bie:createWsLink></td>
        </tr>
        <tr>
            <td>Habitat list</td>
            <td>GET JSON</td>
            <td><bie:createWsLink>${g.createLink(controller: 'search', action: 'habitats')}</bie:createWsLink></td>
        </tr>
        <tr>
            <td>Habitat tree</td>
            <td>GET JSON</td>
            <td><bie:createWsLink>${g.createLink(controller: 'search', action: 'habitatTree')}</bie:createWsLink></td>
        </tr>
        <tr>
            <td>Habitats for species</td>
            <td>GET JSON</td>
            <td><bie:createWsLink>${g.createLink(controller: 'search', action: 'getHabitatIDs')}/{GUID}</bie:createWsLink></td>
        </tr>
        <tr>
            <td>Habitat details</td>
            <td>GET JSON</td>
            <td><bie:createWsLink>${g.createLink(controller: 'search', action: 'getHabitat')}/{GUID}</bie:createWsLink></td>
        </tr>
        <tr>
            <td>Recognised ranks</td>
            <td>GET JSON</td>
            <td><bie:createWsLink>${g.createLink(controller: 'import', action: 'ranks')}</bie:createWsLink></td>
        </tr>
        <tr>
            <td>Indexed fields</td>
            <td>GET XML</td>
            <td><bie:createWsLink>${g.createLink(controller: 'misc', action: 'indexFields')}</bie:createWsLink></td>
        </tr>
        <tr>
            <td>Species groups</td>
            <td>GET JSON</td>
            <td><bie:createWsLink>${g.createLink(controller: 'misc', action: 'speciesGroups')}</bie:createWsLink></td>
        </tr>
    </table>
</div>
</body>
</html>
