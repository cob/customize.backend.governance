// vim: et sw=4 ts=4


import com.cultofbits.integrationm.service.dictionary.recordm.RecordmStats
import groovy.transform.Field

import com.google.common.cache.*
import java.util.concurrent.TimeUnit

import org.codehaus.jettison.json.*
import com.fasterxml.jackson.databind.ObjectMapper

import java.util.Calendar
import java.time.YearMonth;
import java.time.Year;
import javax.ws.rs.client.ClientBuilder

import javax.ws.rs.client.*
import javax.ws.rs.core.Cookie
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

import config.GovernanceConfig

@Field rmRest = actionPacks.get("rmRest")
@Field now = new Date()

@Field static REGEX_VARS_ESPECIAIS = /[;,]?\$([^\$]*)\$[;,]?/

@Field static cacheOfDefinitions = CacheBuilder.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build()

@Field DEF_MANUAL_FORM = "Questionário"


// ====================================================================================================
//  MAIN LOGIC - START  -  As Avaliações (Assessments) acontece em 2 circunstâncias:
//   1) nos instantes previstos pela periodicidade do Control (ver ~/others/GovernanceGlobal/governance_clock.sh)
//   2) ou directamente na interface do Control (ver recordm/customUI/js/cob/governance.js)
// ====================================================================================================

if ((msg.product == "governance" && msg.type == "clock" && msg.action == "clockTick")
        || (msg.product == "governance" && msg.type == "controlUI" && msg.action == "forceAssessment")
        || (msg.product == "governance" && msg.type == "controlUI" && msg.action == "forceQuestions")) {
    log.info("Start Controls evaluations.")

    if (GovernanceConfig.usesEmailActionPack) GovernanceConfig.emailActionPack = email
    if (GovernanceConfig.usesSmsActionPack) GovernanceConfig.smsActionPack = sms

    // Obtem lista dos controls ligados
    def controls = getInstances("Control", "-periodicidade.raw:Off")

    // Obtem matriz com  todos os pesos por objectivo para depois usar no cálculo da importância relativa de cada assessment
    def pesos = obtemMatrizCompletaDePesos(controls)


    // Para cada um destes controls :
    controls.each { control ->
        // If one control evaluation fails, we catch the error, log it and continue to the next controls.
        //try {
        // Se for uma avaliação pedida na interface fazer skip a todos os controls menos a esse id específico
        if (msg.action == "forceAssessment" && control.id != msg.id) return
        if (msg.action == "forceQuestions" && control.id != msg.id) return

        // obtem um assessment válido (com a indicação no control de se necessita de ser avaliado agora)
        def assessment = getAssessmentInstance(control, msg.action, pesos)


        // Se control marcado para avaliação então avalia, actualiza resultado do assessment e cria/actualiza findings
        if (control["_marked_ToEval_"] || control["_marked_CollectDeviceMValues_"] || control["_marked_assessmentId_"]) {
            log.info("Evaluate Control and gather Assessment info ${control[_("Nome")]} ...")

            //Avalia control e complementa dados do assessment com os resultados !!
            assessment << assessControl(control)


            //Processa acções complementares: envia Emails e SMSs
            executaAccoesComplementares(control, assessment)

            // Se não for necessário actualizar dados remove campo de Data e Observações para não haver alterações na instância desnecessárias
            if (control["_marked_OnlyUpdateDataIfChanged"] && !assessment["_marked_Changed"]) {
                assessment.remove("Data do Resultado")
                assessment.remove("Observações")
            }
        } else {
            log.info("Just create (or update if exists) the daily Assessment info ${control[_("Nome")]} ...")
        }

        // cria ou actualiza instância de Assessment
        def new_assessment = createOrUpdateInstance("Assessment", assessment)

        // Logic for questionarios manuais criation and updates
        if (control[_("Assessment Tool")][0].equals("Manual")) {
            manualFormsCreationVerifications(control, new_assessment, msg.action)
        }
        //} catch (e) {
        //    log.info("Error assessing control with ID ${control.id}. Error: " + e)
        //}

    }
    log.info("Finished Controls evaluations.")
}

// ====================================================================================================
//  MAIN LOGIC - END
// ====================================================================================================

/*
    Para quando nao é especificados dias de antecedencia para a criaçao de questionarios, criamos os quests on-the-spot.
    Quando estes dias sao especificados, é preciso calcular em que dia do timespan (semana ou mês) é que os questionarios
    tem que ser criados. Isto é feito via uma subtraçao normalizada, que tem em conta os dias da semana, e os dias de
    cada mês (que podem variar).

    O 'canCreate' deve ser/é true quando estamos num dia de criaçao de Questionarios.
 */

def manualFormsCreationVerifications(control, new_assessment, runType) {
    def new_assessment_id = (new_assessment instanceof RecordmStats) ? "" : new_assessment["id"]
    // Bool para saber se existia assessment do dia anterior.
    def hasPreviousDayAssessment = control.containsKey('_marked_hasPrevious_') ? control['_marked_hasPrevious_'] : false
    def old_assessment_id = control.containsKey('_marked_previousAssessmentId_') ? control['_marked_previousAssessmentId_'] : ""
    // ID do ultimo assessment valido encontrado
    def canCreate = false

    def days_advance = 0 //control.containsKey("período_lançamento_de_perguntas") ? Integer.parseInt(control[_("Período Lançamento de Perguntas")][0]) : 0
    if (control.containsKey("período_lançamento_de_perguntas")) {
        if (!control[_("Período Lançamento de Perguntas")][0].equals("undefined")) {
            days_advance = Integer.parseInt(control[_("Período Lançamento de Perguntas")][0])
        }
    }
    // vamos buscar a periodicidade e os dias atuais (Semana e mes)
    def periodicidade = control[_("Periodicidade")][0]
    def dayOfWeek = now.getAt(Calendar.DAY_OF_WEEK)
    def dayOfMonth = now.getAt(Calendar.DAY_OF_MONTH)
    def currMonth = now.getAt(Calendar.MONTH)
    def dayOfYear = now.getAt(Calendar.DAY_OF_YEAR)

    // Inicializamos outra vez os valores target (conforme feito no copyOrCreateAssessment)
    def targetDayWeek = 2
    def targetDayMonth = 1
    def targetMonths = []

    // Vamos buscar os dias em que os controlos é suposto serem avaliados
    if (periodicidade.equals("Semanal")) {
        targetDayWeek = getDayOfWeekNumber(control.containsKey("dia_da_semana") ? control[_("Dia da Semana")][0] : "Segunda")
    }
    if (periodicidade.equals("Mensal")) {
        targetDayMonth = control.containsKey("dia_do_mês") ? control[_("Dia do Mês")][0] : 1
    }
    if (periodicidade.equals("Anual")) {
        def months = control[_("Meses")] //.split("\u0000")
        for (month in months) {
            targetMonths.add(getMonthNumber(month))
        }
        targetMonths = targetMonths.toSet()
    }

    /*
    Se foram especificados dias para criaçao previa de questoes, temos que calcular em que dia
    é que temos que criar os questionarios para ter isso em conta.
    O calculateQuestionCreationDay calcula exatamente esse dia para o timespan em questao.
 */
    switch (periodicidade) {
        case "Diária":
            canCreate = true
            break;
        case "Semanal":
            if (days_advance > 0) {
                canCreate = calculateQuestionCreationDay(7, targetDayWeek, days_advance, dayOfWeek)
            } else {
                canCreate = (targetDayWeek == dayOfWeek)
            }
            break;
        case "Mensal":
            if (days_advance > 0) {
                def timespan = getTotalDaysInTimespan(periodicidade)
                canCreate = calculateQuestionCreationDay(timespan, targetDayMonth, days_advance, dayOfMonth)
            } else {
                canCreate = (targetDayMonth == dayOfMonth)
            }
            break;
        case "Anual":
            if (days_advance > 0) {
                // Precisamos de ter em conta se só escolheu 1 mês, ou vários meses
                def days_in_year = getTotalDaysInTimespan(periodicidade)
                def targetDayOfYear = getDayOfYear(currMonth, 1) //get day of year for target month's first day
                if (targetMonths.size() == 1 || (targetMonths.size() > 1 && targetMonths.contains(currMonth))) {
                    canCreate = calculateQuestionCreationDay(days_in_year, targetDayOfYear, days_advance, dayOfYear)
                }
            } else {
                // Se nao forem especificados dias de antecedencia, é sempre no primeiro dia do mes atual.
                canCreate = (targetMonths.contains(currMonth) && dayOfMonth == 1)
            }
            break;
    }

    def quarterHour = now.getAt(Calendar.MINUTE).intdiv(15)
    def hourOfDay = now.getAt(Calendar.HOUR_OF_DAY)

    if (((hourOfDay == 8 && quarterHour == 2) || (runType == "forceQuestions"))) {

        // Se pedimos para criar perguntas especificamente, temos que invalidar as perguntas anteriores
        // A invalidaçao e feita apenas quando é feita uma avaliaçao, algo que nao acontece quando
        // pedimos para criar questionarios "forcibly"
        if (runType == "forceQuestions") {
            recordm.update(DEF_MANUAL_FORM, "control:${control[_('id')]} AND activo:Sim", ["Activo": "Não"])
        }

        if (canCreate || (runType == "forceQuestions")) {

            // Rever esta ordem dos IFs
            if (control["_marked_assessmentId_"] || control["_marked_ToEval_"]) {
                createManualForms(control, new_assessment_id ? new_assessment_id : control["_marked_assessmentId_"])
            } else if (new_assessment_id && !hasPreviousDayAssessment) {
                createManualForms(control, new_assessment_id)
            } else if (!new_assessment_id && old_assessment_id) {
                createManualForms(control, old_assessment_id)
            } else if (!new_assessment_id && old_assessment_id && days_advance > 0) {
                // CASO por averiguar
                createManualForms(control, old_assessment_id)
            } else if (new_assessment_id) {
                createManualForms(control, new_assessment_id)
            }
        }
    }

    /*
        CASO UPDATE QUESTIONARIOS

        A mudar de um dia para o outro e é criado novo assessment:
        é necessario atualizar os pointers dos questionarios ACTIVOS para o novo assessment,
        mesmo que nao se faça a avaliacao!
     */
    if (new_assessment_id && hasPreviousDayAssessment) {
        updateManualForms(control, old_assessment_id, new_assessment_id)
    }

}


