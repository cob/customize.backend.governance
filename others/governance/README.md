# COB Governance

### Passos manuais a fazer no servidor após o `cob-cli customize`

* Importar as 5 definições que estão no `others/governance/definitions` pela ordem:
  * Informação
  * Goal
  * Control
  * Finding
  * Assessment
* Opcionalmente criar um dominio Governance e por lá estas defs

* IMportar o ficheiro informação.xlsx para a definição Informação (garantir que o importer instalado suporta duplicados)
* Depois de importadas as informações, fazer upload do recordm-tool.png para a info de controls e corrigir o link do ficheiro no texto

* configurar os ids das definições criadas no `dist/dashboard.html` 
* //TODO JBARATA: configurar o id do dashboard Kibana no`dist/dashboard.html` (default MM-GOV-2) 
* configurar o `integrationm/common/config/GovernanceConfig` com os valores adequados  
* adicionar conteúdo do `others/governance/crontab` ao crontab do sistema 
  (VERIFICAR se o sendMsg2IM já tem suporte para receber o product no 3 argumento - correr o chef garante isso) 