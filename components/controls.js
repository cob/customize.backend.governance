import React from 'react';
import ReactDOM  from 'react-dom';

import { Sparklines, SparklinesLine } from 'react-sparklines';

import {
    Button,
    PanelGroup, Panel,
    Well
} from 'react-bootstrap'

import EvolutionBar from './evolution-bar';
import ControlDetails from './control-details';

//TODO JBARATA passar esta função para uma classe de uteis
let unimplementedFn = function(){
    window.console.error("NOT IMPLEMENTED - Default prop here! The caller of this component is *NOT* passing this prop.")
};

class Controls extends React.Component{
    constructor(props) {
        super(props);
        this.state = {
            controls: [],
            noControlInfo:undefined,
            showDetails:{}
        };
    }


    componentDidMount() {
        this.loadControls(this.props.goal.id, "nome", true);
        this.buildCreateControlUrl();
    }

    componentWillReceiveProps(nextProps){
        if(this.props.onlyUserFindings != nextProps.onlyUserFindings){
            this.loadControls(this.props.goal.id, "nome", true);
        }
    }

    loadControls(goalId, sortBy, sortAsc){
        let _this = this;
        let sort = "&sort="+(sortBy||"peso");
        if(sortAsc != undefined){
            sort += "&ascending=" + sortAsc;
        } else{
            sort += "&ascending=false"
        }

        $.ajax({
          url: "/recordm/recordm/definitions/search/" + _this.props.confs.controlsDefId + "?q=goal_nível_3.raw:" + goalId + sort,
          xhrFields: { withCredentials: true },
          dataType: 'json',
          cache: false,
          success: function(json) {
              let controls = [];
              json.hits.hits.forEach(function(hit){
                  let control = hit._source;

                  controls.push(control);
              });

              window.console.log(controls);

              _this.setState({controls: controls}); // render controls right away

              controls.forEach(function(control){
                  _this.loadControlTotals(control.id, function(sparklineData){
                      control.sparklineData = sparklineData;

                      _this.setState({controls: controls}); // render controls totals as they arrive
                  });

                  _this.loadControlLastAssessmentInfo(control.id, function(assessment){
                      control.lastAssessment = assessment || {};

                      _this.setState({controls: controls});
                  });

                  _this.loadFindingsCounts(control.id, function(findingsCounts, userFindingsTotal){
                      control.findingsCounts = findingsCounts;
                      control.userAttributedFindingsTotal = userFindingsTotal;

                      _this.setState({controls: controls});
                  });
              })
          },complete: function() {

              if(_this.state.controls.length == 0) {
                _this.loadNoControlInfo();
              }
          }
        });

    }