def createManualForms(control, assessment_id) {
    def definition = control[_("Definição")] ? control[_("Definição")][0] : ""
    def query = control[_("Filtro")] ? control[_("Filtro")][0] : ""

    // TODO - check if failsafe is necessary -> previous forms are always disaled when creating new ones
    recordm.update(DEF_MANUAL_FORM, "control:${control[_('id')]} AND activo:Sim", ["Activo": "Não"])

    // Iteramos pelas instancias da definition e query configuradas
    // e criamos um questionario para cada um
    if (definition?.trim() && query?.trim()) {
        recordm.stream(definition, query, { hit ->
            def manual_form = [:]
            def assessment_type = control[_("Tipo de Assessment")][0]
            manual_form << ["Assessment": assessment_id]
            manual_form << ["Control": control.id]
            manual_form << ["Data": new Date().time]
            manual_form << ["Pergunta ou Verificação": control[_("Pergunta ou Verificação")][0]]
            manual_form << ["Tipo de Assessment": assessment_type]
            manual_form << ["Âmbito": control[_("Âmbito")][0]]

            def definitionId = hit.getRaw()._source._definitionInfo.id
            manual_form << ["Entidade": hit.id]
            manual_form << ["Definição": definition]
            manual_form << ["ID Definição": definitionId]

            manual_form << ["Descrição de Controlo de Origem": control[_("Descrição")][0] ]

            if (assessment_type.equals("Atingimento de valor")) {
                manual_form << ["Valor alvo": control[_("Valor alvo")][0]]
            }

            recordm.create(DEF_MANUAL_FORM, manual_form)
        })
    } else {
        def manual_form = [:]
        def assessment_type = control[_("Tipo de Assessment")][0]
        manual_form << ["Assessment": assessment_id]
        manual_form << ["Control": control.id]
        manual_form << ["Data": new Date().time]
        manual_form << ["Pergunta ou Verificação": control[_("Pergunta ou Verificação")][0]]
        manual_form << ["Tipo de Assessment": assessment_type]
        manual_form << ["Âmbito": control[_("Âmbito")][0]]

        manual_form << ["Entidade": control.id]
        // here we associate the form to the control itself because there is no specified scope
        manual_form << ["Definição": "Control"]
        manual_form << ["ID Definição": control._definitionInfo.id]

        manual_form << ["Descrição de Controlo de Origem": control[_("Descrição")][0] ]

        if (assessment_type.equals("Atingimento de valor")) {
            manual_form << ["Valor alvo": control[_("Valor alvo")][0]]
        }

        recordm.create(DEF_MANUAL_FORM, manual_form)
    }

}


def updateManualForms(control, old_assessment_id, new_assessment_id) {
    def query = "assessment:${old_assessment_id} AND activo:Sim"
    recordm.update(DEF_MANUAL_FORM, query, ["Assessment": new_assessment_id])
}


// ====================================================================================================
// MAIN LOGIC SUPPORT METHODS
// ====================================================================================================
// ----------------------------------------------------------------------------------------------------
// obtemMatrizCompletaDePesos - retorna matriz com pesos de cada nível e control
// ----------------------------------------------------------------------------------------------------
def obtemMatrizCompletaDePesos(controls) {
    def pesos = [:]
    def done = [:]

    controls.each { control ->

        def g1 = control[_("Id Goal Nível 1")][0]
        def g2 = control[_("Id Goal Nível 2")][0]
        def g3 = control[_("Id Goal Nível 3")][0]
        def c = control.id

        // Obtem peso de cada nível. Caso seja 0 põe valor perto de 0 pois queremos contabilizar todos os elementos.
        float p1 = (control[_("Peso Goal Nível 1")][0]).toInteger() ?: 0.00001
        float p2 = (control[_("Peso Goal Nível 2")][0]).toInteger() ?: 0.00001
        float p3 = (control[_("Peso Goal Nível 3")][0]).toInteger() ?: 0.00001
        float pC = (control[_("Peso")][0]).toInteger() ?: 0.00001

        if (!done[g1]) {
            done[g1] = true
            pesos["global"] = pesos["global"] ? pesos["global"] + p1 : p1
        }
        if (!done[g2]) {
            done[g2] = true
            pesos[g1] = pesos[g1] ? pesos[g1] + p2 : p2
        }
        if (!done[g3]) {
            done[g3] = true
            pesos[g2] = pesos[g2] ? pesos[g2] + p3 : p3
        }
        if (!done[c]) {
            done[c] = true
            pesos[g3] = pesos[g3] ? pesos[g3] + pC : pC
        }
    }
    return pesos
}

// Extension / new version of previous method to support dynamic weights.
// May be redundant. WIP.
def obtemMatrizCompletaDePesosDynamic(controls) {
    def pesos = [:]
    def done = [:]

    controls.each { control ->
        // Control
        def c = control.id
        float pC = (control[_("Peso")][0]).toInteger() ?: 0.00001
        // Goal IDs and Weights
        def g1, g2, g3
        def p1, p2, p3

        // ATM of writing, lvl1 always exists
        g1 = control[_("Id Goal Nível 1")][0]
        p1 = (control[_("Peso Goal Nível 1")][0]).toInteger() ?: 0.00001
        if (!done[g1]) {
            done[g1] = true
            pesos["global"] = pesos["global"] ? pesos["global"] + p1 : p1
        }

        // Se existir um lvl2
        if ( control[_("Nome Goal Nível 2")] ) {
            g2 = control[_("Id Goal Nível 2")][0]
            p2 = (control[_("Peso Goal Nível 2")][0]).toInteger() ?: 0.00001
            if (!done[g2]) {
                done[g2] = true
                pesos[g1] = pesos[g1] ? pesos[g1] + p2 : p2
            }
        }

        // atm, por defeito da def, o lvl3 existe smp tmb e PODE ser igual ao lvl1 se corresponder
        // ao goal escolhido.
        g3 = control[_("Id Goal Nível 3")][0]
        p3 = (control[_("Peso Goal Nível 3")][0]).toInteger() ?: 0.00001
        if (g1 != g3) {
            if (!done[g3]) {
                done[g3] = true
                // se existiu um lvl2, queremos ter isso em conta. senao vamos usar os pesos do lvl1
                pesos[g2 ?: g1] = pesos[g2 ?: g1] ? pesos[g2 ?: g1] + p3 : p3

            }
        }

        // Contar com peso final do controlo
        if (!done[c]) {
            done[c] = true
            // se existe um g3 != g1, entao é o ultimo goal cujos pesos queremos contar
            if (g1 != g3) {
                pesos[g3] = pesos[g3] ? pesos[g3] + pC : pC
            } else {
                // caso contrario queremos o ultimo goal: g1 ou g2 dependendo do nivel
                pesos[g3] = pesos[g2 ?: g1] ? pesos[g2 ?: g1] + pC : pC
            }

        }

    }
    return pesos
}

