package plc.homework;

import java.util.regex.Pattern;

/**
 * Contains {@link Pattern} constants, which are compiled regular expressions.
 * See the assignment page for resources on regexes as needed.
 */
public class Regex {

    public static final Pattern
            EMAIL = Pattern.compile("[A-Za-z0-9._\\-]+@[A-Za-z0-9-]*\\.[a-z]{2,3}"),
            EVEN_STRINGS = Pattern.compile("^(?=.{10,20}$)(?=.{2,20}$)^(..)*$"),
            INTEGER_LIST = Pattern.compile("^\\[\\s*(\\d*\\s*(,\\s*\\d+\\s*)*)?\\s*\\]$"), //TODO
            NUMBER = Pattern.compile("^[+-]?(\\d+(\\.\\d+)?)$"), //TODO
            STRING = Pattern.compile("^\\\"([^\\\"\\\\\\\\]|\\\\[btnr'\\\"\\\\\\\\])*\\\"$"); //TODO
}
