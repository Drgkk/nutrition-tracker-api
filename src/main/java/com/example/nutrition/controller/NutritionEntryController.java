package com.example.nutrition.controller;

import com.example.nutrition.model.DailyNutritionSummary;
import com.example.nutrition.model.NutritionEntry;
import com.example.nutrition.model.NutritionEntryRequest;
import com.example.nutrition.service.NutritionEntryService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/nutrition-entries")
public class NutritionEntryController {
    private final NutritionEntryService service;

    public NutritionEntryController(NutritionEntryService service) {
        this.service = service;
    }

    @GetMapping
    public List<NutritionEntry> findAll() {
        return service.findAll();
    }

    @GetMapping("/search")
    public List<NutritionEntry> search(
            @RequestParam(required = false) String food,
            @RequestParam(required = false) BigDecimal minProtein,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.search(food, minProtein, date);
    }

    @GetMapping("/daily-summary")
    public DailyNutritionSummary getDailySummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "2500.00") BigDecimal targetCalories) {
        return service.getDailySummary(date, targetCalories);
    }

    @GetMapping("/{id}")
    public NutritionEntry findById(@PathVariable long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<NutritionEntry> create(@Valid @RequestBody NutritionEntryRequest request) {
        NutritionEntry created = service.create(request);
        return ResponseEntity.created(URI.create("/api/nutrition-entries/" + created.id()))
                .body(created);
    }

    @PostMapping("/{id}/portion")
    public ResponseEntity<NutritionEntry> logScaledPortion(
            @PathVariable long id,
            @RequestParam(defaultValue = "1.0") BigDecimal scale,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime consumedAt) {
        NutritionEntry created = service.logScaledPortion(id, scale, consumedAt);
        return ResponseEntity.created(URI.create("/api/nutrition-entries/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public NutritionEntry update(@PathVariable long id, @Valid @RequestBody NutritionEntryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}