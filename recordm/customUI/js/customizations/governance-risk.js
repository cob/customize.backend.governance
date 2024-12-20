import axios from 'https://cdn.jsdelivr.net/npm/axios@1.1.2/+esm'

cob.custom.customize.push(function (core, utils, ui) {
    const DEFINITION = "Risk";
    const RISK_ANALYSIS_DEF = "Risk Analysis";

        // Add visual aux to show risk classification level.
        // TODO: change this to use the field "Classificação Risco" instead of querying for the
        // most recent analysis. This was done before such field existed.
        core.customizeInstances(DEFINITION, async function (instance, presenter) {
            if (!instance.isNew() && !presenter.isGroupEdit()) {
                let riscoVal = 1
                let classes = "text-center items-center py-1 text-white rounded-md text-xl border-2 "
                let classificationText = "No Analysis Found"

                let last_analysis = await axios.get("/recordm/recordm/definitions/search", {
                    params: {
                        def: RISK_ANALYSIS_DEF,
                        q: "risk:" + instance.data.id,
                        from: 0,
                        size: 1,
                        sort: "data_análise:desc"
                    }
                })

                if (last_analysis.data.hits.total.value > 0) {
                    riscoVal = last_analysis.data.hits.hits[0]._source["classificação_risco"][0]
                    console.log("teste risco", riscoVal)
                    if (riscoVal >= 1 && riscoVal <= 2) {
                        // Very Low
                        classificationText = "Very Low"
                        classes += "bg-green-700 border-green-600"
                    }
                    if (riscoVal >= 3 && riscoVal <= 4) {
                        // Low
                        classificationText = "Low"
                        classes += "bg-green-600 border-green-500"
                    }
                    if (riscoVal >= 5 && riscoVal <= 11) {
                        // Medium
                        classificationText = "Medium"
                        classes += "bg-yellow-400 border-yellow-200"
                    }
                    if (riscoVal >= 13 && riscoVal <= 18) {
                        // High
                        classificationText = "High"
                        classes +=  "bg-orange-400 border-orange-200 "
                    }
                    if (riscoVal >= 19 && riscoVal <= 25) {
                        // Very High
                        classificationText = "Very High"
                        classes += "bg-red-700 border-red-500"
                    }
                } else {
                    classes += "bg-cyan-500"
                }
    
                const closeBtn = `
                <div class="border-t-2 border-gray-300 pt-2">
                    <div class='${classes}' >
                    <i class='fa-solid fa-triangle-exclamation mr-1'></i>
                    <span>${classificationText}</span>
                    </div>

                    <div class="text-center text-xs text-gray-400">
                    Classificação do Risco
                    </div>
                </div>
                `
                //js-sidenav-btn-container
                document.querySelector(".sidenav").insertAdjacentHTML("beforeend", closeBtn)
            }
            
            
        });

})