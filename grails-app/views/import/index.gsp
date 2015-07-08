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
    <h2 class="heading-medium">DWC-A import</h2>

    <p class="lead">
        Place expanded (unzipped) DwC-A files in the import directory /data/bie/import on this machine.
    </p>

    <div>
        <form>
            <table class="table">
                <g:each in="${filePaths}" var="filePath">
                    <tr>
                        <td>${filePath}</td>
                        <td><button class="btn btn-primary" onclick="javascript:loadDwCA('${filePath}', false);">Import DwCA</button></td>
                        <td><button class="btn btn-primary" onclick="javascript:loadDwCA('${filePath}', true);">Clean & Import DwCA</button></td>
                    </tr>
                </g:each>
            </table>

            <h2>Or..</h2>

            <div class="form-group">
                <label for="dwca_dir">Absolute file system path to expanded (unzipped) DwC-A</label>
                <input type="text" id="dwca_dir" name="dwca_dir" value="/data/bie/import/dwc-a" class="form-control"/>
            </div>

            <div class="form-group">
                <div class="checkbox">
                    <label>
                        <input type="checkbox" id="clear_index" name="clear_index"/> Clear existing data taxonomic data
                    </label>
                </div>
            </div>
        </form>
        <button id="start-import" class="btn btn-primary">Import DwC-A</button>
    </div>

    <div class="well import-info alert-info hide" style="margin-top:20px;">
        <p></p>
    </div>

    <r:script>
        $('#start-import').click(function() {
            $.get("${createLink(controller:'import', action:'importDwcA' )}?dwca_dir=" + $('#dwca_dir').val() + "&clear_index=" + $('#clear_index').val() + "&field_delimiter=" + $('#field_delimiter').val(), function( data ) {
              if(data.success){
                $('.import-info p').html('Import successfully started....')
                $('#start-import').prop('disabled', true);
              } else {
                $('.import-info p').html('Import failed. Check file path...')
              }
              $('.import-info').removeClass('hide');
            });
        });

        function loadDwCA(filePath, clearIndex) {
            $.get("${createLink(controller:'import', action:'importDwcA' )}?dwca_dir=" + filePath + "&clear_index=" + clearIndex, function( data ) {
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