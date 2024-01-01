<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="${grailsApplication.config.skin.layout}"/>
    <title><g:message code="admin.index.title" args="[grailsApplication.config.skin.orgNameLong]"/></title>
    <asset:javascript src="sockets"/>
</head>
<body>
<div id="page-body" role="main">
    <h2><g:message code="admin.index.title" args="[grailsApplication.config.skin.orgNameLong]"/></h2>
    <ul>
        <li><g:link controller="import" action="all"><g:message code="admin.import.all.label"/></g:link> <g:message code="admin.import.all.lead"/></li>
        <li><g:link controller="import" action="index"><g:message code="admin.import.dwca.label"/></g:link> <g:message code="admin.import.dwca.lead"/></li>
        <li><g:link controller="import" action="collectory"><g:message code="admin.import.collectory.label"/></g:link> <g:message code="admin.import.collectory.lead"/></li>
        <li><g:link controller="import" action="layers"><g:message code="admin.import.layers.label"/></g:link> <g:message code="admin.import.layers.lead"/></li>
        <li><g:link controller="import" action="regions"><g:message code="admin.import.regions.label"/></g:link> <g:message code="admin.import.regions.lead"/></li>
        <li><g:link controller="import" action="localities"><g:message code="admin.import.localities.label"/></g:link> <g:message code="admin.import.localities.lead"/></li>
        <li><g:link controller="import" action="specieslist"><g:message code="admin.import.specieslist.label"/></g:link> <g:message code="admin.import.specieslist.lead"/></li>
        <li><g:link controller="import" action="wordpress"><g:message code="admin.import.wordpress.label"/></g:link> <g:message code="admin.import.wordpress.lead"/></li>
        <li><g:link controller="import" action="knowledgebase"><g:message code="admin.import.knowledgebase.label"/></g:link> <g:message code="admin.import.knowledgebase.lead"/></li>
        <li><g:link controller="import" action="links"><g:message code="admin.import.links.label"/></g:link> <g:message code="admin.import.links.lead"/></li>
        <li><g:link controller="import" action="occurrences"><g:message code="admin.import.occurrences.label"/></g:link> <g:message code="admin.import.occurrences.lead"/></li>
        <li><g:link controller="import" action="sitemap"><g:message code="admin.import.sitemap.label"/></g:link> <g:message code="admin.import.sitemap.lead"/></li>
        <li><g:link controller="import" action="biocollect"><g:message code="admin.import.biocollect.label"/></g:link> <g:message code="admin.import.biocollect.lead"/></li>
        <br>
        <li><g:link controller="job" action="index"><g:message code="admin.job.label"/></g:link> <g:message code="admin.job.lead"/></li>
        <li><g:link controller="alaAdmin" action="index"><g:message code="admin.ala.label"/></g:link> <g:message code="admin.ala.lead"/></li>
    </ul>
</div>

<div>
    <table class="table-bordered table">
        <thead>
        <tr>
            <th>Index</th>
            <th>Documents</th>
            <th>Version</th>
            <th>Path</th>
        </tr>
        </thead>
        <tbody>
        <tr>
            <td>bie</td><td>${info.response.status.bie.index.numDocs}</td><td>${info.response.status.bie.index.version}</td><td>${info.response.status.bie.instanceDir}</td>
        </tr>
        <tr>
            <td>bie-offline</td><td>${info.response.status['bie-offline'].index.numDocs}</td><td>${info.response.status['bie-offline'].index.version}</td><td>${info.response.status['bie-offline'].instanceDir}</td>
        </tr>
        </tbody>
    </table>
</div>
<div>
    <button id="start-import-swap" onclick="javascript:loadInfo('${createLink(controller:'import', action:'swap')}')" class="btn btn-primary import-button"><g:message code="admin.button.swap"/></button>
</div>
<div>
    <g:render template="status" model="${[showTitle: true, showJob: true, showLog: true, startLog: false]}"/>
</div>

</body>
</html>
