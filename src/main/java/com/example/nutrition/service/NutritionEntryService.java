package com.example.nutrition.service;

import com.example.nutrition.exception.ResourceNotFoundException;
import com.example.nutrition.model.DailyNutritionSummary;
import com.example.nutrition.model.NutritionEntry;
import com.example.nutrition.model.NutritionEntryRequest;
import com.example.nutrition.repository.NutritionEntryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class NutritionEntryService {
    private static final BigDecimal KCAL_PER_GRAM_PROTEIN = BigDecimal.valueOf(4);
    private static final BigDecimal KCAL_PER_GRAM_CARB = BigDecimal.valueOf(4);
    private static final BigDecimal KCAL_PER_GRAM_FAT = BigDecimal.valueOf(9);
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TARGET_TOLERANCE_KCAL = BigDecimal.valueOf(50);

    private final NutritionEntryRepository repository;

    public NutritionEntryService(NutritionEntryRepository repository) {
        this.repository = repository;
    }

    public List<NutritionEntry> findAll() {
        return repository.findAll();
    }

    public NutritionEntry findById(long id) {
        return repository.findById(id)
                .orElseThrow(() -> notFound(id));
    }

    public List<NutritionEntry> search(String foodName, BigDecimal minProtein, LocalDate date) {
        if (minProtein != null && minProtein.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("minProtein must not be negative");
        }
        return repository.search(foodName, minProtein, date);
    }

    public NutritionEntry create(NutritionEntryRequest request) {
        return repository.create(request);
    }

    public NutritionEntry logScaledPortion(long id, BigDecimal scale, LocalDateTime consumedAt) {
        if (scale == null || scale.compareTo(BigDecimal.ZERO) <= 0 || scale.compareTo(BigDecimal.TEN) > 0) {
            throw new IllegalArgumentException("Portion scale must be between 0.01 and 10.00");
        }

        NutritionEntry base = findById(id);
        LocalDateTime timestamp = (consumedAt != null)
                ? consumedAt
                : LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        NutritionEntryRequest scaledRequest = new NutritionEntryRequest(
                base.foodName() + " (x" + scale.stripTrailingZeros().toPlainString() + ")",
                scaleValue(base.calories(), scale),
                scaleValue(base.proteinGrams(), scale),
                scaleValue(base.carbohydratesGrams(), scale),
                scaleValue(base.fatGrams(), scale),
                timestamp
        );

        return repository.create(scaledRequest);
    }

    public DailyNutritionSummary getDailySummary(LocalDate date, BigDecimal targetCalories) {
        if (targetCalories == null || targetCalories.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("targetCalories must be greater than 0");
        }

        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        List<NutritionEntry> entries = repository.findByDate(targetDate);

        BigDecimal totalCalories = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalProtein = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCarbs = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalFat = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (NutritionEntry entry : entries) {
            totalCalories = totalCalories.add(entry.calories());
            totalProtein = totalProtein.add(entry.proteinGrams());
            totalCarbs = totalCarbs.add(entry.carbohydratesGrams());
            totalFat = totalFat.add(entry.fatGrams());
        }

        BigDecimal proteinKcal = totalProtein.multiply(KCAL_PER_GRAM_PROTEIN);
        BigDecimal carbKcal = totalCarbs.multiply(KCAL_PER_GRAM_CARB);
        BigDecimal fatKcal = totalFat.multiply(KCAL_PER_GRAM_FAT);
        BigDecimal macroTotalKcal = proteinKcal.add(carbKcal).add(fatKcal);

        BigDecimal proteinPct = percentage(proteinKcal, macroTotalKcal);
        BigDecimal carbPct = percentage(carbKcal, macroTotalKcal);
        BigDecimal fatPct = percentage(fatKcal, macroTotalKcal);

        BigDecimal normalizedTarget = targetCalories.setScale(2, RoundingMode.HALF_UP);
        BigDecimal difference = totalCalories.subtract(normalizedTarget).setScale(2, RoundingMode.HALF_UP);

        String status;
        if (difference.abs().compareTo(TARGET_TOLERANCE_KCAL) <= 0) {
            status = "ON_TARGET";
        } else if (difference.compareTo(BigDecimal.ZERO) > 0) {
            status = "SURPLUS";
        } else {
            status = "DEFICIT";
        }

        return new DailyNutritionSummary(
                targetDate,
                entries.size(),
                totalCalories,
                totalProtein,
                totalCarbs,
                totalFat,
                proteinPct,
                carbPct,
                fatPct,
                normalizedTarget,
                difference,
                status
        );
    }

    public NutritionEntry update(long id, NutritionEntryRequest request) {
        if (!repository.update(id, request)) {
            throw notFound(id);
        }
        return findById(id);
    }

    public void delete(long id) {
        if (!repository.deleteById(id)) {
            throw notFound(id);
        }
    }

    private BigDecimal scaleValue(BigDecimal value, BigDecimal factor) {
        return value.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal part, BigDecimal total) {
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return part.multiply(ONE_HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
    }

    private ResourceNotFoundException notFound(long id) {
        return new ResourceNotFoundException("Nutrition entry with id " + id + " was not found");
    }
}