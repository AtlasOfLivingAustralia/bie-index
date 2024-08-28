<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
  <title><g:message code="admin.import.all.label"/></title>
  <meta name="layout" content="${grailsApplication.config.skin.layout}"/>
  <meta name="breadcrumbParent" content="${createLink(controller:'admin', action:'index', absolute:true)},${message(code: 'breadcrumb.admin')}"/>
  <asset:javascript src="sockets"/>
</head>
<body>
<div>
    <h2 class="heading-medium"><g:message code="admin.import.all.label"/></h2>

    <div class="row">
        <p class="col-md-8 lead"><g:message code="admin.import.all.lead"/></p>
        <p class="col-md-4 well"><g:message code="admin.import.swap"/></p>
    </div>

    <div>
        <button id="start-import" onclick="javascript:loadInfo('${createLink(controller:'import', action:'importAll')}?online=' + $('#use-online').is(':checked'))" class="btn btn-primary import-button"><g:message code="admin.button.importall"/></button>
    </div>

    <div>
        <button id="start-import-daily" onclick="javascript:loadInfo('${createLink(controller:'import', action:'importDaily')}?online=' + $('#use-online').is(':checked'))" class="btn btn-primary import-button"><g:message code="admin.button.daily"/></button>
    </div>

    <div>
        <button id="start-import-weekly" onclick="javascript:loadInfo('${createLink(controller:'import', action:'importWeekly')}?online=' + $('#use-online').is(':checked'))" class="btn btn-primary import-button"><g:message code="admin.button.weekly"/></button>
    </div>
    <div>
        <input type="checkbox" id="use-online" name="use-online"/> <g:message code="admin.label.useonline"/>
    </div>
    <br/>


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
            <g:each in="${info.response.status}" var="core" >
               <tr>
                   <td>${core.key}</td><td>${core.value.index.numDocs}</td><td>${core.value.index.version}</td><td>${core.value.instanceDir}</td>
               </tr>
            </g:each>
            </tbody>
        </table>
    </div>
    <div>
        <button id="start-import-swap" onclick="javascript:loadInfo('${createLink(controller:'import', action:'swap')}')" class="btn btn-primary import-button"><g:message code="admin.button.swap"/></button>
    </div>


    <g:render template="status" model="${[showTitle: true, showJob: true, showLog: true, startLog: false]}"/>
</div>
</body>
</html>
