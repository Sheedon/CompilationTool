package org.sheedon.compilationtool.retrieval;

import org.sheedon.compilationtool.retrieval.core.AbstractGenericsRetrieval;
import org.sheedon.compilationtool.retrieval.core.IGenericsRecord;
import org.sheedon.compilationtool.retrieval.core.IRetrieval;
import org.sheedon.compilationtool.retrieval.core.RetrievalClassModel;
import org.sheedon.compilationtool.utils.ClassUtils;
import org.sheedon.compilationtool.utils.GenericsRecordUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

/**
 * 「泛型接口」的检索类，在处理编译时信息时，若我们的目标接口包含泛型，那么为了关联「泛型类型」和「数据类型」，
 * 这必然通过套泛型的检索算法来获取「泛型类型」所指向的「数据类型」，这里采用的是鱼骨优先检索，
 * 如鱼骨，有一根主鱼骨（继承的类），和鱼骨延伸的小鱼骨（类上实现的接口），再加上鱼刺（接口继承的接口）
 * 优先核实主鱼骨和延伸小鱼骨（当前类的形式父类和形式接口），是否已存在「目标泛型存储数据」，没有则优先搜索父类，
 * 依旧没有搜索到，则检索接口，并层级向上检索接口继承的接口
 * 例如：
 * <code>
 * // 我们设置的目标接口
 * public interface TargetInterface<T,K>{
 * }
 * <p>
 * // 直接继承自目标类的包装类
 * public interface ParentInterface<K,Model> extends List<K>, TargetInterface<K,String>{
 * }
 * // 实际使用的类
 * public class CurrentClass extends ParentClass<Org,UserModel> implements ParentInterface<String,UserModel>{
 * }
 * </code>
 * 检索的逻辑为，从当前类开始向父类依次检索，直至搜索到「目标类」或「被检查过」的类为止，
 * 若搜索不到，则代表当前类不在我们目标检索的类范围，反馈null，否则得到搜索到的结果返回调用者。
 * 在这里，我采用的是，优先检索当前类的形式父类和形式接口，若查不到，则到实际父类中再度按此策略检索，直至到根节点为止。
 * 若都搜索不到，则按照由根节点的形式接口遍历深度搜索，我把这种搜索方式叫做「鱼骨优先搜索」。
 * 主鱼骨：类的继承关系。
 * 从主鱼骨延伸的鱼刺骨：实现的接口。
 * 其他未由鱼骨延伸的小鱼刺：接口的继承关系。
 * 所以这里的检索逻辑是：全局深搜，局部广搜，节点延伸再度深搜（广搜可能更好）。
 * <p>
 * 在此，我的实现逻辑分为如下步骤：
 * 0.核实当前类是否被检索过，优化效率。
 * 1.检索记录当前类信息
 * 1.1 加载形式父类泛型存储信息，存在则直接关联到当前类上，无需再度向上检索，不存在继续向下执行。
 * 1.2 加载形式实现接口存储信息，存在则直接关联到当前类上，无需再度向上检索，不存在继续向下执行。
 * 1.3 核实形式接口是否是目标接口，是则与当前类关联，否则继续向下执行。
 * 1.4 形式类是否在排除包中，是则说明搜索不到，否则搜索该形式父类的实际类，递归拿到数据（回到0，检索父类信息）。
 * 1.4 形式接口是否在排除包中，是则说明搜索不到，否则搜索该形式接口的实际接口，递归拿到数据，
 * 若该接口延伸拿不到，再检索其他接口（回到0，检索父类信息）。
 * 1.5 都拿不到则返回null
 * 2.关联行为，分为「同类-层级关联」和「继承类-坐标关联」
 * 2.1 同类-层级关联：
 * 在 ParentInterface 接口中的 形式接口 TargetInterface<K,String> 和 实际接口 TargetInterface<T,K> 进行泛型关联。
 * 情况一：将泛型类型和泛型类型关联，TargetInterface<K,String>中的K 和 TargetInterface<T,K> 的T关联。
 * 情况二：将数据类型 String 与 K 绑定。
 * 由此，在 ParentInterface 实际关联的 TargetInterface 泛型为 T 和 K=String
 * 2.2 继承类-坐标关联:
 * 同样在 ParentInterface 类中，ParentInterface<K,Model> 和 TargetInterface<K,String> 要进行位置上的关联。
 * 由 2.1 可知 TargetInterface<K,String> 中的K，实际上是「目标类」中的T，
 * 所以对等，要将 ParentInterface 等效为T，从而记录格式并且向子类传递。
 * <p>
 * 由以上3步，便可得到当前类所关联的目标类的定义信息。
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
     * 3.核实接口是否已加载泛型存储数据，有则根据是否泛型填充完整，做关联行为，同2。
     * 4.核实是否是目标节点接口，是则关联泛型信息，并且返回。
     * 5.核实父类是否是在过滤包中，不是则检索形式父类的实际类，回到 searchClassGenerics()方法。
     * 5.核实接口是否是在过滤包中，不是则检索形式接口的实际类，回到 searchClassGenerics()方法。
     * 6.从父类/接口得到数据后，将泛型数据关联「同类-层级关联」+「继承类-坐标关联」。
     * 7.都拿不到则返回null
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

        // 形式父类信息，若形式父类数据存在「泛型检索信息」则直接返回。
        TypeMirror superTypeMirror = element.getSuperclass();
        String superclassName = null;
        if (superTypeMirror != null) {

            Element superElement = types.asElement(superTypeMirror);
            superclassName = ClassUtils.loadQualifiedName(superElement);
            RetrievalClassModel checkLoaded = GenericsRecordUtils.checkTypeElementAndLoaded(superElement, classMap, currentModel, element, retrieval);
            if (checkLoaded != null) {
                return checkLoaded;
            }
        }

        // 目标节点（目标接口）
        String targetClassName = retrieval.canonicalName();
        // 形式接口信息
        List<? extends TypeMirror> interfaces = element.getInterfaces();
        for (TypeMirror mirror : interfaces) {
            // 在形式接口上是否已经加载泛型存储数据
            Element interfaceElement = types.asElement(mirror);
            RetrievalClassModel checkLoaded = GenericsRecordUtils.checkTypeElementAndLoaded(interfaceElement, classMap,
                    currentModel, element, retrieval);
            if (checkLoaded != null) {
                return checkLoaded;
            }

            // 检索父类是否是目标类，不是则检索下一个
            String interfaceName = ClassUtils.loadQualifiedName(interfaceElement);
            if (!Objects.equals(interfaceName, targetClassName)) {
                continue;
            }

            // 目标节点
            RetrievalClassModel nodeClass = GenericsRecordUtils.traverseTargetGenerics(mirror, qualifiedName, retrieval);
            if (nodeClass != null) {
                GenericsRecordUtils.appendBindPosition(nodeClass, element.getTypeParameters());
                return nodeClass;
            }
        }


        // 检索类——根节点/需要过滤的节点，不是则检索到实际父类上
        if (superclassName != null) {
            Set<String> filterablePackages = retrieval.filterablePackages();
            long count = filterablePackages.stream().filter(superclassName::startsWith).count();
            if (count == 0) {
                // 得到父类检索信息
                RetrievalClassModel superClassModel = searchGenerics((TypeElement) types.asElement(superTypeMirror), types);
                if (superClassModel != null) {
                    return GenericsRecordUtils.traverseNodeAndBindPosition(superTypeMirror, currentModel, superClassModel, element, retrieval);
                }
            }
        }

        // 检索接口——根节点/需要过滤的节点，不是则检索到实际接口上
        for (TypeMirror typeMirror : interfaces) {
            String interfaceName = ClassUtils.loadQualifiedName(types.asElement(typeMirror));
            if (interfaceName == null) {
                continue;
            }

            Set<String> filterablePackages = retrieval.filterablePackages();
            long count = filterablePackages.stream().filter(interfaceName::startsWith).count();
            if (count == 0) {
                // 得到接口检索信息
                RetrievalClassModel interfaceClassModel = searchGenerics((TypeElement) types.asElement(typeMirror), types);
                if (interfaceClassModel != null) {
                    return GenericsRecordUtils.traverseNodeAndBindPosition(typeMirror, currentModel, interfaceClassModel, element, retrieval);
                }
            }
        }
        return null;
    }
}
