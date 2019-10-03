import React from 'react';
import ReactDOM  from 'react-dom';

import {Button,Alert,Well} from 'react-bootstrap';

import ManualAssessmentForm from './manual-assessment-form';
import ReactMarkdown from 'react-markdown';

//TODO JBARATA passar esta função para uma classe de uteis
let unimplementedFn = function(){
    window.console.error("NOT IMPLEMENTED - Default prop here! The caller of this component is *NOT* passing this prop.")
};

class ControlDetails extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            assessmentCount:0,
            findingsCount:0,
            assessmentViews:[],
            findingViews:[]
        };
    }

    componentDidMount() {
        const _this=this;

        this.loadAssessmentCount(this.props.control.id, function(count){
            _this.loadViewsForDefinition(_this.props.confs.assessmentsDefId, function(viewsJsonArr) {
                _this.setState({
                    assessmentCount: count,
                    assessmentViews: viewsJsonArr
                });
            });
        });

        this.loadFindingsCount(this.props.control.id, function(count){
            _this.loadViewsForDefinition(_this.props.confs.findingsDefId, function(viewsJsonArr) {
                _this.setState({
                    findingsCount: count,
                    findingViews: viewsJsonArr
                });
            });
        });
    }

    loadViewsForDefinition(defId, onSuccess){
        const viewsUrl = "/recordm/user/settings/definitions-" + defId + "/views";

        $.ajax({
            url: viewsUrl,
            xhrFields: { withCredentials: true },
            dataType: 'json',
            cache: false,
            success: function(viewsJsonArr) {
                onSuccess(viewsJsonArr);
            }
        });

    }

    loadAssessmentCount(controlId, onSuccess){
        let _this = this;
        let searchAssessmentsUrl = "/recordm/recordm/definitions/search/" + this.props.confs.assessmentsDefId + "?q=" + encodeURIComponent("id_control.raw:" + controlId);

        $.ajax({
          url: searchAssessmentsUrl,
          xhrFields: { withCredentials: true },
          dataType: 'json',
          cache: false,
          success: function(json) {
              onSuccess(json.hits.total);
          }
        });
    }

    loadFindingsCount(controlId, onSuccess){
        let _this = this;
        let searchFindingsUrl = "/recordm/recordm/definitions/search/" + this.props.confs.findingsDefId + "?q=" + encodeURIComponent("control.raw:" + controlId + " AND -estado:(Resolvido OR Cancelado)");

        $.ajax({
          url: searchFindingsUrl,
          xhrFields: { withCredentials: true },
          dataType: 'json',
          cache: false,
          success: function(json) {
              onSuccess(json.hits.total);
          }
        });
    }


    handleAssessmentSubmited(){
        let _this = this;
        this.loadAssessmentCount(this.props.control.id, function(count){
            _this.setState({assessmentCount: count});
        });
    }

    render() {
        let control = this.props.control;
        let details;

        if(!control.descrição || !control.descrição[0]){
            details = (<span>Este control não tem descrição ...</span>);
        }else{
            details = (<div><ReactMarkdown source={control.descrição[0]} /></div>);
        }


        let viewControlHref = "#/instance/" + control.id;
        let searchAssessmentsHref = "#/definitions/" + this.props.confs.assessmentsDefId + "/q=" + encodeURIComponent("id_control.raw:" + control.id);
        let searchFindingsHref = "#/definitions/" + this.props.confs.findingsDefId + "/q=" + encodeURIComponent("control.raw:" + control.id + " AND -estado:(Resolvido OR Cancelado)");

        if(control["vista_de_assessments_no_governance"] !== undefined
           && control["vista_de_assessments_no_governance"][0] !== undefined){
            const assessmentView = this.state.assessmentViews.find((view) => {
                return (view.key === control["vista_de_assessments_no_governance"][0]
                        && (view.isShared || view.user === cob.app.getCurrentLoggedInUser()));
            });

            if(assessmentView !== undefined){
                searchAssessmentsHref += "&av=" + assessmentView.id;
            }
        }

        if(control["vista_de_findings_no_governance"] !== undefined
           && control["vista_de_findings_no_governance"][0] !== undefined){
            const findingView = this.state.findingViews.find((view) => {
                return (view.key === control["vista_de_findings_no_governance"][0]
                        && (view.isShared || view.user === cob.app.getCurrentLoggedInUser()));
            });

            if(findingView !== undefined){
                searchFindingsHref += "&av=" + findingView.id;
            }
        }

        let manualAss;
        if(control["assessment_tool"]=="Manual"){
            manualAss =(<ManualAssessmentForm control={control} onAssessmentSubmited={this.handleAssessmentSubmited}/>);
        }


        let baseKibanaUrl = "/kibana/#/dashboard/__KIB_DASHBOARD_ID__?embed&_g=(refreshInterval:(display:Off,pause:!f,section:0,value:0),time:(from:__LOWER_DATE__,mode:quick,to:__UPPER_DATE__))&_a=(filters:!(),query:(query_string:(analyze_wildcard:!t,query:'id_control.raw:__CONTROL_ID__')))";
        baseKibanaUrl = baseKibanaUrl.replace(/__KIB_DASHBOARD_ID__/g, this.props.confs.totals.kibanaControlsDashboardId)
                                .replace(/__CONTROL_ID__/g, control.id)
                                .replace(/__LOWER_DATE__/g, this.props.confs.totals.lowerDate)
                                .replace(/__UPPER_DATE__/g, this.props.confs.totals.upperDate);


        return(
            <Alert onDismiss={ () => this.props.onHideControlDetails(control.id) } closeLabel="" className="governance-info-control" key={this.props.control.id}>
                {details}

                {manualAss}

                <br/>
                <Button bsStyle="link" target="_blank" href={viewControlHref}>
                    Detalhes
                </Button>
                <Button bsStyle="link" target="_blank" href={searchAssessmentsHref}>
                    Assessments ({this.state.assessmentCount})
                </Button>
                <Button bsStyle="link" target="_blank" href={searchFindingsHref}>
                    Open Findings ({this.state.findingsCount})
                </Button>

                <iframe src={baseKibanaUrl} className="governance-kibana"></iframe>
            </Alert>
        );
    }
};

ControlDetails.defaultProps = {
    control:undefined,
    onHideControlDetails: unimplementedFn,
    confs:{
            assessmentsDefId:undefined,
            findingsDefId:undefined,

            totals:{
                lowerDate:'now-6M/d',
                upperDate:'now/m',
                kibanaControlsDashboardId:'NOT DEFINED'
            }
        }
};

export default ControlDetails;