    loadControlTotals(controlId, onSucess){
        let _this = this;
        //NOTA: query base construidaa partir de uma query kibana tipo:
        //http://prod2.lidl:8080/kibana/?#/visualize/create?_a=(filters:!(),linked:!f,query:(query_string:(analyze_wildcard:!t,query:'id_control.raw:27816')),vis:(aggs:!((id:'1',params:(value:resultado_assessment.raw,weight:peso_control.raw),schema:metric,type:weighted-mean),(id:'2',params:(customInterval:'2h',extended_bounds:(),field:data.date,interval:m,min_doc_count:1),schema:bucket,type:date_histogram)),listeners:(),params:(perPage:10,showMeticsAtAllLevels:!f,showPartialRows:!f,spyPerPage:10),type:table))&indexPattern=recordm-112&type=table&_g=(refreshInterval:(display:Off,pause:!f,section:0,value:0),time:(from:now-8h,mode:relative,to:now))

        let baseQuery = {
          "size": 0,
          "query": {
            "bool": {
              "must": [
                {
                  "query_string": {
                    "query": "id_control.raw:__CONTROL_ID__",
                    "analyze_wildcard": true
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
                        .replace(/__CONTROL_ID__/g, controlId)
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

    loadNoControlInfo(){
        let _this = this;
        let query = "título.raw:\"Control\"";

        $.ajax({
          url: "/recordm/recordm/definitions/search/" + _this.props.confs.informacaoDefId + "?q=" + encodeURIComponent(query),
          xhrFields: { withCredentials: true },
          dataType: 'json',
          cache: false,
          success: function(json) {
              let info="Não há Controls definidos neste goal";
              json.hits.hits.forEach(function(hit){
                  info = hit._source["conteúdo"];
              });

              _this.setState({noControlInfo: info});
          }
        });
    }

    loadControlLastAssessmentInfo(controlId, onSucess){
        let _this = this;
        let sort = "&sort=id&ascending=false&size=1"
        $.ajax({
          url: "/recordm/recordm/definitions/search/" + _this.props.confs.assessmentsDefId + "?q=id_control.raw:" + controlId + sort,
          xhrFields: { withCredentials: true },
          dataType: 'json',
          cache: false,
          success: function(json) {
              let assessment = (json.hits.hits.length > 0 ? json.hits.hits[0]._source : undefined);

              onSucess(assessment);
          }
        });
    }

    loadFindingsCounts(controlId, onSucess){
        let _this = this;
        //NOTA: query base construidaa partir de uma query kibana tipo:
        //http://prod.lidl:8080/kibana/?#/visualize/create?type=table&indexPattern=recordm-97&_g=(refreshInterval:(display:Off,pause:!f,section:0,value:0),time:(from:now-30d,mode:quick,to:now))&_a=(filters:!(),linked:!f,query:(query_string:(analyze_wildcard:!t,query:'control.raw:64494')),vis:(aggs:!((id:'1',params:(),schema:metric,type:count),(id:'2',params:(field:estado.raw,order:desc,orderBy:'1',size:5),schema:bucket,type:terms),(id:'3',params:(field:reposi%C3%A7%C3%A3o_%3F_detectada.raw,order:desc,orderBy:'1',size:5),schema:bucket,type:terms)),listeners:(),params:(perPage:10,showMeticsAtAllLevels:!f,showPartialRows:!f),type:table))
        let baseQuery = {
          "size": 0,
          "query": {
            "bool": {
              "must": {
                "query_string": {
                  "analyze_wildcard": true,
                  "query": "control.raw:__CONTROL_ID__" + (_this.props.onlyUserFindings ? " AND atribuido_a_username.raw:\""+ cob.app.getCurrentLoggedInUser()+"\"":"")
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

        let aggsQuery = JSON.stringify(baseQuery).replace(/__CONTROL_ID__/g, controlId);

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

    buildCreateControlUrl(){
        let data='{"opts":{"auto-paste-if-empty":true},"fields":[{"value":"__PARENT_GOAL_ID__","fieldDefinition":{"name":"Goal Nível 3"}}]}';

        let createUrl = '#/instance/create/' + this.props.confs.controlsDefId + '/data=' + data.replace(/__PARENT_GOAL_ID__/g,this.props.goal.id);

        this.setState({createControlUrl: createUrl});
    }

    buildExtraInfo(control){
        let _this = this;
        let currentUsername = cob.app.getCurrentLoggedInUser();

        let extraInfo = {};
        let assessmentInfo;

        if(control.lastAssessment){
            assessmentInfo = (
                <div className="last-assessment-wrapper">
                    <h5> Observações <span className="last-assessment-obs-date">({control.lastAssessment["data_do_resultado_formatted"]})</span></h5>
                    <div dangerouslySetInnerHTML={_this.getMarkup(control.lastAssessment["observações"])} ></div>
                </div>
            );
        }

        let findingsInfo;
        let nokCount,okCount;

        if(control.findingsCounts){
            let baseFindingsHref = "#/definitions/" + _this.props.confs.findingsDefId +
                            "/q=" + encodeURIComponent("control.raw:" + control.id +
                            " AND estado.raw:\"__ESTADO__\" AND reposição_detectada.raw:__REPOSICAO__");

            if(_this.props.onlyUserFindings){
                baseFindingsHref += encodeURIComponent(" AND atribuido_a_username.raw:\"" + currentUsername + "\"");
            }

            let nokNovoUrl        = baseFindingsHref.replace(/__ESTADO__/g, "Por Tratar").replace(/__REPOSICAO__/g, "Não");
            let nokEmResolucaoUrl = baseFindingsHref.replace(/__ESTADO__/g, "Em Resolução").replace(/__REPOSICAO__/g, "Não");
            let okNovoUrl         = baseFindingsHref.replace(/__ESTADO__/g, "Por Tratar").replace(/__REPOSICAO__/g, "Sim");
            let okEmResolucaoUrl  = baseFindingsHref.replace(/__ESTADO__/g, "Em Resolução").replace(/__REPOSICAO__/g, "Sim");

            let totalFindingsUrl = "#/definitions/" + _this.props.confs.findingsDefId +
                            "/q=" + encodeURIComponent("control.raw:" + control.id + " AND -estado.raw:Resolvido");
            let userTotalFindingsUrl = totalFindingsUrl + encodeURIComponent(" AND	atribuido_a_username.raw:\"" + currentUsername + "\"");

            let novoNOK         = control.findingsCounts["Por Tratar_NOK"] || 0;
            let emResolucaoNOK  = control.findingsCounts["Em Resolução_NOK"] || 0;
            let novoOK          = control.findingsCounts["Por Tratar_OK"] || 0;
            let emResolucaoOK   = control.findingsCounts["Em Resolução_OK"] || 0;

            nokCount = novoNOK + emResolucaoNOK;
            okCount = novoOK + emResolucaoOK;


            let findingsHeader;
            if(_this.props.onlyUserFindings){
                findingsHeader =  (<h5>Meus Findings Abertos:
                                        <span className="findings-total"><a target="_blank" href={userTotalFindingsUrl}>{nokCount + okCount}</a></span>
                                    </h5>);
            }else{
                let usrFindingsLink;
                if(control.userAttributedFindingsTotal >0 ){
                    usrFindingsLink = (<a target="_blank" href={userTotalFindingsUrl}>(meus: {control.userAttributedFindingsTotal})</a>);
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

            extraInfo.contents = (<div>{findingsInfo}{assessmentInfo}</div>);
            extraInfo.id = control.id;

            if(_this.props.onlyUserFindings){
                extraInfo.iconText = okCount+nokCount > 0 ? 'warn': 'info';
                extraInfo.iconColor = nokCount > 0 ? (novoNOK > 0 ?'urgent':'warning') : 'inactive';
            }else{
                extraInfo.iconText = control.userAttributedFindingsTotal > 0 ?'warn':(okCount+nokCount > 0 ? 'info': '');
                extraInfo.iconColor = nokCount > 0 ? (novoNOK > 0 ?'urgent':'warning') : 'info';
            }

        }


        return extraInfo;
    }

    handleShowDetails(controlId){
        let _this = this;

        let showDetails = this.state.showDetails;
        showDetails[controlId] = !this.state.showDetails[controlId] ;

        this.setState( {
            showDetails:showDetails
        } );
    }

    handleHideDetails(controlId){
        let showDetails = this.state.showDetails;
        showDetails[controlId] = false;

        this.setState( {
            showDetails:showDetails
        } );
    }

    getMarkup(text) {
        let rawMarkup = text? marked(text.toString(), {sanitize: false}) : "";
        return { __html: rawMarkup };
    }

    render() {
        let rows = [];
        let _this = this;
        let emptyRow;


        this.state.controls.forEach(function(control) {
            let details;
            if(_this.state.showDetails[control.id]){
                details = <ControlDetails control={control}
                                          onHideControlDetails={() => _this.handleHideDetails(control.id)}
                                          confs={_this.props.confs}
                          />
            }



            rows.push(
                <Panel  key={control.id} className="governance-control-item">

                    <img src="localresource/governance/img/control.png" className="governance-icon"/>

                     <a onClick={ () => _this.handleShowDetails(control.id)} className="control-name-link">
                     {control["âmbito"]} - {control.nome}
                     </a>

                    <div className="control-evolution-bar-container" >
                        <EvolutionBar sparklineData={control.sparklineData}
                                      weight={(+control.peso).toFixed(0)}
                                      showTotal={_this.props.showTotals}
                                      extraInfo={_this.buildExtraInfo(control)}
                                  />

                    </div>

                    {details}

                </Panel>
            );
        });

        if(rows.length == 0 && this.state.noControlInfo){
            //emptyRow = (<Well bsSize="small">Não há controls definidos para este Goal</Well>);
            emptyRow = (<Well bsSize="small" className="governance-info" >
                            <span dangerouslySetInnerHTML={this.getMarkup(this.state.noControlInfo)} />
                        </Well>);
        }


        let createControl = (<Button bsStyle="link" target="_blank"
                                     href={this.state.createControlUrl}
                                     title="Criar Novo Control"
                                     className="control-create-btn">
                            <i className="icon-plus-sign"/>
                        </Button>)


        let toggleResultWeightHtml = (<Button bsStyle="link"
                                        title={this.props.showTotals ? 'Mostrar Pesos' : 'Mostrar Totais'}
                                        onClick={ () => _this.props.onToggleTotalsWeightsClick() }
                                        className="toggle-resultWeight-btn">
                                        {this.props.showTotals ? <img src="localresource/governance/img/weight.png" /> : '%'}
                                </Button>)

        let toggleOnlyUserFindingsHtml = (<Button bsStyle="link"
                                        title={this.props.onlyUserFindings ? 'Mostrar contagem de todos os findings' : 'Mostrar contagem apenas dos meus findings'}
                                        onClick={ () => _this.props.onToggleOnlyUserFindings() }
                                        className="toggle-resultWeight-btn">
                                        {this.props.onlyUserFindings ? <i className="icon-info-sign"/> : <i className="icon-warning-sign"/>}
                                </Button>)



        return(
            <PanelGroup>
                {emptyRow}
                {rows}
                <div className="control-create-btn-container">{toggleOnlyUserFindingsHtml}{toggleResultWeightHtml}{createControl}</div>
            </PanelGroup>
        );
    }

};

Controls.defaultProps = {
    goal:{},
    confs:{
        controlsDefId:undefined,
        assessmentsDefId:undefined,
        informacaoDefId: undefined,
        onToggleTotalsWeightsClick:unimplementedFn,
        showTotals:undefined,
        onToggleOnlyUserFindings:unimplementedFn,
        onlyUserFindings:undefined,

        totals:{
            lowerDate:'now-6m/d',
            upperDate:'now/m',
            dateInterval:'1d',
        }
    }
};

export default Controls;
