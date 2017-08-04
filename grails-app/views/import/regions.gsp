<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
  <title><g:message code="admin.import.regions.label"/></title>
  <meta name="layout" content="${grailsApplication.config.skin.layout}"/>
    <meta name="breadcrumbParent" content="${createLink(controller:'admin', action:'index', absolute:true)},${message(code: 'breadcrumb.admin')}"/>
    <asset:javascript src="sockets"/>
</head>
<body>
<div>
    <!-- Breadcrumb -->
    <ol class="breadcrumb">
        <li><a class="font-xxsmall" href="../">Home</a></li>
        <li class="font-xxsmall active" href="#">Import</li>
    </ol>
    <!-- End Breadcrumb -->
    <h2 class="heading-medium"><g:message code="admin.import.regions.label"/></h2>

    <div class="row">
        <p class="col-md-8 lead"><g:message code="admin.import.regions.lead"/></p>
        <p class="col-md-4 well"><g:message code="admin.import.swap"/></p>
    </div>

    <div>
        <button id="start-import" onclick="javascript:loadInfo()" class="btn btn-primary"><g:message code="admin.button.importregions"/></button>
    </div>

    <div class="row">
        <div class="col-md-12 well import-info alert-info hide" style="margin-top:20px;">
            <p></p>
            <p id="import-info-web-socket"></p>
        </div>
    </div>

    <asset:script type="text/javascript">
        function loadInfo(){
            $.get("${createLink(controller:'import', action:'importRegions')}", function( data ) {
              if(data.success){
                $('.import-info p').html('Import successfully started....')
                $('#start-import').prop('disabled', true);
              } else {
                $('.import-info p').html('Import failed. Check file path...')
              }
              $('.import-info').removeClass('hide');
            });
        }
    </asset:script>
    <asset:script type="text/javascript">
        $(function() {
            var socket = new SockJS("${createLink(uri: '/stomp')}");
            var client = Stomp.over(socket);
            client.connect({}, function() {
                client.subscribe("/topic/import-feedback", function(message) {
                    $("#import-info-web-socket").append('<br/>' + message.body);
                });
            });
        });
    </asset:script>
</div>
</body>
</html>