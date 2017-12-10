%{--
  - Copyright (C) 2016 Atlas of Living Australia
  - All Rights Reserved.
  - The contents of this file are subject to the Mozilla Public
  - License Version 1.1 (the "License"); you may not use this file
  - except in compliance with the License. You may obtain a copy of
  - the License at http://www.mozilla.org/MPL/
  - Software distributed under the License is distributed on an "AS
  - IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
  - implied. See the License for the specific language governing
  - rights and limitations under the License.
  --}%

<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
  <title><g:message code="admin.import.occurrences.label"/></title>
  <meta name="layout" content="${grailsApplication.config.skin.layout}"/>
    <meta name="breadcrumbParent" content="${createLink(controller:'admin', action:'index', absolute:true)},${message(code: 'breadcrumb.admin')}"/>
    <asset:javascript src="sockets"/>
    <style type="text/css">
        .progress {
            height: 10px !important;
        }
        #import-info-web-socket {
            height: 400px;
            overflow-y: scroll;
        }
    </style>
</head>
<body>
<div>
    <h2 class="heading-medium"><g:message code="admin.import.occurrences.label"/></h2>

    <div class="row">
        <p class="col-md-8 lead"><g:message code="admin.import.occurrences.lead"/></p>
        <p class="col-md-4 well"><g:message code="admin.import.swap"/></p>
    </div>

    <div>
        <button id="start-import" onclick="javascript:loadInfo('${createLink(controller:'import', action:'importOccurrences')}')" class="btn btn-primary import-button"><g:message code="admin.button.loadoccurrence"/></button>
    </div>

    <g:render template="status" model="${[showTitle: true, showJob: true, showLog: true, startLog: false]}"/>
</div>
</body>
</html>