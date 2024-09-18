package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 * <p>
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 * <p>
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        List<Ast.Field> fields = new ArrayList<>();
        List<Ast.Method> methods = new ArrayList<>();

        while (match("LET")) {
            fields.add(parseField());
        }

        while (match("DEF")) {
            methods.add(parseMethod());
        }

        if (tokens.has(0)) {
            throw new ParseException("Unexpected token: " + tokens.get(0).getLiteral(), tokens.get(0).getIndex());
        }

        return new Ast.Source(fields, methods);
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    public Ast.Field parseField() throws ParseException {
        if (!match("LET")) {
            throw new ParseException("Expected LET token", tokens.get(0).getIndex());
        }

        Token name = tokens.get(0);
        if (!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier", name.getIndex());
        }

        Optional<Ast.Expr> value = Optional.empty();
        if (match("=")) {
            value = Optional.of(parseExpression());
        }

        if (!match(";")) {
            throw new ParseException("Expected semicolon", tokens.get(0).getIndex());
        }

        return new Ast.Field(name.getLiteral(), value);
    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    public Ast.Method parseMethod() throws ParseException {
        if (!match("DEF")) {
            throw new ParseException("Expected DEF", tokens.get(0).getIndex());
        }

        Token name = tokens.get(0);
        if (!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier", name.getIndex());
        }

        if (!match("(")) {
            throw new ParseException("Expected '('", tokens.get(0).getIndex());
        }

        List<String> parameters = new ArrayList<>();
        if (!peek(")")) {
            do {
                Token param = tokens.get(0);
                if (!match(Token.Type.IDENTIFIER)) {
                    throw new ParseException("Expected parameter name", param.getIndex());
                }
                parameters.add(param.getLiteral());
            } while (match(","));
        }

        if (!match(")")) {
            throw new ParseException("Expected ')'", tokens.get(0).getIndex());
        }

        if (!match("{")) {
            throw new ParseException("Expected '{'", tokens.get(0).getIndex());
        }

        List<Ast.Stmt> statements = new ArrayList<>();
        while (!match("}")) {
            statements.add(parseStatement());
        }

        return new Ast.Method(name.getLiteral(), parameters, statements);
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Stmt parseStatement() throws ParseException {
        if (match("LET")) {
            return parseDeclarationStatement();
        } else if (match("IF")) {
            return parseIfStatement();
        } else if (match("FOR")) {
            return parseForStatement();
        } else if (match("WHILE")) {
            return parseWhileStatement();
        } else if (match("RETURN")) {
            return parseReturnStatement();
        } else {
            Ast.Stmt.Expr lhs = parseExpression();
            if (!match("=")) {
                if (!match(";")) {
                    throw new ParseException("Expected semicolon", tokens.get(-1).getIndex());
                }
                return new Ast.Stmt.Expression(lhs);
            }

            Ast.Stmt.Expr rhs = parseExpression();

            if (!match(";")) {
                throw new ParseException("Expected semicolon", tokens.get(-1).getIndex());
            }
            return new Ast.Stmt.Assignment(lhs, rhs);
        }
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Stmt.Declaration parseDeclarationStatement() throws ParseException {
        match("LET");
        Token name = tokens.get(0);
        if (!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier", name.getIndex());
        }

        Optional<Ast.Expr> value = Optional.empty();
        if (match("=")) {
            value = Optional.of(parseExpression());
        }

        if (!match(";")) {
            throw new ParseException("Expected ';'", tokens.get(0).getIndex());
        }

        return new Ast.Stmt.Declaration(name.getLiteral(), value);
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Stmt.If parseIfStatement() throws ParseException {
        Ast.Expr condition = parseExpression();
        List<Ast.Stmt> thenStatements = new ArrayList<>();
        List<Ast.Stmt> elseStatements = new ArrayList<>();
        if (!match("THEN")) {
            throw new ParseException("No THEN", tokens.index);
        }

        while (!match("END")) {

            thenStatements.add(parseStatement());

            if (match("ELSE")) {
                while (!match("END")) {
                    elseStatements.add(parseStatement());
                }
                break;
            }
        }
        return new Ast.Stmt.If(condition, thenStatements, elseStatements);
    }

    /**
     * Parses a for statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a for statement, aka
     * {@code FOR}.
     */
    public Ast.Stmt.For parseForStatement() throws ParseException {
        match("FOR");

        Token name = tokens.get(0);
        if (!match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier", name.getIndex());
        }

        if (!match("IN")) {
            throw new ParseException("Expected 'IN'", tokens.get(0).getIndex());
        }

        Ast.Expr value = parseExpression();
        if (!match("{")) {
            throw new ParseException("Expected '{'", tokens.get(0).getIndex());
        }

        List<Ast.Stmt> statements = new ArrayList<>();
        while (!match("}")) {
            statements.add(parseStatement());
        }

        return new Ast.Stmt.For(name.getLiteral(), value, statements);
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Stmt.While parseWhileStatement() throws ParseException {
        Ast.Expr condition = parseExpression();
        List<Ast.Stmt> statements = new ArrayList<>();

        if (!match("DO")) {
            throw new ParseException("No DO", tokens.index);
        }

        while (!match("END")) {
            statements.add(parseStatement());
        }

        return new Ast.Stmt.While(condition, statements);
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Stmt.Return parseReturnStatement() throws ParseException {
        match("RETURN");

        Ast.Expr value = parseExpression();
        if (!match(";")) {
            throw new ParseException("Expected ';'", tokens.get(0).getIndex());
        }

        return new Ast.Stmt.Return(value);
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expr parseExpression() throws ParseException {
        return parseEqualityExpression();
    }

    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expr parseEqualityExpression() throws ParseException {
        Ast.Expr left = parseAdditiveExpression();
        while (match("==") || match("!=") || match("AND") || match("OR")) {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expr right = parseAdditiveExpression();
            left = new Ast.Expr.Binary(operator, left, right);
        }
        return left;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expr parseAdditiveExpression() throws ParseException {
        Ast.Expr left = parseMultiplicativeExpression();
        while (match("+") || match("-")) {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expr right = parseMultiplicativeExpression();
            left = new Ast.Expr.Binary(operator, left, right);
        }
        return left;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expr parseMultiplicativeExpression() throws ParseException {
        Ast.Expr left = parsePrimaryExpression();
        while (match("*") || match("/")) {
            String operator = tokens.get(-1).getLiteral();
            Ast.Expr right = parsePrimaryExpression();
            left = new Ast.Expr.Binary(operator, left, right);
        }
        return left;
    }


    private String processEscapeSequences(String literal) {
        return literal
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\r", "\r")
                .replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\\\", "\\"); // Handle backslash itself
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expr parsePrimaryExpression() throws ParseException {
        // Existing cases
        if (match("TRUE")) {
            return new Ast.Expr.Literal(Boolean.TRUE);
        } else if (match("FALSE")) {
            return new Ast.Expr.Literal(Boolean.FALSE);
        } else if (match(Token.Type.DECIMAL)) {
            return new Ast.Expr.Literal(new BigDecimal(tokens.get(-1).getLiteral()));
        } else if (match(Token.Type.STRING)) {
            String literal = tokens.get(-1).getLiteral();
            literal = literal.substring(1, literal.length() - 1); // Removing quotes
            literal = processEscapeSequences(literal); // Process escape sequences
            return new Ast.Expr.Literal(literal);
        } else if (match(Token.Type.INTEGER)) {
            return new Ast.Expr.Literal(new BigInteger(tokens.get(-1).getLiteral()));
        } else if (match(Token.Type.CHARACTER)) { // Add this case for character literals
            String literal = tokens.get(-1).getLiteral();
            if (literal.length() == 3 && literal.startsWith("'") && literal.endsWith("'")) {
                char character = literal.charAt(1); // Extract the character between the single quotes
                return new Ast.Expr.Literal(character);
            } else {
                throw new ParseException("Invalid character literal", tokens.get(-1).getIndex());
            }
        } else if (match(Token.Type.IDENTIFIER)) {
            // Handle identifiers, which could be variable access or function calls.
            String name = tokens.get(-1).getLiteral();
            Ast.Expr expr = new Ast.Expr.Access(Optional.empty(), name);

            // Handle field access or function calls
            while (match(".")) {  // Check for field access
                Token fieldName = tokens.get(0);
                if (!match(Token.Type.IDENTIFIER)) {
                    throw new ParseException("Expected identifier after '.'", fieldName.getIndex());
                }

                // If followed by '(', it's a method call
                if (match("(")) {
                    List<Ast.Expr> arguments = new ArrayList<>();
                    if (!match(")")) {  // Function has arguments
                        arguments.add(parseExpression());
                        while (match(",")) {
                            arguments.add(parseExpression());
                        }
                        if (!match(")")) {
                            throw new ParseException("Expected closing parentheses", tokens.get(-1).getIndex());
                        }
                    }
                    return new Ast.Expr.Function(Optional.of(expr), fieldName.getLiteral(), arguments);
                }

                // Otherwise, it's a field access, not a function call
                expr = new Ast.Expr.Access(Optional.of(expr), fieldName.getLiteral());
            }

            // Check for a function call after the initial identifier (no field access)
            if (match("(")) {  // This indicates a function call
                List<Ast.Expr> arguments = new ArrayList<>();
                if (!match(")")) {  // Function has arguments
                    arguments.add(parseExpression());
                    while (match(",")) {
                        arguments.add(parseExpression());
                    }
                    if (!match(")")) {
                        throw new ParseException("Expected closing parentheses", tokens.get(-1).getIndex());
                    }
                }
                return new Ast.Expr.Function(Optional.empty(), name, arguments);
            }

            return expr;  // Return the Access expression if no function call
        } else if (match("(")) {
            // Handle grouped expressions (expressions within parentheses)
            Ast.Expr expression = parseExpression();
            if (!match(")")) {
                throw new ParseException("Unclosed expression", tokens.get(0).getIndex());
            }
            return new Ast.Expr.Group(expression);
        } else {
            throw new ParseException("Invalid primary expression token", tokens.get(0).getIndex());
        }
    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     * <p>
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) {

        for (int i = 0; i < patterns.length; i++) {
            if (!tokens.has(i)) {
                return false;
            } else if (patterns[i] instanceof Token.Type) {
                if (patterns[i] != tokens.get(i).getType()) {
                    return false;
                }
            } else if (patterns[i] instanceof String) {
                if (!patterns[i].equals(tokens.get(i).getLiteral())) {
                    return false;
                }
            } else {
                throw new AssertionError();
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {

        boolean peek = peek(patterns);
        if (peek) {
            for (int i = 0; i < patterns.length; i++) {
                tokens.advance();
            }
        }
        return peek;

    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}
