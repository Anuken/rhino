package rhino.ast;

import rhino.*;

/**
 * Throw statement.  Node type is {@link Token#THROW}.
 *
 * <pre><i>ThrowStatement</i> :
 *      <b>throw</b> [<i>no LineTerminator here</i>] Expression ;</pre>
 */
public class ThrowStatement extends AstNode{

    private AstNode expression;

    {
        type = Token.THROW;
    }

    public ThrowStatement(){
    }

    public ThrowStatement(int pos){
        super(pos);
    }

    public ThrowStatement(int pos, int len){
        super(pos, len);
    }

    public ThrowStatement(AstNode expr){
        setExpression(expr);
    }

    public ThrowStatement(int pos, AstNode expr){
        super(pos, expr.getLength());
        setExpression(expr);
    }

    public ThrowStatement(int pos, int len, AstNode expr){
        super(pos, len);
        setExpression(expr);
    }

    /**
     * Returns the expression being thrown
     */
    public AstNode getExpression(){
        return expression;
    }

    /**
     * Sets the expression being thrown, and sets its parent
     * to this node.
     * @throws IllegalArgumentException} if expression is {@code null}
     */
    public void setExpression(AstNode expression){
        assertNotNull(expression);
        this.expression = expression;
        expression.setParent(this);
    }

    @Override
    public String toSource(int depth){
        String sb = makeIndent(depth) +
        "throw" +
        " " +
        expression.toSource(0) +
        ";\n";
        return sb;
    }

    /**
     * Visits this node, then the thrown expression.
     */
    @Override
    public void visit(NodeVisitor v){
        if(v.visit(this)){
            expression.visit(v);
        }
    }
}
