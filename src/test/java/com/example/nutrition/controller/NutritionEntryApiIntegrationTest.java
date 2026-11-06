package com.example.nutrition.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class NutritionEntryApiIntegrationTest {
    private static final String API = "/api/nutrition-entries";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void clearEntries() {
        jdbcClient.sql("TRUNCATE TABLE nutrition_entries RESTART IDENTITY").update();
    }

    @Test
    void supportsTheCompleteCrudLifecycle() throws Exception {
        mockMvc.perform(get(API))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        String location = mockMvc.perform(post(API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("Porridge", "350.00")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.foodName").value("Porridge"))
                .andReturn().getResponse().getHeader("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);

        mockMvc.perform(get(API + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calories").value(350.0));

        mockMvc.perform(put(API + "/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("Updated porridge", "410.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foodName").value("Updated porridge"))
                .andExpect(jsonPath("$.calories").value(410.0));

        mockMvc.perform(get(API))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].foodName").value("Updated porridge"));

        mockMvc.perform(delete(API + "/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(API + "/" + id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void calculatesDailySummaryAndCaloricBalance() throws Exception {
        mockMvc.perform(post(API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customRequest("Oatmeal", "500.00", "25.00", "75.00", "11.11", "2026-11-06T08:00:00")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customRequest("Chicken and Buckwheat", "700.00", "50.00", "75.00", "22.22", "2026-11-06T14:00:00")))
                .andExpect(status().isCreated());

        // Entry on a different date should not be included in 2026-11-06 summary
        mockMvc.perform(post(API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customRequest("Previous Day Snack", "300.00", "10.00", "30.00", "10.00", "2026-11-05T20:00:00")))
                .andExpect(status().isCreated());

        mockMvc.perform(get(API + "/daily-summary")
                        .param("date", "2026-11-06")
                        .param("targetCalories", "1000.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-11-06"))
                .andExpect(jsonPath("$.mealCount").value(2))
                .andExpect(jsonPath("$.totalCalories").value(1200.0))
                .andExpect(jsonPath("$.totalProteinGrams").value(75.0))
                .andExpect(jsonPath("$.totalCarbohydratesGrams").value(150.0))
                .andExpect(jsonPath("$.totalFatGrams").value(33.33))
                .andExpect(jsonPath("$.proteinCaloriePercentage").value(25.0))
                .andExpect(jsonPath("$.carbohydratesCaloriePercentage").value(50.0))
                .andExpect(jsonPath("$.fatCaloriePercentage").value(25.0))
                .andExpect(jsonPath("$.targetCalories").value(1000.0))
                .andExpect(jsonPath("$.calorieDifference").value(200.0))
                .andExpect(jsonPath("$.caloricStatus").value("SURPLUS"));
    }

    @Test
    void logsScaledPortionOfAnExistingEntry() throws Exception {
        String location = mockMvc.perform(post(API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customRequest("Baked Trout", "400.00", "40.00", "0.00", "24.00", "2026-11-06T13:00:00")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);

        mockMvc.perform(post(API + "/" + id + "/portion")
                        .param("scale", "1.5")
                        .param("consumedAt", "2026-11-06T19:30:00"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.foodName").value("Baked Trout (x1.5)"))
                .andExpect(jsonPath("$.calories").value(600.0))
                .andExpect(jsonPath("$.proteinGrams").value(60.0))
                .andExpect(jsonPath("$.carbohydratesGrams").value(0.0))
                .andExpect(jsonPath("$.fatGrams").value(36.0))
                .andExpect(jsonPath("$.consumedAt").value("2026-11-06T19:30:00"));
    }

    @Test
    void searchesEntriesByFoodNameMinProteinAndDate() throws Exception {
        mockMvc.perform(post(API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customRequest("Protein Oats", "450.00", "30.00", "55.00", "9.00", "2026-11-06T08:00:00")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customRequest("Apple Oats", "310.00", "8.00", "52.00", "5.00", "2026-11-06T10:00:00")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customRequest("Protein Oats", "450.00", "30.00", "55.00", "9.00", "2026-11-05T08:00:00")))
                .andExpect(status().isCreated());

        mockMvc.perform(get(API + "/search")
                        .param("food", "oats")
                        .param("minProtein", "20.00")
                        .param("date", "2026-11-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].foodName").value("Protein Oats"))
                .andExpect(jsonPath("$[0].proteinGrams").value(30.0));
    }

    @Test
    void rejectsInvalidNutritionValues() throws Exception {
        String invalidRequest = """
                {
                  "foodName": " ",
                  "calories": -10,
                  "proteinGrams": 5,
                  "carbohydratesGrams": 20,
                  "fatGrams": 2,
                  "consumedAt": "2026-11-06T08:00:00"
                }
                """;

        mockMvc.perform(post(API)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Request validation failed"));
    }

    @Test
    void rejectsInvalidQueryParameters() throws Exception {
        mockMvc.perform(post(API + "/1/portion")
                        .param("scale", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Invalid request parameter"));

        mockMvc.perform(get(API + "/daily-summary")
                        .param("targetCalories", "-100"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Invalid request parameter"));

        mockMvc.perform(get(API + "/search")
                        .param("minProtein", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Invalid request parameter"));
    }

    @Test
    void returnsProblemDetailsForAnUnknownId() throws Exception {
        mockMvc.perform(get(API + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));

        mockMvc.perform(put(API + "/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("No such meal", "100.00")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete(API + "/999"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(API + "/999/portion")
                        .param("scale", "1.5"))
                .andExpect(status().isNotFound());
    }

    private static String validRequest(String foodName, String calories) {
        return customRequest(foodName, calories, "12.50", "35.00", "6.00", "2026-11-06T08:00:00");
    }

    private static String customRequest(
            String foodName,
            String calories,
            String protein,
            String carbs,
            String fat,
            String consumedAt) {
        return """
                {
                  "foodName": "%s",
                  "calories": %s,
                  "proteinGrams": %s,
                  "carbohydratesGrams": %s,
                  "fatGrams": %s,
                  "consumedAt": "%s"
                }
                """.formatted(foodName, calories, protein, carbs, fat, consumedAt);
    }
}