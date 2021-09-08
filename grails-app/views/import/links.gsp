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
    <asset:javascript src="sockets"/>
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
        <button id="denormalise-taxa" onclick="javascript:denormaliseTaxa()" class="btn btn-primary import-button"><g:message code="admin.button.denormalise"/></button>
    </div>
    <div>
        <button id="build-link-identifiers" onclick="javascript:buildLinkIdentifiers()" class="btn btn-primary import-button"><g:message code="admin.button.buildlinks"/></button>
    </div>
    <div>
        <button id="load-images" onclick="javascript:loadImages()" class="btn btn-primary import-button"><g:message code="admin.button.loadimagesall"/></button>
    </div>
    <div>
        <button id="load-preferred-images" onclick="javascript:loadPreferredImages()" class="btn btn-primary import-button"><g:message code="admin.button.loadimagespref"/></button>
    </div>
    <div>
        <button id="dangling-synonyms" onclick="javascript:removeDanglingSynonyms()" class="btn btn-primary import-button"><g:message code="admin.button.removeorphans"/></button>
    </div>
    <div>
        <button id="build-favourites" onclick="javascript:buildFavourites()" class="btn btn-primary import-button"><g:message code="admin.button.buildfavourites"/></button>
    </div>
    <div>
        <button id="build-weights" onclick="javascript:buildWeights()" class="btn btn-primary import-button"><g:message code="admin.button.buildweights"/></button>
    </div>
    <div>
        <button id="build-suggest-index" onclick="javascript:buildSuggestIndex()" class="btn btn-primary import-button"><g:message code="admin.button.buildsuggestindex"/></button>
    </div>
    <div>
        <input type="checkbox" id="use-online" name="use-online"/> <g:message code="admin.label.useonline"/>
    </div>

    <g:render template="status" model="${[showTitle: true, showJob: true, showLog: true, startLog: false]}"/>

    <asset:script type="text/javascript">
        function buildSuggestIndex(){
            loadInfo("${createLink(controller:'import', action:'buildSuggestIndex')}?online=" + $('#use-online').is(':checked'));
        }
        function buildFavourites(){
            loadInfo("${createLink(controller:'import', action:'buildFavourites')}?online=" + $('#use-online').is(':checked'));
        }
        function buildWeights(){
            loadInfo("${createLink(controller:'import', action:'buildWeights')}?online=" + $('#use-online').is(':checked'));
        }
        function denormaliseTaxa(){
            loadInfo("${createLink(controller:'import', action:'denormaliseTaxa')}?online=" + $('#use-online').is(':checked'));
        }

        function removeDanglingSynonyms(){
            loadInfo("${createLink(controller:'import', action:'deleteDanglingSynonyms')}?online=" + $('#use-online').is(':checked'));
        }

        function buildLinkIdentifiers(){
            loadInfo("${createLink(controller:'import', action:'buildLinkIdentifiers')}?online=" + $('#use-online').is(':checked'));
        }

        function loadImages(){
            loadInfo("${createLink(controller:'import', action:'loadImages')}?online=" + $('#use-online').is(':checked'));
        }

        function loadPreferredImages(){
            loadInfo("${createLink(controller:'import', action:'loadPreferredImages')}?online=" + $('#use-online').is(':checked'));
        }
    </asset:script>
</div>
</body>
</html>