package com.example.pfe.enums;


public enum EvaluationScore {
    EXCELLENT(5),
    VERY_GOOD(4),
    GOOD(3),
    AVERAGE(2),
    NEEDS_IMPROVEMENT(1);

    private final int value;

    EvaluationScore(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}

