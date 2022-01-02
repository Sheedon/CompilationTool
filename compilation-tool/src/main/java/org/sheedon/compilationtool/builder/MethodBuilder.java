package org.sheedon.compilationtool.builder;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

/**
 * 方法构造者
 *
 * @Author: sheedon
 * @Email: sheedonsun@163.com
 * @Date: 2022/1/2 4:32 下午
 */
public class MethodBuilder extends AnnotationBuilder {

    private final TypeBuilder typeBuilder;
    private String name;
    private TypeMirror returnType;
    private final Map<String, TypeParameterBuilder> typeParameters = new LinkedHashMap<>();

    public MethodBuilder(TypeBuilder typeBuilder, ExecutableElement element) {
        this.typeBuilder = typeBuilder;
        attachExecutableElement(element);
    }

    private void attachExecutableElement(ExecutableElement element) {
        if (element == null) return;
        this.name = element.getSimpleName().toString();
        this.returnType = element.getReturnType();

        element.getTypeParameters().forEach(action ->
                this.typeParameters.put(action.getSimpleName().toString(),
                        new TypeParameterBuilder(this, action)));
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

    public Map<String, TypeParameterBuilder> getTypeParameters() {
        return typeParameters;
    }

    public TypeParameterBuilder getTypeParameterBuilder(String name) {
        return typeParameters.get(name);
    }
}
