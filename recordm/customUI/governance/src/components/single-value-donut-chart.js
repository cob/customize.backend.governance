//Based on https://gist.github.com/elijahmanor/34c5f5d4f6204f151295

import React from 'react';
import ReactDOM  from 'react-dom';
import PropTypes from 'prop-types';

class SingleValueDonutChart extends React.Component {
  constructor(props) {
    super(props);
  }

  render() {
    let { width, height } = this.props;

    return <svg className="chart-donut" width={width} height={height} viewBox={`0 0 ${width} ${height}`}>
      {this.renderPaths()}
      {this.renderText()}
    </svg>;
  }

  renderPaths() {
    let total = this.props.total;
    let value = this.props.value;
    let angle  = 0;
    let paths  = [];

    paths.push(this.renderPath(value, angle))

    if (value < total) {
      angle += (value / total) * 360;
      let emptyValue = total - value;
      if(emptyValue==total) emptyValue=total-0.0001; //force showing the circle

      paths.push(this.renderEmptyPath(emptyValue, angle));
    }

    return paths;
  }

  renderText() {
    let value = Math.floor(100*this.props.value/this.props.total) + " %";

    return <g>
      <text className="chart-text" x="50%" y="50%" text-align="middle">
        <tspan dx="0" dy="4" textAnchor="middle">{value}</tspan>
   	  </text>

    </g>;
  }

  renderPath(value, startAngle) {
    let d = this.getPathData(value, this.props.total, startAngle, this.props.width, this.props.innerRadius, this.props.outerRadius);

  	return <path className="chart-path" d={d} key={startAngle}></path>;
  }

  renderEmptyPath(value, startAngle) {
    let d = this.getPathData(value, this.props.total, startAngle, this.props.width, this.props.innerRadius + 0.03, this.props.outerRadius - 0.03);

    return <path className="chart-path chart-path--empty" d={d} key={startAngle}></path>;
  }

   getPathData(data, total, startAngle, width, innerRadius, outerRadius) {
    let activeAngle = data / total * 360;
    let endAngle = startAngle + activeAngle;
    let largeArcFlagSweepFlagOuter = activeAngle > 180 ? '1 1' : '0 1';
    let largeArcFlagSweepFlagInner = activeAngle > 180 ? '1 0' : '0 0';
		let half = width / 2;
    let x1 = half + half * outerRadius * Math.cos(Math.PI * startAngle / 180);
    let y1 = half + half * outerRadius * Math.sin(Math.PI * startAngle / 180);
    let x2 = half + half * outerRadius * Math.cos(Math.PI * endAngle / 180);
    let y2 = half + half * outerRadius * Math.sin(Math.PI * endAngle / 180);
    let x3 = half + half * innerRadius * Math.cos(Math.PI * startAngle / 180);
    let y3 = half + half * innerRadius * Math.sin(Math.PI * startAngle / 180);
    let x4 = half + half * innerRadius * Math.cos(Math.PI * endAngle / 180);
    let y4 = half + half * innerRadius * Math.sin(Math.PI * endAngle / 180);

    return `M${x1},${y1} ${this.getArc(width, outerRadius, largeArcFlagSweepFlagOuter, x2, y2)} L${x4},${y4} ${this.getArc(width, innerRadius, largeArcFlagSweepFlagInner, x3, y3)} z`;
	}

  getArc(canvasSide, radius, largeArcFlagSweepFlag, x, y) {
    let z = canvasSide / 2 * radius;

    return `A${z},${z} 0 ${largeArcFlagSweepFlag} ${x},${y}`;
  }

};

SingleValueDonutChart.propTypes = {
  height: PropTypes.number,
  width: PropTypes.number,
  outerRadius: PropTypes.number,
  outerRadiusHover: PropTypes.number,
  innerRadius: PropTypes.number,
  innerRadiusHover: PropTypes.number,
  emptyWidth: PropTypes.number,
  total: PropTypes.number,
  value: PropTypes.number
};

SingleValueDonutChart.defaultProps = {
    height: 50,
    width: 50,
    outerRadius: 0.95,
    outerRadiusHover: 1,
    innerRadius: 0.85,
    innerRadiusHover: 0.85,
    emptyWidth: .06,
    total: 100,
    value:0
};

export default SingleValueDonutChart;


/*
Example Usage

import SingleValueDonutChart from './components/single-value-donut-chart';
<SingleValueDonutChart  value={5}  />

*/