// ----------------------------------------------------------------------------------------------------
//  getAssessmentInstance - obtem assessment valido actual e cria se não existir. Marca se for para avaliar.
// ----------------------------------------------------------------------------------------------------
def getAssessmentInstance(control, runType, pesos) {

    // Obtem último assessment ainda válido feito para este control
    def lastValidAssessment = getLastValidAssessment(control)

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
    assessment << ["Control": "" + control[_("Nome")][0]]
    assessment << ["Id Control": "" + control.id]
    assessment << ["Âmbito": "" + control[_("Âmbito")][0] ?: ""]

    assessment << ["Goal Nível 1": "" + control[_("Nome Goal Nível 1")][0]]
    assessment << ["Id Goal Nível 1": "" + g1]

    assessment << ["Goal Nível 2": "" + control[_("Nome Goal Nível 2")][0]]
    assessment << ["Id Goal Nível 2": "" + g2]

    assessment << ["Goal Nível 3": "" + control[_("Nome Goal Nível 3")][0]]
    assessment << ["Id Goal Nível 3": "" + g3]

    // Calcula peso relativo de cada nível (usando o total de pesos por nível guardado na matriz) - multiplica por 100 para não provocar arredondamentos imprevistos
    p1 = p1 ? p1.toInteger() * 100 / pesos["global"] : 0
    p2 = p2 ? p2.toInteger() * 100 / pesos["" + g1] : 0
    p3 = p3 ? p3.toInteger() * 100 / pesos["" + g2] : 0
    p4 = p4 ? p4.toInteger() * 100 / pesos["" + g3] : 0
    float pglobal = p1 * p2 * p3 * p4 / (100 * 100 * 100)
    pglobal = pglobal ?: 0.001
    //Caso o peso global seja 0, porque um ou mais dos elementos avaliados tem peso 0, substituimos o valor calculado por um valor muito muito baixo, mas diferente de zero

    // Atribui peso global relativo ao control. A relação com qq outro control estará assim regulada. - dividido por 100*100*100 para compensar as multiplicações
    assessment << ["Peso Global": "" + pglobal]

    return assessment
}

// New version of previous method for dynamic weight handling. WIP
def getAssessmentInstanceDynamicWeights(control, runType, pesos) {
    // Obtem último assessment ainda válido feito para este control
    def lastValidAssessment = getLastValidAssessment(control)

    // Avalia existência, datas, periodicidade para decidir se se copia dados do assessment ou se se cria um novo
    def assessment = copyOrCreateAssessment(control, lastValidAssessment, runType)

    def goals = []
    def weights = []
    def processed = [:]

    def peso_control = control[_("Peso")][0]

    // Completa preenchimento dos dados do assessment
    assessment << ["Control": "" + control[_("Nome")][0]]
    assessment << ["Id Control": "" + control.id]
    assessment << ["Âmbito": "" + control[_("Âmbito")][0] ?: ""]

    assessment << ["Goal Nível 1": "" + control[_("Nome Goal Nível 1")]?.get(0) ?: ""]
    assessment << ["Id Goal Nível 1": "" + control[_("Id Goal Nível 1")]?.get(0) ?: ""]

    assessment << ["Goal Nível 2": "" + control[_("Nome Goal Nível 2")]?.get(0) ?: ""]
    assessment << ["Id Goal Nível 2": "" + control[_("Id Goal Nível 2")]?.get(0) ?: ""]

    assessment << ["Goal Nível 3": "" + control[_("Nome Goal Nível 3")]?.get(0) ?: ""]
    assessment << ["Id Goal Nível 3": "" + control[_("Id Goal Nível 3")]?.get(0) ?: ""]


    def goal_key = "Id Goal Nível "
    def weight_key = "Peso Goal Nível "

    // LOOP para obter goals and weights. Atualmente mega overcomplicated por causa da definiçao.
    // ---
    // We go over each goal we find in our Control, and we store its ID and respective weight.
    // currently we need to perform checks for repeated goals if we use a non-level 3 goal,
    // which will appear multiple times due to a currently inflexible definition.
    for(int i = 1; i <=3; i++) {
            // if control has "id goal nivel X"
            if ( control[_(goal_key+i)] ) {
                def goal_id = control[_(goal_key+i)][0].toInteger()
                // check if we've processed this model (verification described in the comment above the loop)
                if ( !processed[goal_id] ) {
                    processed[goal_id] = true
                    goals << control[_(goal_key+i)][0]
                    // if control has "peso goal nivel X"
                    if ( control[_(weight_key+i)] ) {
                        weights << control[_(weight_key+i)][0]
                    }
                }
            }
    }

    // LOOP para calcular o peso relativo de cada nivel
    def pLevels = []
    def totalWeight = pesos["global"] ?: 1
    // Percorrer todos os goals presentes
    for (int i = 0; i < goals.size(); i++) {
        def weight = weights[i] ?: 0 // default to 0 if weight not provided
        if (i == 0) {
            pLevels << (weight.toInteger() * 100 / totalWeight) // equivalent to peso["global"]
        } else {
            def parentGoal = goals[i - 1]
            def parentWeight = pesos["${parentGoal}"] ?: 1
            pLevels << (weight.toInteger() * 100 / parentWeight)
        }
    }


    // Calcula o peso global (nao tem em conta peso do controlo)
    float pglobal = 1
    for (int pLevel : pLevels) {
        pglobal *= pLevel / 100
    }
    // logica para contar com o peso do controlo
    peso_control = peso_control ? peso_control.toInteger() * 100 / pesos["" + goals[goals.size()-1]] : 0
    pglobal *= peso_control

    pglobal = pglobal ?: 0.001
    // Atribui peso global relativo ao control. A relação com qq outro control estará assim regulada. - dividido por 100*100*100 para compensar as multiplicações
    assessment << ["Peso Global": "" + pglobal]

    return assessment
}

