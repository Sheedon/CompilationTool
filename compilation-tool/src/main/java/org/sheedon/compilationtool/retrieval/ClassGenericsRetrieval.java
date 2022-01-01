package org.sheedon.compilationtool.retrieval;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * 检索 绑定在类上的泛型
 *
 * @Author: sheedon
 * @Email: sheedonsun@163.com
 * @Date: 2021/12/31 10:53 下午
 */
public class ClassGenericsRetrieval extends AbstractGenericsRetrieval {


    public ClassGenericsRetrieval(IRetrieval.AbstractRetrieval retrieval) {
        super(retrieval);
    }

    /**
     * 搜索从当前类开始，层级向上，检索至目标类为止
     * 将RequestCard, ResponseModel所对应的「实体类全类名」绑定到泛型类型上，
     * 构建成RetrievalClassModel 存入classMap
     *
     * @param element 类型元素
     * @param types   类型工具类
     */
    @Override
    public RetrievalClassModel searchClassGenerics(TypeElement element, Types types) {

        // 当前类的全类名
        String qualifiedName = element.getQualifiedName().toString();

        // 核实当前类是否执行过检索
        Map<String, RetrievalClassModel> retrievalMap = retrieval.retrievalClassMap();
        // 当前的检索记录
        RetrievalClassModel currentModel = retrievalMap.get(qualifiedName);
        if (currentModel != null) {
            return currentModel;
        }

        // 当前类必需是类，并且父类必需存在，最终要继承目标类
        if (isInterfaceOrNotHasParentClass(element)) {
            return null;
        }

        // 构建当前泛型记录类
        currentModel = new RetrievalClassModel() {
            @Override
            protected IGenericsRecord createGenericsRecord() {
                return retrieval.genericsRecord();
            }
        };
        retrievalMap.putIfAbsent(qualifiedName, currentModel);


        // 检索得到当前类的泛型信息
        return retrievalCurrentClass(element, types);
    }

    /**
     * 检索得到当前类
     * 1。是否是目标类
     * 2。是否是剔除包下的类
     */
    private RetrievalClassModel retrievalCurrentClass(TypeElement element, Types types) {
        TypeMirror superTypeMirror = element.getSuperclass();
        if (superTypeMirror == null) {
            return null;
        }

        Element superElement = types.asElement(superTypeMirror);
        if (!(superElement instanceof TypeElement)) {
            return null;
        }

        // 获取父类信息
        Map<String, RetrievalClassModel> classMap = retrieval.retrievalClassMap();
        TypeElement superTypeElement = (TypeElement) superElement;
        // 父类RetrievalClassModel
        String superclassName = superTypeElement.getQualifiedName().toString();
        RetrievalClassModel superRetrievalModel = classMap.get(superclassName);
        // 当前类的RetrievalClassModel
        String qualifiedName = element.getQualifiedName().toString();
        RetrievalClassModel currentModel = classMap.get(qualifiedName);

        // 先核实一步，若存在，可减少后续目标节点和过滤节点的盘点耗时
        RetrievalClassModel checkLoaded = checkLoaded(superRetrievalModel, currentModel, element);
        if (checkLoaded != null) {
            return checkLoaded;
        }

        // 目标节点
        String targetClassName = retrieval.canonicalName();
        if (Objects.equals(superclassName, targetClassName)) {
            // 目标节点
            RetrievalClassModel nodeClass = traverseTargetGenerics(superTypeMirror, qualifiedName);
            if (nodeClass == null) {
                return null;
            }
            appendBindPosition(nodeClass, element.getTypeParameters());
            return nodeClass;
        }

        // 节点类型
        // 根节点
        Set<String> filterablePackages = retrieval.filterablePackages();
        for (String filterablePackage : filterablePackages) {
            if (superclassName.startsWith(filterablePackage)) {
                return null;
            }
        }

        // 得到父类检索信息
        RetrievalClassModel superClassModel = searchClassGenerics(superTypeElement, types);
        if (superClassModel == null) {
            return null;
        }

        return traverseNodeAndBindPosition(superTypeMirror, currentModel, superClassModel, element);
    }


    // 先核实一步，若存在，可减少后续目标节点和过滤节点的盘点耗时
    private RetrievalClassModel checkLoaded(RetrievalClassModel superRetrievalModel,
                                            RetrievalClassModel currentModel,
                                            TypeElement element) {
        if (superRetrievalModel == null) {
            return null;
        }

        // 是否填充完整，是则浅拷贝，并且返回
        if (superRetrievalModel.isCompeted()) {
            currentModel.bindGenericsRecord(currentModel.getRecord());
            return currentModel;
        }

        return traverseNodeAndBindPosition(element.getSuperclass(), currentModel, superRetrievalModel, element);
    }

