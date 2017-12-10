<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
  <title><g:message code="admin.import.dwca.label"/></title>
  <meta name="layout" content="${grailsApplication.config.skin.layout}"/>
  <meta name="breadcrumbParent" content="${createLink(controller:'admin', action:'index', absolute:true)},${message(code: 'breadcrumb.admin')}"/>
    <asset:javascript src="sockets"/>
</head>
<body>
<div>
    <!-- End Breadcrumb -->
    <h2 class="heading-medium"><g:message code="admin.import.dwca.label"/></h2>

    <div class="row">
        <div class="col-md-8">
            <p class="lead"><g:message code="admin.import.dwca.lead"/></p>
            <p><g:message code="admin.import.dwca.detail"/></p>
        </div>
        <p class="col-md-4 well"><g:message code="admin.import.swap"/></p>
    </div>

    <div class="form-horizontal">
        <div class="form-group">
            <label class="col-md-offset-4 col-md-4">
                <input type="checkbox" id="clear_index" name="clear_index"/>
                <g:message code="admin.label.cleardata"/>
            </label>
        </div>
        <g:each in="${filePaths}" var="filePath" statis="fs">
            <div class="form-group">
                <label for="dwca-${fs}" class="col-md-4 control-label">${filePath}</label>
                <div class="col-md-2">
                    <button id="dwca-${fs}" class="btn btn-primary import-button" onclick="javascript:loadDwCA('${filePath}');"><g:message code="admin.button.importdwca"/></button>
                </div>
            </div>
        </g:each>
        <div class="form-group">
            <label for="dwca_dir" class="sr-only control-label"><g:message code="admin.label.dwcapath"/></label>
            <div class="col-md-4">
                <input type="text" class="form-control" id="dwca_dir" name="dwca_dir" value="/data/bie/import/dwc-a" class="form-control"/>
            </div>
            <div class="col-md-2">
                <button id="start-import" onclick="javascript:loadDwCAFromDir();" class="btn btn-primary import-button"><g:message code="admin.button.importdwca"/></button>
            </div>
            <span class="help-block"><g:message code="admin.label.dwcapath.help"/></span>
        </div>
    </div>

    <g:render template="status" model="${[showTitle: true, showJob: true, showLog: true, startLog: false]}"/>

    <asset:script type="text/javascript">

        function loadDwCAFromDir(){
            loadDwCA($('#dwca_dir').val())
        }

        function loadDwCA(filePath) {
            loadInfo('${createLink(controller:'import', action:'importDwcA' )}?dwca_dir=' + filePath + '&clear_index=' + $('#clear_index').is(':checked'));
        }
    </asset:script>
</div>
</body>
</html>