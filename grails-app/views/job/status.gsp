<!DOCTYPE html>
<html>
<head>
    <title><g:message code="job.status.title"/></title>
    <meta name="layout" content="${grailsApplication.config.skin.layout}"/>
    <meta name="breadcrumbParent" content="${createLink(controller:'admin', action:'index', absolute:true)},${message(code: 'breadcrumb.admin')}"/>
</head>
<body>
<div id="page-body" role="main">
    <h2><g:message code="job.status.title" /></h2>
        <div class="pull-right">
            <g:if test="${job?.active}"><g:link class="btn btn-danger" action="cancel" id="${job.id}" title="${message(code: 'job.cancel.detail')}"><span class="glyphicon glyphicon-remove"></span><span class="sr-only"><g:message code="default.cancel.label"/></span></g:link> </g:if>
            <g:if test="${job?.completed}"><g:link class="btn btn-success" action="remove" id="${job.id}" title="${message(code: 'job.remove.detail')}"><span class="glyphicon glyphicon-remove-sign"><span class="sr-only"><g:message code="default.remove.label"/></span></g:link> </g:if>
        </div>
     <div class="container-fluid">
    <g:render template="status" bean="${job}"/>
    </div>
</div>
</body>
</html>
