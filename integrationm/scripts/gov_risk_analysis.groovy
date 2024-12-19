import groovy.transform.Field


@Field DEF_RISKANALYSIS = "Risk Analysis";
@Field DEF_RISK = "Risk";

if (msg.product == "recordm" && msg.type == DEF_RISKANALYSIS && msg.user != "integrationm") {
    switch (msg.action) {
        case "add":
            recordm.update(DEF_RISK, "id:"+msg.value("Risk"), build_risk_updates(msg)).getResponse()
            break;
        case "update":
            // We get make sure the updated risk analysis is the latest / most recent one for its target risk
            def query_options = [:]
            query_options["size"] = 1
            query_options["sort"] = "data_análise:desc"
            def results = recordm.search(DEF_RISKANALYSIS,"risk:"+msg.value("Risk"),query_options).getHits()
            if (msg.id == results[0].value("id")) {
                recordm.update(DEF_RISK, "id:"+msg.value("Risk"), build_risk_updates(msg))
            }
            break;
        case "delete":
            log.info("Risk Analysis deletion not implemented.")
            break;
    }
}

def build_risk_updates(msg) {
    def updates = [:]
    updates["Data Última Análise"] = ""+msg.value("Data Análise")
    updates["Data Próxima Análise"] = ""+msg.value("Data Próxima Análise")
    updates["Conclusão Última Análise"] = ""+msg.value("Conclusão Análise")
    updates["Data Limite Implementação"] = ""+msg.value("Data Limite Implementação")
    updates["Classificação Risco"] = getRiskClassification(msg.value("Classificação Risco"))
    return updates
}


def getRiskClassification(risk_value) {
    def risk_value_int = Math.round(Double.parseDouble(risk_value))
    if (1 <= risk_value_int && risk_value_int <= 2) {
        log.info("very low")
        return "Very Low"
    }
    if (3 <= risk_value_int && risk_value_int <= 4) {
        log.info("low")
        return "Low"
    }
    if (5 <= risk_value_int && risk_value_int <= 11) {
        log.info("medium")
        return "Medium";
    }
    if (12 <= risk_value_int && risk_value_int <= 18) {
        log.info("high")
        return "High"
    }
    if (19 <= risk_value_int && risk_value_int <= 25) {
        log.info("very high")
        return "Very High"
    }
}
