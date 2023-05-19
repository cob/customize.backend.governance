# COB Governance Dashboard

## Deploy

* Compilar com `npm run build-prod`

* Copiar os ficheiros necessários para a directoria local do servidor executando o comando 
`DEST=<path (absoluto ou relativo) para directoria destino> npm run local-dist`

  * Ficheiros a copiar para o servidor:
    * build/browser-bundle.js
    * css/*
    * img/*


O `governance.html` deve existir no servidor com as confs que são particulares 
de cada instalação (existe aqui só para exemplo)
