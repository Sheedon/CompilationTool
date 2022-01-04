# CompilationTool
`CompilationTool` 是一个用来编写 `APT(Annotation Process Tool)` 的工具库。

在执行诸如注释处理或与元数据文件交互等操作时，将 `javax.lang.model.element.Element` 所延伸的类 `VariableElement`、`TypeParameterElement`、`TypeElement`、`PackageElement` 和 `ExecutableElement` ，所对应于类中重要元素提取，方便日常开发。



## 当前支持

- [x] 类/接口元素 TypeElement —— **TypeBuilder**
- [x] 字段元素 VariableElement —— **FieldBuilder**
- [x] 方法元素 ExecutableElement —— **MethodBuilder**
- [x] 形式参数 TypeParameterElement —— **TypeParameterBuilder**
- [x] 泛型类检索 **ClassGenericsRetrieval**
- [x] 泛型接口检索 **InterfaceGenericsRetrieval**



## 功能介绍

### 基础元素

四个元素的关系是「类/TypeBuilder」 包含「字段/FieldBuilder」和「方法/MethodBuilder」。

相对的「字段/FieldBuilder」和「方法/MethodBuilder」也持有了「类」的引用。

同时「方法/MethodBuilder」上还包含了「形参/TypeParameterBuilder」。

以上就是这四个就是在 **APT** 编写中最常用到的元素。

