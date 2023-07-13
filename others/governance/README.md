# COB Governance

### Passos manuais a fazer no servidor após o `cob-cli customize`
              
#### Importar definições
* Importar as 5 definições que estão no `others/governance/definitions` pela ordem:
  * Informação
  * Goal
  * Control
  * Finding
  * Assessment
* Opcionalmente criar um dominio Governance e por lá estas defs
* Corrigir os 2 links na def de assessments para apontarem para a def id correcta: 
  * Histórico das Inconformidades Encontradas (FINDINGS)
  * Ver Control
* Corrigir Links na def de Controls para apontarem para a def id correcta:
  * GOALS
  * Histórico das Avaliações Feitas (ASSESSMENTS)
  * Histórico das Inconformidades Encontradas (FINDINGS)
  * COPIAR CONTROL
* Corrigir Links na def de Goals para apontarem para a def id correcta:
  * Controls
  * Copiar Goal

#### Importar dados
* IMportar o ficheiro informação.xlsx para a definição Informação (garantir que o importer instalado suporta duplicados)
* Depois de importadas as informações, fazer upload do recordm-tool.png para a info de controls e corrigir o link do ficheiro no texto

#### Configurar coisas
* configurar os ids das definições criadas no `dist/dashboard.html` 
* //TODO JBARATA: configurar o id do dashboard Kibana no`dist/dashboard.html` (default MM-GOV-2) 
* configurar o `integrationm/common/config/GovernanceConfig` com os valores adequados  
* adicionar conteúdo do `others/governance/crontab` ao crontab do sistema 
  (VERIFICAR se o sendMsg2IM já tem suporte para receber o product no 3 argumento - correr o chef garante isso) 
 
####   Configurar com.cultofbits.integrationm.service.properties
Configurar actionPacks `recordm,rmRest,email,userm` (necessário ter o integrationM>=14.0.0-SNAPSHOT por caua do userm)

Exemplo:
````
action.names=recordm,rmRest,email,userm

action.recordm=recordm
action.recordm.recordm.base-url=http://localhost:40280

action.userm=userm
action.userm.userm.base-url=http://localhost:40780

action.rmRest=rest
action.rmRest.base-url=http://localhost:40280
action.rmRest.cookie-name=cobtoken
action.rmRest.cookie-value=XXXXXXXXXXX

action.email=email
action.email.email.default-sender=no-reply@jbarata.cultofbits.com
````