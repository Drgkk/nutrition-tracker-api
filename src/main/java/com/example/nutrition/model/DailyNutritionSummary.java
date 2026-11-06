package com.example.nutrition.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyNutritionSummary(
        LocalDate date,
        int mealCount,
        BigDecimal totalCalories,
        BigDecimal totalProteinGrams,
        BigDecimal totalCarbohydratesGrams,
        BigDecimal totalFatGrams,
        BigDecimal proteinCaloriePercentage,
        BigDecimal carbohydratesCaloriePercentage,
        BigDecimal fatCaloriePercentage,
        BigDecimal targetCalories,
        BigDecimal calorieDifference,
        String caloricStatus
) {
}