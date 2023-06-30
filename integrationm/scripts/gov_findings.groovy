// vim: et sw=4 ts=4

import org.apache.commons.logging.*

log = LogFactory.getLog("GOVERNANCE Finding");

// ====================================================================================================
//  MAIN LOGIC - START
// ====================================================================================================

if( msg.product == "recordm" && msg.type == "Finding") {
    if(msg.action == "update" ) {
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
//  createOrUpdateInstance
// ----------------------------------------------------------------------------------------------------
def createOrUpdateInstance(definitionName, instance) {
    if(instance.id) {
        // Update mas apenas se tiver mais que 1 campo (ou seja, excluindo o id)
        if(instance.size() > 1) {
            recordm.update(definitionName, "recordmInstanceId:" + instance["id"], instance)
        }
    } else {
        // Create
        recordm.create(definitionName, instance)
    }
}

// ----------------------------------------------------------------------------------------------------
//  valorDoCampo
// ----------------------------------------------------------------------------------------------------
def valorDoCampo(fields, name){
    return fields.find{it.fieldDefinition.name == name}?.value
}
