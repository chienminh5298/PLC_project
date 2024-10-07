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
        Ast.Stmt.Declaration declaration = parseDeclarationStatement();

        // Check if the declaration has a type
        if (declaration instanceof Ast.Stmt.Declaration) {
            return new Ast.Field(declaration.getName(), declaration.getValue());
        } else {
            throw new ParseException("Invalid field declaration", tokens.get(0).getIndex());
        }
    }


    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */

    public Ast.Method parseMethod() throws ParseException {
        if (match(Token.Type.IDENTIFIER)) {
            String functionName = tokens.get(-1).getLiteral();

            if (match("(")) {
                List<String> params = new ArrayList<>();
                List<String> paramTypes = new ArrayList<>();

                // get all params
                while (match(Token.Type.IDENTIFIER)) {
                    params.add(tokens.get(-1).getLiteral());

                    if (match(":", Token.Type.IDENTIFIER)) {
                        // Type is required
                        paramTypes.add(tokens.get(-1).getLiteral());
                    } else {
                        // Type not found, throw ParseException directly
                        throw new ParseException("Type not found while parsing method parameters", tokens.get(0).getIndex());
                    }

                    if (!match(",")) {
                        if (!peek(")")) {
                            throw new ParseException("Expected comma between identifiers", tokens.get(0).getIndex());
                        }
                    }
                }

                // check for closing parenthesis
                if (!match(")")) {
                    throw new ParseException("Expected Parenthesis", tokens.get(0).getIndex());
                }

                Optional<String> returnType = Optional.empty();
                // check for return type
                if (match(":", Token.Type.IDENTIFIER)) {
                    returnType = Optional.of(tokens.get(-1).getLiteral());
                }

                // check for DO
                if (!match("DO")) {
                    throw new ParseException("Expected DO statement", tokens.get(0).getIndex());
                }

                // get all statements
                List<Ast.Stmt> statements = new ArrayList<>();
                while (!match("END") && tokens.has(0)) {
                    statements.add(parseStatement());
                }

                if (!tokens.get(-1).getLiteral().equals("END")) {
                    throw new ParseException("Missing END", tokens.get(-1).getIndex());
                }

                // Return only the parameters accepted by Ast.Method constructor
                return new Ast.Method(functionName, params, statements);
            } else {
                throw new ParseException("Expected Parenthesis", tokens.get(0).getIndex());
            }
        } else {
            throw new ParseException("Expected Identifier", tokens.get(0).getIndex());
        }
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
        // Parse the condition expression for the IF statement
        Ast.Expr condition = parseExpression();

        // Check for the 'DO' keyword to start the 'IF' statement body
        if (match("DO")) {
            boolean isElse = false; // Flag to track if 'ELSE' block is encountered
            List<Ast.Stmt> doStatements = new ArrayList<>(); // Statements in the 'DO' block
            List<Ast.Stmt> elseStatements = new ArrayList<>(); // Statements in the 'ELSE' block

            // Parse the statements inside the 'DO' and 'ELSE' blocks
            while (!match("END") && tokens.has(0)) {
                // Check for 'ELSE' keyword
                if (match("ELSE")) {
                    // If 'ELSE' is encountered after another 'ELSE', throw an error
                    if (!isElse) {
                        isElse = true;
                    } else {
                        throw new ParseException("Too many 'ELSE' statements", tokens.get(0).getIndex());
                    }
                }

                // Add statements to the correct block based on the 'isElse' flag
                if (isElse) {
                    elseStatements.add(parseStatement());
                } else {
                    doStatements.add(parseStatement());
                }
            }

            // Ensure the 'IF' statement ends with 'END'
            if (!tokens.get(-1).getLiteral().equals("END")) {
                throw new ParseException("Missing 'END' to close the 'IF' statement", tokens.get(-1).getIndex());
            }

            // Return the 'IF' statement node with the condition, 'DO' statements, and optional 'ELSE' statements
            return new Ast.Stmt.If(condition, doStatements, elseStatements);
        }

        // If 'DO' keyword is not found, throw an error
        throw new ParseException("Expected 'DO' to start the 'IF' statement body", tokens.get(0).getIndex());
    }


    /**
     * Parses a for statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a for statement, aka
     * {@code FOR}.
     */
    public Ast.Stmt.For parseForStatement() throws ParseException {
        // Check for loop variable identifier
        if (match(Token.Type.IDENTIFIER)) {
            String name = tokens.get(-1).getLiteral();

            // Check for 'IN' keyword
            if (!match("IN")) {
                throw new ParseException("Expected 'IN' after loop variable", tokens.get(0).getIndex());
            }

            // Parse the expression for the iterable part
            Ast.Expr expression = parseExpression();

            // Check for 'DO' keyword before the loop body
            if (!match("DO")) {
                throw new ParseException("Expected 'DO' to start the 'FOR' loop body", tokens.get(0).getIndex());
            }

            // Parse the statements inside the 'FOR' loop body
            List<Ast.Stmt> statements = new ArrayList<>();
            while (!match("END") && tokens.has(0)) {
                statements.add(parseStatement());
            }

            // Ensure the loop ends with 'END'
            if (!tokens.get(-1).getLiteral().equals("END")) {
                throw new ParseException("Missing 'END' to close the 'FOR' loop body", tokens.get(-1).getIndex());
            }

            // Return the 'FOR' statement node with the loop variable, iterable expression, and body statements
            return new Ast.Stmt.For(name, expression, statements);
        }

        // If no loop variable identifier found, throw an exception
        throw new ParseException("Expected loop variable identifier", tokens.get(0).getIndex());
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
            if (!tokens.has(0)) {
                throw new ParseException("Expected 'END' but found end of input.", tokens.get(-1).getIndex());
            }
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
        try {
            return parseLogicalExpression();
        } catch (ParseException exception) {
            throw new ParseException(exception.getMessage(), exception.getIndex());
        }
    }

    public Ast.Expr parseLogicalExpression() throws ParseException {
        try {
            Ast.Expr currentExpr = parseEqualityExpression();
            while (match("AND") || match("OR")) { // Check for logical operators
                String operator = tokens.get(-1).getLiteral();
                if (!tokens.has(0)) { // Ensure there is a following operand
                    throw new ParseException("Operand missing for logical operator", tokens.index);
                }
                Ast.Expr rightOperand = parseEqualityExpression();
                currentExpr = new Ast.Expr.Binary(operator, currentExpr, rightOperand);
            }
            return currentExpr;
        } catch (ParseException exception) {
            throw new ParseException(exception.getMessage(), exception.getIndex());
        }
    }

    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expr parseEqualityExpression() throws ParseException {
        Ast.Expr left = parseAdditiveExpression();

        while (peek(">=") || peek("<") || peek("==") || peek("<=") || peek(">") || peek("!=")) {
            String operator = tokens.get(0).getLiteral();

            match(Token.Type.OPERATOR);

            Ast.Expr right = parseAdditiveExpression();

            if (!peek(">=") && !peek("<") && !peek("==") && !peek("<=") && !peek(">") && !peek("!=")) {
                return new Ast.Expr.Binary(operator, left, right);
            } else {
                left = new Ast.Expr.Binary(operator, left, right);
            }
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

            if (!tokens.has(0)) {
                throw new ParseException("Expected right operand after '" + operator + "'", tokens.get(-1).getIndex());
            }

            Ast.Expr right = parseMultiplicativeExpression();
            left = new Ast.Expr.Binary(operator, left, right);
        }

        return left;
    }


    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expr parseMultiplicativeExpression() throws ParseException {
        Ast.Expr output = parseSecondaryExpression();

        while (match("/") || match("*")) {
            String operation = tokens.get(-1).getLiteral();

            if (!tokens.has(0)) {
                throw new ParseException("Expected right operand after '" + operation + "'", tokens.get(-1).getIndex());
            }

            Ast.Expr rightExpr = parseSecondaryExpression();
            output = new Ast.Expr.Binary(operation, output, rightExpr);
        }

        return output;
    }

    public Ast.Expr parseSecondaryExpression() throws ParseException {
        Ast.Expr initialExpr = parsePrimaryExpression();

        while (match(".")) {
            if (!match(Token.Type.IDENTIFIER)) {
                throw new ParseException("Invalid Identifier", tokens.get(0).getIndex());
            }

            String receiver = tokens.get(-1).getLiteral();

            if (!match("(")) {
                initialExpr = new Ast.Expr.Access(Optional.of(initialExpr), receiver);
            } else {
                List<Ast.Expr> args = new ArrayList<>();
                if (!match(")")) {
                    args.add(parseExpression());
                    while (match(",")) {
                        args.add(parseExpression());
                    }
                    if (!match(")")) {
                        throw new ParseException("Invalid function: closing parentheses not found", tokens.get(0).getIndex());
                    }
                }
                initialExpr = new Ast.Expr.Function(Optional.of(initialExpr), receiver, args);
            }
        }

        return initialExpr;
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
        if (match("NIL")) {
            return new Ast.Expr.Literal(null);
        } else if (match("TRUE")) {
            return new Ast.Expr.Literal(true);
        } else if (match("FALSE")) {
            return new Ast.Expr.Literal(false);
        } else if (match(Token.Type.INTEGER)) { // INTEGER LITERAL FOUND
            return new Ast.Expr.Literal(new BigInteger(tokens.get(-1).getLiteral()));
        } else if (match(Token.Type.DECIMAL)) { // DECIMAL LITERAL FOUND
            return new Ast.Expr.Literal(new BigDecimal(tokens.get(-1).getLiteral()));
        } else if (match(Token.Type.CHARACTER)) { // CHARACTER LITERAL FOUND
            String str = tokens.get(-1).getLiteral();
//            str = str.replace("\\n", "\n")
//                    .replace("\\t", "\t")
//                    .replace("\\b", "\b")
//                    .replace("\\r", "\r")
//                    .replace("\\'", "'")
//                    .replace("\\\\", "\\")
//                    .replace("\\\"", "\"");
//            if(str.contains("\n") || str.contains("\t") || str.contains("\b") || str.contains("\r") || str.contains("\\") || str.contains("\"") || str.charAt(1) == '\'')
//                return new Ast.Expr.Literal(str)
            return new Ast.Expr.Literal(str.charAt(1));
        } else if (match(Token.Type.STRING)) { // STRING LITERAL FOUND
            String str = tokens.get(-1).getLiteral();
            str = str.substring(1, str.length() - 1);
            if (str.contains("\\")) {
                str = str.replace("\\n", "\n")
                        .replace("\\t", "\t")
                        .replace("\\b", "\b")
                        .replace("\\r", "\r")
                        .replace("\\'", "'")
                        .replace("\\\\", "\\")
                        .replace("\\\"", "\"");
            }
            return new Ast.Expr.Literal(str);
        } else if (match(Token.Type.IDENTIFIER)) { // IDENTIFIER FOUND
            String name = tokens.get(-1).getLiteral();
            if (!match("(")) { // no expression after identifier
                return new Ast.Expr.Access(Optional.empty(), name);
            } else { // expression after identifier
                if (!match(")")) { // expression arguments found
                    Ast.Expr initalExpr = parseExpression();
                    List<Ast.Expr> args = new ArrayList<>();
                    args.add(initalExpr);

                    while (match(",")) {
                        args.add(parseExpression());
                    }

                    if (match(")")) { // Check closing parentheses
                        return new Ast.Expr.Function(Optional.empty(), name, args);
                    } else {
                        throw new ParseException("Closing parentheses expected", tokens.get(-1).getIndex());
                    }
                } else {
                    if (!tokens.get(-1).getLiteral().equals(")")) {
                        throw new ParseException("Closing parentheses expected", tokens.get(-1).getIndex());
                    } else {
                        return new Ast.Expr.Function(Optional.empty(), name, Collections.emptyList());
                    }
                }


            }

        } else if (match("(")) {
            Ast.Expr expr = parseExpression();
            if (!match(")")) {
                throw new ParseException("Expected closing parenthesis", tokens.get(-1).getIndex());
            }
            return new Ast.Stmt.Expr.Group(expr);
        } else {
            throw new ParseException("Invalid Primary Expression", tokens.get(-1).getIndex());
            // TODO: handle storing the actual character index instead of I
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
                throw new AssertionError("Unexpected pattern: " + patterns[i]);
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        if (peek(patterns)) {
            for (int i = 0; i < patterns.length; i++) {
                tokens.advance();
            }
            return true;
        }
        return false;
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
