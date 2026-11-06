package com.example.nutrition.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record NutritionEntryRequest(
        @NotBlank(message = "Food name must not be blank")
        @Size(max = 120, message = "Food name must be at most 120 characters")
        String foodName,

        @NotNull(message = "Calories are required")
        @DecimalMin(value = "0.0", message = "Calories must not be negative")
        @Digits(integer = 7, fraction = 2, message = "Calories may have at most 7 integer and 2 decimal digits")
        BigDecimal calories,

        @NotNull(message = "Protein is required")
        @DecimalMin(value = "0.0", message = "Protein must not be negative")
        @Digits(integer = 5, fraction = 2, message = "Protein may have at most 5 integer and 2 decimal digits")
        BigDecimal proteinGrams,

        @NotNull(message = "Carbohydrates are required")
        @DecimalMin(value = "0.0", message = "Carbohydrates must not be negative")
        @Digits(integer = 5, fraction = 2, message = "Carbohydrates may have at most 5 integer and 2 decimal digits")
        BigDecimal carbohydratesGrams,

        @NotNull(message = "Fat is required")
        @DecimalMin(value = "0.0", message = "Fat must not be negative")
        @Digits(integer = 5, fraction = 2, message = "Fat may have at most 5 integer and 2 decimal digits")
        BigDecimal fatGrams,

        @NotNull(message = "Consumption date and time are required")
        LocalDateTime consumedAt
) {
}
