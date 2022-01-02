package org.sheedon.compilationtool.app;

import android.view.View;

import org.sheedon.annotation.GenericsClassTest;
import org.sheedon.annotation.GenericsInterfaceTest;

/**
 * java类作用描述
 *
 * @Author: sheedon
 * @Email: sheedonsun@163.com
 * @Date: 2022/1/2 12:36 上午
 */
@GenericsInterfaceTest
public class Test6 extends Test5 implements View.OnClickListener,Interface3<String, String, Integer> {

    @Override
    public int compareTo(Integer o) {
        return 0;
    }

    @Override
    public void onClick(View v) {

    }
}
