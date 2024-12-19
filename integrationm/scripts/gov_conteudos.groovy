import groovy.transform.Field

@Field DEF_COLAB = "Colaborador GOV";
@Field DEF_CONTENT = "Conteúdos";
@Field DEF_FEED_ITEM = "Feed Item";

if (msg.product == "recordm" && msg.type == DEF_CONTENT && msg.user != "integrationm") {
    switch (msg.action) {
        case "add":
            log.info("=== Starting ADD FEED ITEM FROM CONTENT ===");
            // get all users and create feed item for them
            searchUsersAndAddFeeditem(msg, msg.value("Perfil"))
            log.info("=== Ending ADD FEED ITEM FROM CONTENT ===");
            break;
        case "update":
            if (msg.field("Perfil").changed()) {
                def oldContent = msg.getOldInstance()
                def oldProfiles = oldContent.value("Perfil").split("\u0000")
                def currProfiles = msg.value("Perfil").split("\u0000")
                def newProfiles = currProfiles - oldProfiles
                def removedProfiles = oldProfiles - currProfiles

                // Add new feed items for users with new profiles
                if(newProfiles.size() > 0) {
                    searchUsersAndAddFeeditem(msg,newProfiles.join("\u0000"))
                }

                // Get all users with removed profiles and delete the
                // feeditems for the current content
                if(removedProfiles.size() > 0) {
                    searchUsersAndDeleteFeedItem(msg, removedProfiles.join("\u0000"))
                }
            }
            break;
        case "delete":
            recordm.delete(DEF_FEED_ITEM, "conteúdo:" + msg.id);
            break;
    }

}

def searchUsersAndAddFeeditem(msg, profiles) {
    def query_perfil = "("+ profiles.replace("\u0000"," OR ") + ")"

    recordm.stream(DEF_COLAB, "perfil:"+query_perfil, { hit ->
        def feedItem = [
                "Colaborador"        : "" + hit.id,
                "username"           : hit.value('username'),
                "Conteúdo"           : "" + msg.id,
                "Tipo Conteúdo"      : msg.value("Tipo") ?: "",
                "Título Conteúdo"    : msg.value("Título") ?: "",
                "Resumo Conteúdo"    : msg.value("Resumo") ?: "",
                "markdown Conteúdo"  : msg.value("Markdown Apresentação") ?: "",
                "urlToImage Conteúdo": msg.value("urlToimage") ?: "",
                "Orderm Conteúdo"    : msg.value("Ordem") ?: "",
                "Categoria Conteúdo" : msg.value("Categoria") ?: "",
                "Visivel Conteudo"   : msg.value("Visível") ?: "",
                "Data criação"       : "" + new Date().time
        ];

        recordm.create(DEF_FEED_ITEM, feedItem);
    })
}

def searchUsersAndDeleteFeedItem(msg, removedProfiles) {
    def query_perfil = "("+ removedProfiles.replace("\u0000"," OR ") + ")"
    recordm.stream(DEF_COLAB, "perfil:"+query_perfil, { hit ->
        def fi_delete_query = "conteúdo:$msg.id AND colaborador:$hit.id"
        recordm.delete(DEF_FEED_ITEM, fi_delete_query);
    })
}

