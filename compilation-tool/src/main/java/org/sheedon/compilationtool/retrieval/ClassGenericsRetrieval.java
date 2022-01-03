package org.sheedon.compilationtool.retrieval;

import org.sheedon.compilationtool.retrieval.core.AbstractGenericsRetrieval;
import org.sheedon.compilationtool.retrieval.core.IGenericsRecord;
import org.sheedon.compilationtool.retrieval.core.IRetrieval;
import org.sheedon.compilationtool.retrieval.core.RetrievalClassModel;
import org.sheedon.compilationtool.utils.GenericsRecordUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * 「泛型类」的检索类，在处理编译时信息时，若我们的目标类包含泛型，那么为了关联「泛型类型」和「数据类型」，
 * 这必然通过套泛型的检索算法来获取「泛型类型」所指向的「数据类型」。
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
 * 1.核实当前是类，并且父类存在，以确保功能搜索方向没有错（当前是「泛型类」检索类）。
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
public class ClassGenericsRetrieval extends AbstractGenericsRetrieval {


    public ClassGenericsRetrieval(IRetrieval.AbstractRetrieval retrieval) {
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
        RetrievalClassModel checkLoaded = GenericsRecordUtils.checkLoaded(superRetrievalModel, currentModel, element, retrieval);
        if (checkLoaded != null) {
            return checkLoaded;
        }

        // 目标节点
        String targetClassName = retrieval.canonicalName();
        if (Objects.equals(superclassName, targetClassName)) {
            // 目标节点
            RetrievalClassModel nodeClass = GenericsRecordUtils.traverseTargetGenerics(superTypeMirror, qualifiedName, retrieval);
            if (nodeClass == null) {
                return null;
            }
            GenericsRecordUtils.appendBindPosition(nodeClass, element.getTypeParameters());
            return nodeClass;
        }


        // 根节点/需要过滤的节点
        Set<String> filterablePackages = retrieval.filterablePackages();
        for (String filterablePackage : filterablePackages) {
            if (superclassName.startsWith(filterablePackage)) {
                return null;
            }
        }

        // 得到父类检索信息
        RetrievalClassModel superClassModel = searchGenerics(superTypeElement, types);
        if (superClassModel == null) {
            return null;
        }

        // 将泛型数据关联「同类-层级关联」+「继承类-坐标关联」
        return GenericsRecordUtils.traverseNodeAndBindPosition(superTypeMirror, currentModel, superClassModel, element, retrieval);
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
}
