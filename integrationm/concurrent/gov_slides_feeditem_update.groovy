import org.codehaus.jettison.json.*;
import groovy.transform.Field

@Field DEF_FEEDITEM = "Feed Item";

// 0 - feed item id
log.info("### FEEDITEM UPDATE STARTED ###")
try {
def concurr_args = argsMap.myArguments
if(concurr_args.size >= 1) {
    def updates = [:]
    updates["Visto"] = "Sim"

    def updateRes = recordm.update(DEF_FEEDITEM,concurr_args[0], updates)
}
} catch (Exception e) {
    return json(500, [msg: e.getMessage()]);
}


log.info("### FEEDITEM UPDATE ENDED ###")