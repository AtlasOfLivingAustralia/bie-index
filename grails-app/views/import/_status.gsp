<g:if test="${showTitle}">
<div class="row processing-log import-info alert alert-info" id="import-info-status" style="margin-top: 2ex">
    <div class="container-fluid">
        <div class="row">
            <div class="col-md-offset-1 col-md-9" id="import-info-message"></div>

            <div class="col-md-2">
                <g:if test="${showLog}">
                <button id="reconnect" onclick="javascript:reconnectLog()" class="btn btn-info" title="<g:message code="admin.button.reconnect.detail"/>">
                    <span class="glyphicon glyphicon-refresh"></span>
                    <span class="sr-only"><g:message code="admin.button.reconnect"/></span>
                </button>
                </g:if>
                <g:if test="${showJob}">
                <button id="toggle-status" onclick="javascript:toggleStatus()" class="btn btn-info" title="<g:message code="admin.button.jobStatus.detail"/>">
                    <span class="glyphicon glyphicon-info-sign"></span>
                    <span class="sr-only"><g:message code="admin.button.jobStatus"/></span>
                </button>
                <button id="cancel-job" onclick="javascript:cancelJob()" class="btn btn-danger" title="<g:message code="job.cancel.detail"/>">
                    <span class="glyphicon glyphicon-remove"></span>
                    <span class="sr-only"><g:message code="default.cancel.label"/></span>
                </button>
                </g:if>
            </div>
        </div>

        <g:if test="${showJob}">
        <div id="job-status" class="row hide">
            <div class="col-md-offset-1 col-md-9">
                <div id="job-status-report" class="container-fluid clearfix"></div>
            </div>
            <div id="job-status-actions" class="col-md-2">
                <button id="refresh-status" onclick="javascript:showJob()" class="btn btn-info" title="<g:message code="admin.button.refresh.detail"/>">
                    <span class="glyphicon glyphicon-refresh"></span>
                    <span class="sr-only"><g:message code="admin.button.refresh"/></span>
                </button>
            </div>
        </div>
        </g:if>
    </div>
</div>
</g:if>

<g:if test="${showLog}">
<div id="progress1" class="progress hide">
    <div class="progress-bar progress-bar-success" style="width: 0%;" role="progressbar" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100">
        <span class="sr-only"><span class="percent">0</span>% Complete</span>
    </div>
</div>
<div id="progress2" class="progress hide">
    <div class="progress-bar" style="width: 0%;" role="progressbar" aria-valuenow="0" aria-valuemin="0" aria-valuemax="100">
        <span class="sr-only"><span class="percent">0</span>% Complete</span>
    </div>
</div>
<div class="row processing-log import-info alert well ${startLog ? '' : 'hide'}">
    <ol id="import-info-log" class="col-md-offset-1 col-md-11"></ol>
</div>
</g:if>


<asset:script type="text/javascript">
    var socket = null;
    var client = null;
    var jobID = null;

    function toggleStatus() {
        $('#job-status').toggleClass('hide');
        showJob();
    }

    function showJob() {
        if (jobID == null) {
            $('#job-status-report').html('');
            return;
        }
        var url = '<g:createLink controller="job" action="index" absolute="true"/>/' + jobID + '/panel.html';
        $.get(url, function(data) {
            $('#job-status-report').html(data)
        })
    }

    function cancelJob() {
        if (jobID == null) {
            return;
        }
        var url = '<g:createLink controller="job" action="index" absolute="true"/>/' + jobID + '/cancel.json';
        $.get(url, function(data) {
            showJob();
        })
    }

    function reconnectLog() {
        $('#progress1').addClass('hide');
        $('#progress2').addClass('hide');
        $('.import-info').removeClass('hide');
        $("#import-info-log").append('<li>...</li>');
        connectLog();
    }

    function connectLog() {
        if (client != null) {
            client.disconnect();
            client = null;
        }
        if (socket != null) {
            socket.close()
            socket = null
        }
        socket = new SockJS("${createLink(uri: '/stomp')}");
        client = Stomp.over(socket);
        client.connect({}, function() {
            <g:if test="${showLog}">
            client.subscribe("/topic/import-feedback", function(message) {
                var msg = $.trim(message.body);
                $("#import-info-log").append('<li>' + msg+ '</li>');
            });
            client.subscribe("/topic/import-progress", function(message) {
                var msg = $.trim(message.body);
                if ($.isNumeric(msg)) {
                    // update progress bar
                    $('#progress1 div.progress-bar').css('width', msg + '%').attr('aria-valuenow', msg);
                    $('#progress1 div.progress-bar span.percent').html(msg);
                    $('#progress1').removeClass('hide');
                }
            });
            client.subscribe("/topic/import-progress2", function(message) {
                var msg = $.trim(message.body);
                if ($.isNumeric(msg)) {
                    // update progress bar
                    $('#progress2 div.progress-bar').css('width', msg + '%').attr('aria-valuenow', msg);
                    $('#progress2  div.progress-bar span.percent').html(msg);
                    $('#progress2').removeClass('hide');
                }
            });
            </g:if>
            <g:if test="${showTitle}">
            client.subscribe("/topic/import-control", function(message) {
                var status = $("#import-info-status");
                status.removeClass('alert-info alert-success alert-warning alert-danger')
                if (message.body == 'STARTED') {
                    status.addClass('alert-info');
                    $('#import-info-message').html('<g:message code="admin.import.status.started"/>')
                } else if (message.body == 'FINISHED') {
                    status.addClass('alert-success');
                    $('#import-info-message').html('<g:message code="admin.import.status.completed"/>')
                    $('.import-button').prop('disabled', false);
                } else if (message.body == 'ERROR') {
                    $('#import-info-message').html('<g:message code="admin.import.status.error"/>')
                    status.addClass('alert-danger');
                    $('.import-button').prop('disabled', false);
                }
            });
            </g:if>
        });
    }

    function loadInfo(link){
        jobID = null
        $("#import-info-log").empty();
        $("#import-info-status").removeClass('alert-info alert-success alert-warning alert-danger');
        $("#import-info-status").addClass('alert-warning');
        $('#import-info-message').html('<g:message code="admin.import.status.requested"/>');
        $('#progress1').addClass('hide');
        $('#progress2').addClass('hide');
        $('.import-button').prop('disabled', true);
        $('.import-info').removeClass('hide');
        $.get(link, function( data ) {
            jobID = data.id
            $('#job-status').addClass('hide');
            if(!data.success) {
                $('#import-info-message').html(data.message);
                $('.import-button').prop('disabled', false);
            }
            showJob();
        });
    }

    $(function() {
        connectLog();
    });
</asset:script>
