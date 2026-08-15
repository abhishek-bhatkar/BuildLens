package com.example.b;

import com.example.a.Calculator;

public class Total {
    public static int total(int a, int b, int c) {
        return Calculator.add(Calculator.add(a, b), c);
    }
}
