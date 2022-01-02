package org.sheedon.compilationtool.builder;

import org.sheedon.compilationtool.utils.ClassUtils;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * 类或接口构造者
 *
 * @Author: sheedon
 * @Email: sheedonsun@163.com
 * @Date: 2022/1/2 4:35 下午
 */
public class TypeBuilder extends AnnotationBuilder {

    private final String packageName;
    private final String qualifiedName;
    private final String simpleName;
    private TypeBuilder superTypeBuilder;
    private final List<TypeBuilder> interfaceBuilders = new ArrayList<>();
    // 目标泛型
    // 字段
    private final List<FieldBuilder> fieldBuilders = new ArrayList<>();
    private final List<MethodBuilder> methodBuilders = new ArrayList<>();

    public TypeBuilder(TypeElement element, Types types) {
        this.packageName = ClassUtils.loadPackageName(element);
        this.qualifiedName = element.getQualifiedName().toString();
        this.simpleName = element.getSimpleName().toString();

        Element superTypeElement = types.asElement(element.getSuperclass());
        if (superTypeElement instanceof TypeElement) {
            this.superTypeBuilder = new TypeBuilder((TypeElement) superTypeElement, types);
        }
        List<? extends TypeMirror> typeMirrors = element.getInterfaces();
        //noinspection ConstantConditions
        typeMirrors.stream().filter(it -> !(it instanceof TypeElement)).forEach(action ->
                interfaceBuilders.add(new TypeBuilder((TypeElement) action, types))
        );

        element.getEnclosedElements()
                .stream()
                .filter(it -> it instanceof VariableElement)
                .forEach(item -> fieldBuilders.add(new FieldBuilder(this, (VariableElement) item)));

        element.getEnclosedElements()
                .forEach(item -> {
                    if (item instanceof VariableElement) {
                        fieldBuilders.add(new FieldBuilder(this, (VariableElement) item));
                    } else if (item instanceof ExecutableElement) {
                        methodBuilders.add(new MethodBuilder(this, (ExecutableElement) item));
                    }
                });
    }

    // TODO 传入目标搜索内容
    public void attachGeneric() {

    }

    public String getPackageName() {
        return packageName;
    }

    public String getQualifiedName() {
        return qualifiedName;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public TypeBuilder getSuperTypeBuilder() {
        return superTypeBuilder;
    }

    public List<TypeBuilder> getInterfaceBuilders() {
        return interfaceBuilders;
    }

    public List<FieldBuilder> getFieldBuilders() {
        return fieldBuilders;
    }

    public List<MethodBuilder> getMethodBuilders() {
        return methodBuilders;
    }
}
