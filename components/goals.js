import React from 'react';
import ReactDOM  from 'react-dom';

import { Sparklines, SparklinesLine } from 'react-sparklines';

import {
    Button,
    PanelGroup, Panel,
    Well
} from 'react-bootstrap'

import EvolutionBar from './evolution-bar';
import ReactMarkdown from 'react-markdown';


//TODO JBARATA passar esta função para uma classe de uteis
let unimplementedFn = function(){
    window.console.error("NOT IMPLEMENTED - Default prop here! The caller of this component is *NOT* passing this prop.")
};

class Goals extends React.Component{
    constructor(props) {
        super(props);
        this.state = {
            goals: [],
            noGoalsInfo:undefined
        };
    }

    componentDidMount() {
        /*let _this=this;
        setInterval(function(){
            _this.loadGoals(_this.props.level, _this.props.parentGoalId);
        }, 5000);
        */
        this.loadGoals(this.props.level, this.props.parentGoalId,"nome",true);

        this.buildCreateGoalUrl();
    }

    componentWillReceiveProps(nextProps){
        if(this.props.onlyUserFindings != nextProps.onlyUserFindings){
            this.loadGoals(this.props.level, this.props.parentGoalId,"nome",true);
        }
    }

    loadGoals(level, parentGoalId, sortBy, sortAsc){
        let _this = this;
        let query = "nível.raw:" + level;

        if(level >1 ){
            query += " AND nível_" + (level-1) + ".raw:" + parentGoalId;
        }


        let sort = "&sort="+(sortBy||"peso");
        if(sortAsc != undefined){
            sort += "&ascending=" + sortAsc;
        } else{
            sort += "&ascending=false"
        }

        $.ajax({
          url: "/recordm/recordm/definitions/search/" + _this.props.confs.goalsDefId + "?q=" + encodeURIComponent(query) + sort,
          xhrFields: { withCredentials: true },
          dataType: 'json',
          cache: false,
          success: function(json) {
              let goals = [];
              json.hits.hits.forEach(function(hit){
                  let goal = hit._source;

                  goals.push(goal);
              });

              window.console.log(goals);

              _this.setState({goals: goals}); // render goals right away

              goals.forEach(function(goal){
                  _this.loadTotals(level, goal.id, function(sparklineData){
                      goal.sparklineData = sparklineData;

                      _this.setState({goals: goals}); // render goal totals as they arrive
                  });

                  _this.loadFindingsCounts(level, goal.id, function(findingsCounts, userFindingsTotal){
                      goal.findingsCounts = findingsCounts;
                      goal.userAttributedFindingsTotal = userFindingsTotal;

                      _this.setState({goals: goals});
                  });

              })


          },complete: function() {

              if(_this.state.goals.length == 0) {
                _this.loadNoGoalsInfo(_this.props.level);
              }
          }
        });
    }

