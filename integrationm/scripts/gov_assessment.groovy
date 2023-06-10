// vim: et sw=4 ts=4

import org.apache.commons.logging.LogFactory
import org.codehaus.jettison.json.*
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Calendar;
import javax.ws.rs.client.ClientBuilder;

import javax.ws.rs.client.*
import javax.ws.rs.core.Cookie
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

import config.GovernanceConfig

log = LogFactory.getLog("GOVERNANCE Assessment");
rmRest = actionPacks.get("rmRest")
umRest = actionPacks.get("umRest")
now = new Date()

REGEX_VARS_ESPECIAIS = /[;,]?\$([^\$]*)\$[;,]?/

// ====================================================================================================
//  MAIN LOGIC - START  -  As Avaliações (Assessments) acontece em 2 circunstâncias:
//   1) nos instantes previstos pela periodicidade do Control (ver ~/others/GovernanceGlobal/governance_clock.sh)
//   2) ou directamente na interface do Control (ver recordm/customUI/js/cob/governance.js)
// ====================================================================================================
if(    (msg.product == "governance" && msg.type == "clock"     && msg.action == "clockTick")
        || (msg.product == "governance" && msg.type == "controlUI" && msg.action == "forceAssessment") ) {
    log.info ("Start Controls evaluations.")

    // Obtem lista dos controls ligados
    def controls = getInstances("Control", "-periodicidade.raw:Off")

    // Obtem matriz com  todos os pesos por objectivo para depois usar no cálculo da importância relativa de cada assessment
    def pesos = obtemMatrizCompletaDePesos(controls)

    // Para cada um destes controls :
    controls.each{ control ->
        // Se for uma avaliação pedida na interface fazer skip a todos os controls menos a esse id específico
        if(msg.action == "forceAssessment" && control.id != msg.id) return

        // obtem um assessment válido (com a indicação no control de se necessita de ser avaliado agora)
        def assessment = getAssessmentInstance(control, msg.action, pesos)

        // Se control marcado para avaliação então avalia, actualiza resultado do assessment e cria/actualiza findings
        if( control["_marked_ToEval_"] || control["_marked_CollectDeviceMValues_"] ) {
            log.info ("Evaluate Control and gather Assessment info ${control[_("Nome")]} ...")

            //Avalia control e complementa dados do assessment com os resultados !!
            assessment << assessControl(control)

            //Processa acções complementares: envia Emails e SMSs
            executaAccoesComplementares(control, assessment)

            // Se não for necessário actualizar dados remove campo de Data e Observações para não haver alterações na instância desnecessárias
            if( control["_marked_OnlyUpdateDataIfChanged"] && !assessment["_marked_Changed"] ) {
                assessment.remove("Data do Resultado")
                assessment.remove("Observações")
            }
        } else {
            log.info ("Just create (or update if exists) the daily Assessment info ${control[_("Nome")]} ...")
        }

        // cria ou actualiza instância de Assessment
        createOrUpdateInstance("Assessment", assessment)
    }
    log.info ("Finished Controls evaluations.")
}

// ====================================================================================================
//  MAIN LOGIC - END
// ====================================================================================================



// ====================================================================================================
// MAIN LOGIC SUPPORT METHODS
// ====================================================================================================
// ----------------------------------------------------------------------------------------------------
// obtemMatrizCompletaDePesos - retorna matriz com pesos de cada nível e control
// ----------------------------------------------------------------------------------------------------
def obtemMatrizCompletaDePesos(controls){
    def pesos = [:]
    def done = [:]

    controls.each{ control ->

        def g1 = control[_("Id Goal Nível 1")][0]
        def g2 = control[_("Id Goal Nível 2")][0]
        def g3 = control[_("Id Goal Nível 3")][0]
        def c = control.id

        // Obtem peso de cada nível. Caso seja 0 põe valor perto de 0 pois queremos contabilizar todos os elementos.
        float p1 = (control[_("Peso Goal Nível 1")][0]).toInteger()?:0.00001
        float p2 = (control[_("Peso Goal Nível 2")][0]).toInteger()?:0.00001
        float p3 = (control[_("Peso Goal Nível 3")][0]).toInteger()?:0.00001
        float pC = (control[_("Peso")][0]).toInteger()?:0.00001

        if(!done[g1]) {
            done[g1]=true
            pesos["global"] = pesos["global"]  ? pesos["global"] + p1 : p1
        }
        if(!done[g2]) {
            done[g2]=true
            pesos[g1] = pesos[g1] ? pesos[g1] + p2 : p2
        }
        if(!done[g3]) {
            done[g3]=true
            pesos[g2] = pesos[g2] ? pesos[g2] + p3 : p3
        }
        if(!done[c]) {
            done[c]=true
            pesos[g3] = pesos[g3] ? pesos[g3] + pC : pC
        }
    }
    return pesos
}

