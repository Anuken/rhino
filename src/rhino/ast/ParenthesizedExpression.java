package rhino.ast;

import rhino.*;

/**
 * AST node for a parenthesized expression.
 * Node type is {@link Token#LP}.
 */
public class ParenthesizedExpression extends AstNode{

    private AstNode expression;

    {
        type = Token.LP;
    }

    public ParenthesizedExpression(){
    }

    public ParenthesizedExpression(int pos){
        super(pos);
    }

    public ParenthesizedExpression(int pos, int len){
        super(pos, len);
    }

    public ParenthesizedExpression(AstNode expr){
        this(expr != null ? expr.getPosition() : 0,
        expr != null ? expr.getLength() : 1,
        expr);
    }

    public ParenthesizedExpression(int pos, int len, AstNode expr){
        super(pos, len);
        setExpression(expr);
    }

    /**
     * Returns the expression between the parens
     */
    public AstNode getExpression(){
        return expression;
    }

    /**
     * Sets the expression between the parens, and sets the parent
     * to this node.
     * @param expression the expression between the parens
     * @throws IllegalArgumentException} if expression is {@code null}
     */
    public void setExpression(AstNode expression){
        assertNotNull(expression);
        this.expression = expression;
        expression.setParent(this);
    }

    @Override
    public String toSource(int depth){
        return makeIndent(depth) + "(" + expression.toSource(0) + ")";
    }

    /**
     * Visits this node, then the child expression.
     */
    @Override
    public void visit(NodeVisitor v){
        if(v.visit(this)){
            expression.visit(v);
        }
    }
}
