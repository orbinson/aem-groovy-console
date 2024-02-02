package be.orbinson.aem.groovy.console.library.impl

import be.orbinson.aem.groovy.console.library.Library
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassCodeVisitorSupport
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.control.messages.Message

class LibraryCompilationCustomizer extends CompilationCustomizer {

    final List<String> libraries = new ArrayList<>();

    LibraryCompilationCustomizer() {
        super(CompilePhase.CONVERSION);
    }

    @Override
    void call(final SourceUnit source, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {
        new ClassCodeVisitorSupport() {

            @Override
            protected SourceUnit getSourceUnit() {
                return source;
            }

            @Override
            void visitAnnotations(AnnotatedNode node) {
                for (AnnotationNode annotationNode : node.getAnnotations()) {
                    String name = annotationNode.getClassNode().getName();
                    if (name.equals(Library.class.getCanonicalName()) ||
                            // In the CONVERSION phase we will not have resolved the implicit import yet.
                            name.equals(Library.class.getSimpleName())) {
                        Expression value = annotationNode.getMember("value");
                        if (value == null) {
                            source.getErrorCollector().addErrorAndContinue(Message.create("@Library was missing a value", source));
                        } else {
                            processExpression(value);
                        }
                    }
                }
            }

            private void processExpression(Expression value) {
                if (value instanceof ConstantExpression) { // one library
                    Object constantValue = ((ConstantExpression) value).getValue();
                    if (constantValue instanceof String) {
                        libraries.add((String) constantValue);
                    } else {
                        source.getErrorCollector().addErrorAndContinue(Message.create("@Library value ‘" + constantValue + "’ was not a string", source));
                    }
                } else if (value instanceof ListExpression) { // several libraries
                    for (Expression element : ((ListExpression) value).getExpressions()) {
                        processExpression(element);
                    }
                } else {
                    source.getErrorCollector().addErrorAndContinue(Message.create("@Library value ‘" + value.getText() + "’ was not a constant; did you mean to use the ‘library’ step instead?", source));
                }
            }
        }.visitClass(classNode);
    }
}


