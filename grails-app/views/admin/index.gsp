<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="${grailsApplication.config.skin.layout}"/>
    <title>Admin |BIE Web services | ${grailsApplication.config.skin.orgNameLong}</title>
</head>
<body>
<div id="page-body" role="main">
    <h2>Admin tools</h2>
    <ul>
        <li><g:link controller="import" action="index">Taxonomy import tool</g:link> - import DwC-A with taxonomic information</li>
        <li><g:link controller="import" action="collectory">Collectory import tool</g:link> - import collectory information</li>
        <li><g:link controller="import" action="layers">Layer import tool</g:link> - import layer information</li>
    </ul>
</div>
</body>
</html>