// ----------------------------------------------------------------------------------------------------
//  getAssessmentInstance - obtem assessment valido actual e cria se não existir. Marca se for para avaliar.
// ----------------------------------------------------------------------------------------------------
def getAssessmentInstance(control,runType, pesos) {

    // Obtem último assessment ainda válido feito para este control
    def lastValidAssessment =  getLastValidAssessment(control)

    // Avalia existência, datas, periodicidade para decidir se se copia dados do assessment ou se se cria um novo
    def assessment = copyOrCreateAssessment(control, lastValidAssessment, runType)

    // Obtem hierarquia de objectivos do control e respectivos pesos relativos
    def g1 = control[_("Id Goal Nível 1")][0]
    def g2 = control[_("Id Goal Nível 2")][0]
    def g3 = control[_("Id Goal Nível 3")][0]
    def p1 = control[_("Peso Goal Nível 1")][0]
    def p2 = control[_("Peso Goal Nível 2")][0]
    def p3 = control[_("Peso Goal Nível 3")][0]
    def p4 = control[_("Peso")][0]

    // Completa preenchimento dos dados do assessment
    assessment << ["Control":         "" + control[_("Nome")][0]]
    assessment << ["Id Control":      "" + control.id]
    assessment << ["Âmbito":          "" + control[_("Âmbito")][0]?: ""]

    assessment << ["Goal Nível 1":    "" + control[_("Nome Goal Nível 1")][0]]
    assessment << ["Id Goal Nível 1": "" + g1 ]

    assessment << ["Goal Nível 2":    "" + control[_("Nome Goal Nível 2")][0]]
    assessment << ["Id Goal Nível 2": "" + g2 ]

    assessment << ["Goal Nível 3":    "" + control[_("Nome Goal Nível 3")][0]]
    assessment << ["Id Goal Nível 3": "" + g3 ]

    // Calcula peso relativo de cada nível (usando o total de pesos por nível guardado na matriz) - multiplica por 100 para não provocar arredondamentos imprevistos
    p1 = p1 ? p1.toInteger() * 100 / pesos["global"] : 0
    p2 = p2 ? p2.toInteger() * 100 / pesos[""+g1] : 0
    p3 = p3 ? p3.toInteger() * 100 / pesos[""+g2] : 0
    p4 = p4 ? p4.toInteger() * 100 / pesos[""+g3] : 0
    float pglobal = p1*p2*p3*p4/(100*100*100)
    pglobal = pglobal?:0.001 //Caso o peso global seja 0, porque um ou mais dos elementos avaliados tem peso 0, substituimos o valor calculado por um valor muito muito baixo, mas diferente de zero

    // Atribui peso global relativo ao control. A relação com qq outro control estará assim regulada. - dividido por 100*100*100 para compensar as multiplicações
    assessment << ["Peso Global": "" + pglobal ]

    return assessment
}
// ----------------------------------------------------------------------------------------------------
def getLastValidAssessment(control) {
    def assessment = [:]

    def limiteInferiorData
    switch (control[_("Periodicidade")][0]) {
        case "Mensal":  limiteInferiorData = "M"; break;
        case "Semanal": limiteInferiorData = "w"; break;
        default:        limiteInferiorData = "d"; break;  // Diário e 15/15m
    }

    // Obtem offset em horas da timezone para corrigir pesquisa ao ES
    def cal = Calendar.instance
    Date date = cal.getTime()
    TimeZone tz = cal.getTimeZone()
    long msFromEpochGmt = date.getTime() - ((8 * 60) + 30 ) * 60 * 1000 //Só muda o cálculo às 8h30
    int offsetFromUTC = tz.getOffset(msFromEpochGmt)/3600000

    // Obtem registo mais recente (primeiro dos resultados) de assessment válidos no RecordM. data.date:>=now-8h-30m+1h\/d+8h+30m-1h
    // o cálculo é: 'now' menos 'offsetUTC' (para não considerar o dia errado, que provoca engano nas semanas e meses) arredondado ao periodo (d | w | M) e deslocado para as 8h30 (com a devida correcção de 'offsetUTC')
    def lastValidAssessmentFilter = "${_("Id Control")}.raw:${control.id} AND ${_("Data do Resultado")}.date:>=now-8h-30m+${offsetFromUTC}h\\/${limiteInferiorData}+8h+30m-${offsetFromUTC}h".toString();
    return getInstances("Assessment", lastValidAssessmentFilter)[0]
}
// ----------------------------------------------------------------------------------------------------
def copyOrCreateAssessment(control, lastValidAssessment, runType) {
    def assessment = [:]
    def periodicidade = control[_("Periodicidade")][0]
    def today = new Date(new Date(now.time).time - new Date((8*60+30)*60000).time).clearTime()
    today.set(hourOfDay: 8, minute: 30)
    today = ""+today.time

    if(lastValidAssessment) {
        if(lastValidAssessment[_("Data")][0] == today) {
            // Se a data da assessment anterior é igual à actual mantem o id (ou seja actualiza o assessment existente) caso contrário cria um novo
            assessment << ["id":   "" + lastValidAssessment.id]

            // Verifica se há comandos devicem com resultados por avaliar
            if( control[_("Assessment Tool")][0] == "DeviceM" ) {
                if( getFirstValue(lastValidAssessment,_("DeviceM JobID")) != null ) {
                    control << ["_marked_CollectDeviceMValues_":   lastValidAssessment[_("DeviceM JobID")][0] ]
                }
            }
        } else {
            assessment << ["Data": today]
        }

        assessment << ["Objectivo":         "" + getFirstValue(lastValidAssessment,_("Objectivo"))?:""]
        assessment << ["Resultado":         "" + getFirstValue(lastValidAssessment,_("Resultado"))?:""]
        assessment << ["Atingimento":       "" + getFirstValue(lastValidAssessment,_("Atingimento"))?:""]
        assessment << ["Data do Resultado": "" + getFirstValue(lastValidAssessment,_("Data do Resultado"))?:""]
        assessment << ["Observações":       "" + getFirstValue(lastValidAssessment,_("Observações"))?:""]

        // Decompõe o clock tick (arrendondado aos 15m certos)
        def quarterHour = now.getAt(Calendar.MINUTE).intdiv(15)
        def hourOfDay   = now.getAt(Calendar.HOUR_OF_DAY)
        def dayOfWeek   = now.getAt(Calendar.DAY_OF_WEEK)
        def dayOfMonth  = now.getAt(Calendar.DAY_OF_MONTH)

        // Se há um assessment válido só será para reavaliar se:
        if( (runType == "forceAssessment")  // Avaliação pedida explicitamente na interface
                ||
                (periodicidade == "15m" )  // Periodicidade menor que o dia (corre sempre pois é a unidade minima de tempo)
                ||
                (hourOfDay == 8 && quarterHour == 2)  // Se for o início do dia (definido como 8h30) e:
                && (
                periodicidade == "Diária"   // Ou for diário
                        ||
                        (dayOfWeek == 2 && periodicidade == "Semanal") // Ou for Semanal e for segunda(além das 8h30)
                        ||
                        (dayOfMonth == 1 && periodicidade == "Mensal") // Ou for Mensal e primeiro dia do mês (além das 8h30)
        )
        ) {
            control    << ["_marked_ToEval_":    true]
            assessment << ["Data do Resultado": "" + now.time]
            // Se não é novo marca para apenas actualizar a data se a avaliação mudar
            if(assessment["id"]) control << ["_marked_OnlyUpdateDataIfChanged": true]
        }
    } else {
        assessment << ["Data": today]
        // Se algo correr mal a avaliação base é 0
        assessment << ["Atingimento":       "0"]
        assessment << ["Data do Resultado": "" + now.time]
        // Se é um novo assessment é sempre para avaliar
        control    << ["_marked_ToEval_":    true]
    }
    return assessment
}

