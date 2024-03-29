package config

class GovernanceConfig {

    //For ES searching
    public static final String ES_URL = "http://localhost:9200";
    public static final String COBTOKEN = "XXXXXXXXX";

    //For controls
    /**
     * Custom functions to be used inside Controls' Success Condition code.
     * Each entry is the function name and the closure.
     * Gov_assessment methods can be invoked inside these closures.
     *
     * Example
     *
         public static Map customAssessmentFunctions = [
            "customLogFn": { msg ->
                log.info("INSIDE CUSTOM LOG FUNCTION: " + msg)
            }
         ];
     */
    public static Map customAssessmentFunctions = [:];


    //For Mail

    /* if "usesEmailActionPack = true", we must have `email` action pack configured in
        com.cultofbits.integrationm.service.properties and mail settings configured in
        com.cultofbits.genesis.comm.properties
     */
    public final static boolean usesEmailActionPack = true;
    public static emailActionPack; //will be set to the action pack instance by gov_assessment

    final static String SENDER = "governance@cultofbits.com"
    final static String SENDER_NAME = "Governance"

    /*
       This send is only to be used when usesEmailActionPack = false and we have a different send mail system.
       lidl for example, uses a curl script
     */

    static void sendMail(subject, body, emails, emailsBcc) {
        if (usesEmailActionPack) {
            def _tos = emails.split(",").findAll { it != null } //hack to convert to List
            def _bccs = emailsBcc.split(",").findAll { it != null }

            emailActionPack.send(subject, body, [from: (SENDER_NAME + "<" + SENDER + ">"), to: _tos, bcc: _bccs])

        } else {
            // else implement own sender. Some clientes, for example, use a curl script because they need to use a proxy
            //utils.CurlEmailSender.send(SENDER, SENDER_NAME, emails, emailsBcc, subject, body, true);
        }
    };


    //For SMS

    /* if "usesSmsActionPack = true", we must have `sms` action pack configured in
        com.cultofbits.integrationm.service.properties
     */
    public final static boolean usesSmsActionPack = true;
    public static smsActionPack; //will be set to the action pack instance by gov_assessment

    // governance specific phone number for SMSs; if null a general configured number will be used
    final static String GOV_PHONE_NUMBER = null;

    /*
       This send is only to be used when usesSmsActionPack = false and we have a different send sms system.
       lidl for example, uses a curl script
     */

    static void sendSms(subject, body, phone) {
        String smsText = subject + "\n\n" + body.replaceAll("\\*\\*", "").replaceAll("<.?b>", "");

        if (usesSmsActionPack) {
            def opts = [:]
            if (GOV_PHONE_NUMBER != null) {
                opts.put("from", GOV_PHONE_NUMBER)
            }

            smsActionPack.send(smsText, [phone], opts)
        } else {
            // else implement own sender. Some clientes, for example, use a curl script because they need to use a proxy
            //utils.CurlSmsSender.send(smsText, phone, GOV_PHONE_NUMBER);
        }
    };

}
