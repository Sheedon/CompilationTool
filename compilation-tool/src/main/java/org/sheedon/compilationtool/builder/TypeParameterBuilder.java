package org.sheedon.compilationtool.builder;

import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;

/**
 * 形式参数构建者
 * * 方法/接口的链接
 * * 参数名称
 * * 参数类型
 * * 注解
 *
 * @Author: sheedon
 * @Email: sheedonsun@163.com
 * @Date: 2022/1/2 4:28 下午
 */
public class TypeParameterBuilder extends AnnotationBuilder{

    // 方法构建者 形参关联的方法
    private final MethodBuilder methodBuilder;
    // 形式参数名
    private String name;
    private TypeMirror returnType;

    public TypeParameterBuilder(MethodBuilder methodBuilder, TypeParameterElement element) {
        this.methodBuilder = methodBuilder;
        attachTypeParameter(element);
    }

    /**
     * 附加形式参数信息 字段名+类型
     *
     * @param element 形式参数元素
     */
    private void attachTypeParameter(TypeParameterElement element) {
        if (element == null) return;

        name = element.getSimpleName().toString();
        returnType = element.asType();
    }

    public MethodBuilder getMethodBuilder() {
        return methodBuilder;
    }

    public String getName() {
        return name;
    }

    public TypeMirror getReturnType() {
        return returnType;
    }
}
