package plc.homework;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Contains JUnit tests for {@link Regex}. A framework of the test structure
 * is provided, you will fill in the remaining pieces.
 * <p>
 * To run tests, either click the run icon on the left margin, which can be used
 * to run all tests or only a specific test. You should make sure your tests are
 * run through IntelliJ (File > Settings > Build, Execution, Deployment > Build
 * Tools > Gradle > Run tests using <em>IntelliJ IDEA</em>). This ensures the
 * name and inputs for the tests are displayed correctly in the run window.
 */
public class RegexTests {

    /**
     * This is a parameterized test for the {@link Regex#EMAIL} regex. The
     * {@link ParameterizedTest} annotation defines this method as a
     * parameterized test, and {@link MethodSource} tells JUnit to look for the
     * static method {@link #testEmailRegex()}.
     * <p>
     * For personal preference, I include a test name as the first parameter
     * which describes what that test should be testing - this is visible in
     * IntelliJ when running the tests (see above note if not working).
     */
    @ParameterizedTest
    @MethodSource
    public void testEmailRegex(String test, String input, boolean success) {
        test(input, Regex.EMAIL, success);
    }

    public static Stream<Arguments> testEmailRegex() {
        return Stream.of(
                // Matching cases
                Arguments.of("Alphanumeric", "thelegend27@gmail.com", true),
                Arguments.of("UF Domain", "otherdomain@ufl.edu", true),
                Arguments.of("Underscore in Local Part", "john_doe@example.com", true),
                Arguments.of("Hyphen in Domain", "john.doe@mail-server.com", true),
                Arguments.of("Google Domain start with number", "1othe2rdomain@1gmail.com", true),

                // Non-Matching cases
                Arguments.of("Missing Domain Dot", "missingdot@gmailcom", false),
                Arguments.of("Symbols", "symbols#$%@gmail.com", false),
                Arguments.of("Missing '@' Symbol", "john.doeatexample.com", false),
                Arguments.of("Trailing Dot in Domain", "john.doe@example.com.", false),
                Arguments.of("Double Dots in Domain", "john.doe@example..com", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testEvenStringsRegex(String test, String input, boolean success) {
        test(input, Regex.EVEN_STRINGS, success);
    }

    public static Stream<Arguments> testEvenStringsRegex() {
        return Stream.of(
                // Matching cases
                Arguments.of("10 Characters", "automobile", true),
                Arguments.of("12 Characters", "123456789012", true),
                Arguments.of("14 Characters", "i<3pancakes10!", true),
                Arguments.of("16 Characters", "abcdefghijklmnop", true),
                Arguments.of("20 Characters", "abcdefghijklmnopqrst", true),

                // Non-Matching cases
                Arguments.of("6 Characters", "6chars", false),
                Arguments.of("13 Characters", "i<3pancakes9!", false),
                Arguments.of("9 Characters", "abcdefghi", false),
                Arguments.of("21 Characters", "abcdefghijklmnopqrstuv", false),
                Arguments.of("19 Characters", "abcdefghijklmnopqrs", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testIntegerListRegex(String test, String input, boolean success) {
        test(input, Regex.INTEGER_LIST, success);
    }

    public static Stream<Arguments> testIntegerListRegex() {
        return Stream.of(
                // Matching cases
                Arguments.of("Single Element", "[1]", true),
                Arguments.of("Multiple Elements", "[1,2,3]", true),
                Arguments.of("Empty List", "[]", true),
                Arguments.of("Mixed Spacing", "[1, 2,3 , 4]", true),
                Arguments.of("List with Large Numbers", "[12345, 67890]", true),

                // Non-Matching cases
                Arguments.of("Missing Brackets", "1,2,3", false),
                Arguments.of("Missing Commas", "[1 2 3]", false),
                Arguments.of("Trailing Comma", "[1,2,3,]", false),
                Arguments.of("Invalid Characters", "[1,a,3]", false),
                Arguments.of("Nested Brackets", "[1,[2],3]", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testNumberRegex(String test, String input, boolean success) {
        test(input, Regex.NUMBER, success);
    }

    public static Stream<Arguments> testNumberRegex() {
        return Stream.of(
                // Matching cases
                Arguments.of("Single Number", "1", true),
                Arguments.of("Decimals Number", "12.23", true),
                Arguments.of("Decimals Number with optional sign", "+123.23", true),
                Arguments.of("Number with optional sign", "-124", true),
                Arguments.of("Number heading by 0", "0124", true),

                // Non-Matching cases
                Arguments.of("Leading decimals", ".123", false),
                Arguments.of("Leading decimals with optional sign", "-.123", false),
                Arguments.of("Trailing decimals with optional sign", "+123.", false),
                Arguments.of("Use comma as seperator", "1,123", false),
                Arguments.of("Trailing decimals", "123.", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testStringRegex(String test, String input, boolean success) {
        test(input, Regex.STRING, success);
    }

    public static Stream<Arguments> testStringRegex() {
        return Stream.of(
                // Matching cases
                Arguments.of("Empty String", "\"\"", true),
                Arguments.of("Simple String", "\"Hello, World!\"", true),
                Arguments.of("String with Tab", "\"1\\t2\"", true),
                Arguments.of("String with Newline", "\"Line1\\nLine2\"", true),
                Arguments.of("String with Mixed Escapes", "\"Quote: \\\" and Backslash: \\\\\\\\\"", true),

                // Non-Matching cases
                Arguments.of("Unterminated String", "\"unterminated", false),
                Arguments.of("Invalid Escape", "\"invalid\\escape\"", false),
                Arguments.of("Unescaped Backslash", "\"C:\\Program Files\\MyApp\"", false),
                Arguments.of("Single Quote String", "'This is not double-quoted'", false),
                Arguments.of("Closing Quote Inside", "\"This is \"unterminated\"", false)
        );
    }


    /**
     * Asserts that the input matches the given pattern. This method doesn't do
     * much now, but you will see this concept in future assignments.
     */
    private static void test(String input, Pattern pattern, boolean success) {
        Assertions.assertEquals(success, pattern.matcher(input).matches());
    }

}
