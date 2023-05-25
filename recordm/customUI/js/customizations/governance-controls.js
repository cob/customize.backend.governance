cob.custom.customize.push(function(core, utils, ui){
    core.customizeInstances("Control", function(instance, presenter){
        //Por enaquanto isto só serve para por o botao de fazer assessments e isso é só em edit; podemos sair já.
        if(instance.isNew() || !instance.canUpdate()) return;

        let instanceInitialValues = {
            definicao : instance.findFields("Definição")[0].value,
            filtro    : instance.findFields("Filtro")[0].value,
            condicao  : instance.findFields("Condição de sucesso")[0].value
        };

        let $btn = $('<button class="btn btn-primary" style="width: 148px;" data-loading-text="Realizando avaliação..." autocomplete="off">Actualizar Avaliação (Assess)</button>');

        $('.js-save-instance').parent().append($btn);

        $btn.click(function (e) {
            e.preventDefault();

            if (!_isValid()) return false;
            $btn.button('loading');

            _buildAndSendMessageToIM(function(){
                $btn.button('reset')
            });
        });

        let _isValid = function(){
            let errorMsg="";

            if (instanceInitialValues.definicao != presenter.findFieldPs("Definição")[0].getValue()) {
                errorMsg += " * Definição <br>";
            }

            if (instanceInitialValues.filtro != presenter.findFieldPs("Filtro")[0].getValue()) {
                errorMsg += " * Filtro <br>";
            }

            if (instanceInitialValues.condicao != presenter.findFieldPs("Condição de sucesso")[0].getValue()) {
                errorMsg += " * Condição de sucesso <br>";
            }

            if(errorMsg !=""){
                ui.notification.showError("Control alterado: <br> "+errorMsg+"Gravar primeiro as alterações.", true);
                return false;
            }

            return true;
        }
        let _buildAndSendMessageToIM = function(successCb){
            let msg = {
                "product":"governance",
                "type":"controlUI",
                "action":"forceAssessment",
                "user":core.getCurrentLoggedInUser(),
                "id": instance.data.id
            };

            $.ajax({
                url: "/integrationm/msgs/",
                data : JSON.stringify(msg),
                dataType: 'json',
                type: "POST",
                xhrFields: { withCredentials: true },
                cache: false,
                success: function() {
                    ui.notification.showInfo('Avaliação do Control concluida.');
                    utils.delayed(successCb,1000);
                },
                error:function(e) {
                    window.console.log("ERRO a avaliar o Control.",e);
                    ui.notification.showError("ERRO a avaliar o Control.");
                }
            });
        }

    })
});