// ----------------------------------------------------------------------------------------------------
def getLastValidAssessment(control) {
    def assessment = [:]
    def dayOfWeek = now.getAt(Calendar.DAY_OF_WEEK)
    def dayOfMonth = now.getAt(Calendar.DAY_OF_MONTH)

    // Obtem offset em horas da timezone para corrigir pesquisa ao ES
    def cal = Calendar.instance
    Date date = cal.getTime()
    TimeZone tz = cal.getTimeZone()
    long msFromEpochGmt = date.getTime() - ((8 * 60) + 30) * 60 * 1000 //Só muda o cálculo às 8h30
    int offsetFromUTC = tz.getOffset(msFromEpochGmt) / 3600000

    def limiteInferiorData
    def subtracaoPeriodo = "" //utilizado para subtrair o periodo correspondente ao periodo do controlo - se a avaliaçao nao corresponder a 2ªf
    switch (control[_("Periodicidade")][0]) {
        case "Mensal":
            limiteInferiorData = "M";
            if (dayOfMonth != 1) { subtracaoPeriodo = "-1M" }
            break
        case "Semanal":
            limiteInferiorData = "w";
            if (dayOfWeek != 1) { subtracaoPeriodo = "-1w" }
            break
        case "Anual": limiteInferiorData = "y";  break
        default: limiteInferiorData = "d"; break  // Diário e 15/15m
    }

    /* Old query:
     Obtem registo mais recente (primeiro dos resultados) de assessment válidos no RecordM. data.date:>=now-8h-30m+1h\/d+8h+30m-1h
     o cálculo é: 'now' menos 'offsetUTC' (para não considerar o dia errado, que provoca engano nas semanas e meses) arredondado ao periodo (d | w | M) e deslocado para as 8h30 (com a devida correcção de 'offsetUTC')

     Updated query
     mantem-se toda a logica, com a adiçao de suporte de subtracao de um periodo correspondente à periodicidade do controlo.
     isto é necessario para quando os controlos nao sao avaliados às segundas feiras ou no primeiro dia dos meses.
     data.date:>=now-8h-30m+1h[subtracaoPeriodo]\/w+8h+30m-1h -> data.date:>=now-8h-30m+1h[-1w]\/w+8h+30m-1h
    */
    def lastValidAssessmentFilter = "${_("Id Control")}.raw:${control.id} AND ${_("Data do Resultado")}.date:>=now-8h-30m+${offsetFromUTC}h${subtracaoPeriodo}\\/${limiteInferiorData}+8h+30m-${offsetFromUTC}h".toString()
    return getInstances("Assessment", lastValidAssessmentFilter)[0]
}
// ----------------------------------------------------------------------------------------------------
def copyOrCreateAssessment(control, lastValidAssessment, runType) {
    def assessment = [:]
    def periodicidade = control[_("Periodicidade")][0]
    def today = new Date(new Date(now.time).time - new Date((8 * 60 + 30) * 60000).time).clearTime()
    today.set(hourOfDay: 8, minute: 30)
    today = "" + today.time

    // Get target days
    def targetDayWeek = 2
    def targetDayMonth = 1
    def targetMonths = [0] //months are 0-indexed

    // Vai buscar o target day of the week ou target day of the month dependendo da periodicidade do controlo
    // para saber quando pode correr.
    if (periodicidade.equals("Semanal")) {
        targetDayWeek = getDayOfWeekNumber(control.containsKey("dia_da_semana") ? control[_("Dia da Semana")][0] : "Segunda")
    }
    if (periodicidade.equals("Mensal")) {
        targetDayMonth = control.containsKey("dia_do_mês") ? Integer.parseInt(control[_("Dia do Mês")][0]) : 1
    }
    if (periodicidade.equals("Anual")) {
        for (month in control[_("Meses")][0].split("\u0000")) {
            targetMonths.add(getMonthNumber(month))
        }
        targetMonths = targetMonths.toSet()
    }

    if (lastValidAssessment) {
        if (lastValidAssessment[_("Data")][0] == today) {
            // Se a data da assessment anterior é igual à actual mantem o id (ou seja actualiza o assessment existente) caso contrário cria um novo
            assessment << ["id": "" + lastValidAssessment.id]

            // Verifica se há comandos devicem com resultados por avaliar
            if (control[_("Assessment Tool")][0] == "DeviceM") {
                if (getFirstValue(lastValidAssessment, _("DeviceM JobID")) != null) {
                    control << ["_marked_CollectDeviceMValues_": lastValidAssessment[_("DeviceM JobID")][0]]
                }
            }
        } else {
            assessment << ["Data": today]
            // necessario para verificacoes de casos de criaçao/gestao de questionarios manuais
            control['_marked_hasPrevious_'] = true
        }
        // Necessario para poder atualizar pointers de novos questionarios manuais
        control['_marked_previousAssessmentId_'] = lastValidAssessment.id

        assessment << ["Objectivo": "" + getFirstValue(lastValidAssessment, _("Objectivo")) ?: ""]
        assessment << ["Resultado": "" + getFirstValue(lastValidAssessment, _("Resultado")) ?: ""]
        assessment << ["Atingimento": "" + getFirstValue(lastValidAssessment, _("Atingimento")) ?: ""]
        assessment << ["Data do Resultado": "" + getFirstValue(lastValidAssessment, _("Data do Resultado")) ?: ""]
        assessment << ["Observações": "" + getFirstValue(lastValidAssessment, _("Observações")) ?: ""]

        // Decompõe o clock tick (arrendondado aos 15m certos)
        def quarterHour = now.getAt(Calendar.MINUTE).intdiv(15)
        def hourOfDay = now.getAt(Calendar.HOUR_OF_DAY)
        def dayOfWeek = now.getAt(Calendar.DAY_OF_WEEK)
        def dayOfMonth = now.getAt(Calendar.DAY_OF_MONTH)
        def currMonth = now.getAt(Calendar.MONTH)

        // Se há um assessment válido só será para reavaliar se:
        if ((runType == "forceAssessment")  // Avaliação pedida explicitamente na interface
                ||
                (periodicidade == "15m")  // Periodicidade menor que o dia (corre sempre pois é a unidade minima de tempo)
                ||
                (hourOfDay == 8 && quarterHour == 2) // Se for o início do dia (definido como 8h30) e:
                && (
                periodicidade == "Diária"   // Ou for diário
                        ||
                        (dayOfWeek == targetDayWeek && periodicidade == "Semanal") // Ou for Semanal e for segunda(além das 8h30)
                        ||
                        (dayOfMonth == targetDayMonth && periodicidade == "Mensal") // Ou for Mensal e primeiro dia do mês (além das 8h30)
                        ||
                        (targetMonths.contains(currMonth) && dayOfMonth == 1 && periodicidade == "Anual") // Ou se for Anual, e primeiro dia de um mês marcado para avaliaçao
        )
        ) {

            // Se ele for manual, e está dentro da periodicidade, vamos marcar o control com o ID do assessment
            // para onde os questionarios mais recentes / os ultimos questionarios ativos apontam
            if (control[_("Assessment Tool")][0].equals("Manual")) {
                control << ["_marked_assessmentId_": lastValidAssessment.id]
            }

            control << ["_marked_ToEval_": true]
            assessment << ["Data do Resultado": "" + now.time]
            // Se não é novo marca para apenas actualizar a data se a avaliação mudar
            if (assessment["id"]) control << ["_marked_OnlyUpdateDataIfChanged": true]
        }
    } else {
        assessment << ["Data": today]
        // Se algo correr mal a avaliação base é 0
        assessment << ["Atingimento": "0"]
        assessment << ["Data do Resultado": "" + now.time]
        // Se é um novo assessment é sempre para avaliar, EXCEPTO SE FOR MANUAL E NAO DIARIO. se for manual temos que fazer verificaçao
        if (!control[_("Assessment Tool")][0].equals("Manual")) {
            control << ["_marked_ToEval_": true]
        } else if (periodicidade.equals("Diária")) {
            control << ["_marked_ToEval_": true]
        } else {
            // If its a manual control and its NOT daily - we need to confirm the periodicity to check if it CAN be evaluated
            if (checkPeriodicity(runType, periodicidade, now.getAt(Calendar.HOUR_OF_DAY), now.getAt(Calendar.MINUTE).intdiv(15),
                    now.getAt(Calendar.DAY_OF_WEEK), targetDayWeek,
                    now.getAt(Calendar.DAY_OF_MONTH), targetDayMonth, targetMonths, now.getAt(Calendar.MONTH))
            ) {
                control << ["_marked_ToEval_": true]
            }
        }

    }
    return assessment
}


// ----------------------------------------------------------------------------------------------------
//  assessControl - executa o assessmentTool
// ----------------------------------------------------------------------------------------------------
def assessControl(control) {
    def assessment = [
            "Findings": []
    ]

    // Constroi lista com findings abertos do control (mais performante que pedir um de cada vez)
    def openFindings = obterFindingsAbertos(control)

    //Obtem lista de elementos a avaliar
    def evaluationData
    switch (control[_("Assessment Tool")][0]) {
        case "RecordM": evaluationData = getEvaluationDataRecordM(control); break
        case "DeviceM": evaluationData = getEvaluationDataDeviceM(control); break
        case "Manual": evaluationData = getEvaluationDataManual(control); break
    }
    def evaluationList = evaluationData.evalList
    assessment << evaluationData.assessmentInfo

    //Se a resposta já tem o valor de atingimento a zero é porque foi encontrado alguma inconformidade e já trás o erro obtido
    if (assessment["Atingimento"] == "0") {
        return assessment
    }

    // Inicializa resultados
    def objectivoTotal = 0
    def atingimentoTotal = 0
    def processedFindings = []

    def specialVars = obterVariaveisEspeciaisDoControlo(control)
    def specialVarAssessments = [:]

    // Realiza a avaliação do control contra cada registo especificado
    evaluationList.each { instanceToEval ->
        def previousFinding = openFindings["" + instanceToEval.id]
        def resultado = evalInstance(control, instanceToEval, previousFinding)
        objectivoTotal += resultado["Objectivo"]
        atingimentoTotal += resultado["Atingimento"]

        def finding = createOrUpdateFinding(control, openFindings, instanceToEval, resultado)

        if (finding != null) {
            processedFindings += finding
            assessment["Findings"] += finding

            specialVars.each { specVar ->
                addSpecialAssessMap(specVar, resultado, finding, specialVarAssessments)
            }
        }

        openFindings.remove("" + instanceToEval.id)
    }

    specialVarAssessments.each { var, assessMap ->
        assessMap.findAll { it.value["Findings"].size() > 0 }.each { key, sAssess ->
            sAssess << buildAssessmentResultMap(sAssess["Findings"], Double.valueOf(sAssess["Objectivo"]), Double.valueOf(sAssess["Atingimento"]))
        }
    }

    assessment << buildAssessmentResultMap(processedFindings, objectivoTotal, atingimentoTotal)

    // Para cada finding aberto que não tenha sido processado (por não fazer mais parte da lista de instâncias a avaliar) repor e indicar remoção
    if (!(control[_("Assessment Tool")][0] == "DeviceM" && control["_marked_ToEval_"])) {
        openFindings.each { finding ->
            def resultado = ["Objectivo": "mock_value", "Atingimento": "mock_value", "Observações": "Instance removed from evaluation filter"]
            def instanceToEval = ["id": finding.key]
            processedFindings += createOrUpdateFinding(control, openFindings, instanceToEval, resultado) ?: []
        }
    }

    def suspeitos = processedFindings.findAll { !it.containsKey("_marked_Inaltered") && (it.containsKey("Estado") && !it["Estado"].contains("Suspeito")) }
    def markedNew = processedFindings.findAll { it.containsKey("_marked_New") }

    // Se houver alterações aos findingss (ie, se há algum que não esteja _marked_Inaltered) então marca o assessment como alterado para: indicar envio do email de alterações e necessidade de actualizar Observações e Data de Resultado
    if (suspeitos.size() != 0) {
        assessment << ["_marked_Changed": true]

        suspeitos.each { finding ->
            specialVarAssessments.each { var, assessMap ->
                assessMap.findAll { it.value["Findings"].contains(finding) }.each { key, sAssess ->
                    sAssess["_marked_Changed"] = true
                }
            }
        }

//TODO: se pelo menos uma action com atraso no aviso, então adiciona campo ao assessment com todos os findings
        if (markedNew.size() != 0) {
            assessment << ["_marked_New_Findings": true]

            markedNew.each { finding ->
                specialVarAssessments.each { var, assessList ->
                    assessList.findAll { it.value["Findings"].contains(finding) }.each { key, sAssess ->
                        sAssess["_marked_New_Findings"] = true
                    }
                }
            }
        }
    }

    assessment["Assessments Especiais"] = specialVarAssessments

    return assessment
}

static def obterVariaveisEspeciaisDoControlo(control) {
    Set<String> specialVars = new HashSet<>()

    ["Telemóvel", "Email Destino"].each { fieldName ->
        def value = control.containsKey(_(fieldName)) ? control[_(fieldName)][0] : null

        if (value != null) {
            def vars = (value =~ REGEX_VARS_ESPECIAIS)

            vars.each {
                specialVars.add(it[1]) //it[1] = Nome da variável SEM delimitadores (ex: $a$ = a)
            }
        }
    }

    return specialVars
};

