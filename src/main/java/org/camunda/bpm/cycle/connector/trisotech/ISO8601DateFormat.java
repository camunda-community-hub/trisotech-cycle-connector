package org.camunda.bpm.cycle.connector.trisotech;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class ISO8601DateFormat extends SimpleDateFormat {

    private static final long serialVersionUID = 8842391093629642428L;
    
    public static final ISO8601DateFormat INSTANCE = new ISO8601DateFormat();
    
    public ISO8601DateFormat() {
        super("yyyy-MM-dd'T'HH:mm:ss'Z'");
        setTimeZone(TimeZone.getTimeZone("UTC"));
    }
}
