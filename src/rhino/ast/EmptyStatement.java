package rhino.ast;

import rhino.*;

/**
 * AST node for an empty statement.  Node type is {@link Token#EMPTY}.
 */
public class EmptyStatement extends AstNode{

    {
        type = Token.EMPTY;
    }

    public EmptyStatement(){
    }

    public EmptyStatement(int pos){
        super(pos);
    }

    public EmptyStatement(int pos, int len){
        super(pos, len);
    }

    @Override
    public String toSource(int depth){
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth)).append(";\n");
        return sb.toString();
    }

    /**
     * Visits this node.  There are no children.
     */
    @Override
    public void visit(NodeVisitor v){
        v.visit(this);
    }
}
