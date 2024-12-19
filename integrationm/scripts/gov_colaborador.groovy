
import groovy.transform.Field

@Field DEF_COLAB = "Colaborador GOV";
@Field DEF_CONTENT = "Conteúdos";
@Field DEF_FEED_ITEM = "Feed Item";

// Feed item creation upon user/Colaborador creation/update (activation)
if (msg.product == "recordm" && msg.type == DEF_COLAB && msg.user != "integrationm") {
    switch (msg.action) {
        case "add":
            log.info("=== Starting ADD FEED ITEM TO USER ===");
            searchContentsAndAddFeedItem([username: msg.value("Username"),
                                          colaboratorId: msg.id,
                                          perfil: msg.value("Perfil")])
            log.info("=== Ending ADD FEED ITEM TO USER ===");
            break;
        case "delete":
            recordm.delete(DEF_FEED_ITEM, "colaborador:" + msg.id);
            break;
    }

}


def searchContentsAndAddFeedItem(userMap) {
    recordm.stream(DEF_CONTENT, "perfil:"+userMap["perfil"], { hit ->
        def feedItem = [
                "Colaborador"        : "" + userMap['colaboratorId'],
                "username"           : userMap['username'],
                "Conteúdo"           : "" + hit.id,
                "Tipo Conteúdo"      : hit.value("Tipo") ?: "",
                "Título Conteúdo"    : hit.value("Título") ?: "",
                "Resumo Conteúdo"    : hit.value("Resumo") ?: "",
                "markdown Conteúdo"  : hit.value("Markdown Apresentação") ?: "",
                "urlToImage Conteúdo": hit.value("urlToimage") ?: "",
                "Orderm Conteúdo"    : hit.value("Ordem") ?: "",
                "Categoria Conteúdo" : hit.value("Categoria") ?: "",
                "Visivel Conteudo"   : hit.value("Visível") ?: "",
                "Data criação"       : "" + new Date().time
        ];

        recordm.create(DEF_FEED_ITEM, feedItem);
    })
}