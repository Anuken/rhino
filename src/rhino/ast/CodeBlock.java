package rhino.ast;

import rhino.*;

/**
 * A block statement delimited by curly braces.  The node position is the
 * position of the open-curly, and the length extends to the position of
 * the close-curly.  Node type is {@link Token#BLOCK}.
 *
 * <pre><i>Block</i> :
 *     <b>{</b> Statement* <b>}</b></pre>
 */
public class CodeBlock extends AstNode{

    {
        this.type = Token.BLOCK;
    }

    public CodeBlock(){
    }

    public CodeBlock(int pos){
        super(pos);
    }

    public CodeBlock(int pos, int len){
        super(pos, len);
    }

    /**
     * Alias for {@link #addChild}.
     */
    public void addStatement(AstNode statement){
        addChild(statement);
    }

    @Override
    public String toSource(int depth){
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append("{\n");
        for(Node kid : this){
            AstNode astNodeKid = (AstNode)kid;
            sb.append(astNodeKid.toSource(depth + 1));
            if(astNodeKid.getType() == Token.COMMENT){
                sb.append("\n");
            }
        }
        sb.append(makeIndent(depth));
        sb.append("}");
        if(this.getInlineComment() != null){
            sb.append(this.getInlineComment().toSource(depth));
        }
        sb.append("\n");
        return sb.toString();
    }

    @Override
    public void visit(NodeVisitor v){
        if(v.visit(this)){
            for(Node kid : this){
                ((AstNode)kid).visit(v);
            }
        }
    }
}
