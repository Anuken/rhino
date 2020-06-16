package org.mozilla.javascript.ast;

/**
 * AST node representing the set of assignment operators such as {@code =},
 * {@code *=} and {@code +=}.
 */
public class Assignment extends InfixExpression {

    public Assignment() {
    }

    public Assignment(int pos) {
        super(pos);
    }

    public Assignment(int pos, int len) {
        super(pos, len);
    }

    public Assignment(int pos, int len, AstNode left, AstNode right) {
        super(pos, len, left, right);
    }

    public Assignment(AstNode left, AstNode right) {
        super(left, right);
    }

    public Assignment(int operator, AstNode left,
                      AstNode right, int operatorPos) {
        super(operator, left, right, operatorPos);
    }
}
