package org.sheedon.compilationtool.app;

import org.sheedon.annotation.GenericsClassTest;

import java.util.ArrayList;

/**
 * java类作用描述
 *
 * @Author: sheedon
 * @Email: sheedonsun@163.com
 * @Date: 2022/1/2 12:36 上午
 */
@GenericsClassTest
public class Test5 extends ArrayList<String>{

    private String name;
    private int age;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
