cob.custom.customize.push(function(core, utils, ui) {  
    function valueNotBetween5(value) {
        const valueNumb = Number(value)
        return !(valueNumb > 0 && valueNumb < 6)
    }
    core.validateInstances("Risk Analysis", function(instance, successCb, failCb) {
      const errorMsg = "Value must be in the interval [1;5]"
      let arr = [instance.findFields("Impacto na Produtividade")[0],
      instance.findFields("Impacto no Custo da Resposta")[0],
      instance.findFields("Impacto Legal")[0],
      instance.findFields("Probabilidade")[0]]
      let field;  
      for (let index in arr) {
        field = arr[index]
        // code block to be executed
        if(valueNotBetween5(field.value)){
            failCb([{ fieldId:field.id, localizedMessage: "required.done.invalid_interval", l10nSource: "risk_analysis"}]);
            return
        }
      }      
      successCb()
    });
  });	