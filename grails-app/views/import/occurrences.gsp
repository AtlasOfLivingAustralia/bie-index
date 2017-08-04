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
        <button id="start-import" onclick="javascript:loadOccurrenceInfo()" class="btn btn-primary"><g:message code="admin.button.loadoccurrence"/></button>
    </div>

    <div class="row">
        <div class="well import-info alert-info hide" style="margin-top:20px;">
            <p></p>
            <div class="progress hide">
                <div class="progress-bar" style="width: 0%;" role="progressbar" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100">
                    <span class="sr-only"><span class="percent">0</span>% Complete</span>
                </div>
            </div>
            <div id="import-info-web-socket"></div>
        </div>
    </div>

    <asset:script type="text/javascript">
        function loadOccurrenceInfo(){
            $.get("${createLink(controller:'import', action:'importOccurrences')}", function( data ) {
              if(data.success){
                $('.import-info p').html('Import successfully started....')
                $('#start-import').prop('disabled', true);
              } else {
                $('.import-info p').html('Import failed. Check file path...')
              }
              $('.import-info').removeClass('hide');
              $('.progress').removeClass('hide');
            });
        }
    </asset:script>

    <asset:script type="text/javascript">
        $(function() {
            var socket = new SockJS("${createLink(uri: '/stomp')}");
            var client = Stomp.over(socket);
            client.connect({}, function() {
                client.subscribe("/topic/import-feedback", function(message) {
                    var msg = $.trim(message.body);
                    if ($.isNumeric(msg)) {
                        // update progress bar
                        console.log('msg', msg);
                        $('.progress-bar ').css('width', msg + '%').attr('aria-valuenow', msg);
                        $('.progress-bar span.percent').html(msg);
                    } else {
                        // just a message
                        $("#import-info-web-socket").append('<br/>' + message.body);
                        $('#import-info-web-socket').scrollTop(1E10); // keep at bottom of div
                    }
                });
            });
        });
    </asset:script>
</div>
</body>
</html>