<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
  <title><g:message code="admin.import.collectory.label"/></title>
  <meta name="layout" content="${grailsApplication.config.skin.layout}"/>
    <meta name="breadcrumbParent" content="${createLink(controller:'admin', action:'index', absolute:true)},${message(code: 'breadcrumb.admin')}"/>
    <asset:javascript src="sockets"/>
</head>
<body>
<div>
    <!-- End Breadcrumb -->
    <h2 class="heading-medium"><g:message code="admin.import.collectory.label"/></h2>

    <div class="row">
        <p class="col-md-8 lead"><g:message code="admin.import.collectory.lead"/></p>
        <p class="col-md-4 well"><g:message code="admin.import.swap"/></p>
    </div>

    <div>
        <button id="start-import" onclick="javascript:loadInfo('${createLink(controller:'import', action:'importCollectory')}')" class="btn btn-primary import-button"><g:message code="admin.button.importcollectory"/></button>
    </div>

    <g:render template="status"/>
</div>
</body>
</html>