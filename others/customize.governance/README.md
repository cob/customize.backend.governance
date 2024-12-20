# COB Governance

### Dependencias:
- Dashboard Customization
- Workflows Customization

### Passos manuais a fazer no servidor após o `cob-cli customize`
              
#### Importar definições
* Importar as definições que estão no `others/governance/definitions` pela ordem:
  * Informação
  * Politica v2
  * Control
  * Finding
  * Assessment
  * Colaborador GOV
  * Opções
  * Actividades v2
  * Conteudos
  * Feed Item
  * Threat
  * Asset Types
  * Asset
  * Risk
  * Risk Analysis
  * Consentimentos
  * Entidades Externas
  * Incidentes
  * Questionário
  * Vulnerability
* Opcionalmente criar um dominio Governance e por lá estas defs
* Corrigir os 2 links na def de Assessments para apontarem para a def id correcta: 
  * Histórico das Inconformidades Encontradas (FINDINGS)
  * Ver Control
* Corrigir Links na def de Controls para apontarem para a def id correcta:
  * GOALS
  * Histórico das Avaliações Feitas (ASSESSMENTS)
  * Histórico das Inconformidades Encontradas (FINDINGS)
  * COPIAR CONTROL

### After December 2024

#### Permissões
* Criar permissões para as defs listadas em cima usando o UserM easy com o template "Perms base Definição RM com delete separado"
* Criar 3 grupos (estes grupos são usados para limitar o acesso aos dashboards):
  - GOV Base Read - Base group for read access to Governance related defs and instances.
  - GOV Base ISO - Base group for read, write and update access to Governance related defs and instances.
  - GOV Feed Item - Group that contains roles to read and update feed items via concurrent

#### Corrigir Links Dashboards
* Necessário percorrer os dashboards, e substituir os IDs das definiçoes nos hrefs que levam à criação de novas instâncias. Hint: procurar por "/instance/create/X" e substituir o X pelo ID da def correto.

#### Corrigir Imagens Dashboards
* Upload das imagens na pasta `dashboard files` para a def Dashboard-Files
* Atualizar os links das imagens nos dashboards, conforme a relação seguinte:
| Dashboard       | Image name |
|----------------|-----------|
| Base Setup     | modulo1        | 
| GDPR Awareness        | modulo2        |
| Cybersecurity Awareness        | modulo2        |


#### Importar Conteúdos exemplo para a def Conteúdos
* Importar as instâncias de exemplos de conteúdos para a def `Conteúdos` através do ficheiro que está em `others/customize.governance/instances/conteúdos exemplos.xlsx`.


#### Importar Kibanas
* **ANTES DE SE IMPORTAR OS KIBANAS**, é preciso substituir os titles dos index-patterns no ficheiro `exported dashes with related objs.ndjson`.
Exemplo: Para a def Assessment, faz-se um *find & replace* de `recordm-343` por `recordm-DefIdNaMáquinaNova`.

| Def Name       |  Dogfooding   |
|----------------|------------   |
| Assessment     |  recordm-343  |
| Finding        |  recordm-347  |
| Risk Analysis  |  recordm-360  |
| Risk           |  recordm-361  |
| Asset          |  recordm-364  |
| Vulnerability  |  recordm-358  |
| Conteúdos      |  recordm-342  |
| Feed Item      |  recordm-371  |
| Threat         |  recordm-359  |

* Depois de fazer estas atualizações, podemos importar o ficheiro `exported dashes with related objs.ndjson`. Os dashboards vao ser carregados, e os index patterns também com os titles no formato `recordm-DefIdNaMáquinaNova`, mas com um ID único pré-configurado no ficheiro. E.g: o ID interno do kibana para o index-patter dos Assessments é `recordm-assessment`.

* Depois de importado, é preciso atualizar o link do Kibana dos dashboards conforme a seguinte tabela:
| Dashboard RecordM       |  Dashboard Kibana   |
|----------------|------------   |
| Awareness Statistics     |  Awareness 1  |
| Asset Management        |  Assets sorted by Asset Type  |
| Findings Overview  |  Findings 1  |
|    Maturity Monitor  |  Maturidade Overview   |
| Risk Analyzer          |  Risk Analysis (Risk Individual)  |
| Risks Overview  |  Risks 1  |
| Threats Catalog      |  Threats 1  |
| Vulnerabilities Overview      |  Vulnerabilities 1  |


---

### Before December 2024

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

#### ID to Definition Name

| Def Name       | GDPR-Gov3 | Dogfooding |
|----------------|-----------|------------|
| Assessment     | 29        | 343        |
| Finding        | 31        | 347        |
| Risk Analysis  | 53        | 360        |
| Risk           | 49        | 361        |
| Asset          | 26        | 364        |
| Vulnerability  | 54        | 358        |
| Conteúdos      | 9         | 342        |
| Feed Item      | 11        | 371        |
| Threat         | 52        | 359        |
