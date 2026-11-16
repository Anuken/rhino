/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import rhino.ast.AbstractObjectProperty;
import rhino.ast.ArrayLiteral;
import rhino.ast.Assignment;
import rhino.ast.AstNode;
import rhino.ast.ClassNode;
import rhino.ast.FunctionCall;
import rhino.ast.FunctionNode;
import rhino.ast.Name;
import rhino.ast.ObjectLiteral;
import rhino.ast.ObjectProperty;
import rhino.ast.ParenthesizedExpression;
import rhino.ast.Scope;
import rhino.ast.ScriptNode;
import rhino.ast.Spread;
import rhino.ast.SpreadObjectProperty;
import rhino.ast.Symbol;
import rhino.ast.VariableInitializer;

/**
 * Finds the references to let, const and class bindings that may run before the binding is
 * initialized, so that only those check for the temporal dead zone. IRFactory reports references
 * and declarations as it transforms them; {@link #finish} then marks the references that need a
 * check with {@link Node#TDZ_CHECK_PROP}, and their symbols as {@link Symbol#isTdzChecked}.
 *
 * <p>A reference needs no check when it is known to run after the declaration: it is in the same
 * function and textually after the end of the declaration, or it is in a function created there. A
 * scope is only ever entered at its start, so a loop cannot run a reference before the
 * declarations that precede it, but a switch can jump past a declaration into another case.
 * Function declarations are created on entry to their scope, before anything is initialized.
 */
final class TdzAnalysis {
    private static final class Reference {
        final Node node;
        final Symbol symbol;
        // position from which the reference can run, or -1 if it may run at any time
        final int position;

        Reference(Node node, Symbol symbol, int position) {
            this.node = node;
            this.symbol = symbol;
            this.position = position;
        }
    }

    private final List<Reference> references = new ArrayList<>();
    // Declarations without an initializer: let x;
    private final Map<Node, Symbol> uninitialized = new IdentityHashMap<>();
    private final List<Symbol> declared = new ArrayList<>();
    // Whether direct eval can reference any binding at any time
    private boolean hasEval;
    // Functions that cannot be called before a position, such as a function initializing a binding
    private final Map<FunctionNode, Integer> uncalledUntil = new IdentityHashMap<>();
    // Methods, field initializers and constructors, with the class whose body they are part of
    private final Map<FunctionNode, ClassNode> classBodies = new IdentityHashMap<>();

    /** Records a reference to a name in the given scope of the given function. */
    void reference(Name name, Scope scope, ScriptNode function) {
        if (scope == null) {
            return;
        }
        AstNode parent = name.getParent();
        if (parent instanceof VariableInitializer
                && ((VariableInitializer) parent).getTarget() == name) {
            return;
        }
        String id = name.getIdentifier();
        if ("eval".equals(id)
                && parent instanceof FunctionCall
                && ((FunctionCall) parent).getTarget() == name) {
            hasEval = true;
        }
        Scope defining = definingScope(scope, id);
        if (defining == null) {
            return;
        }
        Symbol symbol = defining.getSymbol(id);
        if (symbol == null
                || (symbol.getDeclType() != Symbol.Type.LET
                        && symbol.getDeclType() != Symbol.Type.CONST)) {
            return;
        }
        ScriptNode declaring = functionOf(symbol.getContainingTable());
        int position;
        if (function == declaring) {
            position = name.getAbsolutePosition();
        } else {
            // The outermost function within the declaring one decides when this can run
            ScriptNode outermost = function;
            for (; ; ) {
                ScriptNode enclosing = enclosingFunction(outermost);
                if (enclosing == declaring || enclosing == null) {
                    break;
                }
                outermost = enclosing;
            }
            if (!(outermost instanceof FunctionNode) || enclosingFunction(outermost) == null) {
                position = -1;
            } else {
                FunctionNode fn = (FunctionNode) outermost;
                if (classBodies.get(fn) == symbol.getContainingTable()) {
                    // A class body cannot run before the class's inner binding is initialized
                    return;
                }
                if (fn.getFunctionType() == FunctionNode.FUNCTION_STATEMENT) {
                    position = -1;
                } else {
                    position = fn.getAbsolutePosition();
                    Integer until = uncalledUntil.get(fn);
                    if (until != null && until > position) {
                        position = until;
                    }
                }
            }
        }
        references.add(new Reference(name, symbol, position));
    }

