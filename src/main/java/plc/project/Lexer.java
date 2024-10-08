package plc.project;

import java.util.ArrayList;
import java.util.List;

/**
 * The lexer works through three main functions:
 *
 *  - {@link #lex()}, which repeatedly calls lexToken() and skips whitespace
 *  - {@link #lexToken()}, which lexes the next token
 *  - {@link CharStream}, which manages the state of the lexer and literals
 *
 * If the lexer fails to parse something (such as an unterminated string) you
 * should throw a {@link ParseException} with an index at the character which is
 * invalid or missing.
 *
 * The {@link #peek(String...)} and {@link #match(String...)} functions are
 * helpers you need to use, they will make the implementation a lot easier.
 */
public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    /**
     * Repeatedly lexes the input using {@link #lexToken()}, also skipping over
     * whitespace where appropriate.
     */
    public List<Token> lex() {
        List<Token> tokens = new ArrayList<>();
        while (chars.has(0)) {
            // whitespace
            if(!(match(" ") || match("\t") || match("\r") || match("\b") || match("\n")))
                tokens.add(lexToken());
            else {
                chars.skip();
            }
        }
        return tokens;
    }


    /**
     * This method determines the type of the next token, delegating to the
     * appropriate lex method. As such, it is best for this method to not change
     * the state of the char stream (thus, use peek not match).
     *
     * The next character should start a valid token since whitespace is handled
     * by {@link #lex()}
     */
    public Token lexToken() {
        if (Character.isLetter(chars.get(0)) || chars.get(0) == '_') {
            return lexIdentifier();
        } else if (Character.isDigit(chars.get(0)) || chars.get(0) == '-' || chars.get(0) == '+') {
            return lexNumber();
        } else if (chars.get(0) == '\'') {
            return lexCharacter();
        } else if (chars.get(0) == '"') {
            return lexString();
        } else {
            return lexOperator();
        }
    }


    public Token lexIdentifier() {
        while (chars.has(0) && (Character.isLetterOrDigit(chars.get(0)) || chars.get(0) == '_' || chars.get(0) == '-')) {
            chars.advance();
        }
        return chars.emit(Token.Type.IDENTIFIER);
    }



    public Token lexNumber() {
        boolean hasSign = chars.get(0) == '-' || chars.get(0) == '+';
        if (hasSign) {
            chars.advance();
        }
        if (!chars.has(0) || !Character.isDigit(chars.get(0))) {
            if (hasSign) {
                return chars.emit(Token.Type.OPERATOR); // Treat it as an operator if no digits follow
            }
            throw new ParseException("Invalid number", chars.index);
        }
        while (chars.has(0) && Character.isDigit(chars.get(0))) {
            chars.advance();
        }
        if (chars.has(0) && chars.get(0) == '.') {
            chars.advance();
            if(chars.has(0) && Character.isDigit(chars.get(0))) {
                while (chars.has(0) && Character.isDigit(chars.get(0))) {
                    chars.advance();
                }
                return chars.emit(Token.Type.DECIMAL);
            }else{
                throw new ParseException("Invalid decimal format", chars.index);
            }
        }
        return chars.emit(Token.Type.INTEGER);
    }



    public Token lexCharacter() {
        chars.advance(); // Skip first '
        if (!chars.has(0)) {
            throw new ParseException("Unterminated character", chars.index);
        }
        if (chars.get(0) == '\\') {
            lexEscape(); // Handle escape sequence
        } else {
            if (!chars.has(0) || chars.get(0) == '\'') {
                throw new ParseException("Unterminated character", chars.index); // Handle empty case
            }
            chars.advance(); // Read normal char
        }
        if (!chars.has(0) || chars.get(0) != '\'') {
            throw new ParseException("Unterminated character", chars.index);
        }
        chars.advance(); // Skip closing '
        return chars.emit(Token.Type.CHARACTER);
    }


    public Token lexString() {
        chars.advance(); // skip "
        while (chars.has(0) && chars.get(0) != '"') {
            if (chars.get(0) == '\\') {
                lexEscape(); // handle escape tag
            } else {
                chars.advance();
            }
        }
        if (!chars.has(0) || chars.get(0) != '"') {
            throw new ParseException("Unterminated string", chars.index);
        }
        chars.advance(); // skip closing "
        return chars.emit(Token.Type.STRING);
    }


    public void lexEscape() {
        chars.advance(); // skip \
        if (!"bfnrt'\"".contains(Character.toString(chars.get(0)))) {
            throw new ParseException("Invalid escape character", chars.index);
        }
        chars.advance();
    }


    public Token lexOperator() {
        // check operation has multiple sign
        if (match("<=", ">=", "!=", "==")) {
            return chars.emit(Token.Type.OPERATOR);
        } else {
            chars.advance();
            return chars.emit(Token.Type.OPERATOR);
        }
    }


    /**
     * Returns true if the next sequence of characters match the given patterns,
     * which should be a regex. For example, {@code peek("a", "b", "c")} would
     * return true if the next characters are {@code 'a', 'b', 'c'}.
     */
    public boolean peek(String... patterns) {
        for (String pattern : patterns) {
            // Check if there are enough characters in the input for this pattern
            if (!chars.has(pattern.length() - 1)) {
                return false;
            }
            // Check if the characters in the stream match the pattern
            for (int i = 0; i < pattern.length(); i++) {
                if (chars.get(i) != pattern.charAt(i)) {
                    return false;
                }
            }
        }
        return true;
    }



    /**
     * Returns true in the same way as {@link #peek(String...)}, but also
     * advances the character stream past all matched characters if peek returns
     * true. Hint - it's easiest to have this method simply call peek.
     */
    public boolean match(String... patterns) {
        for (String pattern : patterns) {
            if (peek(pattern)) {
                // If it matches, advance the character stream by the length of the pattern
                for (int i = 0; i < pattern.length(); i++) {
                    chars.advance();
                }
                return true;
            }
        }
        return false;
    }



    /**
     * A helper class maintaining the input string, current index of the char
     * stream, and the current length of the token being matched.
     *
     * You should rely on peek/match for state management in nearly all cases.
     * The only field you need to access is {@link #index} for any {@link
     * ParseException} which is thrown.
     */
    public static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        public char get(int offset) {
            return input.charAt(index + offset);
        }

        public void advance() {
            index++;
            length++;
        }

        public void skip() {
            length = 0;
        }

        public Token emit(Token.Type type) {
            int start = index - length;
            skip();
            return new Token(type, input.substring(start, index).trim(), start);
        }

    }

}
