package org.mozilla.javascript.ast;

import org.mozilla.javascript.Token;

/**
 * AST node for an empty expression.  Node type is {@link Token#EMPTY}.<p>
 *
 * To create an empty statement, wrap it with an {@link ExpressionStatement}.
 */
public class EmptyExpression extends AstNode {

    {
        type = Token.EMPTY;
    }

    public EmptyExpression() {
    }

    public EmptyExpression(int pos) {
        super(pos);
    }

    public EmptyExpression(int pos, int len) {
        super(pos, len);
    }

    @Override
    public String toSource(int depth) {
        return makeIndent(depth);
    }

    /**
     * Visits this node.  There are no children.
     */
    @Override
    public void visit(NodeVisitor v) {
        v.visit(this);
    }
}