    /**
     * Records the declaration of a binding, initialized from {@code position} on. For a binding
     * declared in a case clause, {@code caseEnd} is the end of the clause, otherwise -1.
     */
    void declare(Scope scope, String name, int position, int caseEnd) {
        Scope defining = scope.getDefiningScope(name);
        if (defining == null) {
            return;
        }
        Symbol symbol = defining.getSymbol(name);
        if (symbol != null && symbol.getInitPosition() < 0) {
            symbol.setInitPosition(position);
            symbol.setCaseEnd(caseEnd);
            declared.add(symbol);
        }
    }

    /** Records a let declaration without an initializer, which must then store undefined. */
    void declareWithoutInitializer(Scope scope, Node name) {
        Scope defining = scope.getDefiningScope(name.getString());
        if (defining != null) {
            uninitialized.put(name, defining.getSymbol(name.getString()));
        }
    }

    /** Records the declarations of the names a binding pattern binds. */
    void declarePattern(Scope scope, AstNode pattern, int position, int caseEnd) {
        while (pattern instanceof ParenthesizedExpression) {
            pattern = ((ParenthesizedExpression) pattern).getExpression();
        }
        if (pattern instanceof Name) {
            declare(scope, ((Name) pattern).getIdentifier(), position, caseEnd);
        } else if (pattern instanceof Assignment) {
            declarePattern(scope, ((Assignment) pattern).getLeft(), position, caseEnd);
        } else if (pattern instanceof Spread) {
            declarePattern(scope, ((Spread) pattern).getExpression(), position, caseEnd);
        } else if (pattern instanceof ArrayLiteral) {
            for (AstNode element : ((ArrayLiteral) pattern).getElements()) {
                declarePattern(scope, element, position, caseEnd);
            }
        } else if (pattern instanceof ObjectLiteral) {
            for (AbstractObjectProperty p : ((ObjectLiteral) pattern).getElements()) {
                if (p instanceof SpreadObjectProperty) {
                    declarePattern(
                            scope, ((SpreadObjectProperty) p).getSpreadNode(), position, caseEnd);
                } else if (p instanceof ObjectProperty) {
                    declarePattern(scope, ((ObjectProperty) p).getValue(), position, caseEnd);
                }
            }
        }
    }

    /**
     * Records that the functions an initializer creates without being able to call them, the
     * initializer itself or the methods of an object literal, run no earlier than {@code until}.
     */
    void initializer(AstNode init, int until) {
        while (init instanceof ParenthesizedExpression) {
            init = ((ParenthesizedExpression) init).getExpression();
        }
        if (init instanceof FunctionNode) {
            uncalledUntil.put((FunctionNode) init, until);
        } else if (init instanceof ObjectLiteral) {
            for (AbstractObjectProperty p : ((ObjectLiteral) init).getElements()) {
                if (p instanceof ObjectProperty) {
                    initializer(((ObjectProperty) p).getValue(), until);
                }
            }
        } else if (init instanceof ArrayLiteral) {
            for (AstNode element : ((ArrayLiteral) init).getElements()) {
                if (!(element instanceof Spread)) {
                    initializer(element, until);
                }
            }
        }
    }

    /** Records a function that is part of the body of a class. */
    void classBody(FunctionNode fn, ClassNode cls) {
        if (fn != null) {
            classBodies.put(fn, cls);
        }
    }

