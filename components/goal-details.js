import React from 'react';
import ReactDOM  from 'react-dom';

import {Button,Alert} from 'react-bootstrap'


class GoalDetails extends React.Component{
    getMarkup(text) {
        let rawMarkup = text? marked(text.toString(), {sanitize: false}) : "";
        return { __html: rawMarkup };
    }

    buildCloneGoalUrl(goal){

        let createArgs = "103" +
                        ",Nome=nome" +
                        ",Descrição=descrição" +
                        ",Âmbito=âmbito" +
                        ",Nível=nível" +
                        ",Nível 1=nível_1" +
                        ",Nível 2=nível_2" +
                        ",Peso=peso";

        let cloneField = new recordm.fields.Create(
            createArgs,
            {
                 getValueOf: function(esFieldName) {return goal[esFieldName]?goal[esFieldName][0]:""}
            }
        );

        return cloneField.buildUrlFromSearchData();
    }

    render() {
        let goal = this.props.goal;
        let details;

        if(!goal.descrição || !goal.descrição[0]){
            details = (<span>Este Goal não tem descrição ...</span>);
        }else{
            details = (<span dangerouslySetInnerHTML={this.getMarkup(goal.descrição)} />);
        }

        let viewGoalHref = "#/instance/" + goal.id;

        return(
            <Alert onDismiss={this.props.onHideGoalDetails} closeLabel="" className="governance-info" >
                {details}

        		<Button bsStyle="link" target="_blank" href={viewGoalHref}>
        		    Detalhes
        		</Button>
            </Alert>
        );
    }
};

export default GoalDetails;
