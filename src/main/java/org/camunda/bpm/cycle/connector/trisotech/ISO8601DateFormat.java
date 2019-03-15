
package org.camunda.bpm.cycle.connector.trisotech;

import java.text.FieldPosition;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * This class is a {@link java.text.DateFormat} implementation that parse and format date according to the ISO 8601 standard. It supports both the basic and the
 * extended format. This standard is not supported out of the box in java. It is possible to closely achieve it using the SimpleDateFormat but the format is not
 * exactly compliant with the ISO standard. This clas on the other hand properly format the dates according to the standard. You can also as an added bonus
 * parse RFC822 format (this is the format natively supported by {@link SimpleDateFormat})<BR>
 * <BR>
 * To see some example of usage you can refer to the http://www.w3.org/TR/NOTE-datetime web site which give examples. You can also check the test case to see
 * examples
 *
 * @see com.trisotech.des.publicapi.util.ISO8601DateFormatTest
 *
 * @author sringuette
 */
public class ISO8601DateFormat extends SimpleDateFormat {

    private static final long serialVersionUID = 4113936183730283416L;

    public static final ISO8601DateFormat INSTANCE = new ISO8601DateFormat();

    /**
     * Instantiate a new DateFormat
     */
    public ISO8601DateFormat() {
        super("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    }

    /** {@inheritDoc} */
    @Override
    public Date parse(String s) throws ParseException {
        boolean hasTime = s.indexOf('T') >= 0;
        s = s.trim();
        // Replace ending UTC timezone marker Z by the RFC822 marker
        if (s.endsWith("Z")) {
            s = s.substring(0, s.length() - 1) + "+0000";
        } else if (hasTime && s.indexOf('+') == -1 && (s.lastIndexOf('-') < s.indexOf('T') || s.indexOf('-') == -1)) {
            s = s + "+0000";
        } else if (hasTime && (s.indexOf('+') != -1 || (s.lastIndexOf('-') > s.indexOf('T')))) {
            // We check if the hour only was used for the time zone ([...]T23:10-05 for example)
            if (s.indexOf('+') != -1) {
                if (s.length() == s.indexOf('+') + 3) {
                    s = s + "00";
                }
            } else if (s.length() == s.lastIndexOf('-') + 3) {
                s = s + "00";
            }
        }

        // Change from ISO time to RFC822 Time (+01:00 to +0100)
        if (s.length() > 3 && s.charAt(s.length() - 3) == ':') {
            s = s.substring(0, s.length() - 3) + s.substring(s.length() - 2, s.length());
        }

        // Now we need to check if the date use week number with 2010-W01-2 or 2010W012 format (we also accept if the day is missing, 1 is assumed)
        String datePart = hasTime ? s.substring(0, s.indexOf('T')) : s;
        String timePart = hasTime ? s.substring(s.indexOf('T')) : "";
        if (datePart.length() >= 7 && s.indexOf('W') == 4 || datePart.length() >= 8 && datePart.indexOf("-W") == 4) {
            s = getStringDateFromWeek(datePart) + timePart;
        } else {
            // Does it use the day of year? 2010001 or 2010-001

            if (datePart.length() == 7 && datePart.indexOf('-') == -1
                    || datePart.length() == 8 && datePart.indexOf('-') != -1 && datePart.indexOf('-') == datePart.lastIndexOf('-')) {
                // This is a date number format
                s = getStringDateFromDayNumber(datePart) + timePart;
            }
        }

        // If the date doesn't use the -, we will add them now
        // 20101010
        if ((s.indexOf("-") == -1 || s.indexOf("T") != -1 && s.indexOf('-') > s.indexOf('T')) && s.length() > 4) {
            s = s.substring(0, 4) + "-" + s.substring(4);
            // 2010-1010
            if (s.length() > 7) {
                s = s.substring(0, 7) + "-" + s.substring(7);
                // 2010-10-10
            }
        }

        // If the time doesn't use the :, we add it now
        // 2010-10-10T101010
        if (s.indexOf("T") != -1 && s.indexOf('T') < s.length() - 3 && s.indexOf(":") == -1) {
            s = s.substring(0, s.indexOf('T') + 3) + ":" + s.substring(s.indexOf('T') + 3);
            // 2010-10-10T10:1010+0000
            if (s.length() > s.indexOf(':') + 3 && s.charAt(s.indexOf(":") + 3) != '-' && s.charAt(s.indexOf(":") + 3) != '+') {
                s = s.substring(0, s.indexOf(':') + 3) + ":" + s.substring(s.indexOf(':') + 3);
                // 2010-10-10T10:10:10+0000
            }
        }

        // Now we complete the ISO date/time to the maximum representation so that the parent parser can read it
        String completionPattern = "0000-01-01T00:00:00.0";
        if (hasTime && s.length() < completionPattern.length() + 5) {
            // If a time is specified
            String dateWithoutTimzone = s.substring(0, s.length() - 5);
            String timezone = s.substring(s.length() - 5, s.length());
            s = dateWithoutTimzone + completionPattern.substring(dateWithoutTimzone.length(), completionPattern.length()) + timezone;
        } else if (!hasTime && s.length() < completionPattern.length()) {
            // If no time is specified, we don't need to remove the timezone at the end, simply complete and add a time zone
            s = s + completionPattern.substring(s.length(), completionPattern.length()) + "+0000";
        }

        return super.parse(s);
    }

    /**
     * From a datePart with the format aaaaddd or aaaa-ddd where ddd is the day number in the year, creates a date with the month and the day of the month.
     *
     * @param datePart the date of the specified format.
     * @return a date with the format aaaa-mm-dd
     * @throws ParseException if trying to parse a date from unexpected characters.
     */
    private String getStringDateFromDayNumber(String datePart) throws ParseException {
        try {
            int year = Integer.parseInt(datePart.substring(0, 4));
            int dayOfYear = datePart.length() == 8 ? Integer.parseInt(datePart.substring(5)) : Integer.parseInt(datePart.substring(4));

            Calendar c = Calendar.getInstance();
            c.set(Calendar.YEAR, year);
            c.set(Calendar.DAY_OF_YEAR, dayOfYear);

            return getDateFromCalendar(c);
        } catch (NumberFormatException e) {
            throw new ParseException(e.getMessage(), 0);
        }
    }

    /**
     * From a datePart with the format aaaaWww, aaaWwwd, aaaa-Www or aaaa-Www-d where Www is the week number in the year precced by W and d the day of the week
     * 1 being Monday and 7 being Sunday, creates a date with the month and the day of the month.
     *
     * @param datePart the date of the specified format.
     * @return a date with the format aaaa-mm-dd
     * @throws ParseException if trying to parse a date from unexpected characters.
     */
    private String getStringDateFromWeek(String datePart) throws ParseException {
        try {
            int year = Integer.parseInt(datePart.substring(0, 4));
            int week = Integer.parseInt(datePart.substring(datePart.indexOf('W') + 1, datePart.indexOf('W') + 3));
            int dayOfWeek = 1;
            Calendar c = Calendar.getInstance();

            // Those settings follow ISO-8601
            c.setFirstDayOfWeek(Calendar.MONDAY);
            c.setMinimalDaysInFirstWeek(4);

            c.set(Calendar.YEAR, year);
            c.set(Calendar.WEEK_OF_YEAR, week);
            // Next thing should be the day in week now.
            if (datePart.length() > 7 && datePart.indexOf('W') == 4) {
                dayOfWeek = Integer.parseInt(datePart.substring(7, 8));
            } else if (datePart.length() > 9 && datePart.indexOf("-W") == 4) {
                dayOfWeek = Integer.parseInt(datePart.substring(9, 10));
            }

            // In ISO-8601 (what we expect), MONDAY is 1 and SUNDAY is 7 but Calendar.MONDAY = 2 and Calendar.SUNDAY=1. Fix this.
            if (dayOfWeek == 7) {
                dayOfWeek = 1;
            } else {
                dayOfWeek++;
            }
            c.set(Calendar.DAY_OF_WEEK, dayOfWeek);
            return getDateFromCalendar(c);
        } catch (NumberFormatException e) {
            throw new ParseException(e.getMessage(), 0);
        }
    }

    /**
     * @param c the calendar.
     * @return the current date on the calendar with aaaa-mm-dd format.
     */
    private String getDateFromCalendar(Calendar c) {
        String s = "" + c.get(Calendar.YEAR) + "-";
        if (c.get(Calendar.MONTH) + 1 < 10) {
            s = s + "0";
        }
        s = s + (c.get(Calendar.MONTH) + 1) + "-";
        if (c.get(Calendar.DAY_OF_MONTH) < 10) {
            s = s + "0";
        }
        s = s + c.get(Calendar.DAY_OF_MONTH);
        return s;
    }

    /** {@inheritDoc} */
    @Override
    public StringBuffer format(Date date, StringBuffer stringbuffer, FieldPosition fieldposition) {
        StringBuffer buffer = super.format(date, stringbuffer, fieldposition);
        String strDate = buffer.toString();
        // If we are at the UTC time zone, replace it with Z
        if (strDate.endsWith("0000")) {
            strDate = strDate.substring(0, strDate.length() - 5) + "Z";
        } else {
            // Change the RFC822 timezone to ISO (+0100 to +01:00)
            strDate = strDate.substring(0, strDate.length() - 2) + ":" + strDate.substring(strDate.length() - 2, strDate.length());
        }
        return new StringBuffer(strDate);
    }
}
