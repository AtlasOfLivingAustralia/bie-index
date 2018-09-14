<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="${grailsApplication.config.skin.layout}"/>
    <title>BIE Web services | ${grailsApplication.config.skin.orgNameLong}</title>
    <asset:javascript src="swagger-ui.js"/>
    <asset:stylesheet src="swagger-ui.css"/>
</head>
<body>
<div role="main" id="swagger-ui">
</div>
<asset:script type="application/javascript">
    window.onload = function() {
    var ui = SwaggerUIBundle({
        url: "${resource(file: 'openapi.yml')}",
        dom_id: '#swagger-ui',
        presets: [
            SwaggerUIBundle.presets.apis,
            SwaggerUIStandalonePreset
        ]
    })
    window.ui = ui
}
</asset:script>
</body>
</html>
