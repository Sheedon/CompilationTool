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
 * 「接口上泛型」检索类，在处理编译时信息时，若我们的目标接口包含泛型，那么为了关联「泛型类型」和「数据类型」，
 * 这必然通过套泛型的检索算法来获取「泛型类型」所指向的「数据类型」，这里采用的是鱼骨优先检索，
 * 如鱼骨，有一根主鱼骨（继承的类），和鱼骨延伸的小鱼骨（类上实现的接口），再加上鱼刺（接口继承的接口）
 * 优先核实主鱼骨和延伸小鱼骨（当前类的形式父类和形式接口），是否已存在「目标泛型存储数据」，没有则优先搜索父类，
 * 依旧没有搜索到，则检索接口，并层级向上检索接口继承的接口
 * 例如：
 * <code>
 * // 我们设置的目标类
 * public class TargetClass<T,K>{
 * }
 * // 直接继承自目标类的包装类
 * public class ParentClass<K,Model> extends TargetClass<K,String>{
 * }
 * // 实际使用的类
 * public class CurrentClass extends ParentClass<Org,UserModel>{
 * }
 * </code>
 * 检索的逻辑为，从当前类开始向父类依次检索，直至搜索到「目标类」或「被检查过」的类为止，
 * 若搜索不到，则代表当前类不在我们目标检索的类范围，反馈null，否则得到搜索到的结果返回调用者。
 * 在此，我的实现逻辑分为如下步骤：
 * 0.核实当前类是否被检索过，优化效率。
 * 1.核实当前是类，并且父类存在，以确保功能搜索方向没有错（当前是「类上泛型」检索类）。
 * 2.检索记录当前类信息
 * 2.1 加载父类检索信息，存在则直接关联到当前类上，无需再度向上检索，不存在继续执行检索。
 * 2.2 核实是否是目标类，是则与当前类关联，否则继续检索。
 * 2.3 核实是否在排除包中，是则说明搜索不到，返回null，否则继续检索。
 * 2.4 回到0，检索父类信息
 * 3.关联行为，分为「同类-层级关联」和「继承类-坐标关联」
 * 3.1 同类-层级关联：
 * 在 ParentClass 类中的 形式类 TargetClass<K,String> 和 实际类 TargetClass<T,K> 进行泛型关联。
 * 情况一：将泛型类型和泛型类型关联，TargetClass<K,String>中的K 和 TargetClass<T,K> 的T关联。
 * 情况二：将数据类型 String 与 K 绑定。
 * 由此，在 ParentClass 实际关联的 TargetClass 泛型为 T 和 K=String
 * 3.2 继承类-坐标关联:
 * 同样在 ParentClass 类中，ParentClass<K,Model> 和 TargetClass<K,String> 要进行位置上的关联。
 * 由 3.1 可知 TargetClass<K,String> 中的K，实际上是「目标类」中的T，
 * 所以对等，要将 ParentClass中的K 等效为T，从而记录格式并且向子类传递。
 * <p>
 * 由以上4步，便可得到当前类所关联的目标类的定义信息。
 *
 * @Author: sheedon
 * @Email: sheedonsun@163.com
 * @Date: 2021/12/31 10:53 下午
 */
public class InterfaceGenericsRetrieval extends AbstractGenericsRetrieval {


    public InterfaceGenericsRetrieval(IRetrieval.AbstractRetrieval retrieval) {
        super(retrieval);
    }

    /**
     * 从当前类开始检索，层级向上，搜索到目标类为止，再层级返回填充泛型存储信息，
     * 最终将当前类的泛型关联信息作为结果输出。
     *
     * @param element 类型元素
     * @param types   类型工具类
     */
    @Override
    public RetrievalClassModel searchGenerics(TypeElement element, Types types) {

        // 当前类的全类名
        String qualifiedName = element.getQualifiedName().toString();

        // 核实当前类是否执行过检索
        Map<String, RetrievalClassModel> retrievalMap = retrieval.retrievalClassMap();
        // 当前的检索记录
        RetrievalClassModel currentModel = retrievalMap.get(qualifiedName);
        if (currentModel != null) {
            return currentModel;
        }

        // 构建当前泛型记录类
        currentModel = new RetrievalClassModel() {
            @Override
            protected IGenericsRecord createGenericsRecord() {
                return retrieval.genericsRecord();
            }
        };
        retrievalMap.put(qualifiedName, currentModel);


        // 检索得到当前类的泛型信息
        return retrievalCurrentClass(element, types);
    }

