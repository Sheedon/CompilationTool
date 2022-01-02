package org.sheedon.compilationtool.builder;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

/**
 * 注解构建者
 *
 * @Author: sheedon
 * @Email: sheedonsun@163.com
 * @Date: 2022/1/2 5:32 下午
 */
public class AnnotationBuilder {

    private final Map<Class<? extends Annotation>, Annotation> annotationMap = new HashMap<>();

    public <A extends Annotation> void attachAnnotation(Class<A> annotationClass, A a) {
        annotationMap.put(annotationClass, a);
    }

    @SuppressWarnings("unchecked")
    public <A extends Annotation> A getAnnotationMap(Class<A> annotationClass) {
        Annotation annotation = annotationMap.get(annotationClass);
        return annotation == null ? null : (A) annotation;
    }
}
