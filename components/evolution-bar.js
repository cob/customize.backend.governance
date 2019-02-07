import React from 'react';
import ReactDOM  from 'react-dom';

import { Sparklines, SparklinesLine } from 'react-sparklines';

import SingleValueDonutChart from './single-value-donut-chart';

import { Button,
        Modal
 } from 'react-bootstrap'


class EvolutionBar extends React.Component{
    constructor(props) {
        super(props);
        this.state = {
            showExtraInfo: false
        };

        this.toggleShowExtraInfo = this.toggleShowExtraInfo.bind(this);
    }

    toggleShowExtraInfo() {
        this.setState({ showExtraInfo: !this.state.showExtraInfo });
    }


    calculateTotal(sparklineData){
        let dataLength = sparklineData.length;

        if(dataLength > 0){
            return Math.trunc(sparklineData[dataLength-1]);
        }

        return undefined;

    }

    render() {
        let total = this.calculateTotal(this.props.sparklineData || []);

        let totalHtml;
        if(total!=undefined){
            if(this.props.showTotalAsDonut){
                totalHtml = (<SingleValueDonutChart value={total} />);
            }else{
                totalHtml = (<span>{total}%</span>);
            }

        }

        let weightHtml;
        if(this.props.weight!= undefined){
            weightHtml =(<div className="evolution-data-weight-wrapper">
                            <img src="localresource/governance/img/weight.png" />
                            <span>{this.props.weight}</span>
                        </div>);
        }

        let mainClassname = this.props.size ? "evolution-bar-" + this.props.size
                                            : "evolution-bar"
        let sparklineHtml;
        if(!this.props.hideSparkline){
            sparklineHtml = (<Sparklines data={this.props.sparklineData}
                        limit={this.props.sparklineLimit }
                        width={this.props.sparklineWitdh || 100}
                        height={this.props.sparklineHeight || 20}
                        margin={5}
                        >
                <SparklinesLine />
            </Sparklines>);
        }

        let infoBtn, infoModal;

        if(this.props.extraInfo && total!=undefined){
            /* extrainfo props:
            extraInfo.contents = o html com conteudo do popover;
            extraInfo.id = um id para dar ao popover;
            extraInfo.iconText = o tipo de icon a mostrar conforme o nivel: 'warn' ou'info';
            extraInfo.iconColor = o nivel de severidade para dar uma cor ao icon : 'urgent','warning','info','inactive'
            */

            let iconClass = this.props.extraInfo.iconText == 'warn' ? "icon-warning-sign" : (this.props.extraInfo.iconText == 'info' ? "icon-info-sign" : "icon-ok-sign");

            iconClass += " " + this.props.extraInfo.iconColor + "-text";


            /* TODO JB: depois de actualizar as versões do react e bosostrap ver se isto já funciona bem
            let infoPopover= (<Popover id={"popover-info" + this.props.extraInfo.id } >
                                  {this.props.extraInfo.contents}
                                </Popover>

                            );
            infoBtn = (
                <OverlayTrigger trigger='click' rootClose  placement="left" overlay={infoPopover} >
                            <Button bsStyle="link"
                                     title="Info"
                                     className="info-btn">
                            <i className={iconClass}/>
                        </Button>
                    </OverlayTrigger>
                );
            */

            infoModal = (
                <Modal  show={this.state.showExtraInfo} onHide={this.toggleShowExtraInfo} animation={false}  className="extra-info-modal">
                    <Modal.Body>{this.props.extraInfo.contents}</Modal.Body>
                </Modal>
            );

            if(this.props.extraInfo.contents){

                infoBtn = (<Button bsStyle="link"
                                    title="Info"
                                    className="info-btn"
                                    onClick={this.toggleShowExtraInfo}>
                                <i className={iconClass}/>
                            </Button>
                        );

            }else{
                infoBtn = (<Button bsStyle="link"
                                     className="info-btn-inactive">
                            <i className={iconClass}/>
                        </Button>
                    );
            }
        }

        return(
            <div className={mainClassname}>
                <table style={{"display":"inline"}} >
                    <tbody>
                        <tr className="evolution-data-row">
                            <td className="evolution-data-sparkline">{sparklineHtml}</td>

                            <td className="evolution-data-total">
                                {/*quereo mostrar o total sempre que estou a mostrar a bar do titulo ou se tiver o toggle dos totais*/}
                                {this.props.showTotal || this.props.showTotalAsDonut? totalHtml : weightHtml}
                            </td>
                            <td className="evolution-data-info" >
                                {infoBtn}
                                {infoModal}

                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
        );
    }
};

export default EvolutionBar;
