package rhino.ast;

import rhino.*;

/**
 * AST node for a RegExp literal.
 * Node type is {@link Token#REGEXP}.
 */
public class RegExpLiteral extends AstNode{

    private String value;
    private String flags;

    {
        type = Token.REGEXP;
    }

    public RegExpLiteral(){
    }

    public RegExpLiteral(int pos){
        super(pos);
    }

    public RegExpLiteral(int pos, int len){
        super(pos, len);
    }

    /**
     * Returns the regexp string without delimiters
     */
    public String getValue(){
        return value;
    }

    /**
     * Sets the regexp string without delimiters
     * @throws IllegalArgumentException} if value is {@code null}
     */
    public void setValue(String value){
        assertNotNull(value);
        this.value = value;
    }

    /**
     * Returns regexp flags, {@code null} or "" if no flags specified
     */
    public String getFlags(){
        return flags;
    }

    /**
     * Sets regexp flags.  Can be {@code null} or "".
     */
    public void setFlags(String flags){
        this.flags = flags;
    }

    @Override
    public String toSource(int depth){
        return makeIndent(depth) + "/" + value + "/"
        + (flags == null ? "" : flags);
    }

    /**
     * Visits this node.  There are no children to visit.
     */
    @Override
    public void visit(NodeVisitor v){
        v.visit(this);
    }
}
