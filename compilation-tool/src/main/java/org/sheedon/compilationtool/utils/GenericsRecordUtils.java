package org.sheedon.compilationtool.utils;

import org.sheedon.compilationtool.retrieval.core.IGenericsRecord;
import org.sheedon.compilationtool.retrieval.core.IRetrieval;
import org.sheedon.compilationtool.retrieval.core.RetrievalClassModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * 泛型记录处理工具
 *
 * @Author: sheedon
 * @Email: sheedonsun@163.com
 * @Date: 2022/1/3 1:59 下午
 */
public class GenericsRecordUtils {

    /**
     * 核实是否为类型元素，并且检索是否加载过泛型检索记录
     * @param element 目标元素
     * @param classMap 记录Map
     * @param currentModel 当前类的泛型检索记录
     * @param currentElement 当前元素
     * @return 泛型检索记录信息
     */
    public static RetrievalClassModel checkTypeElementAndLoaded(Element element,
                                                          Map<String, RetrievalClassModel> classMap,
                                                          RetrievalClassModel currentModel,
                                                          TypeElement currentElement,
                                                          IRetrieval retrieval) {
        if (element instanceof TypeElement) {
            String superclassName = ((TypeElement) element).getQualifiedName().toString();
            RetrievalClassModel superRetrievalModel = classMap.get(superclassName);

            // 核实，若存在，则直接返回
            return checkLoaded(superRetrievalModel, currentModel, currentElement, retrieval);
        }

        return null;
    }


    /**
     * 先核实一步，若存在，可减少后续目标节点和过滤节点的盘点耗时
     *
     * @param superRetrievalModel 父类检索信息
     * @param currentModel        当前类的检索信息
     * @param element             当前类的类型元素
     * @return RetrievalClassModel 父类检索信息绑定到当前类
     */
    public static RetrievalClassModel checkLoaded(RetrievalClassModel superRetrievalModel,
                                                  RetrievalClassModel currentModel,
                                                  TypeElement element,
                                                  IRetrieval retrieval) {
        if (superRetrievalModel == null) {
            return null;
        }

        // 是否填充完整，是则浅拷贝，并且返回
        if (superRetrievalModel.isCompeted()) {
            currentModel.bindGenericsRecord(currentModel.getRecord());
            return currentModel;
        }

        // 遍历节点，用于绑定坐标
        return traverseNodeAndBindPosition(element.getSuperclass(), currentModel, superRetrievalModel, element, retrieval);
    }


    /**
     * 「同类-层级关联」+「继承类-坐标关联」
     *
     * @param superTypeMirror 父类类型
     * @param currentModel    当前类的泛型数据存储
     * @param superClassModel 父类的泛型数据存储
     * @param element         当前类的类型元素
     * @return 父类检索信息绑定到当前类
     */
    public static RetrievalClassModel traverseNodeAndBindPosition(TypeMirror superTypeMirror,
                                                                  RetrievalClassModel currentModel,
                                                                  RetrievalClassModel superClassModel,
                                                                  TypeElement element,
                                                                  IRetrieval retrieval) {

        // 否则，采用深拷贝
        IGenericsRecord record = cloneBySuperRecord(retrieval,
                superClassModel.getRecord());
        currentModel.bindGenericsRecord(record);

        traverseNodeGenerics(superTypeMirror, currentModel, superClassModel);
        appendBindPosition(currentModel, element.getTypeParameters());
        return currentModel;
    }