![基础元素](https://raw.githubusercontent.com/Sheedon/CompilationTool/dd59a959a6b6b2014226acff9915a3370aa7b8d9/resource/%E5%9F%BA%E7%A1%80%E5%85%83%E7%B4%A0.svg)

### 泛型检索

定义泛型的位置包括：类、接口和方法。

首先我们可以注意到「泛型方法」，是在调用方法的时候指明泛型的具体类型，如下面这个方法所示。所以我们在「APT」编写时，需要的是实例化的时候所关联上的泛型实际类型，而不是调用时才知名，所以获取实际泛型方法类型是没有必要的。

![methodGenerics](https://github.com/Sheedon/CompilationTool/blob/master/resource/methodGenerics.png?raw=true)

那么，能够在实例化类的时候指明泛型的具体类型的是什么？

答案其实很清楚——**泛型类** 和 **泛型接口** 。所以我这里提供了两个类，来分别满足  `泛型类的检索` 和 `泛型接口的检索`。



#### 泛型类检索

> 「泛型类」的检索类，在处理编译时信息时，若我们的目标类包含泛型，那么为了关联「泛型类型」和「数据类型」，这必然通过套泛型的检索算法来获取「泛型类型」所指向的「数据类型」。

例如如下三个类：

```java
// 我们设置的目标类
public class TargetClass<T,K>{
}

// 直接继承自目标类的包装类
public class ParentClass<K,Model> extends TargetClass<K,String>{
}

// 实际使用的类
public class CurrentClass extends ParentClass<Org,UserModel>{
}
```

我们的目的是将 `CurrentClass` 于 `TargetClass` 泛型类进行关联，这必然牵扯到两种关联行为「同类-层级关联」和「继承类-坐标关联」。

##### 同类-层级关联

在 **ParentClass** 类中的 形式类 `TargetClass<K,String>` 和 实际类 `TargetClass<T,K>` 进行泛型关联。
情况一：将泛型类型和泛型类型关联，`TargetClass<K,String>` 中的 **K** 和 `TargetClass<T,K>` 的 **T** 关联。
情况二：将数据类型 **String** 与 **K** 绑定。
由此，在 `ParentClass` 实际关联的 `TargetClass` 泛型为 *T* 和 *K=String* 。

##### 继承类-坐标关联

同样在 **ParentClass** 类中，`ParentClass<K,Model>` 和 `TargetClass<K,String>` 要进行位置上的关联。
由 **「同类-层级关联」** 可知 `TargetClass<K,String>` 中的 **K** ，实际上是`「目标类」`中的 **T** ，
所以对等，要将 **ParentClass** 中的 **K** 等效为 **T**，从而记录格式并且向子类传递。

##### 完整流程如下：

![ClassGenericsRetrieval](https://raw.githubusercontent.com/Sheedon/CompilationTool/56115f0c11a696ec24de1cb87903506b2479d1c3/resource/ClassGenericsRetrieval.svg)





#### 泛型接口检索

> 「泛型接口」的检索类，在处理编译时信息时，若我们的目标接口包含泛型，那么为了关联「泛型类型」和「数据类型」，这必然通过套泛型的检索算法来获取「泛型类型」所指向的「数据类型」，这里采用的是鱼骨优先检索。

例如如下三个类：

```java
 // 我们设置的目标接口
 public interface TargetInterface<T,K>{
 }
 
 // 直接继承自目标类的包装类
 public interface ParentInterface<K,Model> extends List<K>, TargetInterface<K,String>{
 }
 
 // 实际使用的类
 public class CurrentClass extends ParentClass<Org,UserModel> implements ParentInterface<String,UserModel>{
 }
```

我们的目的是将 `CurrentClass` 于 `TargetInterface` 泛型类进行关联，这必然牵扯到两种关联行为「同类-层级关联」和「继承类-坐标关联」，这与「泛型类检索」一致。

**但是「泛型接口」与「泛型类」在搜索方法上存在差异，「泛型类」和「当前类」的关系就是 `继承` ，呈现的是【链式关系】，所以采用「深度优先搜索 (DFS) 」就能解决。然而，「泛型接口」和「当前类」的关系呢，是实现，实际上采用的是【树状关系】，那仅仅使用深搜就不太合适了。**

采用搜索方法为：全局深搜，局部广搜，节点延伸再度深搜（广搜可能更好）。我把这称为「鱼骨优先搜索」。

如同鱼骨，有一根主鱼骨（继承的类），和鱼骨延伸的小鱼骨（类上实现的接口），再加上鱼刺（接口继承的接口）
优先核实主鱼骨和延伸小鱼骨（当前类的形式父类和形式接口），是否已存在「目标泛型存储数据」，没有则优先搜索父类，
依旧没有搜索到，则检索接口，并层级向上检索接口继承的接口。

##### 完整流程如下：

![InterfaceGenericsRetrieval](https://raw.githubusercontent.com/Sheedon/CompilationTool/56115f0c11a696ec24de1cb87903506b2479d1c3/resource/InterfaceGenericsRetrieval.svg)



## 使用方式

### 第一步：将 JitPack 存储库添加到您的构建文件中

```groovy
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

### 第二步：添加工具库

```groovy
dependencies {
		implementation 'com.github.Sheedon:CompilationTool:0.1.1-SNAPSHOT'
}
```

### 第三步：使用库

#### 1. 创建泛型检索者

```java
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
```

#### 2. 使用「泛型类检索」

```java
// 创建泛型检索配置信息
ClassGenericsRetrievalTest test = new ClassGenericsRetrievalTest();
// 创建泛型类检索者
ClassGenericsRetrieval retrieval = new ClassGenericsRetrieval(test);
// 上面两者，若只是解析一个目标泛型类，只需创建一个就足够了
Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(GenericsClassTest.class);
for (Element element : elements) {
  	// 检索行为
    retrieval.searchGenerics((TypeElement) element, mTypeUtils);
}
// 得到检索结果
System.out.println(test.retrievalClassMap());
```

#### 3. 使用「泛型接口检索」

```java
// 创建泛型检索配置信息
ClassGenericsRetrievalTest test = new ClassGenericsRetrievalTest();
// 创建泛型接口检索者
InterfaceGenericsRetrieval retrieval = new InterfaceGenericsRetrieval(test);
// 上面两者，若只是解析一个目标泛型接口，只需创建一个就足够了
Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(GenericsClassTest.class);
for (Element element : elements) {
  	// 检索行为
    retrieval.searchGenerics((TypeElement) element, mTypeUtils);
}
// 得到检索结果
System.out.println(test.retrievalClassMap());
```

