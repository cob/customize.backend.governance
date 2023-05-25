# COB Governance

### Passos manuais a fazer no servidor após o `cob-cli customize`

* Importar as 5 definições que estão no `others/governance`
* configurar os ids das definições criadas no `dist/dashboard.html`
* configurar o `integrationm/common/config/GovernanceConfig` com os valores adequados  
* adicionar conteúdo do `others/governance/crontab` ao crontab do sistema 
  (VERIFICAR se o sendMsg2IM já tem suporte para receber o product no 3 argumento) 