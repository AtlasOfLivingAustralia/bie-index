<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="${grailsApplication.config.skin.layout}"/>
    <title><g:message code="admin.index.title" args="[grailsApplication.config.skin.orgNameLong]"/></title>
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
        <br>
        <li><g:link controller="job" action="index"><g:message code="admin.job.label"/></g:link> <g:message code="admin.job.lead"/></li>
        <li><g:link controller="alaAdmin" action="index"><g:message code="admin.ala.label"/></g:link> <g:message code="admin.ala.lead"/></li>
    </ul>
</div>
</body>
</html>
