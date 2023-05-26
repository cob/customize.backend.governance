// vim: et sw=4 ts=4

import org.apache.commons.logging.*
import org.codehaus.jettison.json.*
import com.fasterxml.jackson.databind.ObjectMapper;

log = LogFactory.getLog("GOVERNANCE Finding");
rmRest = actionPacks.get("rmRest")

// ====================================================================================================
//  MAIN LOGIC - START
// ====================================================================================================

if( msg.product == "recordm" && msg.type == "Finding") {
    if(msg.action == "add") {
        avaliaProcessosWorkm("create")

    } else if(msg.action == "delete") {
        avaliaProcessosWorkm("cancel")

    } else if(msg.action == "update" ) {
        def updates = ["id": ""+msg.instance.id]

        if(estadoMudouParaEmCurso()){
            updates << ["Data de início resolução" : ""+new Date().time]
        } else if(estadoMudouParaResolvido()){
            updates << ["Data de resolução" : ""+new Date().time]
        }

        createOrUpdateInstance("Finding", updates)
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
    def findingFields = msg.instance.fields
    def control       = getInstances("Control", "id:"+valorDoCampo(findingFields, "Control") )[0]
    def instanceId    = msg.instance.id;

    def proccessActionsIdx = 0
    control[_("Acção Complementar")].eachWithIndex { action, idx ->
        if (action == "Lançar Processo por Inconformidade") {
            def workmMap = [:]
            workmMap << ["processKey" : control[_("Processo")][proccessActionsIdx++] ]
            workmMap << ["externalId" : instanceId ]

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
    def oldEstado = valorDoCampo(msg.oldInstance.fields, "Estado")
    def estado = valorDoCampo(msg.instance.fields, "Estado")

    return oldEstado != estado && estado == "Em Resolução";
}

// ----------------------------------------------------------------------------------------------------
//  estadoMudouParaResolvido
// ----------------------------------------------------------------------------------------------------
def estadoMudouParaResolvido(){
    def oldEstado = valorDoCampo(msg.oldInstance.fields, "Estado")
    def estado = valorDoCampo(msg.instance.fields, "Estado")

    return oldEstado != estado && estado == "Resolvido";
}

// ====================================================================================================
// GENERIC SUPPORT METHODS
// ====================================================================================================

// ----------------------------------------------------------------------------------------------------
//  getInstances - Para um dado Nome de definição e um filtro obtem array com instâncias
// ----------------------------------------------------------------------------------------------------
def getInstances(nomeDefinicao, query){
    return getInstancesById( getDefinitionId(nomeDefinicao), query )
}
// --------------------------------------------------------------------
def getInstancesById(idDefinicao, query){
    return getInstancesPaged(idDefinicao, query, 0, null)
}
// --------------------------------------------------------------------
def getInstancesPaged(idDefinicao, query, from, numInstancias){
    def result = []

    def size = numInstancias != null
            ? numInstancias
            : 500

    def resp = rmRest.get(
            "recordm/definitions/search/" + idDefinicao,
            [
                    'q': query.toString(),
                    'from': "" + from,
                    'size': "" + size
            ],
            "")

    if(resp !="NOT_OK"){
        JSONObject esResult = new JSONObject(resp)
        def totalResults = esResult.hits.total.value.toInteger()
        def hits = esResult.hits.hits
        def numResults = hits.length()

        if( totalResults > 0){
            result.addAll(esSourceList(hits))

            if(numResults == size){
                result.addAll(getInstancesPaged(idDefinicao, query, from+size, size))
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

        sourceList.add(recordmJsonToMap(hit._source.toString()))
    }

    return sourceList
}
// --------------------------------------------------------------------
def recordmJsonToMap(content){
    ObjectMapper mapper = new ObjectMapper()

    return mapper.readValue(content,HashMap.class)
}
// --------------------------------------------------------------------

// ----------------------------------------------------------------------------------------------------
//  getDefinitionId - Obtem o id de uma definição a partir do Nome da mesma
// ----------------------------------------------------------------------------------------------------
def getDefinitionId(definitionName){
    def resp = rmRest.get("recordm/definitions/name/" + definitionName, "")

    if(resp != "NOT_OK"){
        JSONObject definition = new JSONObject(resp)

        return definition.id
    }
    return null
}

// ----------------------------------------------------------------------------------------------------
//  createOrUpdateInstance
// ----------------------------------------------------------------------------------------------------
def createOrUpdateInstance(definitionName, instance) {
    if(instance.id) {
        // Update mas apenas se tiver mais que 1 campo (ou seja, excluindo o id)
        if(instance.size() > 1) {
            actionPackManager.applyActionPackWith("recordm", "update", definitionName, "recordmInstanceId:" + instance["id"], instance)
        }
    } else {
        // Create
        actionPackManager.applyActionPackWith("recordm", "insert", definitionName, "", instance)
    }
}

// ----------------------------------------------------------------------------------------------------
//  toEsName (nome reduzido para "_")  - Converte um nome de campo RecordM no seu correspondente no ES
// ----------------------------------------------------------------------------------------------------
def _(fieldName){
    return fieldName.toLowerCase().replace(" ", "_")
}

// ----------------------------------------------------------------------------------------------------
//  valorDoCampo
// ----------------------------------------------------------------------------------------------------
def valorDoCampo(fields, name){
    return fields.find{it.fieldDefinition.name == name}?.value
}
