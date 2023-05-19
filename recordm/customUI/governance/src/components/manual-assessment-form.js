import React from 'react';
import ReactDOM  from 'react-dom';


import {Well,
        Button,
        ControlLabel,
        FormGroup,
        FormControl,
        Alert
    } from 'react-bootstrap';

class ManualAssessmentForm extends React.Component{
    constructor(props) {
        super(props);
        this.state = {
            value:'',
            obs:'',
            isSaving: false,
            resultMsg:undefined,
            isSubmited:false,
            valueValidationState:"success"
        };
    }

    handleValueChange(e) {
        this.setState({value: e.target.value});
    }

    handleNumericValueChange(minVal, maxVal, e) {
        let val = e.target.value;
        this.setState({value:val});

        if (isNaN(val)){
            this.setState({
                valueValidationState:"error"
            });
        }else{
            this.setState({
                valueValidationState:"success"
            });
        }

    }

    handleObsChange(e) {
        this.setState({obs: e.target.value});
    }

    handleSubmit(e) {
        let _this=this;
        e.preventDefault();

        let value = this.state.value;
        let obs = this.state.obs;

        _this.setState({resultMsg:undefined});

        if (value == '') {
            return;
        }

        this.setState({
            isSaving: true,
            isSubmited:true
        });

        let msg = {
            "product":"custom",
            "action":"exec_manual",
            "type":"Assessment Manual",
            "user":cob.app.getCurrentLoggedInUser(),
            "value": value,//Valor inserido pelo utilizador
            "obs": JSON.stringify(obs),//observações inserido pelo utilizador
            "control": this.props.control //_source do Control que origina estes assessments
        };

        $.ajax({
          url: "/integrationm/msgs/",
          data : JSON.stringify(msg),
          contentType: "application/json",
          dataType: 'json',
          type: "POST",
          xhrFields: { withCredentials: true },
          cache: false,
          success: function() {
              _this.setState({
                  value: '',
                  obs:'',
                  resultMsg:'Assessment submetido com sucesso'
              });
          },
          error:function(e) {
              window.console.log(e);
              _this.setState({
                  resultMsg:'Erro a submeter Assessment'
              });
          },
          complete:function() {
              _this.setState({
                  isSaving: false
              });

              setTimeout(() => {
                  _this.props.onAssessmentSubmited(); //give time for integrationm to insert the record
              }, 3000);

          },

        });
    }

    handleSubmitedAlertDismiss(){
        this.setState({
            isSubmited:false,
            resultMsg:undefined
        });
    }

    render() {
        let control = this.props.control;

        let assessmentTool = control["assessment_tool"];

        if(assessmentTool=="Manual"){
            let tipoAss = control["tipo_de_assessment"];
            let title = "Submeter Assessment do tipo '" +tipoAss + "'";

            let obsField = (<FormControl componentClass="textarea"
                                        placeholder="Introduzir Observações ..."
                                        className="assessment-obs"
                                        value={this.state.obs}
                                        onChange={this.handleObsChange}
                            />);

            let isSaving = this.state.isSaving;
            let submitBtn = (<Button onClick={!isSaving ? this.handleSubmit : null}
                                     disabled={isSaving || this.state.value=='' || this.state.valueValidationState=="error"}>
                                {isSaving ? 'a gravar ...' : 'Submeter'}
                            </Button>);

            let resultMsg =this.state.resultMsg;
            let resultHtml;
            if(resultMsg && resultMsg !=''){
                resultHtml = (<Alert closeLabel="">{resultMsg}</Alert>);
            }


            if(this.state.isSubmited){
                let title = this.state.isSaving? (<h4>Pedido em curso...</h4>):(<h4>{resultMsg}</h4>);
                return (<Alert className="assessment-submited-alert">
                          {title}
                          <br/>
                          <Button onClick={this.handleSubmitedAlertDismiss}>OK</Button>
                        </Alert>)
            }


            if(tipoAss == "Atingimento de valor"){
                let help = (<span className={this.state.valueValidationState=="error"?"assessment-value-label-error":"assessment-value-label"}>
                            {" Valor alvo: " + control["valor_alvo"]}
                        </span>);
                let minVal=0;
                let maxVal=Number(control["valor_alvo"]);

                return (
                    <Well>
                        <form className="assessment-form" >
                            <h4>{title}</h4>
                            <br/>
                            <FormGroup>
                              <FormControl type="text" placeholder="Introduza Valor Atingido ..." className="assessment-value"
                                  value={this.state.value}
                                  onChange={this.handleNumericValueChange.bind(this,minVal,maxVal)}
                                  />
                              {help}
                              <br/>
                              {obsField}
                              <br/>
                              {submitBtn}
                            </FormGroup>

                            {resultHtml}
                        </form>
                    </Well>
                );

            }else if(tipoAss ==  "Avaliação 1 a 10"){
                return (
                    <Well>
                        <form className="assessment-form">
                            <h4>{title}</h4>
                            <br/>
                            <FormGroup>
                              <FormControl componentClass="select" className="assessment-value"
                                  value={this.state.value}
                                  onChange={this.handleValueChange}>

                                  <option value="">Escolha uma avaliação...</option>
                                  <option value="1">1</option>
                                  <option value="2">2</option>
                                  <option value="3">3</option>
                                  <option value="4">4</option>
                                  <option value="5">5</option>
                                  <option value="6">6</option>
                                  <option value="7">7</option>
                                  <option value="8">8</option>
                                  <option value="9">9</option>
                                  <option value="10">10</option>
                              </FormControl>
                              <br/>
                              {obsField}
                              <br/>
                              {submitBtn}
                            </FormGroup>

                            {resultHtml}
                        </form>
                    </Well>
                );

            }else if(tipoAss ==  "OK NOK"){
                return (
                    <Well>
                        <form className="assessment-form">
                            <h4>{title}</h4>
                            <br/>
                            <FormGroup>
                              <FormControl componentClass="select" className="assessment-value"
                                  value={this.state.value}
                                  onChange={this.handleValueChange}>

                                  <option value="">Escolha uma avaliação...</option>
                                  <option value="OK">OK</option>
                                  <option value="NOK">NOK</option>

                              </FormControl>
                              <br/>
                              {obsField}
                              <br/>
                              {submitBtn}
                            </FormGroup>

                            {resultHtml}
                        </form>
                    </Well>
                );
            }
        }
    }
};

export default ManualAssessmentForm;
