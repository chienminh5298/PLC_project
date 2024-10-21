package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Method method;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        boolean mainPresent = false;

        // Visit each field
        ast.getFields().forEach(this::visit);

        // Visit each method
        ast.getMethods().forEach(this::visit);

        // Check if the 'main' method is present with proper arguments
        mainPresent = ast.getMethods().stream()
                .anyMatch(method -> method.getName().equals("main")
                        && method.getReturnTypeName().get().equals("Integer")
                        && method.getParameters().isEmpty());

        if (!mainPresent) {
            throw new RuntimeException("No main method with proper arguments!");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        try {
            if (ast.getValue().isPresent()) {
                // Visit the value if present and validate the type
                visit(ast.getValue().get());
                requireAssignable(Environment.getType(ast.getTypeName()), ast.getValue().get().getType());
                scope.defineVariable(ast.getName(), ast.getName(), ast.getValue().get().getType(), Environment.NIL);
            } else {
                // Define the variable directly if no value is present
                scope.defineVariable(ast.getName(), ast.getName(), Environment.getType(ast.getTypeName()), Environment.NIL);
            }

            // Set the variable in the AST
            ast.setVariable(scope.lookupVariable(ast.getName()));
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }

        return null;
    }


    @Override
    public Void visit(Ast.Method ast) {
        try {
            // Define returnType for Ast.Expr.Return
            Environment.Type returnType = ast.getReturnTypeName()
                    .map(Environment::getType)
                    .orElse(Environment.Type.NIL);
            scope.defineVariable("returnType", "returnType", returnType, Environment.NIL);

            // Handle parameter types
            List<String> paramStrings = ast.getParameterTypeNames();
            Environment.Type[] paramTypes = paramStrings.stream()
                    .map(Environment::getType)
                    .toArray(Environment.Type[]::new);

            scope.defineFunction(ast.getName(), ast.getName(), Arrays.asList(paramTypes), returnType, args -> Environment.NIL);

            // Visit each statement within the method
            for (Ast.Stmt statement : ast.getStatements()) {
                try {
                    scope = new Scope(scope);  // Create new scope for each statement
                    visit(statement);
                } finally {
                    scope = scope.getParent(); // Restore the previous scope
                }
            }

            // Set the function in the AST
            ast.setFunction(scope.lookupFunction(ast.getName(), ast.getParameters().size()));
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }

        return null;
    }


    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        // Visit the expression
        visit(ast.getExpression());

        try {
            // Check if the expression is not a function
            if (!(ast.getExpression() instanceof Ast.Expr.Function)) {
                throw new RuntimeException("Not function type!");
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }

        return null;
    }


    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        try {
            if (ast.getValue().isPresent()) {
                // Visit the value if present and define the variable with its type
                visit(ast.getValue().get());
                scope.defineVariable(ast.getName(), ast.getName(), ast.getValue().get().getType(), Environment.NIL);
            } else {
                // Define the variable with the specified type if no value is present
                scope.defineVariable(ast.getName(), ast.getName(), Environment.getType(ast.getTypeName().get()), Environment.NIL);
            }

            // Set the variable in the AST
            ast.setVariable(scope.lookupVariable(ast.getName()));
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }

        return null;
    }


    @Override
    public Void visit(Ast.Stmt.Assignment ast) {
        try {
            // Check if the receiver is not an instance of Ast.Expr.Access
            if (!(ast.getReceiver() instanceof Ast.Expr.Access)) {
                throw new RuntimeException("Not Access!");
            }

            // Visit the value and the receiver expressions
            visit(ast.getValue());
            visit(ast.getReceiver());

            // Ensure the types are assignable
            requireAssignable(ast.getReceiver().getType(), ast.getValue().getType());
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        try {
            // Ensure there is at least one "then" statement
            if (ast.getThenStatements().isEmpty()) {
                throw new RuntimeException("No then statement!");
            }

            // Visit the condition and ensure it is a boolean expression
            visit(ast.getCondition());
            requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());

            // Visit the "else" statements, if any
            for (Ast.Stmt elseStmt : ast.getElseStatements()) {
                try {
                    scope = new Scope(scope);
                    visit(elseStmt);
                } finally {
                    scope = scope.getParent();
                }
            }

            // Visit the "then" statements
            for (Ast.Stmt thenStmt : ast.getThenStatements()) {
                try {
                    scope = new Scope(scope);
                    visit(thenStmt);
                } finally {
                    scope = scope.getParent();
                }
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }

        return null;
    }


    @Override
    public Void visit(Ast.Stmt.For ast) {
        try {
            // Ensure the "for" loop has statements
            if (ast.getStatements().isEmpty()) {
                throw new RuntimeException("No statements!");
            }

            // Visit the iterable value and ensure it is of type INTEGER_ITERABLE
            visit(ast.getValue());
            requireAssignable(Environment.Type.INTEGER_ITERABLE, ast.getValue().getType());

            // For each statement in the loop
            for (Ast.Stmt stmt : ast.getStatements()) {
                try {
                    scope = new Scope(scope);
                    // Define the loop variable within the new scope
                    scope.defineVariable(ast.getName(), ast.getName(), Environment.Type.INTEGER, Environment.NIL);
                    visit(stmt);
                } finally {
                    scope = scope.getParent();
                }
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }

        return null;
    }


    @Override
    public Void visit(Ast.Stmt.While ast) {
        try {
            // Visit the condition and ensure it is a boolean expression
            visit(ast.getCondition());
            requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());

            // Create a new scope and visit each statement in the "while" body
            try {
                scope = new Scope(scope);
                for (Ast.Stmt stmt : ast.getStatements()) {
                    visit(stmt);
                }
            } finally {
                scope = scope.getParent(); // Restore the previous scope
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }

        return null;
    }


    @Override
    public Void visit(Ast.Stmt.Return ast) {
        try {
            // Visit the return value expression
            visit(ast.getValue());

            // Look up the return type variable from the scope
            Environment.Variable returnTypeVar = scope.lookupVariable("returnType");

            // Ensure the return value type matches the expected return type
            requireAssignable(returnTypeVar.getType(), ast.getValue().getType());
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }

        return null;
    }


    @Override
    public Void visit(Ast.Expr.Literal ast) {
        try {
            Object literal = ast.getLiteral();

            if (literal instanceof String) {
                ast.setType(Environment.Type.STRING);
            } else if (literal instanceof Character) {
                ast.setType(Environment.Type.CHARACTER);
            } else if (literal == Environment.NIL) {
                ast.setType(Environment.Type.NIL);
            } else if (literal instanceof Boolean) {
                ast.setType(Environment.Type.BOOLEAN);
            } else if (literal instanceof BigInteger) {
                BigInteger temp = (BigInteger) literal;
                if (temp.intValueExact() > Integer.MAX_VALUE || temp.intValueExact() < Integer.MIN_VALUE) {
                    throw new RuntimeException("Integer outside range");
                }
                ast.setType(Environment.Type.INTEGER);
            } else if (literal instanceof BigDecimal) {
                BigDecimal temp = (BigDecimal) literal;
                if (temp.doubleValue() > Double.MAX_VALUE || temp.doubleValue() < Double.MIN_VALUE) {
                    throw new RuntimeException("Decimal outside range");
                }
                ast.setType(Environment.Type.DECIMAL);
            } else {
                throw new RuntimeException("Type doesn't exist");
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }

        return null;
    }


    @Override
    public Void visit(Ast.Expr.Group ast) {
        try {
            // Visit the inner expression
            visit(ast.getExpression());

            // Check if the expression is of binary type
            if (!(ast.getExpression() instanceof Ast.Expr.Binary)) {
                throw new RuntimeException("Not binary type!");
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }

        return null;
    }


    @Override
    public Void visit(Ast.Expr.Binary ast) {
        try {
            String op = ast.getOperator();

            // Visit both left and right expressions
            visit(ast.getLeft());
            visit(ast.getRight());

            // Handle logical operators (AND, OR)
            if (op.equals("AND") || op.equals("OR")) {
                requireAssignable(Environment.Type.BOOLEAN, ast.getLeft().getType());
                requireAssignable(Environment.Type.BOOLEAN, ast.getRight().getType());
                ast.setType(Environment.Type.BOOLEAN);
            }
            // Handle comparison operators
            else if (op.equals("<") || op.equals("<=") || op.equals(">") || op.equals(">=") || op.equals("==") || op.equals("!=")) {
                requireAssignable(Environment.Type.COMPARABLE, ast.getLeft().getType());
                requireAssignable(Environment.Type.COMPARABLE, ast.getRight().getType());
                ast.setType(Environment.Type.BOOLEAN);
            }
            // Handle addition
            else if (op.equals("+")) {
                if (ast.getLeft().getType() == Environment.Type.STRING || ast.getRight().getType() == Environment.Type.STRING) {
                    ast.setType(Environment.Type.STRING);
                } else if ((ast.getLeft().getType() == Environment.Type.INTEGER || ast.getLeft().getType() == Environment.Type.DECIMAL) &&
                        ast.getLeft().getType() == ast.getRight().getType()) {
                    ast.setType(ast.getLeft().getType());
                } else {
                    throw new RuntimeException("Not right types for +");
                }
            }
            // Handle subtraction, multiplication, and division
            else if (op.equals("-") || op.equals("*") || op.equals("/")) {
                if ((ast.getLeft().getType() == Environment.Type.INTEGER || ast.getLeft().getType() == Environment.Type.DECIMAL) &&
                        ast.getLeft().getType() == ast.getRight().getType()) {
                    ast.setType(ast.getLeft().getType());
                } else {
                    throw new RuntimeException("Not right types for *, -, /");
                }
            }
            // Handle unexpected operators
            else {
                throw new RuntimeException("Not right types for Binary");
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }

        return null;
    }


    @Override
    public Void visit(Ast.Expr.Access ast) {
        try {
            if (ast.getReceiver().isPresent()) {
                // If the receiver is present, resolve its variable and lookup the appropriate scope
                Ast.Expr.Access receiver = (Ast.Expr.Access) ast.getReceiver().get();
                receiver.setVariable(scope.lookupVariable(receiver.getName()));

                try {
                    // Set the current scope to the receiver's type scope and lookup the variable
                    scope = receiver.getVariable().getType().getScope();
                    ast.setVariable(scope.lookupVariable(ast.getName()));
                } finally {
                    scope = scope.getParent(); // Restore the previous scope
                }
            } else {
                // If no receiver, simply lookup the variable in the current scope
                ast.setVariable(scope.lookupVariable(ast.getName()));
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }

        return null;
    }


    @Override
    public Void visit(Ast.Expr.Function ast) {
        try {
            if (ast.getReceiver().isPresent()) {
                // Visit the receiver and cast it as an Access expression
                visit(ast.getReceiver().get());
                Ast.Expr.Access receiver = (Ast.Expr.Access) ast.getReceiver().get();

                // Retrieve the parameter types from the receiver's method
                List<Environment.Type> params = scope.lookupVariable(receiver.getName())
                        .getType()
                        .getMethod(ast.getName(), ast.getArguments().size())
                        .getParameterTypes();

                // Visit each argument and ensure types are assignable
                for (int i = 0; i < ast.getArguments().size(); i++) {
                    visit(ast.getArguments().get(i));
                    requireAssignable(params.get(i + 1), ast.getArguments().get(i).getType());
                }

                // Set the function in the AST
                ast.setFunction(scope.lookupVariable(receiver.getName())
                        .getType()
                        .getMethod(ast.getName(), ast.getArguments().size()));
            } else {
                // Retrieve the parameter types from the scope's function
                List<Environment.Type> params = scope.lookupFunction(ast.getName(), ast.getArguments().size())
                        .getParameterTypes();

                // Visit each argument and ensure types are assignable
                for (int i = 0; i < ast.getArguments().size(); i++) {
                    visit(ast.getArguments().get(i));
                    requireAssignable(params.get(i), ast.getArguments().get(i).getType());
                }

                // Set the function in the AST
                ast.setFunction(scope.lookupFunction(ast.getName(), ast.getArguments().size()));
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }

        return null;
    }


    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        if (target != type && target != Environment.Type.ANY && target != Environment.Type.COMPARABLE) {
            throw new RuntimeException("Not matching types: " + target + " and " + type);
        }
    }


}
