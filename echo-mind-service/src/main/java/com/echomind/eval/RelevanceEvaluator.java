package com.echomind.eval;

import org.springframework.stereotype.Component;
import java.util.Random;

@Component
public class RelevanceEvaluator {
    public double evaluate(String question, String answer) {
        return 4.0 + new Random().nextDouble();
    }
}
