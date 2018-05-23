<g:set var="orgNameLong" value="${grailsApplication.config.skin.orgNameLong}"/>
<g:set var="orgNameShort" value="${grailsApplication.config.skin.orgNameShort}"/>
<g:applyLayout name="main">
    <head>
        <title><g:layoutTitle/></title>
        <link href="${grailsApplication.config.skin.favicon}" rel="shortcut icon"  type="image/x-icon"/>
        <meta name="breadcrumb" content="${pageProperty(name: 'meta.breadcrumb', default: pageProperty(name: 'title').split('\\|')[0].decodeHTML())}"/>
        <meta name="breadcrumbParent" content=""/>

        <g:layoutHead/>

        <hf:head />
    </head>
    <body class="${pageProperty(name:'body.class')}" id="${pageProperty(name:'body.id')}" onload="${pageProperty(name:'body.onload')}">

    <g:set var="fluidLayout" value="${pageProperty(name:'meta.fluidLayout')?:grailsApplication.config.skin?.fluidLayout}"/>
    <!-- Container -->
    <div class="${fluidLayout ? 'container-fluid' : 'container'}" id="main">
        <g:layoutBody />
    </div><!-- End container #main col -->

    <!-- Footer -->

    <!-- End footer -->
    </body>
</g:applyLayout>