if (msg.product == "recordm" && msg.type == "Finding" && msg.action == "update") {

    def updates = ["id": "" + msg.instance.id]

    if (msg.field("Estado").changed()) {
        if (msg.value("Estado") == "Em Resolução") {
            updates << ["Data de início resolução": "" + new Date().time]

        } else if (msg.value("Estado") == "Resolvido") {
            updates << ["Data de resolução": "" + new Date().time]
        }
    }

    createOrUpdateInstance("Finding", updates)

    log.info("Finished processing Finding ${msg.id}.")
}

// ====================================================================================================
// GENERIC SUPPORT METHODS
// ====================================================================================================

def createOrUpdateInstance(definitionName, instance) {
    if (instance.id) {
        // Update mas apenas se tiver mais que 1 campo (ou seja, excluindo o id)
        if (instance.size() > 1) {
            recordm.update(definitionName, "recordmInstanceId:" + instance["id"], instance)
        }
    } else {
        // Create
        recordm.create(definitionName, instance)
    }
}
