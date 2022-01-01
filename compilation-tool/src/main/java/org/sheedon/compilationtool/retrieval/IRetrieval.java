package org.sheedon.compilationtool.retrieval;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 检索
 *
 * @Author: sheedon
 * @Email: sheedonsun@163.com
 * @Date: 2022/1/1 9:13 下午
 */
public interface IRetrieval {


    /**
     * 目标类的全类名
     */
    String canonicalName();

    /**
     * 过滤的包名
     */
    Set<String> filterablePackages();


    /**
     * 检索类存储Map
     * String: 全类名
     * RetrievalClassModel: 存储检索信息
     */
    Map<String, RetrievalClassModel> retrievalClassMap();


    /**
     * 泛型记录实体类
     */
    IGenericsRecord genericsRecord();


    /**
     * 检索工厂类
     */
    class Factory {

        AbstractRetrieval createRetrieval() {
            return null;
        }
    }

    abstract class AbstractRetrieval implements IRetrieval{

        private final Map<String, RetrievalClassModel> classMap = new HashMap<>();

        @Override
        public Set<String> filterablePackages() {
            return new HashSet<>();
        }

        @Override
        public Map<String, RetrievalClassModel> retrievalClassMap() {
            return classMap;
        }
    }
}
