package rhino.ast;

/**
 *
 */
public class GeneratorExpressionLoop extends ForInLoop{

    public GeneratorExpressionLoop(){
    }

    public GeneratorExpressionLoop(int pos){
        super(pos);
    }

    public GeneratorExpressionLoop(int pos, int len){
        super(pos, len);
    }

    /**
     * Returns whether the loop is a for-each loop
     */
    @Override
    public boolean isForEach(){
        return false;
    }

    /**
     * Sets whether the loop is a for-each loop
     */
    @Override
    public void setIsForEach(boolean isForEach){
        throw new UnsupportedOperationException("this node type does not support for each");
    }

    @Override
    public String toSource(int depth){
        return makeIndent(depth)
        + " for "
        + (isForEach() ? "each " : "")
        + "("
        + iterator.toSource(0)
        + (isForOf() ? " of " : " in ")
        + iteratedObject.toSource(0)
        + ")";
    }

    /**
     * Visits the iterator expression and the iterated object expression.
     * There is no body-expression for this loop type.
     */
    @Override
    public void visit(NodeVisitor v){
        if(v.visit(this)){
            iterator.visit(v);
            iteratedObject.visit(v);
        }
    }
}