// ----------------------------------------------------------------------------------------------------
//  assessControl - executa o assessmentTool
// ----------------------------------------------------------------------------------------------------
def assessControl(control){
    def assessment = [
            "Findings":[]
    ]

    // Constroi lista com findings abertos do control (mais performante que pedir um de cada vez)
    def openFindings = obterFindingsAbertos(control)

    //Obtem lista de elementos a avaliar
    def evaluationData
    switch (control[_("Assessment Tool")][0]) {
        case "RecordM" : evaluationData = getEvaluationDataRecordM(control); break
        case "DeviceM" : evaluationData = getEvaluationDataDeviceM(control); break
        case "Manual"  : evaluationData = getEvaluationDataManual(control);  break
    }
    def evaluationList = evaluationData.evalList
    assessment << evaluationData.assessmentInfo

    //Se a resposta já tem o valor de atingimento a zero é porque foi encontrado alguma inconformidade e já trás o erro obtido
    if(assessment["Atingimento"] == "0") { return assessment }

    // Inicializa resultados
    def objectivoTotal    = 0
    def atingimentoTotal  = 0
    def processedFindings = []

    def specialVars = obterVariaveisEspeciaisDoControlo(control);
    def specialVarAssessments = [:];

    // Realiza a avaliação do control contra cada registo especificado
    evaluationList.each { instanceToEval ->
        def previousFinding = openFindings[""+instanceToEval.id];
        def resultado      = evalInstance(control, instanceToEval, previousFinding);
        objectivoTotal    += resultado["Objectivo"]
        atingimentoTotal  += resultado["Atingimento"]

        def finding = createOrUpdateFinding(control, openFindings, instanceToEval, resultado);

        if(finding != null){
            processedFindings += finding
            assessment["Findings"] += finding

            specialVars.each{ specVar ->
                addSpecialAssessMap(specVar, control, instanceToEval, resultado, finding, specialVarAssessments);
            };
        }

        openFindings.remove(""+instanceToEval.id)
    }

    specialVarAssessments.each{ var, assessMap ->
        assessMap.findAll{ it.value["Findings"].size() > 0 }.each{ key, sAssess ->
            sAssess << buildAssessmentResultMap(control, sAssess["Findings"], Double.valueOf(sAssess["Objectivo"]), Double.valueOf(sAssess["Atingimento"]));
        }
    }

    assessment << buildAssessmentResultMap(control, processedFindings, objectivoTotal, atingimentoTotal);

    // Para cada finding aberto que não tenha sido processado (por não fazer mais parte da lista de instâncias a avaliar) repor e indicar remoção
    if(!(control[_("Assessment Tool")][0] == "DeviceM" && control["_marked_ToEval_"])){
        openFindings.each { finding ->
            def resultado = ["Objectivo":"mock_value","Atingimento":"mock_value","Observações":"Instance removed from evaluation filter"]
            def instanceToEval = ["id" : finding.key]
            processedFindings += createOrUpdateFinding(control, openFindings, instanceToEval, resultado)?:[]
        }
    }

    def suspeitos = processedFindings.findAll { !it.containsKey("_marked_Inaltered") && (it.containsKey("Estado") && !it["Estado"].contains("Suspeito")) };
    def markedNew = processedFindings.findAll { it.containsKey("_marked_New") };

    // Se houver alterações aos findingss (ie, se há algum que não esteja _marked_Inaltered) então marca o assessment como alterado para: indicar envio do email de alterações e necessidade de actualizar Observações e Data de Resultado
    if( suspeitos.size() != 0 ) {
        assessment << ["_marked_Changed":    true]

        suspeitos.each{finding ->
            specialVarAssessments.each{ var, assessMap ->
                assessMap.findAll{ it.value["Findings"].contains(finding) }.each{key, sAssess ->
                    sAssess["_marked_Changed"] = true
                }
            }
        }

//TODO: se pelo menos uma action com atraso no aviso, então adiciona campo ao assessment com todos os findings
        if( markedNew.size() != 0 ) {
            assessment << ["_marked_New_Findings":    true]

            markedNew.each{finding ->
                specialVarAssessments.each{ var, assessList ->
                    assessList.findAll{ it.value["Findings"].contains(finding) }.each{key, sAssess ->
                        sAssess["_marked_New_Findings"] = true
                    }
                }
            }
        }
    }

    assessment["Assessments Especiais"] = specialVarAssessments;

    return assessment
}

def obterVariaveisEspeciaisDoControlo(control){
    Set<String> specialVars = new HashSet<>();

    ["Telemóvel", "Email Destino"].each{ fieldName ->
        def value = control.containsKey(_(fieldName))? control[_(fieldName)][0]:null;

        if(value != null){
            def vars = (value =~ REGEX_VARS_ESPECIAIS)

            vars.each{
                specialVars.add(it[1]); //it[1] = Nome da variável SEM delimitadores (ex: $a$ = a)
            };
        }
    }

    return specialVars;
};

