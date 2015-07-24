<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
  <title></title>
  <meta name="layout" content="main"/>
</head>
<body>
<div>
    <!-- Breadcrumb -->
    <ol class="breadcrumb">
        <li><a class="font-xxsmall" href="../">Home</a></li>
        <li class="font-xxsmall active" href="#">Import</li>
    </ol>
    <!-- End Breadcrumb -->
    <h2 class="heading-medium">Layers import</h2>

    <p class="lead">
        Reload layers information into the main search index
    </p>

    <div>
        <button id="start-import" onclick="javascript:loadInfo()" class="btn btn-primary">Import layer information</button>
    </div>

    <div class="well import-info alert-info hide" style="margin-top:20px;">
        <p></p>
    </div>

    <r:script>

        function loadInfo(){
            $.get("${createLink(controller:'import', action:'importLayers')}", function( data ) {
              if(data.success){
                $('.import-info p').html('Import successfully started....')
                $('#start-import').prop('disabled', true);
              } else {
                $('.import-info p').html('Import failed. Check file path...')
              }
              $('.import-info').removeClass('hide');
            });
        }

    </r:script>
</div>
</body>
</html>