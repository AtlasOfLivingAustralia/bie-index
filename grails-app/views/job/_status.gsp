<div class="row">
    <div class="col-md-4"><g:message code="job.title.label"/></div>
    <div class="col-md-8">${it?.title}</div>
</div>
<div class="row">
    <div class="col-md-4"><g:message code="job.id.label"/></div>
    <div class="col-md-8">${it?.id}</div>
</div>
<div class="row">
    <div class="col-md-4"><g:message code="job.lifecycle.label"/></div>
    <div class="col-md-4">${it?.lifecycle}</div>
    <div class="col-md-2"><g:message code="job.success.${it?.success}.label"/></div>
    <div class="col-md-2"><g:message code="job.active.${it?.active}.label"/></div>
</div>
<div class="row">
    <div class="col-md-4"><g:message code="job.lastUpdated.label"/></div>
    <div class="col-md-8"><g:formatDate date="${it?.lastUpdated}" type="datetime" style="MEDIUM"/></div>
</div>
<div class="row">
    <div class="col-md-4"><g:message code="job.message.label"/></div>
    <div class="col-md-8">${it?.message}</div>
</div>

