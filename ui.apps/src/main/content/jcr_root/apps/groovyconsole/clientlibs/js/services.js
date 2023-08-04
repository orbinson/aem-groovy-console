$(function () {
    $.getJSON('/bin/groovyconsole/services', function (services) {
        $('#services-list').typeahead({
            source: Object.keys(services),
            updater: function (key) {
                var declaration = services[key];

                scriptEditor.navigateFileEnd();

                if (scriptEditor.getCursorPosition().column > 0) {
                    scriptEditor.insert('\n\n');
                }

                if ((event.keyCode === 13 || event.which === 13)) {
                    scriptEditor.insert(declaration);
                }
                return '';
            }
        });

        $('#btn-group-services').fadeIn('fast');
    });
});