    loadTotals(level, goalId, onSucess){
        let _this = this;
        //NOTA: query base construidaa partir de uma query kibana tipo:
        //http://prod.lidl:8080/kibana/?#/visualize/create?_a=(filters:!(),linked:!f,query:(query_string:(analyze_wildcard:!t,query:'id_goal_n%C3%ADvel_2.raw:10004')),vis:(aggs:!((id:'1',params:(value:resultado_assessment.raw,weight:peso_goal_n%C3%ADvel_2.raw),schema:metric,type:weighted-mean),(id:'2',params:(customInterval:'2h',extended_bounds:(),field:data.date,interval:m,min_doc_count:1),schema:bucket,type:date_histogram)),listeners:(),params:(perPage:10,showMeticsAtAllLevels:!f,showPartialRows:!f,spyPerPage:10),type:table))&indexPattern=recordm-111&type=table&_g=(refreshInterval:(display:Off,pause:!f,section:0,value:0),time:(from:now-6h,mode:relative,to:now))
        let baseQuery = {
          "size": 0,
          "query": {
            "bool": {
              "must": [
                {
                  "query_string": {
                    "analyze_wildcard": true,
                    "query": "id_goal_nível___NIVEL__.raw:__GOALID__" + (!_this.props.confs.canSeeAll ? " AND  âmbito.raw:__USERORG__" : "")
                  }
                },
                {
                  "range": {
                    "data.date": {
                      "gte": "__LOWER_DATE__",
                      "lte": "__UPPER_DATE__"
                    }
                  }
                }
              ],
              "must_not": []
            }
          },
          "aggs": {
            "2": {
              "date_histogram": {
                "field": "data.date",
                "interval": "__DATE_INTERVAL__",
                "pre_zone": "+00:00",
                "pre_zone_adjust_large_interval": true,
                "min_doc_count": 1,
                "extended_bounds": {
                  "min": "__LOWER_DATE__",
                  "max": "__UPPER_DATE__"
                }
              },
              "aggs": {
                "1": {
                  "weighted-mean": {
                    "value": "resultado.raw",
                    "weight": "peso_global.raw"
                  }
                }
              }
            }
          }
        };

        if(this.props.confs.isESversion5){
            delete baseQuery["aggs"]["2"]["date_histogram"]["pre_zone"];
            delete baseQuery["aggs"]["2"]["date_histogram"]["pre_zone_adjust_large_interval"];

            baseQuery["aggs"]["2"]["aggs"]["1"] = {
                "avg": {
                    "field": "resultado.raw"
                }
            };
        };

        
        let aggsQuery = JSON.stringify(baseQuery)
                        .replace(/__NIVEL__/g, level)
                        .replace(/__GOALID__/g, goalId)
                        .replace(/__USERORG__/g, this.props.confs.userOrg)
                        .replace(/__LOWER_DATE__/g, this.props.confs.totals.lowerDate)
                        .replace(/__UPPER_DATE__/g, this.props.confs.totals.upperDate)
                        .replace(/__DATE_INTERVAL__/g, this.props.confs.totals.dateInterval);

        let url =  "/recordm/recordm/definitions/search/advanced/" + _this.props.confs.assessmentsDefId; //tem de ser advanced para se conseguir enfiar a query do kibana :);

        if (this.props.confs.isESversion5){
            url=  "/es/recordm-"+ _this.props.confs.assessmentsDefId+ "/_search"
        };

        $.ajax({
          url: url,
          data : aggsQuery,
          type: "POST",
          xhrFields: { withCredentials: true },
          cache: false,
          success: function(json) {
              let sparklineData = [];

              //NOTA IMPORTANTE: segundo o mimes é possivel que as 2 keys seguintes mudem caso a query seja alterada (com mais aggs ou assim)
              let aggregationsKey  = "2";
              let bucketsKey  = "1";

              if(json.aggregations){
                  json.aggregations[aggregationsKey].buckets.forEach(function(bucket){
                      let value = bucket[bucketsKey].value;

                      if(value!=null) sparklineData.push(value.toFixed(3))
                  });
              }

              onSucess(sparklineData);
          }
        });
    }

    loadNoGoalsInfo(level){
        let _this = this;
        let query = "título.raw:\"Goals Nível " + level +"\"";

        $.ajax({
          url: "/recordm/recordm/definitions/search/" + _this.props.confs.informacaoDefId + "?q=" + encodeURIComponent(query),
          xhrFields: { withCredentials: true },
          dataType: 'json',
          cache: false,
          success: function(json) {
              let info="Não há goals definidos neste nível";
              json.hits.hits.forEach(function(hit){
                  info = hit._source["conteúdo"];
              });

              _this.setState({noGoalsInfo: info});
          }
        });
    }

