GroovyConsole.Audit = function () {

    var AUDIT_URL = '/bin/groovyconsole/audit.json';

    var DOM = "<'row panel-row'<'col-sm-6'l><'col-sm-6'f>><'row'<'col-sm-12'tr>><'row panel-row'<'col-sm-5'i><'col-sm-7'p>>";

    var table;

    return {
        initialize: function () {
            table = $('.audit').DataTable({
                retrieve: true,
                ajax: AUDIT_URL,
                columns: [
                    {
                        className: 'open-record',
                        orderable: false,
                        searchable: false,
                        data: null,
                        defaultContent: '<span class="glyphicon glyphicon-upload" title="Load Script"></span>'
                    },
                    {
                        className: 'download-record',
                        orderable: false,
                        searchable: false,
                        data: null,
                        defaultContent: ''
                    },
                    {
                        data: 'date',
                        searchable: false
                    },
                    {
                        data: 'jobTitle'
                    },
                    {
                        data: 'script',
                        orderable: false
                    },
                    {
                        data: 'exception',
                        orderable: false
                    },
                    {
                        className: 'delete-record',
                        orderable: false,
                        searchable: false,
                        data: null,
                        defaultContent: '<span class="glyphicon glyphicon-trash" title="Delete Record"></span>'
                    }
                ],
                order: [[2, 'desc']],
                language: {
                    emptyTable: 'No audit records found.',
                    search: 'Contains: ',
                    zeroRecords: 'No matching audit records found.',
                    info: 'Showing _START_ to _END_ of _TOTAL_ records',
                    infoEmpty: '',
                    infoFiltered: '(filtered from _MAX_ total records)'
                },
                rowCallback: function (row, data) {
                    if (data.downloadUrl) {
                        $('td:eq(1)', row).html('<span class="glyphicon glyphicon-floppy-save" title="Download Output"></span>');
                    }

                    $('td:eq(2)', row).html('<a href="/groovyconsole' + data.queryString + '">' + data.date + '</a>');
                    $('td:eq(4)', row).html('<code>' + data.scriptPreview + '</code><div class="hidden">' + data.script + '</div>');
                    $('td:eq(4)', row).popover({
                        container: 'body',
                        content: '<pre>' + data.script + '</pre>',
                        html: true,
                        placement: 'top',
                        trigger: 'hover'
                    });

                    if (data.exception.length) {
                        $('td:eq(5)', row).html('<span class="label label-danger">' + data.exception + '</span>');
                    }
                },
                dom: DOM
            });

            var tableBody = $('.audit tbody');

            tableBody.on('click', 'td.open-record', function () {
                var tr = $(this).closest('tr');
                var data = table.row(tr).data();

                $.getJSON(AUDIT_URL, {'userId': data.userId, 'script': data.relativePath}, function (response) {
                    scriptEditor.getSession().setValue(response.script);

                    if (response.data.length) {
                        dataEditor.getSession().setValue(response.data);

                        GroovyConsole.showData();
                    } else {
                        GroovyConsole.hideData();
                    }

                    GroovyConsole.reset();
                    GroovyConsole.showResult(response);

                    $('html, body').animate({scrollTop: 0});
                });
            });

            tableBody.on('click', 'td.download-record', function () {
                var tr = $(this).closest('tr');
                var data = table.row(tr).data();

                window.location = data.downloadUrl;
            });

            tableBody.on('click', 'td.delete-record', function () {
                var tr = $(this).closest('tr');
                var data = table.row(tr).data();

                $.ajax({
                    url: AUDIT_URL + '?' + $.param({
                        'userId': data.userId,
                        'script': data.relativePath
                    }),
                    traditional: true,
                    type: 'DELETE'
                }).done(function () {
                    GroovyConsole.Audit.showAlert('.alert-success', 'Audit record deleted successfully.');
                    GroovyConsole.Audit.refreshAuditRecords();
                }).fail(function () {
                    GroovyConsole.Audit.showAlert('.alert-danger', 'Error deleting audit record.');
                });
            });

            $('#delete-all-modal').find('.btn-warning').click(function () {
                $.ajax({
                    url: AUDIT_URL,
                    type: 'DELETE'
                }).done(function () {
                    GroovyConsole.Audit.showAlert('.alert-success', 'Audit records deleted successfully.');
                    GroovyConsole.Audit.refreshAuditRecords();
                }).fail(function () {
                    GroovyConsole.Audit.showAlert('.alert-danger', 'Error deleting audit records.');
                }).always(function () {
                    $('#delete-all-modal').modal('hide');
                });
            });
        },

        initializeDatePicker: function () {
            var dateRange = $('#date-range');

            dateRange.daterangepicker({
                maxDate: moment()
            }, function(start, end) {
                var startDate = start.format('YYYY-MM-DD');
                var endDate = end.format('YYYY-MM-DD');

                GroovyConsole.Audit.loadAuditRecords(startDate, endDate);
            });

            dateRange.val('');
            dateRange.attr('placeholder', 'Date Range');

            $('#date-range-clear').click(function () {
                dateRange.val('');

                GroovyConsole.Audit.refreshAuditRecords();
            });
        },

        refreshAuditRecords: function () {
            table.ajax.url(AUDIT_URL).load(function (json) {
                if (json.data.length) {
                    $('.delete-all').removeClass('hidden');
                } else {
                    $('.delete-all').addClass('hidden');
                }
            });
        },

        loadAuditRecords: function (startDate, endDate) {
            var params = $.param({'startDate': startDate, 'endDate': endDate});

            table.ajax.url(AUDIT_URL + '?' + params).load();
        },

        showAlert: function (selector, text) {
            var alert = $('#history ' + selector);

            alert.text(text).fadeIn('fast');

            setTimeout(function () {
                alert.fadeOut('slow');
            }, 3000);
        }
    };
}();

$(function () {
    GroovyConsole.Audit.initialize();
    GroovyConsole.Audit.initializeDatePicker();
});