    /**
     * 遍历目标泛型集合
     * 将当前制定的实体类型 填充到
     * 目标类的泛型上
     * 流程
     * 1. 获取当前类的父类形式泛型
     * 2. 获取真实父类的泛型类型
     * 3. 匹配形式泛型kind == TypeKind.DECLARED，代表可以填充
     * 4. 不匹配的记录到对照表中
     */
    public static RetrievalClassModel traverseTargetGenerics(TypeMirror superTypeMirror,
                                                             String currentQualifiedName,
                                                             IRetrieval retrieval) {
        if (!(superTypeMirror instanceof DeclaredType)) {
            return null;
        }

        DeclaredType declaredType = (DeclaredType) superTypeMirror;

        TypeElement superElement = (TypeElement) declaredType.asElement();
        List<? extends TypeParameterElement> superParameters = superElement.getTypeParameters();
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        // 未设置泛型，或设置的泛型个数不一致
        if (typeArguments.isEmpty() || superParameters.size() != typeArguments.size()) {
            return null;
        }

        // 「同类-层级关联」
        Map<String, RetrievalClassModel> classMap = retrieval == null ? new HashMap<>() : retrieval.retrievalClassMap();
        RetrievalClassModel classModel = classMap.get(currentQualifiedName);
        for (int index = 0; index < superParameters.size(); index++) {
            TypeParameterElement element = superParameters.get(index);
            String typeName = element.asType().toString();

            TypeMirror mirror = typeArguments.get(index);
            if (mirror.getKind() == TypeKind.DECLARED) {
                classModel.addTargetGenericsRecord(typeName, mirror);
            } else {
                classModel.recordType(mirror.toString(), RetrievalClassModel.PREFIX + typeName);
            }
        }
        return classModel;
    }

    /**
     * 追加绑定泛型类型的位置，「继承类-坐标关联」
     * AClass<T> extends BClass<T>{
     * <p>
     * }
     * 绑定T的位置为0
     *
     * @param nodeClass         节点Class信息
     * @param currentParameters 当前类的泛型参数 AClass<T> 中的T
     */
    public static void appendBindPosition(RetrievalClassModel nodeClass, List<? extends TypeParameterElement> currentParameters) {
        int index = 0;
        for (TypeParameterElement parameter : currentParameters) {
            nodeClass.bindPosition(parameter.asType().toString(), index);
            index++;
        }
    }

    /**
     * 遍历目标泛型集合
     * 1. 获取当前类的父类形式泛型
     * 2. 获取真实父类的泛型类型
     * 3. 匹配形式泛型kind == TypeKind.DECLARED，代表可以填充
     * 4. 不匹配的记录到对照表中
     */
    public static void traverseNodeGenerics(TypeMirror superTypeMirror,
                                            RetrievalClassModel currentModel,
                                            RetrievalClassModel superClassModel) {
        if (!(superTypeMirror instanceof DeclaredType)) {
            return;
        }

        DeclaredType declaredType = (DeclaredType) superTypeMirror;

        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();

        if (typeArguments.isEmpty()) {
            return;
        }

        // 「同类-层级关联」通过坐标获取泛型类型
        Set<Integer> positions = superClassModel.getPositions();
        for (Integer position : positions) {
            String typeName = superClassModel.getTypeNameByPosition(position);
            TypeMirror mirror = typeArguments.get(position);
            if (mirror.getKind() == TypeKind.DECLARED) {
                currentModel.addGenericsRecord(typeName, mirror);
            } else {
                currentModel.recordType(mirror.toString(), typeName);
            }
        }

    }


    /**
     * 从父记录中拷贝泛型处理记录
     *
     * @param retrieval 检索者职责
     * @param record    父泛型处理记录
     * @return 当前类的泛型处理记录
     */
    public static IGenericsRecord cloneBySuperRecord(IRetrieval retrieval,
                                                     IGenericsRecord record) {
        if (record == null) {
            return loadGenericsRecord(retrieval);
        }

        try {
            return record.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return loadGenericsRecord(retrieval);
    }

    /**
     * 从检索者职责中获取记录信息
     *
     * @param retrieval 检索者职责
     * @return IGenericsRecord 泛型处理记录
     */
    private static IGenericsRecord loadGenericsRecord(IRetrieval retrieval) {
        return retrieval == null ? null : retrieval.genericsRecord();
    }
}
