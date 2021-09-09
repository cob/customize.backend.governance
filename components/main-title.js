import React from 'react';
import ReactDOM  from 'react-dom';

import { Sparklines, SparklinesLine } from 'react-sparklines';

import EvolutionBar from './evolution-bar';

//TODO JBARATA passar esta função para uma classe de uteis
let unimplementedFn = function(){
    window.console.error("NOT IMPLEMENTED - Default prop here! The caller of this component is *NOT* passing this prop.")
};

class MainTitle extends React.Component{
    constructor(props) {
        super(props);
        this.state = {
            headerSparklineData:[]
        };
    }

    componentDidMount() {
        let _this = this;
        let goalId;
        let headerlevel = this.props.currentLevel - 1;

        if(this.props.currentGoal) goalId = this.props.currentGoal.id;

        this.loadTotals(headerlevel, goalId, function(sparklineData){

            _this.setState({
                headerSparklineData: sparklineData
            });
        })
    }

    loadTotals(headerlevel, goalId, onSucess){
        let _this = this;
        //NOTA: query base construidaa partir de uma query kibana tipo:
        //http://prod2.lidl:8080/kibana/?#/visualize/create?_a=(filters:!(),linked:!f,query:(query_string:(analyze_wildcard:!t,query:'id_goal_n%C3%ADvel_2.raw:10004')),vis:(aggs:!((id:'1',params:(value:resultado_assessment.raw,weight:peso_goal_n%C3%ADvel_2.raw),schema:metric,type:weighted-mean),(id:'2',params:(customInterval:'2h',extended_bounds:(),field:data.date,interval:m,min_doc_count:1),schema:bucket,type:date_histogram)),listeners:(),params:(perPage:10,showMeticsAtAllLevels:!f,showPartialRows:!f,spyPerPage:10),type:table))&indexPattern=recordm-112&type=table&_g=(refreshInterval:(display:Off,pause:!f,section:0,value:0),time:(from:now-6h,mode:relative,to:now))
        let baseQueryGlobal = {
          "size": 0,
          "query": {
            "bool": {
              "must": [
                {
                  "query_string": {
                    "query": "*" + (!_this.props.confs.canSeeAll ? " AND  âmbito.raw:__USERORG__" : "") + " AND peso_global:>0",
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
        let baseQueryGoal = {
          "size": 0,
          "query": {
            "bool": {
              "must": [
                {
                  "query_string": {
                    "query": "id_goal_nível___NIVEL__.raw:__GOALID__"+ (!_this.props.confs.canSeeAll ? " AND  âmbito.raw:__USERORG__" : "") + " AND peso_global:>0",
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
            delete baseQueryGlobal["aggs"]["2"]["date_histogram"]["pre_zone"];
            delete baseQueryGlobal["aggs"]["2"]["date_histogram"]["pre_zone_adjust_large_interval"];

            delete baseQueryGoal["aggs"]["2"]["date_histogram"]["pre_zone"];
            delete baseQueryGoal["aggs"]["2"]["date_histogram"]["pre_zone_adjust_large_interval"];


            baseQueryGlobal["aggs"]["2"]["aggs"]["1"] = {
                "avg": {
                    "field": "resultado.raw"
                }
            };
            baseQueryGoal["aggs"]["2"]["aggs"]["1"] = {
                "avg": {
                    "field": "resultado.raw"
                }
            };
        };


        let aggsQuery;

        if(headerlevel==0){
            aggsQuery= JSON.stringify(baseQueryGlobal)
                            .replace(/__USERORG__/g, this.props.confs.userOrg)
                            .replace(/__LOWER_DATE__/g, this.props.confs.totals.lowerDate)
                            .replace(/__UPPER_DATE__/g, this.props.confs.totals.upperDate)
                            .replace(/__DATE_INTERVAL__/g, this.props.confs.totals.dateInterval);

        }else {
            aggsQuery= JSON.stringify(baseQueryGoal)
                            .replace(/__NIVEL__/g, headerlevel)
                            .replace(/__GOALID__/g, goalId)
                            .replace(/__USERORG__/g, this.props.confs.userOrg)
                            .replace(/__LOWER_DATE__/g, this.props.confs.totals.lowerDate)
                            .replace(/__UPPER_DATE__/g, this.props.confs.totals.upperDate)
                            .replace(/__DATE_INTERVAL__/g, this.props.confs.totals.dateInterval);

        }

        let url =  "/recordm/recordm/definitions/search/advanced/" + _this.props.confs.assessmentsDefId; //tem de ser advanced para se conseguir enfiar a query do kibana :);

        if (this.props.confs.isESversion5){
            url=  "/es/recordm-"+ _this.props.confs.assessmentsDefId+ "/_search"
        };


        $.ajax({
            url: url,
            data: aggsQuery,
            type: "POST",
            contentType: "application/json",
            dataType: 'json',
            xhrFields: {withCredentials: true},
            cache: false,
            success(json) {
                let sparklineData = [];

                //NOTA IMPORTANTE: segundo o mimes é possivel que as 2 keys seguintes mudem caso a query seja alterada (com mais aggs ou assim)
                let aggregationsKey = "2";
                let bucketsKey = "1";

                if (json.aggregations) {
                    json.aggregations[aggregationsKey].buckets.forEach(function(bucket) {
                        let value = bucket[bucketsKey].value;

                        if (value != null) sparklineData.push(value.toFixed(3))
                    });
                }

                onSucess(sparklineData);
            }
        });
    }

    render() {
        let title = "Global";
        let peso;
        let showDetailsBtn;
        let backBtn;
        let goal = this.props.currentGoal;



        if(this.props.currentLevel > 1){
            backBtn = (<a onClick={this.props.onGoBackLevel} className="main-title-back-btn">
                            <i className="icon-level-up"/>
                       </a>);
        }

        if(this.props.currentLevel > 1 && this.props.currentLevel < 5){
            title = (<a onClick={this.props.onShowGoalDetails} className="main-title-name-link">
                        {goal.nome}
                     </a>);
        }

        showDetailsBtn = (<h1 className="main-title-name" >{title} {backBtn}</h1>);


        let upperGoals;
        if(this.props.currentLevel == 3){
            upperGoals = (<div>
                                <h4>{goal["nome_goal_nível_1"]}</h4>
                            </div>);
        }
        if(this.props.currentLevel == 4){
            upperGoals = (<div>
                                <h5>{goal["nome_goal_nível_1"]}</h5>
                                <h4>{goal["nome_goal_nível_2"]}</h4>
                            </div>);
        }


        return(
            <div className="main-title-container">
                {upperGoals}
                {showDetailsBtn}
                <br></br>
                <div>
                    <EvolutionBar sparklineData={this.state.headerSparklineData}
                                  sparklineWitdh={150}
                                  sparklineHeight={45}
                                  size="big"
                                  hideSparkline
                                  showTotalAsDonut/>
                </div>
            </div>
        );
    }
};

MainTitle.defaultProps = {
    currentLevel: 1,
    currentGoal: undefined,
    onGoBackLevel:unimplementedFn,
    onShowGoalDetails:unimplementedFn,
    confs:{
        assessmentsDefId:undefined,
        totals:{
            lowerDate:'now-6m/d',
            upperDate:'now/m',
            dateInterval:'1d',
        }
    }
};

export default MainTitle;
