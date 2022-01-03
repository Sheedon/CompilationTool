package org.sheedon.use.compier;

import org.sheedon.annotation.TargetClass;
import org.sheedon.compilationtool.retrieval.core.IGenericsRecord;
import org.sheedon.compilationtool.retrieval.core.IRetrieval;

import java.util.HashSet;
import java.util.Set;

/**
 * 泛型检索者类
 *
 * @Author: sheedon
 * @Email: sheedonsun@163.com
 * @Date: 2022/1/2 12:30 上午
 */
public class ClassGenericsRetrievalTest extends IRetrieval.AbstractRetrieval{

    private final Set<String> packages = new HashSet<String>(){
        {
            add("java.");
        }
    };

    @Override
    public String canonicalName() {
        return TargetClass.class.getCanonicalName();
    }

    @Override
    public Set<String> filterablePackages() {
        return packages;
    }

    @Override
    public IGenericsRecord genericsRecord() {
        return new RRGenericsRecord();
    }
}