    /**
     * Marks the references that may run before their binding is initialized, and makes their
     * bindings hold {@link UniqueTag#UNINITIALIZED} until then.
     */
    void finish() {
        Map<Symbol, Boolean> checked = new IdentityHashMap<>();
        for (Reference ref : references) {
            Symbol symbol = ref.symbol;
            int init = symbol.getInitPosition();
            if (init < 0) {
                // Not a declaration that can be read too early, such as a catch parameter
                continue;
            }
            if (ref.position < 0
                    || ref.position < init
                    || (symbol.getCaseEnd() >= 0 && ref.position >= symbol.getCaseEnd())) {
                ref.node.putIntProp(Node.TDZ_CHECK_PROP, 1);
                if (!symbol.isTdzChecked()) {
                    symbol.setTdzChecked(true);
                    checked.put(symbol, Boolean.TRUE);
                }
            }
        }
        if (hasEval) {
            for (Symbol symbol : declared) {
                if (!symbol.isTdzChecked()) {
                    symbol.setTdzChecked(true);
                    checked.put(symbol, Boolean.TRUE);
                }
            }
        }
        for (Symbol symbol : checked.keySet()) {
            initializeOnEntry(symbol);
        }
        for (Map.Entry<Node, Symbol> e : uninitialized.entrySet()) {
            if (e.getValue() != null && e.getValue().isTdzChecked()) {
                e.getKey().addChildToBack(new Node(Token.UNDEFINED));
            }
        }
        references.clear();
        uninitialized.clear();
        declared.clear();
    }

    /**
     * Makes a binding uninitialized on entry to its scope. Consts in scope objects already are,
     * except those of a for-in/of head, which only shadow outer bindings while the iterated
     * expression is evaluated.
     */
    private static void initializeOnEntry(Symbol symbol) {
        Scope scope = symbol.getContainingTable();
        int type = scope.getType();
        if (!(scope instanceof ScriptNode)
                && type != Token.BLOCK
                && type != Token.LOOP
                && type != Token.ARRAYCOMP
                && type != Token.SWITCH) {
            // A for (let ...; ...) head split into a scope of its own by IRFactory.createFor
            return;
        }
        ScriptNode fn = functionOf(scope);
        boolean flattened = fn instanceof FunctionNode && !((FunctionNode) fn).requiresActivation();
        Node init;
        if (flattened) {
            Node name = Node.newString(symbol.getName());
            name.setScope(scope);
            init = new Node(Token.RESETVAR, name);
            init.putIntProp(Node.TDZ_CHECK_PROP, 1);
        } else if (symbol.getDeclType() != Symbol.Type.CONST || scope.getType() == Token.LOOP) {
            init =
                    new Node(
                            Token.EXPR_VOID,
                            new Node(
                                    Token.SETNAME,
                                    Node.newString(Token.BINDNAME, symbol.getName()),
                                    IRFactory.intrinsic(Intrinsics.UNINITIALIZED)));
        } else {
            return;
        }
        Node body = scope instanceof FunctionNode ? scope.getFirstChild() : scope;
        body.addChildToFront(init);
    }

    /** Finds the scope defining a name, continuing past functions to their enclosing scopes. */
    private static Scope definingScope(Scope scope, String name) {
        while (scope != null) {
            Scope defining = scope.getDefiningScope(name);
            if (defining != null) {
                return defining;
            }
            Scope top = scope;
            while (top.getParentScope() != null) {
                top = top.getParentScope();
            }
            scope = null;
            for (AstNode n = top.getParent(); n != null; n = n.getParent()) {
                if (n instanceof Scope) {
                    scope = (Scope) n;
                    break;
                }
            }
        }
        return null;
    }

    private static ScriptNode functionOf(Scope scope) {
        return scope instanceof ScriptNode ? (ScriptNode) scope : enclosingFunction(scope);
    }

    private static ScriptNode enclosingFunction(AstNode node) {
        for (AstNode n = node.getParent(); n != null; n = n.getParent()) {
            if (n instanceof ScriptNode) {
                return (ScriptNode) n;
            }
        }
        return null;
    }
}
