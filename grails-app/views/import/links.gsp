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
  <title>Build Links</title>
  <meta name="layout" content="${grailsApplication.config.skin.layout}"/>
    <r:require modules="sockets" />
    <style type="text/css">
        .progress {
            height: 10px !important;
        }
    </style>
</head>
<body>
<div>
    <!-- Breadcrumb -->
    <ol class="breadcrumb">
        <li><a class="font-xxsmall" href="../">Home</a></li>
        <li class="font-xxsmall active" href="#">Import</li>
    </ol>
    <!-- End Breadcrumb -->
    <h2 class="heading-medium">Build Links</h2>

    <p class="lead">
        Scan the index for link identifiers; names that are unique and can be treated as an identifier.
        Scan the index for images; suitable images for various species.
        Note SOLR cores (bie / bie-offline) may require swapping before searches will appear.
    </p>

    <div>
        <button id="build-link-identifiers" onclick="javascript:buildLinkIdentifiers()" class="btn btn-primary">Build Link Identifiers</button>
    </div>
    <div>
        <button id="load-images" onclick="javascript:loadImages()" class="btn btn-primary">Load Images</button>
    </div>
    <div>
        <input type="checkbox" id="use-online" name="use-online"/> Use online index
    </div>

    <div class="well import-info alert-info hide" style="margin-top:20px;">
        <p></p>
        <div class="progress hide">
            <div class="progress-bar" style="width: 0%;" role="progressbar" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100">
                <span class="sr-only"><span class="percent">0</span>% Complete</span>
            </div>
        </div>
        <div id="import-info-web-socket"></div>
    </div>

    <r:script>
        function buildLinkIdentifiers(){
            $.get("${createLink(controller:'import', action:'buildLinkIdentifiers')}?online=" + $('#use-online').is(':checked'), function( data ) {
              if(data.success){
                $('.import-info p').html('Build successfully started....')
                $('#start-import').prop('disabled', true);
              } else {
                $('.import-info p').html('Build failed. Check file path...')
              }
              $('.import-info').removeClass('hide');
              $('.progress').removeClass('hide');
            });
        }

        function loadImages(){
            $.get("${createLink(controller:'import', action:'loadImages')}?online=" + $('#use-online').is(':checked'), function( data ) {
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
    </r:script>

    <r:script>
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
                    }
                });
            });
        });
    </r:script>
</div>
</body>
</html>