package config

class GovernanceConfig {

    //for mail
    public static final String SENDGRID_API_KEY = "XXXXXXXXX";

    //for SMS
    public static final String PLIVO_SEND_SMS_RESOURCE = "https://api.plivo.com/v1/Account/XXXXXXX/Message/"
    public static final String PLIVO_API_KEY = "XXXXXXXXX";


    public static final String ES_URL = "http://localhost:9200";
    public static final String COBTOKEN = "XXXXXXXXX";

    /**
     * Custom functions to be used inside Controls' Success Condition code
     * Each entry is the function name and the closure as a string
     * Notes:
     *  - the closure should be a string so it can be evaluated in the governace script and its 'owner' and 'this' are
     *    correctly set to be the class Script1 that is dinamicaly generated for the success condition of the control
     *  - The delegate of the closure is set to be the gov_assessment class so we can invoke its gov_assessment methods here
     *  - Read https://groovy-lang.org/closures.html for full info on groovy closures
     * Example
     *
         public static Map customAssessmentFunctions = [
             "customLogFn": '''
                { msg -> log.info("INSIDE CUSTOM LOG FUNCTION: "+ msg) }
             '''
         ];
     */
    public static Map customAssessmentFunctions = [:];
}
