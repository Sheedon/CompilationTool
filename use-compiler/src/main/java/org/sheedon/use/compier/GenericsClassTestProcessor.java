package org.sheedon.use.compier;

import com.google.auto.service.AutoService;

import org.sheedon.annotation.GenericsClassTest;
import org.sheedon.compilationtool.retrieval.ClassGenericsRetrieval;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * 泛型检索测试
 *
 * @Author: sheedon
 * @Email: sheedonsun@163.com
 * @Date: 2022/1/2 12:16 上午
 */
@AutoService(Processor.class)
public class GenericsClassTestProcessor extends AbstractProcessor {
    private Messager mMessager;
    private Filer mFiler;
    private Elements mElementUtils;
    private Types mTypeUtils;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        mMessager = processingEnv.getMessager();
        mFiler = processingEnv.getFiler();
        mElementUtils = processingEnv.getElementUtils();
        mTypeUtils = processingEnv.getTypeUtils();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        HashSet<String> supportTypes = new LinkedHashSet<>();
        supportTypes.add(GenericsClassTest.class.getCanonicalName());
        return supportTypes;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        if (annotations == null || annotations.isEmpty()) {
            return false;
        }

        ClassGenericsRetrievalTest test = new ClassGenericsRetrievalTest();

        ClassGenericsRetrieval retrieval = new ClassGenericsRetrieval(test);
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(GenericsClassTest.class);
        for (Element element : elements) {
            retrieval.searchGenerics((TypeElement) element, mTypeUtils);
        }

        System.out.println(test.retrievalClassMap());
        return true;
    }
}
