//DEV deps
//var marked = require('marked');
//import $ from "jquery";
//DEV deps

import React from 'react';
import ReactDOM  from 'react-dom';

import GovernanceDashboard from './components/governance-dashboard';



let _userOrg = cob.app.getGroups().find(function(g){if(g.startsWith("ORG ")) return g;} ); 
if(_userOrg) _userOrg  = _userOrg.substr(4);

const governanceConfs = {
    goalsDefId: 56,
    controlsDefId: 57,
    assessmentsDefId: 58,
    findingsDefId: 59,
    informacaoDefId:40,

    totals:{
        lowerDate:'now-1M/d',
        upperDate:'now/h',
        dateInterval:'1d',
        kibanaControlsDashboardId:'MM-GOV-2'
    },

    isESversion5:true,
    /* ser a versao 5 do ES implica :
        1-ter de usar a media normal pois nao temos o plugin de media ponderada
        2-nao podemos usar o endpoint POST advanced do Rm pois já não passa as aggregations para o ES (quando usarmos a
          REST API do ES já conseguiremos voltar a usar isso - está um TODO no defaultSearchService);
          para já temos de usar o ES directamente (e para isso este tem de estar configurado no nginx)
    */

    userOrg: _userOrg,
    canSeeAll: _userOrg == "Internal"
}

console.log("XXXXXXZZZ");
let url=new URL(document.location);
console.log(url);
let params = new URLSearchParams(url.hash.slice(1));
console.log(params);
params.set('level', 1);

let basePath=document.location.hash.replace("#/","");
//cob.app.navigateTo(basePath + ( basePath.indexOf("?")>=0? "&":"?" )+ params.toString())
//cob.app.changeRouteWithoutNavigation(basePath + ( basePath.indexOf("?")>=0? "&":"?" )+ params.toString())

ReactDOM.render(
    <GovernanceDashboard confs={governanceConfs}/>
    , document.getElementById('governance-dashboard-container')
);
