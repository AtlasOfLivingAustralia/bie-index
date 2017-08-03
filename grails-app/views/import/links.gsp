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
  <title><g:message code="admin.import.links.label"/></title>
  <meta name="layout" content="${grailsApplication.config.skin.layout}"/>
    <meta name="breadcrumbParent" content="${createLink(controller:'admin', action:'index', absolute:true)},${message(code: 'breadcrumb.admin')}"/>
    <r:require modules="sockets" />
    <style type="text/css">
        .progress {
            height: 10px !important;
        }
    </style>
</head>
<body>
<div>
    <h2 class="heading-medium"><g:message code="admin.import.layers.label"/></h2>

    <div class="row">
        <p class="col-md-8 lead"><g:message code="admin.import.links.lead"/></p>
        <p class="col-md-4 well"><g:message code="admin.import.swap"/></p>
    </div>

    <div>
        <button id="denormalise-taxa" onclick="javascript:denormaliseTaxa()" class="btn btn-primary"><g:message code="admin.button.denormalise"/></button>
    </div>
    <div>
        <button id="build-link-identifiers" onclick="javascript:buildLinkIdentifiers()" class="btn btn-primary"><g:message code="admin.button.buildlinks"/></button>
    </div>
    <div>
        <button id="load-images" onclick="javascript:loadImages()" class="btn btn-primary"><g:message code="admin.button.loadimagesall"/>Load All Images</button>
    </div>
    <div>
        <button id="load-preferred-images" onclick="javascript:loadPreferredImages()" class="btn btn-primary"><g:message code="admin.button.loadimagespref"/>Load Preferred Images</button>
    </div>
    <div>
        <button id="dangling-synonyms" onclick="javascript:removeDanglingSynonyms()" class="btn btn-primary"><g:message code="admin.button.removeorphans"/>Remove orphaned synonyms</button>
    </div>
    <div>
        <input type="checkbox" id="use-online" name="use-online"/> <g:message code="admin.label.useonline"/>
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

    <r:script>
        function denormaliseTaxa(){
            $.get("${createLink(controller:'import', action:'denormaliseTaxa')}?online=" + $('#use-online').is(':checked'), function( data ) {
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

        function removeDanglingSynonyms(){
            $.get("${createLink(controller:'import', action:'deleteDanglingSynonyms')}?online=" + $('#use-online').is(':checked'), function( data ) {
              if(data.success){
                $('.import-info p').html('Delete successfully started....')
                $('#start-import').prop('disabled', true);
              } else {
                $('.import-info p').html('Build failed. Check file path...')
              }
              $('.import-info').removeClass('hide');
              $('.progress').removeClass('hide');
            });
        }

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

        function loadPreferredImages(){
            $.get("${createLink(controller:'import', action:'loadPreferredImages')}?online=" + $('#use-online').is(':checked'), function( data ) {
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