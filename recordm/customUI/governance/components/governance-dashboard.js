import React from 'react';
import ReactDOM  from 'react-dom';

import {
    Grid, Row, Col,
    Well
} from 'react-bootstrap'

import MainTitle from './main-title';
import Goals from './goals';
import GoalDetails from './goal-details';
import Controls from './controls';

class GovernanceDashboard extends React.Component{
    constructor(props) {
        super(props);

        this.state = {
            currentLevel:1,
            parentGoalId: undefined,
            currentGoal:undefined,
            showGoalDetails:false,
            showTotals: true,
            onlyUserFindings:false
        };

        this.handleGoToLevel = this.handleGoToLevel.bind(this);
        this.handleGoBackLevel = this.handleGoBackLevel.bind(this);
        this.handleShowGoalDetails = this.handleShowGoalDetails.bind(this);
        this.handleHideGoalDetails = this.handleHideGoalDetails.bind(this);
        this.handleShowTotalsToggle = this.handleShowTotalsToggle.bind(this);
        this.handleOnlyUserFindingsToggle = this.handleOnlyUserFindingsToggle.bind(this);
    }

    handleGoToLevel(level, parentGoal){
        //save previous state for undo later on
        this.props.stateHistory.push(this.state);

        this.setState( {
            currentLevel: level,
            parentGoalId: parentGoal.id,
            currentGoal: parentGoal,
            showGoalDetails:false
        } );

        //TESTES

        console.log("YYYYYYYY");
        let url=new URL(document.location);
        console.log(url);
        let params = new URLSearchParams(url.hash.slice(url.hash.indexOf("?")));
        console.log(params.toString());
        params.delete('_');
        params.has('level')
        params.set('level', level);
        params.set('parentGoalId', parentGoal.id);
        console.log(params.toString());


        let basePath=url.hash.replace("#/","");

        let newPath = basePath.slice(0,basePath.indexOf("?")+1)+params.toString()
        //cob.app.navigateTo(basePath + ( basePath.indexOf("?")>=0? "&":"?" )+ params.toString())
        cob.app.changeRouteWithoutNavigation(newPath)
        //TESTES

    }

    handleGoBackLevel(){
        let currentTotalsToggle = this.state.showTotals;
        let currentOnyluUserFindingsToggle = this.state.onlyUserFindings;
        //just set previous state
        this.setState( this.props.stateHistory.pop() );
        this.setState( {showGoalDetails:false} );
        this.setState( {showTotals:currentTotalsToggle} );
        this.setState( {onlyUserFindings:currentOnyluUserFindingsToggle} );
    }

    handleShowGoalDetails(){
        this.setState( {showGoalDetails:!this.state.showGoalDetails} );
    }

    handleHideGoalDetails(){
        this.setState( {showGoalDetails:false} );
    }

    handleShowTotalsToggle(){
        this.setState( {showTotals:!this.state.showTotals} );
    }

    handleOnlyUserFindingsToggle(){
        this.setState( {onlyUserFindings:!this.state.onlyUserFindings} );
    }

    render() {
        let controls;
        let backBtn;
        let goals;
        let goalDetails;

        if(this.state.currentLevel < this.props.confs.maxGoalsLevel + 1){
            goals =(<Goals  level={this.state.currentLevel}
                            parentGoalId={this.state.parentGoalId}
                            onGoToLevel={this.handleGoToLevel}
                            onToggleTotalsWeightsClick={this.handleShowTotalsToggle}
                            showTotals={this.state.showTotals}
                            onToggleOnlyUserFindings={this.handleOnlyUserFindingsToggle}
                            onlyUserFindings={this.state.onlyUserFindings}
                            confs={this.props.confs}
                    />);
        }

        if(this.state.currentLevel < this.props.confs.maxGoalsLevel + 2 && this.state.currentGoal && this.state.showGoalDetails){
            goalDetails = (<GoalDetails goal={this.state.currentGoal} onHideGoalDetails={this.handleHideGoalDetails} />)
        }

        if(this.state.currentLevel == this.props.confs.maxGoalsLevel + 1 ){
            controls = ( <Controls level={this.state.currentLevel}
                                    goal={this.state.currentGoal}
                                    key={this.state.currentGoal.id}
                                    onToggleTotalsWeightsClick={this.handleShowTotalsToggle}
                                    showTotals={this.state.showTotals}
                                    onToggleOnlyUserFindings={this.handleOnlyUserFindingsToggle}
                                    onlyUserFindings={this.state.onlyUserFindings}
                                    confs={this.props.confs}
                                    /> );
        }

        return (

                <Well bsSize="large" className="governance-inner-container" >
                  <Grid key={this.state.currentLevel}>
                      <Row>
                        <Col md={12}>
                            <MainTitle
                                currentLevel={this.state.currentLevel}
                                currentGoal={this.state.currentGoal}
                                onShowGoalDetails={this.handleShowGoalDetails}
                                onGoBackLevel={this.handleGoBackLevel}
                                confs={this.props.confs}
                             />

                            </Col>
                      </Row>

        		      <Row key={this.state.parentGoalId}>
        			  <Col  md={12}>
        			      {goalDetails}
        			  </Col>
        		      </Row>

                      <Row >
                        <Col md={12}>
                            {goals}
                        </Col>
                      </Row>
                  </Grid>

                  <Grid>
                      <Row key={this.state.parentGoalId}>
                          <Col  md={12}>
                              {controls}
                          </Col>
                      </Row>
                  </Grid>

              </Well>

        );
    }
};

GovernanceDashboard.defaultProps = {
    confs: {
        maxGoalsLevel: 3
    },
    stateHistory : [] //array to hold the dashboard states as we navigate so we can easaly go back
};

export default GovernanceDashboard;