    loadFindingsCounts(level, goalId, onSucess){
        let _this = this;
        //NOTA: query base construidaa partir de uma query kibana tipo:
        //http://prod.lidl:8080/kibana/?#/visualize/create?type=table&indexPattern=recordm-97&_g=(refreshInterval:(display:Off,pause:!f,section:0,value:0),time:(from:now-30d,mode:quick,to:now))&_a=(filters:!(),linked:!f,query:(query_string:(analyze_wildcard:!t,query:'control.raw:64494')),vis:(aggs:!((id:'1',params:(),schema:metric,type:count),(id:'2',params:(field:estado.raw,order:desc,orderBy:'1',size:5),schema:bucket,type:terms),(id:'3',params:(field:reposi%C3%A7%C3%A3o_%3F_detectada.raw,order:desc,orderBy:'1',size:5),schema:bucket,type:terms)),listeners:(),params:(perPage:10,showMeticsAtAllLevels:!f,showPartialRows:!f),type:table))
        let baseQuery =
        {
          "size": 0,
          "query": {
            "bool": {
              "must": {
                "query_string": {
                  "analyze_wildcard": true,
                  "query": "id_goal_nível___NIVEL__.raw:__GOAL_ID__" + (!_this.props.confs.canSeeAll ? " AND  âmbito.raw:__USERORG__" : "") + (_this.props.onlyUserFindings ? " AND atribuido_a_username.raw:\""+ cob.app.getCurrentLoggedInUser()+"\"" : "")
                }
              }
            }
          },
          "aggs": {
            "2": {
              "terms": {
                "field": "estado.raw",
                "size": 5,
                "order": {
                  "_count": "desc"
                }
              },
              "aggs": {
                "3": {
                  "terms": {
                    "field": "reposição_detectada.raw",
                    "size": 5,
                    "order": {
                      "_count": "desc"
                    }
                  }
                }
              }
            }
          }
        };

         let aggsQuery = JSON.stringify(baseQuery)
                                .replace(/__NIVEL__/g, level)
                                .replace(/__GOAL_ID__/g, goalId)
                                .replace(/__USERORG__/g, _this.props.confs.userOrg);

        let url =  "/recordm/recordm/definitions/search/advanced/" + _this.props.confs.findingsDefId;

        if (this.props.confs.isESversion5){
            url =  "/es/recordm-"+ _this.props.confs.findingsDefId+ "/_search"
        };

        $.ajax({
          url: url,
          data : aggsQuery,
          type: "POST",
          xhrFields: { withCredentials: true },
          cache: false,
          success: function(json) {
              let findingsCounts = {};

              //NOTA IMPORTANTE: segundo o mimes é possivel que as 2 keys seguintes mudem caso a query seja alterada (com mais aggs ou assim)
              let aggregationsKey  = "2";
              let bucketsKey  = "3";

              if(json.aggregations){
                  json.aggregations[aggregationsKey].buckets.forEach(function(bucket){
                      let estado = bucket.key;

                      bucket[bucketsKey].buckets.forEach(function(reposicaoBucket){
                          if(reposicaoBucket.key == "Sim"){
                              findingsCounts[estado+"_OK"] = reposicaoBucket.doc_count;
                          }else{
                              findingsCounts[estado+"_NOK"] = reposicaoBucket.doc_count;
                          }
                      });
                      //no final hão-de haver Por Tratar_OK, Por Tratar_NOK, Em Resolução_OK, Em Resolução_NOK, etc...
                  });
              }


            //count findings atribuidos ao user
            let currentUsername = cob.app.getCurrentLoggedInUser();
            let userFindingsTotal = 0;
            json.hits.hits.forEach(function(hit){
                let src = hit._source;
                if (src && src["atribuido_a_username"][0] == currentUsername){
                    userFindingsTotal++;
                }
            });

            onSucess(findingsCounts, userFindingsTotal);
          }
        });

    }

    goLevelClick(goal){
        this.props.onGoToLevel(this.props.level + 1, goal);
    }

    buildCreateGoalUrl (){
        let n1='{"opts":{"auto-paste-if-empty":true},"fields":[{"value":"1","fieldDefinition":{"name":"Nível"}}]}'
        let n2='{"opts":{"auto-paste-if-empty":true},"fields":[{"value":"2","fieldDefinition":{"name":"Nível"}},{"value":"__PARENT_GOAL_ID__","fieldDefinition":{"name":"Nível 1"}}]}'
        let n3='{"opts":{"auto-paste-if-empty":true},"fields":[{"value":"3","fieldDefinition":{"name":"Nível"}},{"value":"__PARENT_GOAL_ID__","fieldDefinition":{"name":"Nível 2"}}]}'

        let createGoalUrl = '#/instance/create/' + this.props.confs.goalsDefId + '/data=';
        if(this.props.level == 1) createGoalUrl += n1;
        if(this.props.level == 2) createGoalUrl += n2.replace(/__PARENT_GOAL_ID__/g,this.props.parentGoalId);
        if(this.props.level == 3) createGoalUrl += n3.replace(/__PARENT_GOAL_ID__/g,this.props.parentGoalId);

        this.setState({createGoalUrl: createGoalUrl});
    }

