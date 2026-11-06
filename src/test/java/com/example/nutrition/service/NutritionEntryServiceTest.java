package com.example.nutrition.service;

import com.example.nutrition.exception.ResourceNotFoundException;
import com.example.nutrition.model.DailyNutritionSummary;
import com.example.nutrition.model.NutritionEntry;
import com.example.nutrition.model.NutritionEntryRequest;
import com.example.nutrition.repository.NutritionEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NutritionEntryServiceTest {
    @Mock
    private NutritionEntryRepository repository;

    @InjectMocks
    private NutritionEntryService service;

    @Test
    void findByIdReturnsTheEntryWhenItExists() {
        NutritionEntry entry = entry(7);
        when(repository.findById(7)).thenReturn(Optional.of(entry));

        assertEquals(entry, service.findById(7));
    }

    @Test
    void findByIdThrowsWhenTheEntryDoesNotExist() {
        when(repository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.findById(99));
    }

    @Test
    void createDelegatesToTheRepository() {
        NutritionEntryRequest request = request();
        NutritionEntry expected = entry(1);
        when(repository.create(request)).thenReturn(expected);

        assertEquals(expected, service.create(request));
        verify(repository).create(request);
    }

    @Test
    void logScaledPortionScalesMacrosAndDelegatesToRepository() {
        NutritionEntry base = entry(7);
        LocalDateTime portionTime = LocalDateTime.parse("2026-11-06T18:00:00");
        NutritionEntryRequest expectedRequest = new NutritionEntryRequest(
                "Oatmeal (x1.5)",
                new BigDecimal("480.00"),
                new BigDecimal("21.00"),
                new BigDecimal("72.00"),
                new BigDecimal("12.00"),
                portionTime
        );
        NutritionEntry created = new NutritionEntry(
                8,
                "Oatmeal (x1.5)",
                new BigDecimal("480.00"),
                new BigDecimal("21.00"),
                new BigDecimal("72.00"),
                new BigDecimal("12.00"),
                portionTime
        );

        when(repository.findById(7)).thenReturn(Optional.of(base));
        when(repository.create(expectedRequest)).thenReturn(created);

        NutritionEntry result = service.logScaledPortion(7, new BigDecimal("1.50"), portionTime);

        assertEquals(created, result);
        verify(repository).create(expectedRequest);
    }

    @Test
    void logScaledPortionRejectsInvalidScaleFactor() {
        assertThrows(IllegalArgumentException.class,
                () -> service.logScaledPortion(7, BigDecimal.ZERO, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.logScaledPortion(7, new BigDecimal("-1.0"), null));
        assertThrows(IllegalArgumentException.class,
                () -> service.logScaledPortion(7, new BigDecimal("10.01"), null));
    }

    @Test
    void getDailySummaryCalculatesMacroPercentagesAndStatus() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        List<NutritionEntry> entries = List.of(
                new NutritionEntry(1, "Meal 1", new BigDecimal("500.00"),
                        new BigDecimal("25.00"), new BigDecimal("75.00"),
                        new BigDecimal("11.11"), date.atTime(8, 0)),
                new NutritionEntry(2, "Meal 2", new BigDecimal("700.00"),
                        new BigDecimal("50.00"), new BigDecimal("75.00"),
                        new BigDecimal("22.22"), date.atTime(14, 0))
        );
        when(repository.findByDate(date)).thenReturn(entries);

        DailyNutritionSummary surplusSummary = service.getDailySummary(date, new BigDecimal("1000.00"));
        assertEquals(2, surplusSummary.mealCount());
        assertEquals(new BigDecimal("1200.00"), surplusSummary.totalCalories());
        assertEquals(new BigDecimal("75.00"), surplusSummary.totalProteinGrams());
        assertEquals(new BigDecimal("150.00"), surplusSummary.totalCarbohydratesGrams());
        assertEquals(new BigDecimal("33.33"), surplusSummary.totalFatGrams());
        assertEquals(new BigDecimal("25.00"), surplusSummary.proteinCaloriePercentage());
        assertEquals(new BigDecimal("50.00"), surplusSummary.carbohydratesCaloriePercentage());
        assertEquals(new BigDecimal("25.00"), surplusSummary.fatCaloriePercentage());
        assertEquals(new BigDecimal("200.00"), surplusSummary.calorieDifference());
        assertEquals("SURPLUS", surplusSummary.caloricStatus());

        DailyNutritionSummary onTargetSummary = service.getDailySummary(date, new BigDecimal("1230.00"));
        assertEquals("ON_TARGET", onTargetSummary.caloricStatus());

        DailyNutritionSummary deficitSummary = service.getDailySummary(date, new BigDecimal("1800.00"));
        assertEquals("DEFICIT", deficitSummary.caloricStatus());
    }

    @Test
    void getDailySummaryHandlesEmptyDayGracefully() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        when(repository.findByDate(date)).thenReturn(List.of());

        DailyNutritionSummary summary = service.getDailySummary(date, new BigDecimal("2500.00"));

        assertEquals(0, summary.mealCount());
        assertEquals(new BigDecimal("0.00"), summary.totalCalories());
        assertEquals(new BigDecimal("0.00"), summary.proteinCaloriePercentage());
        assertEquals(new BigDecimal("-2500.00"), summary.calorieDifference());
        assertEquals("DEFICIT", summary.caloricStatus());
    }

    @Test
    void getDailySummaryRejectsNonPositiveTargetCalories() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        assertThrows(IllegalArgumentException.class,
                () -> service.getDailySummary(date, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> service.getDailySummary(date, new BigDecimal("-500.00")));
    }

    @Test
    void searchDelegatesToRepositoryAndValidatesMinProtein() {
        LocalDate date = LocalDate.of(2026, 11, 6);
        List<NutritionEntry> expected = List.of(entry(1));
        when(repository.search("Oat", new BigDecimal("10.00"), date)).thenReturn(expected);

        assertEquals(expected, service.search("Oat", new BigDecimal("10.00"), date));

        assertThrows(IllegalArgumentException.class,
                () -> service.search("Oat", new BigDecimal("-1.00"), date));
    }

    @Test
    void updateReturnsTheUpdatedEntry() {
        NutritionEntryRequest request = request();
        NutritionEntry expected = entry(7);
        when(repository.update(7, request)).thenReturn(true);
        when(repository.findById(7)).thenReturn(Optional.of(expected));

        assertEquals(expected, service.update(7, request));
    }

    @Test
    void updateThrowsWhenTheEntryDoesNotExist() {
        when(repository.update(99, request())).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.update(99, request()));
    }

    @Test
    void deleteThrowsWhenTheEntryDoesNotExist() {
        when(repository.deleteById(99)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.delete(99));
    }

    @Test
    void deleteDelegatesWhenTheEntryExists() {
        when(repository.deleteById(7)).thenReturn(true);

        service.delete(7);

        verify(repository).deleteById(7);
    }

    private static NutritionEntry entry(long id) {
        return new NutritionEntry(id, "Oatmeal", new BigDecimal("320.00"),
                new BigDecimal("14.00"), new BigDecimal("48.00"),
                new BigDecimal("8.00"), LocalDateTime.parse("2026-11-06T07:30:00"));
    }

    private static NutritionEntryRequest request() {
        return new NutritionEntryRequest("Oatmeal", new BigDecimal("320.00"),
                new BigDecimal("14.00"), new BigDecimal("48.00"),
                new BigDecimal("8.00"), LocalDateTime.parse("2026-11-06T07:30:00"));
    }
}