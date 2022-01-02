package org.sheedon.compilationtool.builder;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * 字段构造者
 *
 * @Author: sheedon
 * @Email: sheedonsun@163.com
 * @Date: 2022/1/2 4:37 下午
 */
public class FieldBuilder extends AnnotationBuilder {

    private final TypeBuilder typeBuilder;
    private String name;
    private TypeMirror returnType;

    public FieldBuilder(TypeBuilder typeBuilder, VariableElement element) {
        this.typeBuilder = typeBuilder;
        attachVariableElement(element);
    }

    private void attachVariableElement(VariableElement element) {
        if (element == null) return;
        this.name = element.getSimpleName().toString();
        this.returnType = element.asType();
    }

    public TypeBuilder getTypeBuilder() {
        return typeBuilder;
    }

    public String getName() {
        return name;
    }

    public TypeMirror getReturnType() {
        return returnType;
    }
}
