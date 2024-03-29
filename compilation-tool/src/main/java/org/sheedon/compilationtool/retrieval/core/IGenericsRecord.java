package org.sheedon.compilationtool.retrieval.core;

import javax.lang.model.type.TypeMirror;

/**
 * 泛型记录职责
 *
 * @Author: sheedon
 * @Email: sheedonsun@163.com
 * @Date: 2021/12/31 10:51 下午
 */
public interface IGenericsRecord extends Cloneable {

    /**
     * 将泛型实际参数类型 存入泛型类型中
     *
     * @param typeName   泛型类型
     * @param typeMirror 实体类型
     */
    void put(String typeName, TypeMirror typeMirror);

    /**
     * 根据泛型类型获取，实体类型Class 全类名
     *
     * @param typeName 泛型类型
     * @return 实体Class全类名
     */
    TypeMirror get(String typeName);


    /**
     * 是否全部类型设置完成
     */
    boolean isCompeted();

    /**
     * 复制泛型记录
     *
     * @return IGenericsRecord
     * @throws CloneNotSupportedException 不支持克隆异常
     */
    IGenericsRecord clone() throws CloneNotSupportedException;

}
