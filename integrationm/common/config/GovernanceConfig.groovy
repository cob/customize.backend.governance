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
