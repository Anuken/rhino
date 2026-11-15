/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino.ast;

import java.util.ArrayList;
import java.util.List;
import rhino.Token;

/**
 * AST node for an ES2015 class declaration or expression. Node type is {@link Token#CLASS}.
 *
 * <p>The node is also the class scope: it holds the class's own name binding and the private names
 * the class body declares, and the class elements are parsed inside it.
 */
public class ClassNode extends Scope {

    /** Kinds of {@link Element}. */
    public static final int METHOD = 0, GETTER = 1, SETTER = 2, FIELD = 3, STATIC_BLOCK = 4;

    /** A single element of a class body, other than the constructor. */
    public static final class Element {
        private final int kind;
        private final boolean isStatic;
        private final AstNode key;
        private final FunctionNode function;

        /**
         * @param kind one of {@link #METHOD}, {@link #GETTER}, {@link #SETTER}, {@link #FIELD} or
         *     {@link #STATIC_BLOCK}
         * @param key the element name: a {@link Name} (private names start with '#'), {@link
         *     StringLiteral}, {@link NumberLiteral}, {@link BigIntLiteral} or {@link
         *     ComputedPropertyKey}; null for a static block
         * @param function the method, the field initializer or the static block body; null for a
         *     field without initializer
         */
        public Element(int kind, boolean isStatic, AstNode key, FunctionNode function) {
            this.kind = kind;
            this.isStatic = isStatic;
            this.key = key;
            this.function = function;
        }

        public int getKind() {
            return kind;
        }

        public boolean isStatic() {
            return isStatic;
        }

        public AstNode getKey() {
            return key;
        }

        public FunctionNode getFunction() {
            return function;
        }

        /** Returns whether the key is a private name such as {@code #x}. */
        public boolean isPrivate() {
            return key instanceof Name && ((Name) key).getIdentifier().startsWith("#");
        }
    }

    private Name className;
    private AstNode heritage;
    private FunctionNode constructor;
    private final List<Element> elements = new ArrayList<>();
    private boolean isStatement;
    private String inferredName;
    private String classVariable;

    {
        type = Token.CLASS;
    }

    public ClassNode() {}

    public ClassNode(int pos) {
        super(pos);
    }

    /** Returns the class name, or null for an anonymous class expression. */
    public Name getClassName() {
        return className;
    }

    public void setClassName(Name className) {
        this.className = className;
        if (className != null) {
            className.setParent(this);
        }
    }

    /** Returns the expression after {@code extends}, or null. */
    public AstNode getHeritage() {
        return heritage;
    }

    public void setHeritage(AstNode heritage) {
        this.heritage = heritage;
        if (heritage != null) {
            heritage.setParent(this);
        }
    }

    /** Returns the constructor, which the parser synthesizes if the class body has none. */
    public FunctionNode getConstructor() {
        return constructor;
    }

    public void setConstructor(FunctionNode constructor) {
        this.constructor = constructor;
        constructor.setParent(this);
    }

    public List<Element> getElements() {
        return elements;
    }

    public void addElement(Element element) {
        elements.add(element);
        if (element.key != null) {
            element.key.setParent(this);
        }
        if (element.function != null) {
            element.function.setParent(this);
        }
    }

    /** Returns whether this is a class declaration rather than a class expression. */
    public boolean isStatement() {
        return isStatement;
    }

    public void setIsStatement(boolean isStatement) {
        this.isStatement = isStatement;
    }

    /** Returns the name an anonymous class takes from its context, as in {@code x = class {}}. */
    public String getInferredName() {
        return inferredName;
    }

    public void setInferredName(String inferredName) {
        this.inferredName = inferredName;
    }

    /** Returns the name of the hidden variable that holds the class while it is defined. */
    public String getClassVariable() {
        return classVariable;
    }

    public void setClassVariable(String classVariable) {
        this.classVariable = classVariable;
    }

    @Override
    public String toSource(int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(makeIndent(depth));
        sb.append("class");
        if (className != null) {
            sb.append(' ').append(className.toSource(0));
        }
        if (heritage != null) {
            sb.append(" extends ").append(heritage.toSource(0));
        }
        sb.append(" {\n");
        if (!constructor.isDefaultClassConstructor()) {
            sb.append(makeIndent(depth + 1)).append("constructor");
            sb.append(functionSource(constructor));
        }
        for (Element e : elements) {
            sb.append(makeIndent(depth + 1));
            if (e.isStatic) {
                sb.append("static ");
            }
            if (e.kind == STATIC_BLOCK) {
                sb.append(e.function.getBody().toSource(0).trim()).append('\n');
                continue;
            }
            if (e.kind == GETTER) {
                sb.append("get ");
            } else if (e.kind == SETTER) {
                sb.append("set ");
            } else if (e.kind == METHOD && e.function.isES6Generator()) {
                sb.append('*');
            }
            sb.append(e.key.toSource(0));
            if (e.kind == FIELD) {
                if (e.function != null) {
                    ReturnStatement init = (ReturnStatement) e.function.getBody().getFirstChild();
                    sb.append(" = ").append(init.getReturnValue().toSource(0));
                }
                sb.append(";\n");
            } else {
                sb.append(functionSource(e.function));
            }
        }
        sb.append(makeIndent(depth)).append('}');
        if (isStatement) {
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String functionSource(FunctionNode fn) {
        return fn.toSource(0).trim() + "\n";
    }

    @Override
    public void visit(NodeVisitor v) {
        if (!v.visit(this)) {
            return;
        }
        if (className != null) {
            className.visit(v);
        }
        if (heritage != null) {
            heritage.visit(v);
        }
        constructor.visit(v);
        for (Element e : elements) {
            if (e.key != null) {
                e.key.visit(v);
            }
            if (e.function != null) {
                e.function.visit(v);
            }
        }
    }
}
