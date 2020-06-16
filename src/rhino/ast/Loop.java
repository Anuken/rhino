package rhino.ast;

/**
 * Abstract base type for loops.
 */
public abstract class Loop extends Scope{

    protected AstNode body;
    protected int lp = -1;
    protected int rp = -1;

    public Loop(){
    }

    public Loop(int pos){
        super(pos);
    }

    public Loop(int pos, int len){
        super(pos, len);
    }

    /**
     * Returns loop body
     */
    public AstNode getBody(){
        return body;
    }

    /**
     * Sets loop body.  Sets the parent of the body to this loop node,
     * and updates its offset to be relative.  Extends the length of this
     * node to include the body.
     */
    public void setBody(AstNode body){
        this.body = body;
        int end = body.getPosition() + body.getLength();
        this.setLength(end - this.getPosition());
        body.setParent(this);
    }

    /**
     * Returns left paren position, -1 if missing
     */
    public int getLp(){
        return lp;
    }

    /**
     * Sets left paren position
     */
    public void setLp(int lp){
        this.lp = lp;
    }

    /**
     * Returns right paren position, -1 if missing
     */
    public int getRp(){
        return rp;
    }

    /**
     * Sets right paren position
     */
    public void setRp(int rp){
        this.rp = rp;
    }

    /**
     * Sets both paren positions
     */
    public void setParens(int lp, int rp){
        this.lp = lp;
        this.rp = rp;
    }
}
