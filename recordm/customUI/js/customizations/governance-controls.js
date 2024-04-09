cob.custom.customize.push(function (core, utils, ui) {
    core.customizeInstances("Control", function (instance, presenter) {
        //Por enaquanto isto só serve para por o botao de fazer assessments e isso é só em edit; podemos sair já.
        // || instance.findFields("Assessment Tool")[0].value == "Manual"
        if (instance.isNew() || !instance.canUpdate()) return;


        let instanceInitialValues = {
            definicao: instance.findFields("Definição")[0].value,
            filtro: instance.findFields("Filtro")[0].value,
            assessment_tool: instance.findFields("Assessment Tool")[0].value,
            condicao: instance.findFields("Condição de sucesso")[0]?.value
        };

        let $btn = $('<button class="btn btn-primary" style="width: 148px;" data-loading-text="Realizando avaliação..." autocomplete="off">Actualizar Avaliação (Assess)</button>');

        $('.js-save-instance').parent().append($btn);

        $btn.click(function (e) {
            e.preventDefault();

            if (!_isValid()) return false;
            $btn.button('loading');

            _buildAndSendMessageToIM(function () {
                $btn.button('reset')
            });
        });

        if (instanceInitialValues.assessment_tool == "Manual") {
            let $btn_questionarios = $('<button class="btn" style="width: 148px;" data-loading-text="Criando questionários..." autocomplete="off">Criar Questionários</button>');
            $btn.parent().append($btn_questionarios);


            $btn_questionarios.click(function (e) {
                e.preventDefault();
    
                if (!_isValid()) return false;
                $btn_questionarios.button('loading');
    
                _buildAndSendMessageToIMQuestions(function () {
                    $btn_questionarios.button('reset')
                });
            });
    
        }

        let _isValid = function () {
            let errorMsg = "";

            if (presenter.findFieldPs("Definição")[0].getValue() && instanceInitialValues.definicao != presenter.findFieldPs("Definição")[0].getValue()) {
                errorMsg += " * Definição <br>";
            }

            if (presenter.findFieldPs("Filtro")[0].getValue() && instanceInitialValues.filtro != presenter.findFieldPs("Filtro")[0].getValue()) {
                errorMsg += " * Filtro <br>";
            }

            if (presenter.findFieldPs("Condição de sucesso")[0]?.getValue() && instanceInitialValues.condicao != presenter.findFieldPs("Condição de sucesso")[0]?.getValue()) {
                errorMsg += " * Condição de sucesso <br>";
            }

            if (presenter.findFieldPs("Assessment Tool")[0].getValue() && instanceInitialValues.assessment_tool != presenter.findFieldPs("Assessment Tool")[0].getValue()) {
                errorMsg += " * Assessment Tool <br>";
            }

            if (errorMsg != "") {
                ui.notification.showError("Control alterado: <br> " + errorMsg + "Gravar primeiro as alterações.", true);
                return false;
            }

            return true;
        }
        let _buildAndSendMessageToIM = function (successCb) {
            let msg = {
                "product": "governance",
                "type": "controlUI",
                "action": "forceAssessment",
                "user": core.getCurrentLoggedInUser(),
                "id": instance.data.id
            };

            $.ajax({
                url: "/integrationm/msgs/",
                data: JSON.stringify(msg),
                dataType: 'json',
                type: "POST",
                xhrFields: { withCredentials: true },
                cache: false,
                success: function () {
                    ui.notification.showInfo('Avaliação do Control concluida.');
                    utils.delayed(successCb, 1000);
                },
                error: function (e) {
                    window.console.log("ERRO a avaliar o Control.", e);
                    ui.notification.showError("ERRO a avaliar o Control.");
                }
            });
        }


        let _buildAndSendMessageToIMQuestions = function (successCb) {
            let msg = {
                "product": "governance",
                "type": "controlUI",
                "action": "forceQuestions",
                "user": core.getCurrentLoggedInUser(),
                "id": instance.data.id
            };

            $.ajax({
                url: "/integrationm/msgs/",
                data: JSON.stringify(msg),
                dataType: 'json',
                type: "POST",
                xhrFields: { withCredentials: true },
                cache: false,
                success: function () {
                    ui.notification.showInfo('Avaliação do Control concluida.');
                    utils.delayed(successCb, 1000);
                },
                error: function (e) {
                    window.console.log("ERRO a avaliar o Control.", e);
                    ui.notification.showError("ERRO a avaliar o Control.");
                }
            });
        }

    })


    core.validateInstances("Control", function (instance, successCb, failCb) {
        const periodicidadeF = instance.findFields("Periodicidade")[0]
        const periodo_perguntasF = instance.findFields("Período Lançamento de Perguntas")[0]
        const dia_do_mes = instance.findFields("Dia do Mês")[0]

        if (periodo_perguntasF) {
            if (periodicidadeF.value == "Semanal") {
                if (periodo_perguntasF.value > 7 || periodo_perguntasF.value < 0) {
                    failCb([{ fieldId: periodo_perguntasF.id, localizedMessage: "required.done.week_period_greater", l10nSource: "controls" }]);
                    return
                }
            }
            if (periodicidadeF.value == "Mensal") {
                if (dia_do_mes && (dia_do_mes.value <= 0 || dia_do_mes.value > 29)) {
                    failCb([{ fieldId: dia_do_mes.id, localizedMessage: "required.done.monthly_day_range", l10nSource: "controls" }]);
                    return
                }
                if (periodo_perguntasF.value > 29 || periodo_perguntasF.value < 0) {
                    failCb([{ fieldId: periodo_perguntasF.id, localizedMessage: "required.done.monthly_period_greater", l10nSource: "controls" }]);
                    return
                }

            }
        } 

        successCb() 
    });
});