    /**
     * 检索得到当前类的泛型存储信息，需要得到当前类的泛型存储信息：
     * 1.父类必然是 TypeElement 类型元素。
     * 2.核实父类是否已加载泛型存储数据，有则根据是否泛型填充完整，做关联行为，
     * 若填充完整，则浅拷贝，共用同一个存储对象。
     * 若不完整，则深拷贝，防止更改时影响父类的存储元素。
     * 3.核实是否是目标节点类，是则遍历关联泛型信息。
     * 4.核实父类是否是在过滤包中，是则返回null，无需再次查找。
     * 5.父类信息检索不到，则向祖父类检索 回到 searchClassGenerics()方法
     * 6.从父类得到数据后，将泛型数据关联「同类-层级关联」+「继承类-坐标关联」。
     *
     * @param element 当前类的元素
     * @param types   类型工具
     * @return RetrievalClassModel 检索泛型数据信息
     */
    private RetrievalClassModel retrievalCurrentClass(TypeElement element, Types types) {
        Map<String, RetrievalClassModel> classMap = retrieval.retrievalClassMap();
        // 当前类的RetrievalClassModel
        String qualifiedName = element.getQualifiedName().toString();
        RetrievalClassModel currentModel = classMap.get(qualifiedName);

        // 形式父类信息
        TypeMirror superTypeMirror = element.getSuperclass();
        String superclassName = null;
        if (superTypeMirror != null) {

            Element superElement = types.asElement(superTypeMirror);
            superclassName = getClassName(superElement);
            RetrievalClassModel checkLoaded = checkTypeElementAndLoaded(superElement, classMap, currentModel, element);
            if (checkLoaded != null) {
                return checkLoaded;
            }
        }

        // 目标节点
        String targetClassName = retrieval.canonicalName();

        // 形式接口信息
        List<? extends TypeMirror> interfaces = element.getInterfaces();
        for (TypeMirror mirror : interfaces) {
            Element interfaceElement = types.asElement(mirror);
            RetrievalClassModel checkLoaded = checkTypeElementAndLoaded(interfaceElement, classMap,
                    currentModel, element);
            if (checkLoaded != null) {
                return checkLoaded;
            }

            String interfaceName = getClassName(interfaceElement);
            if (!Objects.equals(interfaceName, targetClassName)) {
                continue;
            }

            // 目标节点
            RetrievalClassModel nodeClass = traverseTargetGenerics(mirror, qualifiedName);
            if (nodeClass != null) {
                appendBindPosition(nodeClass, element.getTypeParameters());
                return nodeClass;
            }
        }


        // 根节点/需要过滤的节点
        if (superclassName != null) {
            Set<String> filterablePackages = retrieval.filterablePackages();
            long count = filterablePackages.stream().filter(superclassName::startsWith).count();
            if (count == 0) {
                // 得到父类检索信息
                RetrievalClassModel superClassModel = searchGenerics((TypeElement) types.asElement(superTypeMirror), types);
                if (superClassModel != null) {
                    return traverseNodeAndBindPosition(superTypeMirror, currentModel, superClassModel, element);
                }
            }
        }

        for (TypeMirror typeMirror : interfaces) {
            String interfaceName = getClassName(types.asElement(typeMirror));
            if (interfaceName == null) {
                continue;
            }

            Set<String> filterablePackages = retrieval.filterablePackages();
            long count = filterablePackages.stream().filter(interfaceName::startsWith).count();
            if (count == 0) {
                // 得到接口检索信息
                RetrievalClassModel interfaceClassModel = searchGenerics((TypeElement) types.asElement(typeMirror), types);
                if (interfaceClassModel != null) {
                    return traverseNodeAndBindPosition(typeMirror, currentModel, interfaceClassModel, element);
                }
            }
        }
        return null;
    }


    private RetrievalClassModel checkTypeElementAndLoaded(Element element,
                                                          Map<String, RetrievalClassModel> classMap,
                                                          RetrievalClassModel currentModel,
                                                          TypeElement currentElement) {
        if (element instanceof TypeElement) {
            String superclassName = ((TypeElement) element).getQualifiedName().toString();
            RetrievalClassModel superRetrievalModel = classMap.get(superclassName);

            // 核实，若存在，则直接返回
            return checkLoaded(superRetrievalModel, currentModel, currentElement);
        }

        return null;
    }

    private String getClassName(Element element) {
        if (!(element instanceof TypeElement)) {
            return null;
        }

        return ((TypeElement) element).getQualifiedName().toString();
    }

    /**
     * 先核实一步，若存在，可减少后续目标节点和过滤节点的盘点耗时
     *
     * @param superRetrievalModel 父类检索信息
     * @param currentModel        当前类的检索信息
     * @param element             当前类的类型元素
     * @return RetrievalClassModel 父类检索信息绑定到当前类
     */
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

        // 遍历节点，用于绑定坐标
        return traverseNodeAndBindPosition(element.getSuperclass(), currentModel, superRetrievalModel, element);
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

        // 「同类-层级关联」
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
     * 追加绑定泛型类型的位置，「继承类-坐标关联」
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
    private void traverseNodeGenerics(TypeMirror superTypeMirror,
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
                currentModel.addGenericsRecord(typeName, mirror.toString());
            } else {
                currentModel.recordType(mirror.toString(), typeName);
            }
        }

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
