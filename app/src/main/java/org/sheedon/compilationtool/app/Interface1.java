package org.sheedon.compilationtool.app;

import org.sheedon.annotation.TargetInterface;

import java.util.List;

/**
 * java类作用描述
 *
 * @Author: sheedon
 * @Email: sheedonsun@163.com
 * @Date: 2022/1/3 12:42 上午
 */
public interface Interface1<K, T> extends List<K>, TargetInterface<K, T> {
}
