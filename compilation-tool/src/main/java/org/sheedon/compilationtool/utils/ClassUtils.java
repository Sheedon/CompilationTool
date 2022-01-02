package org.sheedon.compilationtool.utils;


import org.sheedon.compilationtool.Contract;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

/**
 * 类处理工具
 *
 * @Author: sheedon
 * @Email: sheedonsun@163.com
 * @Date: 2021/12/8 1:32 下午
 */
public class ClassUtils {

    /**
     * 转化为小驼峰命名
     */
    public static String convertLittleCamelCase(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }

        String firstLetter = name.substring(0, 1);
        return firstLetter.toLowerCase() + name.substring(1);
    }

    /**
     * 转化为大驼峰命名
     */
    public static String convertBigCamelCase(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }

        String firstLetter = name.substring(0, 1);
        return firstLetter.toUpperCase() + name.substring(1);
    }

    /**
     * 加载包名
     *
     * @param element 类型元素
     * @return 包名
     */
    public static String loadPackageName(TypeElement element) {
        if (element == null) return null;
        Element enclosingElement = element.getEnclosingElement();
        if (enclosingElement instanceof PackageElement) {
            return ((PackageElement) enclosingElement).getQualifiedName().toString();
        }

        String qualifiedName = element.getQualifiedName().toString();
        String className = element.getSimpleName().toString();
        int index = qualifiedName.lastIndexOf(Contract.POINT + className);
        if (index == -1) {
            return null;
        }
        return qualifiedName.substring(0, index);
    }
}
