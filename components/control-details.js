import React from 'react';
import ReactDOM  from 'react-dom';

import {Button,Alert,Well} from 'react-bootstrap';

import ManualAssessmentForm from './manual-assessment-form';

//TODO JBARATA passar esta função para uma classe de uteis
let unimplementedFn = function(){
    window.console.error("NOT IMPLEMENTED - Default prop here! The caller of this component is *NOT* passing this prop.")
};

class ControlDetails extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            assessmentCount:0,
            findingsCount:0
        };
    }

    componentDidMount() {
        let _this=this;
        this.loadAssessmentCount(this.props.control.id, function(count){
            _this.setState({assessmentCount: count});
        });

        this.loadFindingsCount(this.props.control.id, function(count){
            _this.setState({findingsCount: count});
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


    getMarkup(text) {
        let rawMarkup = text? marked(text.toString(), {sanitize: false}) : "";
        return { __html: rawMarkup };
    }

    handleAssessmentSubmited(){
        let _this = this;
        this.loadAssessmentCount(this.props.control.id, function(count){
            _this.setState({assessmentCount: count});
        });
    }

    render() {
        let control = this.props.control;
        let details = (<span dangerouslySetInnerHTML={this.getMarkup(control.descrição)} />);


        let viewControlHref = "#/instance/" + control.id;
        let searchAssessmentsHref = "#/definitions/" + this.props.confs.assessmentsDefId + "/q=" + encodeURIComponent("id_control.raw:" + control.id);
        let searchFindingsHref = "#/definitions/" + this.props.confs.findingsDefId + "/q=" + encodeURIComponent("control.raw:" + control.id + " AND -estado:(Resolvido OR Cancelado)") + "&av=9";

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
