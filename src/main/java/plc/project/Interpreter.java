package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.math.RoundingMode;
import java.util.stream.IntStream;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        ast.getFields().forEach(this::visit);
        ast.getMethods().forEach(this::visit);

        return Optional.ofNullable(scope.lookupFunction("main", 0))
                .orElseThrow(() -> new RuntimeException("Main function not found"))
                .invoke(Collections.emptyList());
    }

    @Override
    public Environment.PlcObject visit(Ast.Field ast) {
        Environment.PlcObject value = ast.getValue().isPresent() ? visit(ast.getValue().get()) : Environment.NIL;
        scope.defineVariable(ast.getName(), value);
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Method ast) {
        scope.defineFunction(ast.getName(), ast.getParameters().size(), args -> {
            Scope previousScope = scope; // Store the previous scope
            Scope methodScope = new Scope(previousScope); // Create a new scope for the method
            scope = methodScope; // Set the scope to the method scope
            try {
                // Define arguments in the new method scope
                for (int i = 0; i < args.size(); i++) {
                    scope.defineVariable(ast.getParameters().get(i), args.get(i));
                }

                // Evaluate each statement within the method's body
                ast.getStatements().forEach(this::visit);
            } catch (Return returnValue) {
                return returnValue.value; // Return the value from the method
            } finally {
                scope = previousScope; // Restore the previous scope
            }
            return Environment.NIL; // Return NIL if no return statement is executed
        });
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Expression ast) {
        visit(ast.getExpression());
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Declaration ast) {
        Environment.PlcObject value = ast.getValue().isPresent() ? visit(ast.getValue().get()) : Environment.NIL;
        scope.defineVariable(ast.getName(), value);
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Assignment ast) {
        if (!(ast.getReceiver() instanceof Ast.Expr.Access)) {
            throw new RuntimeException("Invalid assignment target");
        }
        Ast.Expr.Access access = (Ast.Expr.Access) ast.getReceiver();
        Environment.PlcObject value = visit(ast.getValue());
        if (access.getReceiver().isPresent()) {
            visit(access.getReceiver().get()).setField(access.getName(), value);
        } else {
            scope.lookupVariable(access.getName()).setValue(value);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.If ast) {
        Environment.PlcObject condition = visit(ast.getCondition());
        requireType(Boolean.class, condition);
        if ((Boolean) condition.getValue()) {
            for (Ast.Stmt statement : ast.getThenStatements()) {
                visit(statement);
            }
        } else {
            for (Ast.Stmt statement : ast.getElseStatements()) {
                visit(statement);
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.For ast) {
        Optional.ofNullable(requireType(Iterable.class, visit(ast.getValue())))
                .ifPresent(value -> value.forEach(plcObject -> {
                    scope = new Scope(scope);
                    try {
                        scope.defineVariable(ast.getName(), (Environment.PlcObject) plcObject);
                        ast.getStatements().forEach(this::visit);
                    } finally {
                        scope = scope.getParent();
                    }
                }));
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.While ast) {
        // Use the result of requireType directly as a Boolean
        while (Boolean.TRUE.equals(requireType(Boolean.class, visit(ast.getCondition())))) {
            scope = new Scope(scope);
            try {
                ast.getStatements().forEach(this::visit);
            } finally {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }



    @Override
    public Environment.PlcObject visit(Ast.Stmt.Return ast) {
        throw new Return(visit(ast.getValue()));
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Literal ast) {
        return Optional.ofNullable(ast.getLiteral())
                .map(Environment::create)
                .orElse(Environment.NIL);
    }


    @Override
    public Environment.PlcObject visit(Ast.Expr.Group ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Binary ast) {
        Environment.PlcObject left = visit(ast.getLeft());

        switch (ast.getOperator()) {
            case "+":
                return handleAddition(left, visit(ast.getRight()));
            case "-":
                return handleSubtraction(left, visit(ast.getRight()));
            case "*":
                return handleMultiplication(left, visit(ast.getRight()));
            case "/":
                return handleDivision(left, visit(ast.getRight()));
            case "AND":
                return handleAnd(left, visit(ast.getRight()));
            case "OR":
                // Pass right expression (Ast.Expr) to handleOr for short-circuit evaluation
                return handleOr(left, ast.getRight());
            case "==":
                return Environment.create(Objects.equals(left.getValue(), visit(ast.getRight()).getValue()));
            case "!=":
                return Environment.create(!Objects.equals(left.getValue(), visit(ast.getRight()).getValue()));
            case "<":
                return handleComparison(left, visit(ast.getRight()), "<");
            case "<=":
                return handleComparison(left, visit(ast.getRight()), "<=");
            case ">":
                return handleComparison(left, visit(ast.getRight()), ">");
            case ">=":
                return handleComparison(left, visit(ast.getRight()), ">=");
            default:
                return Environment.NIL;
        }
    }


    private Environment.PlcObject handleAddition(Environment.PlcObject left, Environment.PlcObject right) {
        if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) {
            return Environment.create(((BigInteger) left.getValue()).add((BigInteger) right.getValue()));
        } else if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal) {
            return Environment.create(((BigDecimal) left.getValue()).add((BigDecimal) right.getValue()));
        } else if (left.getValue() instanceof String || right.getValue() instanceof String) {
            return Environment.create(left.getValue().toString() + right.getValue().toString());
        }
        throw new RuntimeException("Invalid types for addition");
    }

    private Environment.PlcObject handleSubtraction(Environment.PlcObject left, Environment.PlcObject right) {
        if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) {
            return Environment.create(((BigInteger) left.getValue()).subtract((BigInteger) right.getValue()));
        } else if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal) {
            return Environment.create(((BigDecimal) left.getValue()).subtract((BigDecimal) right.getValue()));
        }
        throw new RuntimeException("Invalid types for subtraction");
    }

    private Environment.PlcObject handleMultiplication(Environment.PlcObject left, Environment.PlcObject right) {
        if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) {
            return Environment.create(((BigInteger) left.getValue()).multiply((BigInteger) right.getValue()));
        } else if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal) {
            return Environment.create(((BigDecimal) left.getValue()).multiply((BigDecimal) right.getValue()));
        }
        throw new RuntimeException("Invalid types for multiplication");
    }

    private Environment.PlcObject handleDivision(Environment.PlcObject left, Environment.PlcObject right) {
        if (left.getValue() instanceof BigInteger && right.getValue() instanceof BigInteger) {
            BigInteger rightValue = (BigInteger) right.getValue();
            if (rightValue.equals(BigInteger.ZERO)) {
                throw new RuntimeException("Division by zero");
            }
            return Environment.create(((BigInteger) left.getValue()).divide(rightValue));
        } else if (left.getValue() instanceof BigDecimal && right.getValue() instanceof BigDecimal) {
            BigDecimal rightValue = (BigDecimal) right.getValue();
            if (rightValue.equals(BigDecimal.ZERO)) {
                throw new RuntimeException("Division by zero");
            }
            return Environment.create(((BigDecimal) left.getValue()).divide(rightValue, RoundingMode.HALF_EVEN));
        }
        throw new RuntimeException("Invalid types for division");
    }

    private Environment.PlcObject handleAnd(Environment.PlcObject left, Environment.PlcObject right) {
        if (left.getValue() instanceof Boolean && !(Boolean) left.getValue()) {
            return Environment.create(false);
        }
        if (right.getValue() instanceof Boolean && !(Boolean) right.getValue()) {
            return Environment.create(false);
        }
        if (left.getValue() instanceof Boolean && right.getValue() instanceof Boolean) {
            return Environment.create(true);
        }
        throw new RuntimeException("Invalid types for AND operation");
    }

    private Environment.PlcObject handleOr(Environment.PlcObject left, Ast.Expr rightExpr) {
        // Short-circuit evaluation: if the left-hand side is true, return true immediately
        if (left.getValue() instanceof Boolean && (Boolean) left.getValue()) {
            return Environment.create(true);
        }

        // If left-hand side is false, evaluate the right-hand side
        Environment.PlcObject right = visit(rightExpr);
        if (right.getValue() instanceof Boolean) {
            return Environment.create((Boolean) right.getValue());
        }

        throw new RuntimeException("Invalid types for OR operation");
    }


    private Environment.PlcObject handleComparison(Environment.PlcObject left, Environment.PlcObject right, String operator) {
        if (left.getValue() instanceof Comparable && left.getValue().getClass().equals(right.getValue().getClass())) {
            int comparisonResult = ((Comparable) left.getValue()).compareTo(right.getValue());
            switch (operator) {
                case "<":
                    return Environment.create(comparisonResult < 0);
                case "<=":
                    return Environment.create(comparisonResult <= 0);
                case ">":
                    return Environment.create(comparisonResult > 0);
                case ">=":
                    return Environment.create(comparisonResult >= 0);
            }
        }
        throw new RuntimeException("Invalid types for comparison operation");
    }


    @Override
    public Environment.PlcObject visit(Ast.Expr.Access ast) {

        if (ast.getReceiver().isPresent()) {
            return visit(ast.getReceiver().get()).getField(ast.getName()).getValue();
        }
        return scope.lookupVariable(ast.getName()).getValue();
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Function ast) {
        // Convert args
        List<Environment.PlcObject> arguments = new ArrayList<>();
        for (Ast.Expr argument : ast.getArguments()) {
            arguments.add(visit(argument));
        }

        if (!ast.getReceiver().isPresent()) {
            // Is a function
            Environment.Function function = scope.lookupFunction(ast.getName(), ast.getArguments().size());
            return function.invoke(arguments);
        } else {
            // Is a Method
            Environment.PlcObject obj = visit(ast.getReceiver().get());
            return obj.callMethod(ast.getName(), arguments);
        }

    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
