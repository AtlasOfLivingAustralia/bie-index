<!DOCTYPE html>
<html>
<head>
    <title><g:message code="job.index.title"/></title>
    <meta name="layout" content="${grailsApplication.config.skin.layout}"/>
    <meta name="breadcrumbParent" content="${createLink(controller:'admin', action:'index', absolute:true)},${message(code: 'breadcrumb.admin')}"/>
    <asset:javascript src="sockets"/>
</head>
<body>
<div id="page-body" role="main">
    <h2><g:message code="job.index.title" /></h2>
    <table class="table">
        <tr>
            <th><g:message code="job.title.label"/></th>
            <th><g:message code="job.id.label"/></th>
            <th><g:message code="job.lifecycle.label"/></th>
            <th><g:message code="job.lastUpdated.label"/></th>
            <th><g:message code="job.message.label"/></th>
            <th><g:message code="job.actions.label"/></th>
        </tr>
        <g:each var="job" in="${jobs}">
        <tr>
            <td>${job.title}</td>
            <td><g:link action="status" id="${job.id}">${job.id}</g:link></td>
            <td>${job.lifecycle}</td>
            <td><g:formatDate date="${job.lastUpdated}" type="datetime" style="MEDIUM"/></td>
            <td>${job.message}</td>
            <td>
                <g:if test="${job.active}"><g:link class="btn btn-danger" action="cancel" id="${job.id}" title="${message(code: 'job.cancel.detail')}"><span class="glyphicon glyphicon-remove"></span><span class="sr-only"><g:message code="default.cancel.label"/></span></g:link></g:if>
                <g:if test="${job.completed}"><g:link class="btn btn-success" action="remove" id="${job.id}" title="${message(code: 'job.remove.detail')}"><span class="glyphicon glyphicon-remove-sign"><span class="sr-only"><g:message code="default.remove.label"/></span></g:link></g:if>
            </td>
        </tr>
        </g:each>
    </table>
    <h3><g:message code="job.log.title"/></h3>
    <g:render template="/import/status" model="${[showTitle: false, showJob: false, showLog: true, startLog: true]}"/>
</div>
</body>
</html>