def obterValorDeVariavelEspecial(control, specialVar){
    def val = "";
    def condSucessControl = control[_("Condição de sucesso")][0];

    def values = (condSucessControl =~ /["']${specialVar}["']\s*:\s*([^\]]*)/)

    if(values.size() > 0){
        val = values[0][1].replaceAll(/["']/, "");
    }

    return val;
};

def addSpecialAssessMap(specVar, control, instanceToEval, resultado, finding, specialVarAssessments){

    def valor = resultado[specVar];

    if(valor != null){
        def specAssessMap = specialVarAssessments[specVar] ?: [:];
        def sa = specAssessMap[valor];

        if(sa == null){
            sa = [
                    "Findings": [finding],
                    "Objectivo": Double.valueOf(resultado["Objectivo"] ?: 0),
                    "Atingimento": Double.valueOf(resultado["Atingimento"] ?: 0),
                    "Observações": ""
            ];

        } else {
            sa["Findings"] += finding;
            sa["Objectivo"] += Double.valueOf(resultado["Objectivo"] ?: 0);
            sa["Atingimento"] += Double.valueOf(resultado["Atingimento"] ?: 0);
        }

        specAssessMap.put(valor, sa);
        specialVarAssessments.put(specVar, specAssessMap);
    }
}

def getCpeRecordMInstance(instanceToEval, definitionName){
    def definitionId = getDefinitionId(definitionName);
    def instance = null;

    switch (definitionName){
        case "Loja":
            //instanceToEval.id = DeviceM external Id = RecordM id
            def query = "id:\"${instanceToEval.id}\""
            def searchResult = getInstancesPaged(definitionId, query, 0, 1)

            if(searchResult.size() == 1){
                instance = searchResult.get(0);
            }

            break;
    }

    return instance;
}

def buildAssessmentResultMap(control, findings, objectivoTotal, atingimentoTotal){
    return [
            "Objectivo": "" + objectivoTotal,
            "Atingimento": "" + atingimentoTotal,
            "Resultado": "" + ( (atingimentoTotal==0 && objectivoTotal==0) ? 100: Math.round((100 * atingimentoTotal / (objectivoTotal?:1) )*100)/100 ), // % arredondada às décimas
            "Observações":  "" + buildReport(control, findings)
    ];
}
// ----------------------------------------------------------------------------------------------------
def obterFindingsAbertos(control){
    def existingFindings = [:]
    def query = "control.raw:${control.id} AND ${_("Estado")}.raw:(Suspeito OR \"Por Tratar\" OR \"Em Resolução\") ".toString();
    def searchResult =  getInstances("Finding", query);
    searchResult.each { finding ->
        existingFindings << [
                (finding[_("Id Asset Origem")][0]) : [
                        "id":                       "" + finding.id,
                        "Control":                  "" + control.id,
                        "Identificador do Finding": getFirstValue(finding,_("Identificador do Finding"))?:"",
                        "Estado":                   getFirstValue(finding,_("Estado"))?:"",
                        "Reposição Detectada":      getFirstValue(finding,_("Reposição Detectada"))?:"",
                        "Label Asset Origem":       getFirstValue(finding,_("Label Asset Origem"))?:"",
                        "Observações":              getFirstValue(finding,_("Observações"))?:"",
                        "Id Definição Origem":      getFirstValue(finding,_("Id Definição Origem"))?:"",
                        "Id Asset Origem":          getFirstValue(finding,_("Id Asset Origem"))?:"",
                        "_limite_suspeita_":        getFirstValue(finding,_("Data Limite da Suspeita"))?:""
                ]
        ]
    }
    return existingFindings
}
// ----------------------------------------------------------------------------------------------------
def evalInstance(control, instanceToEval, previousFinding) {
    def condicaoSucesso = control[_("Condição de sucesso")][0]

    // Assume de cada avaliação vale 1 para o objectivo e que, à partida, o teste vai passar
    def resultado = [:]
    resultado << ["Objectivo"   : 1 ]
    resultado << ["Atingimento" : 1 ]

    def output = null
    try {
        // Prepara dados e código adicional a passar para a avaliação de forma a simplificar o código de avaliação
        def evalInfo = prepareEvalInfo(condicaoSucesso, instanceToEval, previousFinding, control)

        // Avalia o código do control, enqiquecido com os código base
        output = Eval.x(evalInfo.map, evalInfo.code)

        //TODO: confirmar que a estrutura do resultado é a esperada e válida
    } catch (e) {
        // Caso haja erros no código ou na avaliação em reporta o erro e usa resultados default pessimistas
        log.error("Falha na avaliação do control ${control[_("Nome")][0]}", e)
        resultado << ["Observações": "Erro na avaliação de ${instanceToEval.id}: ".toString() + e.message]
    }

    // Suporte ao velho modo de avaliação (que retorna apenas true e false) e ao novo (que adiciona e/ou sobrepõe informação ao resultado)
    if (output == null || output instanceof Boolean) {
        resultado << ["Atingimento": (output ? 1 : 0)]
    } else {
        resultado << output
    }

    return resultado
}
// --------------------------------------------------------------------
def prepareEvalInfo(condicaoSucesso, instanceToEval, previousFinding, control) {
    def evalMap = [:]

    //Aumenta código de avaliação com dados e métodos para simplificar a escrita dos testes
    evalMap["instancia"]        = instanceToEval
    evalMap["previousFinding"]  = previousFinding
    evalMap["log"]              = { msg -> log.info(msg) }
    evalMap["rmRest"]           = rmRest
    evalMap["pesquisaRegistos"] = { definicao,pesquisa,size ->
        def resp = rmRest.get("recordm/definitions/search/"+definicao,
                [
                        'q': ""+pesquisa,
                        'from': "0", 'size': ""+size
                ],
                "");

        if(resp =="NOT_OK"){
            throw new Exception("Não foi possível fazer a pesquisa pretendida ($definicao,$pesquisa).")
        }
        JSONObject esResult = new JSONObject(resp);
        return esResult
    }
    evalMap["contaRegistos"] = { nomeDefinicao,pesquisa ->
        def definicao = getDefinitionId(nomeDefinicao)
        def resp = rmRest.get("recordm/definitions/search/"+definicao,
                [
                        'q': ""+pesquisa,
                        'from': "0", 'size': "0"
                ],
                "");

        if(resp =="NOT_OK"){
            throw new Exception("Não foi possível fazer a pesquisa pretendida ($nomeDefinicao,$pesquisa).")
        }
        JSONObject esResult = new JSONObject(resp)
        return esResult.hits.total.value
    };
    evalMap["utilizadores"] = { String... groups ->
        List users = getUsersWithGroups(groups);

        return [
                "emails": users.collect { it.email }.join(","),
                "telefones": users.collect { it.contact ?: "" }.join(",")
        ];
    };

    //Load custom client functions
    GovernanceConfig.customAssessmentFunctions.each { fnName, code ->
        evalMap[fnName] = evaluate(code)
    }


    // Código adicionado em String - 'x' é o nome da variavel com o mapa passado em Eval.x()
    def baseCode = '''
        import org.codehaus.jettison.json.*;
        import groovy.time.TimeCategory

        def instancia = x.instancia
        def previousFinding = x.previousFinding
        def log = x.log
        def pesquisaRegistos = x.pesquisaRegistos
        def contaRegistos = x.contaRegistos
        def utilizadores = x.utilizadores
        def resultado = [:]
    '''

    //Load custom client functions declarations
    GovernanceConfig.customAssessmentFunctions.each { fnName, code ->
        baseCode += "\n        def " + fnName + " = x." + fnName
    }
    baseCode+="\n"

    def finalReturnCode = '''
        return resultado;
    '''

    def parsedEvalCode = parse(condicaoSucesso, instanceToEval, "instancia");

    return [map:evalMap, code: baseCode + parsedEvalCode + finalReturnCode ]
}

def somaValoresES(indices, pesquisa, campoSoma, campoTempo, momentoInicio) {
    def query = getEsQuery(pesquisa, campoTempo, momentoInicio);
    def aggs =  getEsAgg("field_sum", "sum", campoSoma);

    def esJsonStr = "{\"size\":0, " +
            "\"query\":${query}," +
            "\"aggregations\":${aggs}" +
            "}"

    log.info("$indices || $query || $aggs || $esJsonStr");

    Response response = doAggSearch(indices, esJsonStr);

    String body = response.readEntity(String.class);

    if(response.getStatus().intdiv(100) != 2){
        throw new Exception("Não foi possível fazer a pesquisa pretendida ($indices, $pesquisa, $campoSoma, $campoTempo, $momentoInicio): $body");
    }

    JSONObject esResult = new JSONObject(body);

    return esResult.aggregations.field_sum.value;
};

def mediaValoresES(indices, pesquisa, campoMedia, campoTempo, momentoInicio) {
    def query = getEsQuery(pesquisa, campoTempo, momentoInicio);
    def aggs =  getEsAgg("field_avg", "avg", campoMedia);

    def esJsonStr = "{\"size\":0, " +
            "\"query\":${query}," +
            "\"aggregations\":${aggs}" +
            "}";

    def response = doAggSearch(indices, esJsonStr);

    String body = response.readEntity(String.class);

    if(response.getStatus().intdiv(100) != 2){
        throw new Exception("Não foi possível fazer a pesquisa pretendida ($indices, $pesquisa, $campoMedia, $campoTempo, $momentoInicio): $body");
    }

    JSONObject esResult = new JSONObject(body);

    return esResult.aggregations.field_avg.value;
};

def doAggSearch(indices, esJsonStr){
    //uses integrationm token
    return ClientBuilder.newClient()
            .target(GovernanceConfig.ES_URL)
            .path("/${indices}/_search")
            .request(MediaType.APPLICATION_JSON)
            .cookie(new Cookie("cobtoken", GovernanceConfig.COBTOKEN))
            .post(Entity.entity(esJsonStr, MediaType.APPLICATION_JSON), Response.class)
};

def getEsQuery(pesquisa, campoTempo, momentoInicio){
    def esQuery = "{\"bool\":{" +
            "\"must\":{" +
            "\"query_string\":{" +
            "\"query\":\"${pesquisa}\"," +
            "\"analyze_wildcard\": true" +
            "}" + //query_string
            "}," + //query
            "\"filter\":{" +
            "\"bool\":{" +
            "\"must_not\": []," +
            "\"must\":["

    if(campoTempo && momentoInicio){
        esQuery += "{\"range\":{" +
                "\"${campoTempo}\": {" +
                "\"gte\":\"${momentoInicio}\"" +
                "}" +   //campoTempo
                "}" +   //range
                "}"  //range root
    }

    esQuery += ("]" + //must
            "}" + //bool
            "}" + //filter
            "}" + //filtered
            "}");

    return esQuery;
}

def getEsAgg(name, type, field){
    return "{\"${name}\":{" +
            "\"${type}\":{" +
            "\"field\":\"${field}\"" +
            "}" +
            "}}";
}

def parse(textWithVars, instanceToEval, nomeVarInstancia){
    // $id$ = <id da instancia>
    def parsedText = textWithVars.replace("\$id\$", "" + instanceToEval.id)

    // _[Nome Campo]_ = instancia["<Nome do campo>"][0]
    parsedText = parsedText.replaceAll( /_\[([^\]]*?)\]_/ ) { m -> "(${nomeVarInstancia}.containsKey(\"${_(m[1])}\") ? ${nomeVarInstancia}[\"${_(m[1])}\"][0] : null)" }

    // $Nome Campo$ = "<valor do campo>"
    parsedText = parsedText.replaceAll( /[$](.+?)[$]/ ) { m ->
        def fieldName = m[1];
        def esFieldName = _(fieldName);
        def esField = (instanceToEval["${esFieldName}"]
                ?: instanceToEval["${fieldName}"]);

        if(esField == null){
            log.warn("O campo \"${fieldName}\" não existe na instância a avaliar {{instance:${instanceToEval.id}}}")
            return null;
        }

        return esField[0];
    };

    return parsedText;
}
// ----------------------------------------------------------------------------------------------------
def createOrUpdateFinding(control, openFindings, instanceToEval, resultado) {
    def successFlag     = (resultado["Objectivo"] == resultado["Atingimento"])
    def previousFinding = openFindings[""+instanceToEval.id]
    def previousTestOk  = previousFinding ? previousFinding["Reposição Detectada"] : ""
    def previousState   = previousFinding ? previousFinding["Estado"] : ""
    def report

    if(successFlag){
        if(previousTestOk == "Não"){
            if(previousState == "Suspeito") {
                previousFinding["Estado"] = "Suspeito Cancelado"
            } else {
                previousFinding["_marked_ChangedToOK"] = true
            }
            previousFinding["Data de reposição"] = "" + now.time
            previousFinding["Reposição Detectada"] = "Sim"
            previousFinding["Observações"] = previousFinding["Observações"]?:""

            if(resultado["Observações"]) previousFinding["Observações"] = resultado["Observações"]

            createOrUpdateInstance("Finding", previousFinding)
        } else if( previousTestOk == "Sim" ) {
            previousFinding["_marked_Inaltered"] = true
        }
        // Caso contrário não é necessário fazer nada pois irá retornar o finding existente (se existia) ou então retorna null pois não havia finding (está tudo ok e já estava tudo ok)
    } else {
        // Se havia finding anterior com indicação de 'resposto' então repõe indicação de inconformidade
        if(previousTestOk == "Sim") {
            previousFinding["Reposição Detectada"] = "Não"

            if(resultado["Observações"]) previousFinding["Observações"] = resultado["Observações"]

            createOrUpdateInstance("Finding", previousFinding)
            previousFinding["_marked_ChangedToNOK"] = true

            // Caso houvesse finding anterior só é necessário actualizar o Finding se observação mudou. De qualquer forma retorna o anterior finding para reportar
        } else if( previousTestOk == "Não" ) {
            if(previousState == "Suspeito") {
                if(Long.valueOf(previousFinding["_limite_suspeita_"]) < now.time) {
                    previousFinding["Estado"] = "Por Tratar"
                    previousFinding["Observações"] = resultado["Observações"]?:""
                    createOrUpdateInstance("Finding", previousFinding)
                    previousFinding["_marked_New"] = true
                }
            } else {
                if( resultado["Observações"] && resultado["Observações"].trim() != previousFinding["Observações"]) {
                    if(previousFinding["Observações"] != resultado["Observações"]) {
                        previousFinding["Observações"] = resultado["Observações"]?:""
                        createOrUpdateInstance("Finding", previousFinding)
                    }
                }
                previousFinding["_marked_Inaltered"] = true
            }

            // Caso contrário não havia um finding e então cria um novo
        } else {
            def suspectFindindNotYetToCreate = getFirstValue(control,_("Quando considerar inconformidade"))=="Após repetição da detecção"
            def intervalo
            switch (control[_("Periodicidade")][0]) {
                case "Mensal":  intervalo = 30*24*60; break;
                case "Semanal": intervalo = 7*24*60; break;
                case "Diária":  intervalo = 24*60; break;
                case "15m":     intervalo = 15; break;
            }

            def newFinding = [
                    "Estado":                   suspectFindindNotYetToCreate ? "Suspeito" : "Por Tratar" ,
                    "Data Limite da Suspeita":  suspectFindindNotYetToCreate ? "" + (now.time + control[_("Quantidade repetições")][0].toInteger() * 60000 * intervalo) : "",
                    "Reposição Detectada":      "Não" ,
                    "Control":                  "" + control.id,
                    "Atribuido a":              "" + getFirstValue(control,_("Responsável"))?:"",
                    "Identificador do Finding": "" + control[_("Código")][0] + "-" + instanceToEval.id,
                    "Label Asset Origem":       "" + (instanceToEval[_(instanceToEval._definitionInfo.instanceLabel.name[0])]?:[""])[0],
                    "Id Definição Origem":      "" + instanceToEval._definitionInfo.id,
                    "Id Asset Origem":          "" + instanceToEval.id
            ]

            newFinding["Observações"] = resultado["Observações"] ?: (newFinding["Label Asset Origem"]?:"id:"+instanceToEval.id) + " - " + control[_("Código")][0]

            if(getFirstValue(control,_("Acção"))=="Contabilizar e Reportar Inconformidades") {
                createOrUpdateInstance("Finding", newFinding)
            }

            previousFinding = newFinding //to return
            if(suspectFindindNotYetToCreate == false) {
                if(getFirstValue(control,_("Acção"))=="Contabilizar e Reportar Inconformidades") {
                    previousFinding["_marked_New"] = true
                } else {
                    previousFinding["_marked_NewButNoReport"] = true
                }
            }
        }
    }
    return previousFinding
}
// --------------------------------------------------------------------
def buildReport(control,findings) {
    def report = ""
    report += buidlStateReport(findings,"_marked_NewButNoReport","<b>Inconformidades contabilizadas mas não reportadas:</b>\n")
    report += buidlStateReport(findings,"_marked_New",           "<b>NOVAS inconformidades:</b>\n")
    report += buidlStateReport(findings,"_marked_ChangedToNOK",  "<b>Inconformidades reabertas:</b>\n")
    report += buidlStateReport(findings,"_marked_ChangedToOK",   "<b>Inconformidades aparentemente já não verificadas:</b>\n")
    report += buidlStateReport(findings,"_marked_Inaltered",     "<b>Inconformidades INALTERADAS:</b>\n")
    return report
}
// --------------------------------------------------------------------
def buidlStateReport(findings,changeType,label) {
    def report = ""
    def count = 0
    findings.findAll { it[changeType] }.each { finding ->
        if(count == 0) report += label
        if(count++ < 10) {
            report += " * "
            if( changeType.indexOf("_marked_New") == -1 ) report += "${finding["Estado"]}"
            if( changeType == "_marked_ChangedToOK" || changeType == "_marked_Inaltered" && finding["Reposição Detectada"]=="Sim")  report += "/REPOSTO"
            if( changeType.indexOf("_marked_New") == -1) report += " | "
            report += finding["Observações"] ? finding["Observações"] : finding["Label Asset Origem"]
            report += "\n"
        }
    }
    if(count > 10) report += " * mais ${count-10} registos\n\n"
    if(count > 0)  report += "\n"
    return report
}

// ----------------------------------------------------------------------------------------------------
// getEvaluationData - obtem os dados para avaliação. Varia com o assessmentTool (RecordM,DeviceM ou Manual)
// ----------------------------------------------------------------------------------------------------
def getEvaluationDataRecordM(control) {
    def evaluationList = []
    def assessmentInfo = [:]
    // Obtem dados para realizar a avaliação
    def definitionName = control[_("Definição")][0]
    def definitionId = getDefinitionId(definitionName)
    if(definitionId == null) {
        assessmentInfo << [ "Atingimento": "0" ]
        assessmentInfo << [ "Observações":"Erro na avaliação: Não é possível executar o AssessTool porque a Definição indicada ("+definitionName+") não existe" ]
    } else {
        def filtro = control[_("Filtro")][0]

        // Obtem lista de instancias a avaliar
        evaluationList = getInstancesById(definitionId, filtro)
        if(evaluationList.size() == 0){
            assessmentInfo << [ "Atingimento": "0" ]
            assessmentInfo << [ "Observações":"O filtro indicado não devolve INSTÂNCIAS para avaliar" ]
        }
    }
    return ["evalList":evaluationList,"assessmentInfo": assessmentInfo]
}
// --------------------------------------------------------------------
def getEvaluationDataDeviceM(control) {
    def evalList = []
    def assessmentInfo = [:]

    // Se já há um comando executado obtêm os resultados para avaliar o resultado
    if(control["_marked_CollectDeviceMValues_"]) {
        def definitionName = control[_("Definição")][0]
        def definitionId = getDefinitionId(definitionName)

        def jobId = control["_marked_CollectDeviceMValues_"]
        def taskList = actionPacks.get("cmRest").get("/confm/requests/"+ jobId +"/tasks")
        def taskListObj = new JSONObject('{ "result":'+taskList+'}')
        evalList = []
        for(int index = 0; index < taskListObj.result.length(); index++){
            def hitJson = taskListObj.result.getJSONObject(index);
            /* {commands=R100, state=ok, _definitionInfo={id=36, timeSpent=1, filePreviews=null, uri=http://localhost:40180/confm/requests/2122406/tasks/2122406/tasks/368794552, cpeExternalId=418, id=418, changedFilesInJson=, cpeName=185-Sintra-Agualva, errors=, results=R100=1, cpesJobRequest=2122406, cpe=40, endTimestamp=1502401144176} */
            def hitMap  = recordmJsonToMap(hitJson.toString());
            hitMap << ["id" : hitMap.cpeExternalId ]
            hitMap << ["_definitionInfo"    : ["id" : definitionId, "instanceLabel" : [ "name" : ["_label_fake_field_"] ] ] ]
            hitMap << ["_label_fake_field_" : [hitMap.cpeName] ]
            evalList.add(hitMap);
        }
        if(evalList.size() == 0){
            assessmentInfo << [ "Atingimento": "0" ]
            assessmentInfo << [ "Observações":"O filtro indicado não devolveu EQUIPAMENTOS para avaliar" ]
        }
        assessmentInfo << ["DeviceM JobID": ""]
    }

    // Se está marcado para correr executa comando no DeviceM de forma a poder avaliar o resultado na próxima execução (dentro de 15m)
    if(control["_marked_ToEval_"]) {
        def jobId = execCmdWhere(control[_("Comando")][0],control[_("Filtro")][0]);
        assessmentInfo << ["DeviceM JobID": ""+jobId]
    }

    return ["evalList":evalList, "assessmentInfo":assessmentInfo]
}
// --------------------------------------------------------------------
def execCmdWhere(cmd,condition){
    def fields = new HashMap<String, String>();

    fields["condition"] = condition;
    fields["commands"] = cmd;

    def resp;
    try {
        resp = actionPacks.get("cmRest").post("/confm/integration/cmd",fields);
    } catch (e) {
        resp = "NOT_OK";
    }

    if(resp == "NOT_OK"){
        log.error("Error executing commands {{params : " + fields + "}}")
        return null;
    } else {
        JSONObject job = new JSONObject(resp);
        return job.getInt("id");
    }
}
// ----------------------------------------------------------------------------------------------------
def getEvaluationDataManual(control) {
    return ["evalList": [["id":26075],["id":999]] ]
}
// ----------------------------------------------------------------------------------------------------

// ----------------------------------------------------------------------------------------------------
//  executaAccoesComplementares -  envia Emails e SMSs
// ----------------------------------------------------------------------------------------------------
def executaAccoesComplementares(control, assessment) {

    def mailActionsIdx = 0
    def smsActionsIdx = 0

    def subject = "Resultado avaliação de ${control[_("Nome")]}".toString()

    def body = assessment["Observações"]?:  "Sem observações."

    if ( assessment["_marked_Changed"] || msg.action == "forceAssessment")  {
        control[_("Acção Complementar")].eachWithIndex { action, idx ->
            def sendNow = true
            if(getFirstValue(control,_("Tolerância")) == "Prazo após criação") {
                //TODO: Calacular se prazo já foi atingido
                if(getFirstValue(control,_("Prazo")) > now.time) {
                    sendNow = false
                    //calcular findings a incluir
                    //TODO
                }
            }

            def textoBase = control.containsKey(_("Texto"))? control[_("Texto")][idx] : "";

            if ( sendNow && action == "Enviar Email Resumo alterações" ) {
                def assessmentsEspeciais = [:];

                assessment["Assessments Especiais"].each{ specVar, map ->
                    specAssess = map.findAll{ key, assess-> assess["_marked_Changed"] }

                    if(specAssess.size() > 0){
                        assessmentsEspeciais.put(specVar, specAssess)
                    }
                };

                String emails = control[_("Email Destino")][mailActionsIdx];
                def emailsEspeciais = obterVarsEspeciais(emails, assessmentsEspeciais);

                String emailsBcc = control[_("Email Destino BCC")]!=null? control[_("Email Destino BCC")][mailActionsIdx] :"";

                if(emailsEspeciais.size() > 0){

                    enviarEmailsEspeciais(emailsEspeciais, emailsBcc, subject, textoBase);

                    emails = removerVarsEspeciais(emails, emailsEspeciais);
                }

                if(emails && emails.length() > 0){
                    new SendMail().send(subject, body + "\n\n" + textoBase , emails)
                }

                mailActionsIdx++;
            }

            //Enviar apenas novas inconformidades para poupar caracteres (max 747 nas SMS)
            if ( sendNow && action == "Enviar SMS qd há novas inconformidades" && assessment["_marked_New_Findings"] ) {
                def assessmentsEspeciais = [:];

                assessment["Assessments Especiais"].each{ specVar, map ->
                    specAssess = map.findAll{ key, assess-> assess["_marked_New_Findings"] }

                    if(specAssess.size() > 0){
                        assessmentsEspeciais.put(specVar, specAssess)
                    }
                };

                def codigo = control[_("Código")][0];
                def numsTel = control[_("Telemóvel")][smsActionsIdx++];

                def numsEspeciais = obterVarsEspeciais(numsTel, assessmentsEspeciais);

                if(numsEspeciais.size() > 0){

                    enviarSMSEspeciais(numsEspeciais, codigo, textoBase);

                    numsTel = removerVarsEspeciais(numsTel, numsEspeciais);
                }

                if(numsTel.length() > 0){
                    body = buidlStateReport(assessment["Findings"] ,"_marked_New", "<b>NOVAS inconformidades:</b>\n") ?: "Sem observações.";
                    new SendSMS().send(codigo, (textoBase + "\n\n" + body).toString(), numsTel)
                }
            }
        }
    }
}

def obterVarsEspeciais(vars, assessmentsEspeciais){
    def varsEspeciais = (vars =~ REGEX_VARS_ESPECIAIS);

    return varsEspeciais.collect{
        def parsed = it[1]; //Nome da variável especial SEM delimitadores (ex: email)

        return [
                "raw": it[0] //Nome da variável especial COM delimitadores (ex: $email$)
                , "parsed": parsed
                , "assessments": (assessmentsEspeciais[parsed] ?: [:])
        ]
    };
}

def enviarEmailsEspeciais(emailsEspeciais, String emailsBcc, subject, textoBase){
    emailsEspeciais.each{
        def assessMap = it["assessments"];

        assessMap.findAll { emails, assessment -> emails.length() > 0 }
                .each { emails, assessment ->

                    def body = assessment["Observações"] ?: "Sem observações.";

                    log.info("A enviar email especial para $emails: ${body + "\n\n" + textoBase}}");
                    new SendMail().send(subject, body + "\n\n" + textoBase, emails);
                }
    };
}

def enviarSMSEspeciais(numsEspeciais, codigo, textoBase){
    numsEspeciais.each{
        def assessMap = it["assessments"];

        assessMap.findAll { telefones, assessment -> telefones.length() > 0 }
                .each{ telefones, assessment ->

                    def body = buidlStateReport(assessment["Findings"] ,"_marked_New", "<b>NOVAS inconformidades:</b>\n") ?: "Sem observações.";

                    log.info("A enviar SMS especial para $telefones: ${textoBase + "\n\n" + body}}");
                    new SendSMS().send(codigo, (textoBase + "\n\n" + body).toString(), telefones);
                }
    };
}

def removerVarsEspeciais(vars, varsEspeciais){
    varsEspeciais.each{
        vars -= it.raw;
    }

    return vars;
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
    def result = [];

    def size = (numInstancias != null
            ? numInstancias
            : 500);

    def resp = rmRest.get(
            "recordm/definitions/search/" + idDefinicao,
            [
                    'q': query.toString(),
                    'from': "" + from,
                    'size': "" + size
            ],
            "");

    if(resp !="NOT_OK"){
        JSONObject esResult = new JSONObject(resp);
        def totalResults = esResult.hits.total.value.toInteger();
        def hits = esResult.hits.hits;
        def numResults = hits.length();

        if( totalResults > 0){
            result.addAll(esSourceList(hits));

            if(numResults == size){
                result.addAll(getInstancesPaged(idDefinicao, query, from+size, size));
            }
        }
    }

    return result;
}
// --------------------------------------------------------------------
def esSourceList(hits){
    def sourceList = [];

    for(int index = 0; index < hits.length(); index++){
        def hit = hits.getJSONObject(index);

        sourceList.add(recordmJsonToMap(hit._source.toString()));
    }

    return sourceList;
}
// --------------------------------------------------------------------
def recordmJsonToMap(content){
    ObjectMapper mapper = new ObjectMapper();

    return mapper.readValue(content,HashMap.class);
}
// --------------------------------------------------------------------
def getFirstValue(map, key) {
    return map.containsKey(key) ? map[key][0] : null
}

// ----------------------------------------------------------------------------------------------------
//  getDefinitionId - Obtem o id de uma definição a partir do Nome da mesma
// ----------------------------------------------------------------------------------------------------
def getDefinitionId(definitionName){
    def resp = rmRest.get("recordm/definitions/name/" + definitionName, "");

    if(resp != "NOT_OK"){
        JSONObject definition = new JSONObject(resp);

        return definition.id;
    }
    return null;
}

// ----------------------------------------------------------------------------------------------------
//  createOrUpdateInstance
// ----------------------------------------------------------------------------------------------------
def createOrUpdateInstance(definitionName, instance) {
    def updates = cloneAndStripInstanceForRecordmSaving(instance);

    if(instance.id) {
        // Update mas apenas se tiver mais que 1 campo (ou seja, excluindo o id)
        if(instance.size() > 1) {
            recordm.update(definitionName, "recordmInstanceId:" + instance["id"], updates)
        }
    } else {
        // Create
        recordm.create(definitionName, updates)
    }
}

//necessário remover os Boolean da instância para se conseguir gravar no recordm
def cloneAndStripInstanceForRecordmSaving(instance){
    def updates = [:]
    instance.each { k, v ->
        //log.info("XXXXX KEYk " + k + v)
        if (v instanceof Boolean || v instanceof ArrayList || v instanceof Map) {
            log.trace("Campo removido do update por ser boolean, array ou map :" + k)
        } else {
            updates[k] = v
        }
    }

    return updates
}

// ----------------------------------------------------------------------------------------------------
//  toEsName (nome reduzido para "_")  - Converte um nome de campo RecordM no seu correspondente no ES
// ----------------------------------------------------------------------------------------------------
def _(fieldName){
    return fieldName.toLowerCase().replace(" ", "_");
}
// --------------------------------------------------------------------
def getUsersWithGroups(groups){
    return getUsersWithGroups(groups, 0, 50);
}
// --------------------------------------------------------------------
def getUsersWithGroups(groups, from, size){
    def users = [];

    def query = "groups.name:(\"${groups.join('" AND "')}\") AND -username:test*";

    def resp = umRest.get(
            "userm/search/userm/user",
            [
                    'q': query.toString(),
                    'sort':'_id',
                    'ascending':'true',
                    'from': "" + from,
                    'size': "" + size
            ],
            "");

    if(resp !="NOT_OK"){
        JSONObject esResult = new JSONObject(resp);
        def totalResults = esResult.hits.total.value.toInteger();
        def hits = esResult.hits.hits;
        def numResults = hits.length();

        if( totalResults > 0){
            users.addAll(esSourceList(hits));

            if(numResults == size && size > 1){
                result.addAll(getUsersWithGroups(groups, from+size, size));
            }
        }
    }

    return users;
}
// --------------------------------------------------------------------

// ----------------------------------------------------------------------------------------------------
// Class SendMail
// ----------------------------------------------------------------------------------------------------
class SendMail {
    def log = LogFactory.getLog("send-mail")
    def SENDER = "governance@cultofbits.com"
    def SENDER_NAME = "Sistema de Governância CoB"

    def SENDGRID_SEND_MAIL_RESOURCE = "https://api.sendgrid.com/v3/mail/send"
    def SENDGRID_API_KEY = GovernanceConfig.SENDGRID_API_KEY
    // --------------------------------------------------------------------
    def SendMail() {}
    // --------------------------------------------------------------------
    def send(subject, body, emailTo) {
        if (emailTo == null || emailTo.trim().equals("")) {
            log.error("Cannot send email without an email address.")

        } else {
            def emailJsonStr = buildEmailJsonStr(subject, body, emailTo)

            Response response = ClientBuilder.newClient()
                    .target(SENDGRID_SEND_MAIL_RESOURCE)
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer $SENDGRID_API_KEY".toString())
                    .post(Entity.entity(emailJsonStr, MediaType.APPLICATION_JSON), Response.class)


            if (response.getStatus() != Response.Status.ACCEPTED.getStatusCode()) {
                log.info("There was an error executing the email action pack {{status: " + response.getStatus() + ","
                        + " message: " + response.getEntity(String.class) + ", emailJsonStr: " + emailJsonStr + "}} ")
            }
        }
    }
    // --------------------------------------------------------------------
    def buildEmailJsonStr(subject, body, emails){
        def parsedEmails=[];

        emails.split(/[,;]/).each { email ->
            parsedEmails.push('{"email":"'+email+'"}')
        };

        def escapedBody = text2HTML(body).replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
        return "{\"personalizations\": [{\"to\": [" + parsedEmails.join(",") + " ], \"subject\": \"$subject\" } ], \"from\": { \"email\": \"$SENDER\", \"name\": \"$SENDER_NAME\" }, \"content\": [ { \"type\": \"text/html\", \"value\": \"${escapedBody}\" } ] }".toString()
    }
    // --------------------------------------------------------------------
    def text2HTML(text){
        return text.replaceAll('\n',"<br>\n").replaceAll(' \\* ',"\t * ").replaceAll('\t',"<span class='Apple-tab-span' style='white-space:pre'>    </span>").replaceAll('\\*\\*',"")
    }
    // --------------------------------------------------------------------
}

// ----------------------------------------------------------------------------------------------------
// Class SMS
// ----------------------------------------------------------------------------------------------------
class SendSMS {
    def log = LogFactory.getLog("send-mail")
    def SENDER = "CoB-Govrnc"
    def PLIVO_SEND_SMS_RESOURCE = GovernanceConfig.PLIVO_SEND_SMS_RESOURCE
    def PLIVO_API_KEY = GovernanceConfig.PLIVO_API_KEY
    // --------------------------------------------------------------------
    def SendSMS() {}
    // --------------------------------------------------------------------
    def send(subject, body, phone) {
        if (phone == null || phone.trim().equals("")) {
            log.error("Cannot send SMS without an phone address.")

        } else {
            def smsJsonStr = buildSMSJsonStr(subject, body, phone)

            log.info("Sending SMS")

            Response response = ClientBuilder.newClient()
                    .target(PLIVO_SEND_SMS_RESOURCE)
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Basic " + PLIVO_API_KEY.bytes.encodeBase64().toString())
                    .post(Entity.entity(smsJsonStr, MediaType.APPLICATION_JSON), Response.class)

            if (response.getStatus() != Response.Status.ACCEPTED.getStatusCode()) {
                log.info("There was an error sending the SMS {{status: " + response.getStatus() + ","
                        + " message: " + response.readEntity(String.class) + ", smsJsonStr: " + smsJsonStr + "}} ")
            }
        }
    }
    // --------------------------------------------------------------------
    def buildSMSJsonStr(subject, body, phone){
        return "{\"src\": \"${SENDER}\",\"dst\": \"${phone}\", \"text\": \"${subject} \n${body.replaceAll('\\*\\*','').replaceAll('<.?b>','')}\"}".toString()
    }
    // --------------------------------------------------------------------
}
