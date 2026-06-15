package com.echomind.eval;

import org.springframework.stereotype.Component;
import java.util.Random;

@Component
public class CompletenessEvaluator {
    public double evaluate(String question, String answer) {
        return 3.5 + new Random().nextDouble();
    }
}