    private RetrievalClassModel traverseNodeAndBindPosition(TypeMirror superTypeMirror,
                                                            RetrievalClassModel currentModel,
                                                            RetrievalClassModel superClassModel,
                                                            TypeElement element) {

        // 否则，采用深拷贝
        IGenericsRecord record = cloneBySuperRecord(superClassModel.getRecord());
        currentModel.bindGenericsRecord(record);

        traverseNodeGenerics(superTypeMirror, currentModel, superClassModel);
        appendBindPosition(currentModel, element.getTypeParameters());
        return currentModel;
    }

    /**
     * 当前是接口，或者不存在父类
     *
     * @param element 类型元素
     * @return 当前是接口，或者不存在父类
     */
    private boolean isInterfaceOrNotHasParentClass(TypeElement element) {
        return element.getKind().isInterface() || element.getSuperclass() == null;
    }

    /**
     * 遍历目标泛型集合
     * 将当前制定的实体类型 填充到
     * 目标类的泛型上
     * 流程
     * 1. 获取当前类的父类描述泛型
     * 2. 获取真实父类的泛型类型
     * 3. 匹配描述泛型kind == TypeKind.DECLARED，代表可以填充
     * 4. 不匹配的记录到对照表中
     */
    private RetrievalClassModel traverseTargetGenerics(TypeMirror superTypeMirror, String currentQualifiedName) {
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

        Map<String, RetrievalClassModel> classMap = retrieval.retrievalClassMap();
        RetrievalClassModel classModel = classMap.get(currentQualifiedName);
        for (int index = 0; index < superParameters.size(); index++) {
            TypeParameterElement element = superParameters.get(index);
            String typeName = element.asType().toString();

            TypeMirror mirror = typeArguments.get(index);
            if (mirror.getKind() == TypeKind.DECLARED) {
                classModel.addTargetGenericsRecord(typeName, mirror.toString());
            } else {
                classModel.recordType(mirror.toString(), RetrievalClassModel.PREFIX + typeName);
            }
        }
        return classModel;
    }


    /**
     * 追加绑定泛型类型的位置
     * AClass<T> extends BClass<T>{
     * <p>
     * }
     * 绑定T的位置为0
     *
     * @param nodeClass         节点Class信息
     * @param currentParameters 当前类的泛型参数 AClass<T> 中的T
     */
    private void appendBindPosition(RetrievalClassModel nodeClass, List<? extends TypeParameterElement> currentParameters) {
        int index = 0;
        for (TypeParameterElement parameter : currentParameters) {
            nodeClass.bindPosition(parameter.asType().toString(), index);
            index++;
        }
    }


    /**
     * 遍历目标泛型集合
     * 将
     * 1. 获取当前类的父类描述泛型
     * 2. 获取真实父类的泛型类型
     * 3. 匹配描述泛型kind == TypeKind.DECLARED，代表可以填充
     * 4. 不匹配的记录到对照表中
     */
    private RetrievalClassModel traverseNodeGenerics(TypeMirror superTypeMirror,
                                                     RetrievalClassModel currentModel,
                                                     RetrievalClassModel superClassModel) {
        if (!(superTypeMirror instanceof DeclaredType)) {
            return superClassModel;
        }

        DeclaredType declaredType = (DeclaredType) superTypeMirror;

        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();

        if (typeArguments.isEmpty()) {
            return superClassModel;
        }

        Set<Integer> positions = superClassModel.getPositions();
        for (Integer position : positions) {
            String typeName = superClassModel.getTypeNameByPosition(position);
            TypeMirror mirror = typeArguments.get(position);
            if (mirror.getKind() == TypeKind.DECLARED) {
                currentModel.addGenericsRecord(typeName, mirror.toString());
            } else {
                currentModel.recordType(mirror.toString(), typeName);
            }
        }

        return currentModel;
    }

    /**
     * 从父记录中拷贝泛型处理记录
     *
     * @param record 父泛型处理记录
     * @return 当前类的泛型处理记录
     */
    private IGenericsRecord cloneBySuperRecord(IGenericsRecord record) {
        if (record == null) {
            return retrieval.genericsRecord();
        }

        try {
            return record.clone();
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return retrieval.genericsRecord();
    }
}
