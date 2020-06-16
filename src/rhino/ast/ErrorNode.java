package rhino.ast;

import rhino.*;

/**
 * AST node representing a parse error or a warning.  Node type is
 * {@link Token#ERROR}.
 */
public class ErrorNode extends AstNode{

    private String message;

    {
        type = Token.ERROR;
    }

    public ErrorNode(){
    }

    public ErrorNode(int pos){
        super(pos);
    }

    public ErrorNode(int pos, int len){
        super(pos, len);
    }

    /**
     * Returns error message key
     */
    public String getMessage(){
        return message;
    }

    /**
     * Sets error message key
     */
    public void setMessage(String message){
        this.message = message;
    }

    @Override
    public String toSource(int depth){
        return "";
    }

    /**
     * Error nodes are not visited during normal visitor traversals,
     * but comply with the {@link AstNode#visit} interface.
     */
    @Override
    public void visit(NodeVisitor v){
        v.visit(this);
    }
}
