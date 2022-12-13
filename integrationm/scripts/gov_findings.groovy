// vim: et sw=4 ts=4
import org.json.*
import com.fasterxml.jackson.databind.ObjectMapper;

rmRest = actionPacks.get("rmRest")

// ====================================================================================================
// CONFIGURATION VARIABLES
// ====================================================================================================


// ====================================================================================================
//  MAIN LOGIC - START
// ====================================================================================================

if( msg.product == "recordm" && msg.type == "Finding") {
    if(msg.action == "add") {
        avaliaProcessosWorkm("create")

    } else if(msg.action == "delete") {
        avaliaProcessosWorkm("cancel")

    } else if(msg.action == "update" ) {
        def updates = [:];

        if(estadoMudouParaEmCurso()){
            updates["Data de início resolução"] = ""+new Date().time;
        } else if(estadoMudouParaResolvido()){
            updates["Data de resolução"] = ""+new Date().time;
        }

        recordm.update(definitionName, msg.instance.id, instance);
    }

    log.info ("Finished processing Finding ${msg.id}.")
}

// ====================================================================================================
//  MAIN LOGIC - END
// ====================================================================================================


// ====================================================================================================
// MAIN LOGIC SUPPORT METHODS
// ====================================================================================================

// ----------------------------------------------------------------------------------------------------
//  processosWorkm -  serve para lançar ou cancelar varios processos configurados
// ----------------------------------------------------------------------------------------------------
def avaliaProcessosWorkm(processAction) {
    def control       = getInstances("Control", "id:"+msg.value("Control") )[0]
    def proccessActionsIdx = 0
    control[_("Acção Complementar")].eachWithIndex { action, idx ->
        if (action == "Lançar Processo por Inconformidade") {
            def workmMap = [:]
            workmMap << ["processKey" : control[_("Processo")][proccessActionsIdx++] ]
            workmMap << ["externalId" : msg.instance.id]

            def result
            if(processAction == "create") {
                result = actionPackManager.applyActionPackWith("workm", "createProcessInstance", null, null, workmMap)
            } else if (processAction == "cancel"){
                result = actionPackManager.applyActionPackWith("workm", "cancelProcess", null, null, workmMap)
            }
            if (result) {
                log.info("Process ${processAction} successfull {{ args: ${workmMap}}}")
            } else {
                log.error("Process ${processAction} failled {{ args: ${workmMap}}}")
            }
        }
    }
}

// ----------------------------------------------------------------------------------------------------
//  estadoMudouParaEmCurso
// ----------------------------------------------------------------------------------------------------
def estadoMudouParaEmCurso(){
    return msg.field("Estado").changedTo("Em Resolução");
}

// ----------------------------------------------------------------------------------------------------
//  estadoMudouParaResolvido
// ----------------------------------------------------------------------------------------------------
def estadoMudouParaResolvido(){
    return msg.field("Estado").changedTo("Resolvido");
}

// ====================================================================================================
// GENERIC SUPPORT METHODS
// ====================================================================================================

// ----------------------------------------------------------------------------------------------------
//  getInstances - Para um dado Nome de definição e um filtro obtem array com instâncias
// ----------------------------------------------------------------------------------------------------
def getInstances(nomeDefinicao, query){
    return getInstancesPaged( nomeDefinicao, query, 0, null)
}
// --------------------------------------------------------------------
def getInstancesPaged(nomeDefinicao, query, from, numInstancias){
    def result = []

    def size = numInstancias != null
                ? numInstancias
                : 500

    def resp = rmRest.get(
            "recordm/definitions/search/",
            [
                'def': nomeDefinicao,
                'q': query.toString(),
                'from': "" + from,
                'size': "" + size
            ],
            "")

    if(resp !="NOT_OK"){
        JSONObject esResult = new JSONObject(resp)
        def totalResults = esResult.hits.total.toInteger()
        def hits = esResult.hits.hits
        def numResults = hits.length()

        if( totalResults > 0){
            result.addAll(esSourceList(hits))

            if(numResults == size){
                result.addAll(getInstancesPaged(nomeDefinicao, query, from+size, size))
            }
        }
    }

    return result
}
// --------------------------------------------------------------------
def esSourceList(hits){
    def sourceList = []

    for(int index = 0; index < hits.length(); index++){
        def hit = hits.getJSONObject(index)

        sourceList.add(hit._source.toMap())
    }

    return sourceList
}
// --------------------------------------------------------------------

// ----------------------------------------------------------------------------------------------------
//  toEsName (nome reduzido para "_")  - Converte um nome de campo RecordM no seu correspondente no ES
// ----------------------------------------------------------------------------------------------------
def _(fieldName){
    return fieldName.toLowerCase().replace(" ", "_")
}