    buildExtraInfo(goal){
        let _this = this;
        let currentUsername = cob.app.getCurrentLoggedInUser();
        let findingsInfo;
        let nokCount,okCount;
        let extraInfo = {};

        if(goal.findingsCounts){

            let baseFindingsHref = "#/definitions/" + _this.props.confs.findingsDefId +
                            "/q=" + encodeURIComponent("id_goal_nível_" + _this.props.level + ".raw:" + goal.id +
                             " AND estado.raw:\"__ESTADO__\" AND reposição_detectada.raw:__REPOSICAO__");

            if(_this.props.onlyUserFindings){
                baseFindingsHref += encodeURIComponent(" AND atribuido_a_username.raw:\"" + currentUsername + "\"");
            }

            let nokNovoUrl        = baseFindingsHref.replace(/__ESTADO__/g, "Por Tratar").replace(/__REPOSICAO__/g, "Não");
            let nokEmResolucaoUrl = baseFindingsHref.replace(/__ESTADO__/g, "Em Resolução").replace(/__REPOSICAO__/g, "Não");
            let okNovoUrl         = baseFindingsHref.replace(/__ESTADO__/g, "Por Tratar").replace(/__REPOSICAO__/g, "Sim");
            let okEmResolucaoUrl  = baseFindingsHref.replace(/__ESTADO__/g, "Em Resolução").replace(/__REPOSICAO__/g, "Sim");

            let totalFindingsUrl  = "#/definitions/" + _this.props.confs.findingsDefId +
                                    "/q=" + encodeURIComponent("id_goal_nível_" + _this.props.level + ".raw:" + goal.id + " AND -estado.raw:Resolvido");
            let userTotalFindingsUrl = totalFindingsUrl + encodeURIComponent(" AND	atribuido_a_username.raw:\"" + currentUsername + "\"");


            let novoNOK         = goal.findingsCounts["Por Tratar_NOK"] || 0;
            let emResolucaoNOK  = goal.findingsCounts["Em Resolução_NOK"] || 0;
            let novoOK          = goal.findingsCounts["Por Tratar_OK"] || 0;
            let emResolucaoOK   = goal.findingsCounts["Em Resolução_OK"] || 0;

            nokCount = novoNOK + emResolucaoNOK;
            okCount = novoOK + emResolucaoOK;

            let findingsHeader;
            if(_this.props.onlyUserFindings){
                findingsHeader =  (<h5>Meus Findings Abertos:
                                        <span className="findings-total"><a target="_blank" href={userTotalFindingsUrl}>{nokCount + okCount}</a></span>
                                    </h5>);
            }else{
                let usrFindingsLink;
                if(goal.userAttributedFindingsTotal > 0 ){
                    usrFindingsLink = (<a target="_blank" href={userTotalFindingsUrl}>(meus: {goal.userAttributedFindingsTotal})</a>);
                }

                findingsHeader =  (<h5>Total Findings Abertos:
                                        <span className="findings-total"><a target="_blank" href={totalFindingsUrl}>{nokCount + okCount}</a> {usrFindingsLink}</span>
                                    </h5>);
            }

            findingsInfo = (
                <div className="findings-counts-wrapper">
                    {findingsHeader}

                    <table>
                        <tbody>
                        <tr className={novoNOK == 0 ? "hidden":""}>
                            <td className="findings-counts urgent-text">
                                <div className="count-label urgent-label"><a target="_blank" href={nokNovoUrl}>{novoNOK}</a></div>

                            </td>
                            <td className="urgent-text">NOK</td>
                            <td>&nbsp;Por Tratar</td>
                        </tr>
                        <tr className={emResolucaoNOK == 0 ? "hidden":""}>
                            <td className="findings-counts urgent-text">
                                <div className="count-label warning-label"><a target="_blank" href={nokEmResolucaoUrl}>{emResolucaoNOK}</a></div>

                            </td>
                            <td className="urgent-text">NOK</td>
                            <td>&nbsp;Em Resolução</td>
                        </tr>

                        <tr className={novoOK == 0 ? "hidden":""}>
                            <td className="findings-counts junk-text">
                                <div className="count-label junk-label"><a target="_blank" href={okNovoUrl}>{novoOK}</a></div>
                            </td>
                            <td className="junk-text">OK</td>
                            <td>&nbsp;Por Tratar</td>
                        </tr>
                        <tr className={emResolucaoOK == 0 ? "hidden":""}>
                            <td className="findings-counts junk-text">
                                <div className="count-label junk-label"><a target="_blank" href={okEmResolucaoUrl}>{emResolucaoOK}</a></div>
                            </td>
                            <td className="junk-text">OK</td>
                            <td>&nbsp;Em Resolução</td>
                        </tr>
                        </tbody>
                    </table>
                </div>
            );


            extraInfo.contents = (okCount + nokCount>0) ? (<div>{findingsInfo}</div>) : undefined;
            extraInfo.id = goal.id;

            if(_this.props.onlyUserFindings){
                extraInfo.iconText = okCount+nokCount > 0 ? 'warn': 'info';
                extraInfo.iconColor = nokCount > 0 ? (novoNOK > 0 ?'urgent':'warning') : 'inactive';
            }else{
                extraInfo.iconText = goal.userAttributedFindingsTotal > 0 ?'warn':(okCount+nokCount > 0 ? 'info': '');
                extraInfo.iconColor = nokCount > 0 ? (novoNOK > 0 ?'urgent':'warning') : 'info';
            }
        }

        return extraInfo;
    }
e
    render() {
        let rows = [];
        let _this = this;
        let emptyRow;
        let iconUrl = "localresource/governance/img/goal" + this.props.level + ".png";

        this.state.goals.forEach(function(goal) {

            rows.push(
                <Panel key={goal.id}>

                    <img src={iconUrl} className="governance-icon"/>

                    <Button bsStyle="link" bsSize="large" className="goal-name-link"
                            onClick={ () => _this.goLevelClick(goal) }>
                        {goal.nome}
                    </Button>

                    <div className="goal-evolution-bar-container">
                        <EvolutionBar sparklineData={goal.sparklineData}
                                      weight={(+goal.peso).toFixed(0)}
                                      showTotal={_this.props.showTotals}
                                      extraInfo={_this.buildExtraInfo(goal)}
                                  />
                    </div>

                </Panel>
            );
        });

        if(rows.length == 0 && this.state.noGoalsInfo){
            emptyRow = (<Well bsSize="small" className="governance-info" >
            <ReactMarkdown source={this.state.noGoalsInfo} />
            </Well>);
        }


        let createGoal = (<Button bsStyle="link" target="_blank"
                                  href={this.state.createGoalUrl} title="Criar Novo Goal"
                                  className="goal-create-btn">
                            <i className="icon-plus-sign"/>
                        </Button>)

        let toggleResultWeightHtml = (<Button bsStyle="link"
                                        title={this.props.showTotals ? 'Mostrar Pesos' : 'Mostrar Totais'}
                                        onClick={ () => _this.props.onToggleTotalsWeightsClick() }
                                        className="toggle-resultWeight-btn">
                                        {this.props.showTotals ? <img src="localresource/governance/img/weight.png" /> : '%'}
                                </Button>)

        let toggleOnlyUserFindingsHtml = (<Button bsStyle="link"
                                        title={this.props.onlyUserFindings ? 'Mostrar contagem  de todos os findings' : 'Mostrar contagem  apenas dos meus findings'}
                                        onClick={ () => _this.props.onToggleOnlyUserFindings() }
                                        className="toggle-resultWeight-btn">
                                        {this.props.onlyUserFindings ? <i className="icon-info-sign"/> : <i className="icon-warning-sign"/>}
                                </Button>)

        return(
            <PanelGroup>
                {emptyRow}
                {rows}
                <div className="goal-create-btn-container">{toggleOnlyUserFindingsHtml}{toggleResultWeightHtml}{createGoal}</div>
            </PanelGroup>
        );
    }
};

Goals.defaultProps = {
    level:undefined,
    parentGoalId:undefined,
    onGoToLevel: unimplementedFn,
    onToggleTotalsWeightsClick:unimplementedFn,
    showTotals:undefined,
    onToggleOnlyUserFindings:unimplementedFn,
    onlyUserFindings:undefined,
    confs:{
        goalsDefId:undefined,
        assessmentsDefId:undefined,
        informacaoDefId: undefined,

        totals:{
            lowerDate:'now-6m/d',
            upperDate:'now/m',
            dateInterval:'1d',
        }
    }
};


export default Goals;