def addSpecialAssessMap(specVar, resultado, finding, specialVarAssessments) {

    def valor = resultado[specVar]

    if (valor != null) {
        def specAssessMap = specialVarAssessments[specVar] ?: [:]
        def sa = specAssessMap[valor]

        if (sa == null) {
            sa = [
                    "Findings"   : [finding],
                    "Objectivo"  : Double.valueOf(resultado["Objectivo"] ?: 0),
                    "Atingimento": Double.valueOf(resultado["Atingimento"] ?: 0),
                    "Observações": ""
            ]

        } else {
            sa["Findings"] += finding
            sa["Objectivo"] += Double.valueOf(resultado["Objectivo"] ?: 0)
            sa["Atingimento"] += Double.valueOf(resultado["Atingimento"] ?: 0)
        }

        specAssessMap.put(valor, sa)
        specialVarAssessments.put(specVar, specAssessMap)
    }
}

def buildAssessmentResultMap(findings, objectivoTotal, atingimentoTotal) {
    return [
            "Objectivo"  : "" + objectivoTotal,
            "Atingimento": "" + atingimentoTotal,
            "Resultado"  : "" + ((atingimentoTotal == 0 && objectivoTotal == 0) ? 100 : Math.round((100 * atingimentoTotal / (objectivoTotal ?: 1)) * 100) / 100), // % arredondada às décimas
            "Observações": "" + buildReport(findings)
    ]
}
// ----------------------------------------------------------------------------------------------------
def obterFindingsAbertos(control) {
    def existingFindings = [:]
    def query = "control.raw:${control.id} AND ${_("Estado")}.raw:(Suspeito OR \"Por Tratar\" OR \"Em Resolução\") ".toString()
    def searchResult = getInstances("Finding", query)
    searchResult.each { finding ->
        existingFindings << [
                (finding[_("Id Asset Origem")][0]): [
                        "id"                      : "" + finding.id,
                        "Control"                 : "" + control.id,
                        "Identificador do Finding": getFirstValue(finding, _("Identificador do Finding")) ?: "",
                        "Estado"                  : getFirstValue(finding, _("Estado")) ?: "",
                        "Reposição Detectada"     : getFirstValue(finding, _("Reposição Detectada")) ?: "",
                        "Label Asset Origem"      : getFirstValue(finding, _("Label Asset Origem")) ?: "",
                        "Observações"             : getFirstValue(finding, _("Observações")) ?: "",
                        "Id Definição Origem"     : getFirstValue(finding, _("Id Definição Origem")) ?: "",
                        "Id Asset Origem"         : getFirstValue(finding, _("Id Asset Origem")) ?: "",
                        "_limite_suspeita_"       : getFirstValue(finding, _("Data Limite da Suspeita")) ?: ""
                ]
        ]
    }
    return existingFindings
}
// ----------------------------------------------------------------------------------------------------

// Manual evaluation - instances are always of type DEF_MANUAL_FORM
def evalInstanceManual(control, instanceToEval, previousFinding) {
    def resultado = [:]
    resultado << ["Objectivo": 1]
    resultado << ["Atingimento": 1]
    def observacoes = ""

    // Avaliaçao OK NOK
    if (instanceToEval.tipo_de_assessment[0].equals("OK NOK")) {
        if (instanceToEval.está_ok) {
            resultado << ["Atingimento": (instanceToEval.está_ok[0].equals("OK") ? 1 : 0)]
            observacoes = "Estado: " + instanceToEval.está_ok[0]
        } else {
            resultado << ["Atingimento": 0]
            observacoes = "Estado: NOK"
        }

    }

    // Avaliaçao c/ valor alvo
    if (instanceToEval.tipo_de_assessment[0].equals("Avaliação 1 a 10") ||
            instanceToEval.tipo_de_assessment[0].equals("Atingimento de valor")) {
        def atingimento = instanceToEval.valor_registado[0].toInteger() / (int) instanceToEval.valor_alvo[0].toInteger()
        resultado << ["Atingimento": (atingimento == 1 ? 1 : 0)]
        observacoes = "Evolução: " + (int) Math.ceil(atingimento * 100) + "% (" + instanceToEval.valor_registado[0] + " de " + instanceToEval.valor_alvo[0] + ") "
    }

    //TODO - Averiguar se aqui é o melhor sitio para colocar uma observaçao default!
    resultado << ["Observações": observacoes]
    return resultado
}

