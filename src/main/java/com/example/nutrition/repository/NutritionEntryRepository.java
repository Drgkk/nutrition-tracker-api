package com.example.nutrition.repository;

import com.example.nutrition.model.NutritionEntry;
import com.example.nutrition.model.NutritionEntryRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class NutritionEntryRepository {
    private static final RowMapper<NutritionEntry> ROW_MAPPER = (resultSet, rowNumber) ->
            new NutritionEntry(
                    resultSet.getLong("id"),
                    resultSet.getString("food_name"),
                    resultSet.getBigDecimal("calories"),
                    resultSet.getBigDecimal("protein_g"),
                    resultSet.getBigDecimal("carbohydrates_g"),
                    resultSet.getBigDecimal("fat_g"),
                    resultSet.getTimestamp("consumed_at").toLocalDateTime()
            );

    private final JdbcClient jdbcClient;

    public NutritionEntryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<NutritionEntry> findAll() {
        return jdbcClient.sql("""
                SELECT id, food_name, calories, protein_g, carbohydrates_g, fat_g, consumed_at
                FROM nutrition_entries
                ORDER BY consumed_at DESC, id DESC
                """)
                .query(ROW_MAPPER)
                .list();
    }

    public Optional<NutritionEntry> findById(long id) {
        return jdbcClient.sql("""
                SELECT id, food_name, calories, protein_g, carbohydrates_g, fat_g, consumed_at
                FROM nutrition_entries
                WHERE id = :id
                """)
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    public List<NutritionEntry> findByDate(LocalDate date) {
        Timestamp startOfDay = Timestamp.valueOf(date.atStartOfDay());
        Timestamp startOfNextDay = Timestamp.valueOf(date.plusDays(1).atStartOfDay());

        return jdbcClient.sql("""
                SELECT id, food_name, calories, protein_g, carbohydrates_g, fat_g, consumed_at
                FROM nutrition_entries
                WHERE consumed_at >= :startOfDay
                  AND consumed_at < :startOfNextDay
                ORDER BY consumed_at ASC, id ASC
                """)
                .param("startOfDay", startOfDay)
                .param("startOfNextDay", startOfNextDay)
                .query(ROW_MAPPER)
                .list();
    }

    public List<NutritionEntry> search(String foodName, BigDecimal minProtein, LocalDate date) {
        String pattern = (foodName == null || foodName.isBlank())
                ? "%"
                : "%" + foodName.trim().toLowerCase() + "%";
        BigDecimal proteinFloor = (minProtein == null) ? BigDecimal.ZERO : minProtein;

        StringBuilder sql = new StringBuilder("""
                SELECT id, food_name, calories, protein_g, carbohydrates_g, fat_g, consumed_at
                FROM nutrition_entries
                WHERE LOWER(food_name) LIKE :pattern
                  AND protein_g >= :proteinFloor
                """);

        if (date != null) {
            sql.append(" AND consumed_at >= :startOfDay AND consumed_at < :startOfNextDay \n");
        }
        sql.append(" ORDER BY consumed_at DESC, id DESC ");

        var spec = jdbcClient.sql(sql.toString())
                .param("pattern", pattern)
                .param("proteinFloor", proteinFloor);

        if (date != null) {
            spec.param("startOfDay", Timestamp.valueOf(date.atStartOfDay()))
                    .param("startOfNextDay", Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
        }

        return spec.query(ROW_MAPPER).list();
    }

    public NutritionEntry create(NutritionEntryRequest request) {
        Long id = jdbcClient.sql("""
                INSERT INTO nutrition_entries
                    (food_name, calories, protein_g, carbohydrates_g, fat_g, consumed_at)
                VALUES
                    (:foodName, :calories, :proteinGrams, :carbohydratesGrams, :fatGrams, :consumedAt)
                RETURNING id
                """)
                .param("foodName", request.foodName())
                .param("calories", request.calories())
                .param("proteinGrams", request.proteinGrams())
                .param("carbohydratesGrams", request.carbohydratesGrams())
                .param("fatGrams", request.fatGrams())
                .param("consumedAt", Timestamp.valueOf(request.consumedAt()))
                .query(Long.class)
                .single();

        return findById(id).orElseThrow();
    }

    public boolean update(long id, NutritionEntryRequest request) {
        int updated = jdbcClient.sql("""
                UPDATE nutrition_entries
                SET food_name = :foodName,
                    calories = :calories,
                    protein_g = :proteinGrams,
                    carbohydrates_g = :carbohydratesGrams,
                    fat_g = :fatGrams,
                    consumed_at = :consumedAt
                WHERE id = :id
                """)
                .param("foodName", request.foodName())
                .param("calories", request.calories())
                .param("proteinGrams", request.proteinGrams())
                .param("carbohydratesGrams", request.carbohydratesGrams())
                .param("fatGrams", request.fatGrams())
                .param("consumedAt", Timestamp.valueOf(request.consumedAt()))
                .param("id", id)
                .update();

        return updated == 1;
    }

    public boolean deleteById(long id) {
        int deleted = jdbcClient.sql("DELETE FROM nutrition_entries WHERE id = :id")
                .param("id", id)
                .update();
        return deleted == 1;
    }
}