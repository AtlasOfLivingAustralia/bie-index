
<div class="row processing-log import-info alert alert-info hide" id="import-info-status" style="margin-top: 2ex">
    <div class="col-md-offset-1 col-md-11" id="import-info-message"></div>
</div>
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
<div class="row processing-log import-info alert well hide">
    <ol id="import-info-log" class="col-md-offset-1 col-md-11"></ol>
</div>


<asset:script type="text/javascript">
    function loadInfo(link){
        $("#import-info-log").empty();
        $("#import-info-status").removeClass('alert-info alert-success alert-warning alert-danger');
        $("#import-info-status").addClass('alert-warning');
        $('#import-info-message').html('<g:message code="admin.import.status.requested"/>');
        $('#progress1').addClass('hide');
        $('#progress2').addClass('hide');
        $('.import-button').prop('disabled', true);
        $('.import-info').removeClass('hide');
        $.get(link, function( data ) {
            if(!data.success){
                $('#import-info-message').html(data.message);
                $('.import-button').prop('disabled', false);
              }
        });
    }

    $(function() {
        var socket = new SockJS("${createLink(uri: '/stomp')}");
        var client = Stomp.over(socket);
        client.connect({}, function() {
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
        });
    });
</asset:script>