def evalInstance(control, instanceToEval, previousFinding) {
    // Se o controlo é do tipo Manual, vamos recorrer às verificaçoes manuais
    // para o avaliar.
    if (control[_("Assessment Tool")][0].equals("Manual")) {
        return evalInstanceManual(control, instanceToEval, previousFinding)
    }

    def condicaoSucesso = control[_("Condição de sucesso")][0]

    // Assume de cada avaliação vale 1 para o objectivo e que, à partida, o teste vai passar
    def resultado = [:]
    resultado << ["Objectivo": 1]
    resultado << ["Atingimento": 1]

    def output = null
    try {
        // Prepara dados e código adicional a passar para a avaliação de forma a simplificar o código de avaliação
        def evalInfo = prepareEvalInfo(condicaoSucesso, instanceToEval, previousFinding)

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
def prepareEvalInfo(condicaoSucesso, instanceToEval, previousFinding) {
    def evalMap = [:]

    //Aumenta código de avaliação com dados e métodos para simplificar a escrita dos testes
    evalMap["instancia"] = instanceToEval
    evalMap["previousFinding"] = previousFinding
    evalMap["log"] = { msg -> log.info(msg) }
    evalMap["rmRest"] = rmRest

    evalMap["pesquisaRegistos"] = { nomeDefinicao, pesquisa, size ->
        def resp = recordm.search(nomeDefinicao, pesquisa, ["size": size]);
        if (!resp.ok()) {
            throw new Exception("Não foi possível fazer a pesquisa pretendida ($nomeDefinicao,$pesquisa).")
        }
        return resp
    }

    evalMap["contaRegistos"] = { nomeDefinicao, pesquisa ->
        def resp = recordm.search(nomeDefinicao, pesquisa, ["size": 0]);
        if (!resp.ok()) {
            throw new Exception("Não foi possível fazer a pesquisa pretendida ($nomeDefinicao,$pesquisa).")
        }
        return resp.getTotal()
    }

    evalMap["utilizadores"] = { String... groups ->
        List users = getUsersWithGroups(groups)

        return [
                "emails"   : users.collect { it.email }.join(","),
                "telefones": users.collect { it.contact ?: "" }.join(",")
        ]
    }

    evalMap["getCpeRecordMInstance"] = { instancia ->
        def definitionName = instanceToEval._definitionInfo.name
        def instance = null

        def query = "id:${instanceToEval.cpeExternalId}"
        def searchResult = getInstances(definitionName, query)

        if (searchResult.size() == 1) {
            instance = searchResult.get(0)
        }

        return instance
    }

    //Load custom client functions
    GovernanceConfig.customAssessmentFunctions.each { fnName, code ->
        def customClosure = (code instanceof String ? evaluate(code) : code)

        //We need the closure's delegate to be the gov_assessment class so we can invoke gov_assessment methods
        // like `somaValoresES` or `mediaValoresES` there
        //Read https://groovy-lang.org/closures.html for full info on groovy closures
        //Note: Instead of just redirect the delegate, lets set the closure `this`,`owner` and `delegate` to be like
        //       the other functions in the evalMap
        evalMap[fnName] = customClosure.rehydrate(this, this, this)
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
        def getCpeRecordMInstance = x.getCpeRecordMInstance
        def resultado = [:]
    '''

    //Load custom client functions declarations
    GovernanceConfig.customAssessmentFunctions.each { fnName, code ->
        baseCode += "\n        def " + fnName + " = x." + fnName
    }
    baseCode += "\n"

    def finalReturnCode = '''
        return resultado;
    '''

    def parsedEvalCode = parse(condicaoSucesso, instanceToEval, "instancia")

    return [map: evalMap, code: baseCode + parsedEvalCode + finalReturnCode]
}

//usado em customAssessmentFunctions
def somaValoresES(indices, pesquisa, campoSoma, campoTempo, momentoInicio) {
    def query = getEsQuery(pesquisa, campoTempo, momentoInicio)
    def aggs = getEsAgg("field_sum", "sum", campoSoma)

    def esJsonStr = "{\"size\":0, " +
            "\"query\":${query}," +
            "\"aggregations\":${aggs}" +
            "}"

    log.debug("$indices || $query || $aggs || $esJsonStr")

    Response response = doAggSearch(indices, esJsonStr)

    String body = response.readEntity(String.class)

    if (response.getStatus().intdiv(100) != 2) {
        throw new Exception("Não foi possível fazer a pesquisa pretendida ($indices, $pesquisa, $campoSoma, $campoTempo, $momentoInicio): $body")
    }

    JSONObject esResult = new JSONObject(body)

    return esResult.aggregations.field_sum.value
};

//usado em customAssessmentFunctions
def mediaValoresES(indices, pesquisa, campoMedia, campoTempo, momentoInicio) {
    def query = getEsQuery(pesquisa, campoTempo, momentoInicio)
    def aggs = getEsAgg("field_avg", "avg", campoMedia)

    def esJsonStr = "{\"size\":0, " +
            "\"query\":${query}," +
            "\"aggregations\":${aggs}" +
            "}"

    def response = doAggSearch(indices, esJsonStr)

    String body = response.readEntity(String.class)

    if (response.getStatus().intdiv(100) != 2) {
        throw new Exception("Não foi possível fazer a pesquisa pretendida ($indices, $pesquisa, $campoMedia, $campoTempo, $momentoInicio): $body")
    }

    JSONObject esResult = new JSONObject(body)

    return esResult.aggregations.field_avg.isNull("value") ? null : esResult.aggregations.field_avg.value
};

def doAggSearch(indices, esJsonStr) {
    //uses integrationm token
    return ClientBuilder.newClient()
            .target(GovernanceConfig.ES_URL)
            .path("/${indices}/_search")
            .request(MediaType.APPLICATION_JSON)
            .cookie(new Cookie("cobtoken", GovernanceConfig.COBTOKEN))
            .post(Entity.entity(esJsonStr, MediaType.APPLICATION_JSON), Response.class)
};

static def getEsQuery(pesquisa, campoTempo, momentoInicio) {
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

    if (campoTempo && momentoInicio) {
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
            "}")

    return esQuery
}

static def getEsAgg(name, type, field) {
    return "{\"${name}\":{" +
            "\"${type}\":{" +
            "\"field\":\"${field}\"" +
            "}" +
            "}}"
}

def parse(textWithVars, instanceToEval, nomeVarInstancia) {
    // $id$ = <id da instancia>
    def parsedText = textWithVars.replace("\$id\$", "" + instanceToEval.id)

    // _[Nome Campo]_ = instancia["<Nome do campo>"][0]
    parsedText = parsedText.replaceAll(/_\[([^\]]*?)\]_/) { m -> "(${nomeVarInstancia}.containsKey(\"${_(m[1])}\") ? ${nomeVarInstancia}[\"${_(m[1])}\"][0] : null)" }

    // $Nome Campo$ = "<valor do campo>"
    parsedText = parsedText.replaceAll(/[$](.+?)[$]/) { m ->
        def fieldName = m[1]
        def esFieldName = _(fieldName)
        def esField = (instanceToEval["${esFieldName}"]
                ?: instanceToEval["${fieldName}"])

        if (esField == null) {
            log.warn("O campo \"${fieldName}\" não existe na instância a avaliar {{instance:${instanceToEval.id}}}")
            return null
        }

        return esField[0]
    }

    return parsedText
}
// ----------------------------------------------------------------------------------------------------
def createOrUpdateFinding(control, openFindings, instanceToEval, resultado) {
    def successFlag = (resultado["Objectivo"] == resultado["Atingimento"])
    def previousFinding = openFindings["" + instanceToEval.id]
    def previousTestOk = previousFinding ? previousFinding["Reposição Detectada"] : ""
    def previousState = previousFinding ? previousFinding["Estado"] : ""

    if (successFlag) {
        if (previousTestOk == "Não") {
            if (previousState == "Suspeito") {
                previousFinding["Estado"] = "Suspeito Cancelado"
            } else {
                previousFinding["_marked_ChangedToOK"] = true
            }
            previousFinding["Data de reposição"] = "" + now.time
            previousFinding["Reposição Detectada"] = "Sim"
            previousFinding["Observações"] = previousFinding["Observações"] ?: ""

            if (resultado["Observações"]) previousFinding["Observações"] = resultado["Observações"]

            createOrUpdateInstance("Finding", previousFinding)
        } else if (previousTestOk == "Sim") {
            previousFinding["_marked_Inaltered"] = true
        }
        // Caso contrário não é necessário fazer nada pois irá retornar o finding existente (se existia) ou então retorna null pois não havia finding (está tudo ok e já estava tudo ok)
    } else {
        // Se havia finding anterior com indicação de 'resposto' então repõe indicação de inconformidade
        if (previousTestOk == "Sim") {
            previousFinding["Reposição Detectada"] = "Não"

            if (resultado["Observações"]) previousFinding["Observações"] = resultado["Observações"]

            createOrUpdateInstance("Finding", previousFinding)
            previousFinding["_marked_ChangedToNOK"] = true

            // Caso houvesse finding anterior só é necessário actualizar o Finding se observação mudou. De qualquer forma retorna o anterior finding para reportar
        } else if (previousTestOk == "Não") {
            if (previousState == "Suspeito") {
                if (Long.valueOf(previousFinding["_limite_suspeita_"]) < now.time) {
                    previousFinding["Estado"] = "Por Tratar"
                    previousFinding["Observações"] = resultado["Observações"] ?: ""
                    createOrUpdateInstance("Finding", previousFinding)
                    previousFinding["_marked_New"] = true
                }
            } else {
                if (resultado["Observações"] && resultado["Observações"].trim() != previousFinding["Observações"]) {
                    if (previousFinding["Observações"] != resultado["Observações"]) {
                        previousFinding["Observações"] = resultado["Observações"] ?: ""
                        createOrUpdateInstance("Finding", previousFinding)
                    }
                }
                previousFinding["_marked_Inaltered"] = true
            }

            // Caso contrário não havia um finding e então cria um novo
        } else {
            def suspectFindindNotYetToCreate = getFirstValue(control, _("Quando considerar inconformidade")) == "Após repetição da detecção"
            def intervalo
            switch (control[_("Periodicidade")][0]) {
                case "Mensal": intervalo = 30 * 24 * 60; break
                case "Semanal": intervalo = 7 * 24 * 60; break
                case "Diária": intervalo = 24 * 60; break
                case "15m": intervalo = 15; break
            }

            def newFinding = [
                    "Estado"                  : suspectFindindNotYetToCreate ? "Suspeito" : "Por Tratar",
                    "Data Limite da Suspeita" : suspectFindindNotYetToCreate ? "" + (now.time + control[_("Quantidade repetições")][0].toInteger() * 60000 * intervalo) : "",
                    "Reposição Detectada"     : "Não",
                    "Control"                 : "" + control.id,
                    "Atribuido a"             : "" + getFirstValue(control, _("Responsável")) ?: "",
                    "Identificador do Finding": "" + control[_("Código")][0] + "-" + instanceToEval.id,
                    "Label Asset Origem"      : "" + (instanceToEval[_(instanceToEval._definitionInfo.instanceLabel.name[0])] ?: [""])[0],
                    "Id Definição Origem"     : "" + instanceToEval._definitionInfo.id,
                    "Id Asset Origem"         : "" + instanceToEval.id
            ]

            // Se a instancia a avaliar for um questionario, temos que apontar para o asset
            // para o qual o questionario aponta - em vez de apontar para o proprio questionario,
            // uma vez que nos interessa ver qual o asset problematico, e nao o questionario em si.
            if (instanceToEval._definitionInfo.name == DEF_MANUAL_FORM) {
                newFinding["Id Definição Origem"] = "" + instanceToEval.id_definição[0]
                newFinding["Id Asset Origem"] = "" + instanceToEval.entidade[0]
            }

            newFinding["Observações"] = resultado["Observações"] ?: (newFinding["Label Asset Origem"] ?: "id:" + instanceToEval.id) + " - " + control[_("Código")][0]

            if (getFirstValue(control, _("Acção")) == "Contabilizar e Reportar Inconformidades") {
                createOrUpdateInstance("Finding", newFinding)
            }

            previousFinding = newFinding //to return
            if (suspectFindindNotYetToCreate == false) {
                if (getFirstValue(control, _("Acção")) == "Contabilizar e Reportar Inconformidades") {
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
static def buildReport(findings) {
    def report = ""
    report += buidlStateReport(findings, "_marked_NewButNoReport", "<b>Inconformidades contabilizadas mas não reportadas:</b>\n")
    report += buidlStateReport(findings, "_marked_New", "<b>NOVAS inconformidades:</b>\n")
    report += buidlStateReport(findings, "_marked_ChangedToNOK", "<b>Inconformidades reabertas:</b>\n")
    report += buidlStateReport(findings, "_marked_ChangedToOK", "<b>Inconformidades aparentemente já não verificadas:</b>\n")
    report += buidlStateReport(findings, "_marked_Inaltered", "<b>Inconformidades INALTERADAS:</b>\n")
    return report
}
// --------------------------------------------------------------------
static def buidlStateReport(findings, changeType, label) {
    def report = ""
    def count = 0
    findings.findAll { it[changeType] }.each { finding ->
        if (count == 0) report += label
        if (count++ < 10) {
            report += " * "
            if (changeType.indexOf("_marked_New") == -1) report += "${finding["Estado"]}"
            if (changeType == "_marked_ChangedToOK" || changeType == "_marked_Inaltered" && finding["Reposição Detectada"] == "Sim") report += "/REPOSTO"
            if (changeType.indexOf("_marked_New") == -1) report += " | "
            report += finding["Observações"] ? finding["Observações"] : finding["Label Asset Origem"]
            report += "\n"
        }
    }
    if (count > 10) report += " * mais ${count - 10} registos\n\n"
    if (count > 0) report += "\n"
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
    if (definitionId == null) {
        assessmentInfo << ["Atingimento": "0"]
        assessmentInfo << ["Observações": "Erro na avaliação: Não é possível executar o AssessTool porque a Definição indicada (" + definitionName + ") não existe"]
    } else {
        def filtro = control[_("Filtro")][0]

        // Obtem lista de instancias a avaliar
        evaluationList = getInstances(definitionName, filtro)
        if (evaluationList.size() == 0) {
            assessmentInfo << ["Atingimento": "0"]
            assessmentInfo << ["Observações": "O filtro indicado não devolve INSTÂNCIAS para avaliar"]
        }
    }
    return ["evalList": evaluationList, "assessmentInfo": assessmentInfo]
}
// --------------------------------------------------------------------
def getEvaluationDataDeviceM(control) {
    def evalList = []
    def assessmentInfo = [:]

    // Se já há um comando executado obtêm os resultados para avaliar o resultado
    if (control["_marked_CollectDeviceMValues_"]) {
        def definitionName = control[_("Definição")][0]
        def definitionId = getDefinitionId(definitionName)

        def jobId = control["_marked_CollectDeviceMValues_"]
        def taskList = actionPacks.get("cmRest").get("/confm/requests/" + jobId + "/tasks")
        def taskListObj = new JSONObject('{ "result":' + taskList + '}')
        evalList = []
        for (int index = 0; index < taskListObj.result.length(); index++) {
            try {
                def hitJson = taskListObj.result.getJSONObject(index)
                /* {commands=R100, state=ok, _definitionInfo={id=36, timeSpent=1, filePreviews=null, uri=http://localhost:40180/confm/requests/2122406/tasks/2122406/tasks/368794552, cpeExternalId=418, id=418, changedFilesInJson=, cpeName=185-Sintra-Agualva, errors=, results=R100=1, cpesJobRequest=2122406, cpe=40, endTimestamp=1502401144176} */
                def hitMap = recordmJsonToMap(hitJson.toString())
                hitMap << ["id": hitMap.cpeExternalId]
                hitMap << ["_definitionInfo": ["id": definitionId, "name": definitionName, "instanceLabel": ["name": ["_label_fake_field_"]]]]
                hitMap << ["_label_fake_field_": [hitMap.cpeName]]
                evalList.add(hitMap)
            } catch (e) {
                //someday jbarata: Algumas vezes dá o erro:
                // Error processing script gov_assessment.groovy: No signature of method: java.lang.String.getJSONObject()
                // is ap plicable for argument types: (java.lang.Integer) values: [0]
                log.info("ERROR " + e)
            }
        }
        if (evalList.size() == 0) {
            assessmentInfo << ["Atingimento": "0"]
            assessmentInfo << ["Observações": "O filtro indicado não devolveu EQUIPAMENTOS para avaliar"]
        }
        assessmentInfo << ["DeviceM JobID": ""]
    }

    // Se está marcado para correr executa comando no DeviceM de forma a poder avaliar o resultado na próxima execução (dentro de 15m)
    if (control["_marked_ToEval_"]) {
        def jobId = execCmdWhere(control[_("Comando")][0], control[_("Filtro")][0])
        assessmentInfo << ["DeviceM JobID": "" + jobId]
    }

    return ["evalList": evalList, "assessmentInfo": assessmentInfo]
}
// --------------------------------------------------------------------
def execCmdWhere(cmd, condition) {
    def fields = new HashMap<String, String>()

    fields["condition"] = condition
    fields["commands"] = cmd

    def resp
    try {
        resp = actionPacks.get("cmRest").post("/confm/integration/cmd", fields)
    } catch (e) {
        log.info("ERROR " + e)
        resp = "NOT_OK"
    }

    if (resp == "NOT_OK") {
        log.error("Error executing commands {{params : " + fields + "}}")
        return null
    } else {
        JSONObject job = new JSONObject(resp)
        return job.getInt("id")
    }
}
// ----------------------------------------------------------------------------------------------------
def getEvaluationDataManual(control) {
    def evaluationList = []
    def assessmentInfo = [:]
    def control_id = control[_('id')]
    def questionarios_query = "control:${control_id} AND activo:Sim"
    // Vamos buscar os questionarios ativos conforme o controlo atual
    evaluationList = getInstances(DEF_MANUAL_FORM, questionarios_query) //assessment:${assessment_id}
    if (evaluationList.size() == 0) {
        assessmentInfo << ["Atingimento": "0"]
        assessmentInfo << ["Objectivo": "0"]
        assessmentInfo << ["Observações": "O filtro indicado não devolve QUESTIONARIOS manuais para avaliar"]
    }

    // Desativamos os questionarios da avaliaçao, porque nao os queremos ter em conta para a proxima vez.
    recordm.update(DEF_MANUAL_FORM, questionarios_query, ["Activo": "Não"]).getBody()
    return ["evalList": evaluationList, "assessmentInfo": assessmentInfo]
}
// ----------------------------------------------------------------------------------------------------

// ----------------------------------------------------------------------------------------------------
//  executaAccoesComplementares -  envia Emails e SMSs
// ----------------------------------------------------------------------------------------------------
def executaAccoesComplementares(control, assessment) {

    def mailActionsIdx = 0
    def smsActionsIdx = 0

    def subject = "Resultado avaliação de ${control[_("Nome")]}".toString()

    def body = assessment["Observações"] ?: "Sem observações."

    if (assessment["_marked_Changed"] || msg.action == "forceAssessment") {
        control[_("Acção Complementar")].eachWithIndex { action, idx ->
            def sendNow = true
            if (getFirstValue(control, _("Tolerância")) == "Prazo após criação") {
                //TODO: Calacular se prazo já foi atingido
                if (getFirstValue(control, _("Prazo")) > now.time) {
                    sendNow = false
                    //calcular findings a incluir
                    //TODO
                }
            }

            def textoBase = control.containsKey(_("Texto")) ? control[_("Texto")][idx] : ""

            if (sendNow && action == "Enviar Email Resumo alterações") {
                def assessmentsEspeciais = [:]

                assessment["Assessments Especiais"].each { specVar, map ->
                    specAssess = map.findAll { key, assess -> assess["_marked_Changed"] }

                    if (specAssess.size() > 0) {
                        assessmentsEspeciais.put(specVar, specAssess)
                    }
                }

                String emails = control[_("Email Destino")][mailActionsIdx]
                def emailsEspeciais = obterVarsEspeciais(emails, assessmentsEspeciais)

                String emailsBcc = control[_("Email Destino BCC")] != null ? control[_("Email Destino BCC")][mailActionsIdx] : ""

                if (emailsEspeciais.size() > 0) {

                    enviarEmailsEspeciais(emailsEspeciais, emailsBcc, subject, textoBase)

                    emails = removerVarsEspeciais(emails, emailsEspeciais)
                }

                if (emails && emails.length() > 0) {
                    GovernanceConfig.sendMail(subject, body + "\n\n" + textoBase, emails, emailsBcc)
                }

                mailActionsIdx++
            }

            //Enviar apenas novas inconformidades para poupar caracteres (max 747 nas SMS)
            if (sendNow && action == "Enviar SMS qd há novas inconformidades" && assessment["_marked_New_Findings"]) {
                def assessmentsEspeciais = [:]

                assessment["Assessments Especiais"].each { specVar, map ->
                    specAssess = map.findAll { key, assess -> assess["_marked_New_Findings"] }

                    if (specAssess.size() > 0) {
                        assessmentsEspeciais.put(specVar, specAssess)
                    }
                }

                def codigo = control[_("Código")][0]
                def numsTel = control[_("Telemóvel")][smsActionsIdx++]

                def numsEspeciais = obterVarsEspeciais(numsTel, assessmentsEspeciais)

                if (numsEspeciais.size() > 0) {

                    enviarSMSEspeciais(numsEspeciais, codigo, textoBase)

                    numsTel = removerVarsEspeciais(numsTel, numsEspeciais)
                }

                if (numsTel.length() > 0) {
                    body = buidlStateReport(assessment["Findings"], "_marked_New", "<b>NOVAS inconformidades:</b>\n") ?: "Sem observações."

                    def finalBody = (textoBase + "\n\n" + body).toString()

                    if (numsTel instanceof String) {
                        GovernanceConfig.sendSms(codigo, finalBody, numsTel)
                    } else {
                        numsTel.each { tel ->
                            GovernanceConfig.sendSms(codigo, finalBody, tel)
                        }
                    }
                }
            }
        }
    }
}

static def obterVarsEspeciais(vars, assessmentsEspeciais) {
    def varsEspeciais = (vars =~ REGEX_VARS_ESPECIAIS)

    return varsEspeciais.collect {
        def parsed = it[1] //Nome da variável especial SEM delimitadores (ex: email)

        return [
                "raw"          : it[0] //Nome da variável especial COM delimitadores (ex: $email$)
                , "parsed"     : parsed
                , "assessments": (assessmentsEspeciais[parsed] ?: [:])
        ]
    }
}

def enviarEmailsEspeciais(emailsEspeciais, String emailsBcc, subject, textoBase) {
    emailsEspeciais.each {
        def assessMap = it["assessments"]

        assessMap.findAll { emails, assessment -> emails.length() > 0 }
                .each { emails, assessment ->

                    def body = assessment["Observações"] ?: "Sem observações."

                    log.info("A enviar email especial para $emails: ${body + "\n\n" + textoBase}}")
                    GovernanceConfig.sendMail(subject, body + "\n\n" + textoBase, emails, emailsBcc)
                }
    }
}

def enviarSMSEspeciais(numsEspeciais, codigo, textoBase) {
    numsEspeciais.each {
        def assessMap = it["assessments"]

        assessMap.findAll { telefones, assessment -> telefones.length() > 0 }
                .each { telefones, assessment ->

                    def body = buidlStateReport(assessment["Findings"], "_marked_New", "<b>NOVAS inconformidades:</b>\n") ?: "Sem observações."

                    log.info("A enviar SMS especial para $telefones: ${textoBase + "\n\n" + body}}")

                    def finalBody = (textoBase + "\n\n" + body).toString()

                    if (telefones instanceof String) {
                        GovernanceConfig.sendSms(codigo, finalBody, telefones)
                    } else {
                        telefones.each { tel ->
                            GovernanceConfig.sendSms(codigo, finalBody, tel)
                        }
                    }
                }
    }
}

static def removerVarsEspeciais(vars, varsEspeciais) {
    varsEspeciais.each {
        vars -= it.raw
    }

    return vars
}


// ====================================================================================================
// GENERIC SUPPORT METHODS
// ====================================================================================================
// ----------------------------------------------------------------------------------------------------
//  getInstances - Para um dado Nome de definição e um filtro obtem array com instâncias
// ----------------------------------------------------------------------------------------------------
def getInstances(nomeDefinicao, query) {
    def result = []

    recordm.stream(nomeDefinicao, query, { hit ->
        result.add(hit.getRaw()._source)
    })

    return result
}
// --------------------------------------------------------------------
def esSourceList(hits) {
    def sourceList = []

    for (int index = 0; index < hits.length(); index++) {
        def hit = hits.getJSONObject(index)

        sourceList.add(recordmJsonToMap(hit._source.toString()))
    }

    return sourceList
}
// --------------------------------------------------------------------
def recordmJsonToMap(content) {
    ObjectMapper mapper = new ObjectMapper()

    return mapper.readValue(content, HashMap.class)
}
// --------------------------------------------------------------------
static def getFirstValue(map, key) {
    return map.containsKey(key) ? map[key][0] : null
}

// ----------------------------------------------------------------------------------------------------
//  getDefinitionId - Obtem o id de uma definição a partir do Nome da mesma
// ----------------------------------------------------------------------------------------------------
def _forceGetDefinitionId(definitionName) {
    def resp = rmRest.get("recordm/definitions/name/" + definitionName, "")

    if (resp != "NOT_OK") {
        JSONObject definition = new JSONObject(resp)

        return definition.id
    }
    return null
}

def getDefinitionId(definitionName) {
    return cacheOfDefinitions.get(definitionName, { _forceGetDefinitionId(definitionName) })
}

// ----------------------------------------------------------------------------------------------------
//  createOrUpdateInstance
// ----------------------------------------------------------------------------------------------------
def createOrUpdateInstance(definitionName, instance) {
    def updates = cloneAndStripInstanceForRecordmSaving(instance)

    if (instance.id) {
        // Update mas apenas se tiver mais que 1 campo (ou seja, excluindo o id)
        if (instance.size() > 1) {
            return recordm.update(definitionName, "recordmInstanceId:" + instance["id"], updates).getBody()
        }
    } else {
        // Create
        return recordm.create(definitionName, updates).getBody()
    }
}

//necessário remover os Boolean da instância para se conseguir gravar no recordm
def cloneAndStripInstanceForRecordmSaving(instance) {
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
static def _(fieldName) {
    return fieldName?.toLowerCase()?.replace(" ", "_")
}
// --------------------------------------------------------------------
def getUsersWithGroups(groups) {
    def query = "groups.name:(\"${groups.join('" AND "')}\") AND -username:test*"

    def result = userm.searchUsers(query, [
            'sort': '_id:asc',
            'size': "50"
    ])

    return result.getHits()
}

// converts string-based week day to int corresponding to Calendar's enums
static def getDayOfWeekNumber(day_of_week) {
    switch (day_of_week) {
        case "Domingo":
            return 1;
        case "Segunda":
            return 2;
        case "Terça":
            return 3;
        case "Quarta":
            return 4;
        case "Quinta":
            return 5;
        case "Sexta":
            return 6;
        case "Sábado":
            return 7;
        default:
            return 2
    }
}

// converts string-based months to ints corresponding to Calendar's enums
def getMonthNumber(month) {
    switch (month) {
        case "Janeiro":
            return Calendar.JANUARY
        case "Fevereiro":
            return Calendar.FEBRUARY
        case "Março":
            return Calendar.MARCH
        case "Abril":
            return Calendar.APRIL
        case "Maio":
            return Calendar.MAY
        case "Junho":
            return Calendar.JUNE
        case "Julho":
            return Calendar.JULY
        case "Agosto":
            return Calendar.AUGUST
        case "Setembro":
            return Calendar.SEPTEMBER
        case "Outubro":
            return Calendar.OCTOBER
        case "Novembro":
            return Calendar.NOVEMBER
        case "Dezembro":
            return Calendar.DECEMBER
        default:
            return Calendar.JANUARY
    }
}

// gets the total timespan according to the periodicity. If its monthly, we get the total number of days for that month.
// if its yearly, we get the total number of days in that year.
def getTotalDaysInTimespan(periodicidade) {
    switch (periodicidade) {
        case "Semanal":
            return 7
        case "Mensal":
            return YearMonth.of(now.getAt(Calendar.YEAR), now.getAt(Calendar.MONTH)).lengthOfMonth()
        case "Anual":
            return Year.of(now.getAt(Calendar.YEAR)).length()
    }
}

// Gets day of year for the given month and a day (in that month)
def getDayOfYear(int month, int firstDayOfMonth) {
    Calendar calendar = Calendar.getInstance()
    calendar.set(Calendar.MONTH, month) // Months in Calendar class are 0-indexed
    calendar.set(Calendar.DAY_OF_MONTH, firstDayOfMonth)

    return calendar.get(Calendar.DAY_OF_YEAR)
}


def normalizeSubtraction(int eval_day, int advance_days, int total_days_in_timespan) {
    // advance days cannot be higher than the number of days in curr month
    // isto faz com que, se especificarmos mais dias de antecedencia que os dias do mês, os quests sao
    // criados assim que possivel (corresponde ao dia da avaliaçao)
    if (advance_days > total_days_in_timespan) {
        advance_days = total_days_in_timespan
    }
    def result = (eval_day - advance_days) % total_days_in_timespan
    // If result is negative, add total_days to make it positive
    if (result <= 0) {
        // <= 0 para que se o eval_day e o advance_days forem iguais, ele arredonda para o ultimo dia do mes anterior
        result += total_days_in_timespan
    }
    return result
}

// Returns true if questions can be created today, false if otherwise
def calculateQuestionCreationDay(timespan, targetDay, days_advance, currDay) {
    int targetDayForQuestionCreation = normalizeSubtraction(targetDay, days_advance, timespan)
    return targetDayForQuestionCreation > 0 ? (targetDayForQuestionCreation == currDay) : (targetDay == currDay)
}

// Replicates logic to check if its time to evaluate a control
def checkPeriodicity(runType, periodicidade,
                     hourOfDay, quarterHour,
                     dayOfWeek, targetDayWeek,
                     dayOfMonth, targetDayMonth, targetMonths, currMonth) {
    return ((runType == "forceAssessment")  // Avaliação pedida explicitamente na interface
            ||
            (periodicidade == "15m")  // Periodicidade menor que o dia (corre sempre pois é a unidade minima de tempo)
            ||
            (hourOfDay == 8 && quarterHour == 2) //&& quarterHour == 2 // Se for o início do dia (definido como 8h30) e:
            && (
            periodicidade == "Diária"   // Ou for diário
                    ||
                    (dayOfWeek == targetDayWeek && periodicidade == "Semanal") // Ou for Semanal e for segunda(além das 8h30)
                    ||
                    (dayOfMonth == targetDayMonth && periodicidade == "Mensal") // Ou for Mensal e primeiro dia do mês (além das 8h30)
                    ||
                    (targetMonths.contains(currMonth) && dayOfMonth == 1 && periodicidade == "Anual") // Ou se for Anual, e primeiro dia de um mês marcado para avaliaçao
    ))
}
