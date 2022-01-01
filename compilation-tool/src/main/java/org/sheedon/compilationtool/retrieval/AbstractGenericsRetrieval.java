package org.sheedon.compilationtool.retrieval;

import javax.annotation.processing.Messager;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Types;

/**
 * 抽象泛型检索类
 *
 * @Author: sheedon
 * @Email: sheedonsun@163.com
 * @Date: 2022/1/1 9:28 下午
 */
public abstract class AbstractGenericsRetrieval implements ISearch{

    protected IRetrieval.AbstractRetrieval retrieval;

    public AbstractGenericsRetrieval(IRetrieval.AbstractRetrieval retrieval) {
        this.retrieval = retrieval;
    }
}
