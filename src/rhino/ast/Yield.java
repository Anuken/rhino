package rhino.ast;

import rhino.*;

/**
 * AST node for JavaScript 1.7 {@code yield} expression or statement.
 * Node type is {@link Token#YIELD}.
 *
 * <pre><i>Yield</i> :
 *   <b>yield</b> [<i>no LineTerminator here</i>] [non-paren Expression] ;</pre>
 */
public class Yield extends AstNode{

    private AstNode value;

    public Yield(){
        type = Token.YIELD;
    }

    public Yield(int pos){
        super(pos);
        type = Token.YIELD;
    }

    public Yield(int pos, int len){
        super(pos, len);
        type = Token.YIELD;
    }

    public Yield(int pos, int len, AstNode value, boolean isStar){
        super(pos, len);
        type = isStar ? Token.YIELD_STAR : Token.YIELD;
        setValue(value);
    }

    /**
     * Returns yielded expression, {@code null} if none
     */
    public AstNode getValue(){
        return value;
    }

    /**
     * Sets yielded expression, and sets its parent to this node.
     * @param expr the value to yield. Can be {@code null}.
     */
    public void setValue(AstNode expr){
        this.value = expr;
        if(expr != null)
            expr.setParent(this);
    }

    @Override
    public String toSource(int depth){
        return value == null
        ? "yield"
        : "yield " + value.toSource(0);
    }

    /**
     * Visits this node, and if present, the yielded value.
     */
    @Override
    public void visit(NodeVisitor v){
        if(v.visit(this) && value != null){
            value.visit(v);
        }
    }
}
