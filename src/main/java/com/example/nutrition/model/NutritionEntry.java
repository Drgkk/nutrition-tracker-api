package com.example.nutrition.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record NutritionEntry(
        long id,
        String foodName,
        BigDecimal calories,
        BigDecimal proteinGrams,
        BigDecimal carbohydratesGrams,
        BigDecimal fatGrams,
        LocalDateTime consumedAt
) {
}
