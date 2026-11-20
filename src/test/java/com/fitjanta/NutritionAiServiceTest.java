package com.fitjanta;

import com.fitjanta.service.NutritionAiService;
import com.fitjanta.service.NutritionAiService.Analysis;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Accuracy tests for the FitJanta AI nutrition engine.
 * Expected values come from the per-serving nutrition table in NutritionAiService.
 */
class NutritionAiServiceTest {

    private final NutritionAiService ai = new NutritionAiService();

    private int kcal(String meal) {
        return ai.analyze(meal, 2000).calories();
    }

    @ParameterizedTest(name = "{0} -> {1} kcal")
    @CsvSource(delimiter = '|', value = {
            // Bugs reported from real diary entries (were 39,330 / 39,640 / 495 kcal)
            "200g chicken and 200 raw rice               | 1060",
            "200 cooked rice and 200g cooked paneer      | 900",
            "300g chicken gravy                          | 450",
            // Counts, units and conversions
            "2 roti                                      | 240",
            "two roti and 1 bowl dal tadka               | 420",
            "2 bowls dal                                 | 360",
            "1 plate rice                                | 325",
            "1 plate idli                                | 232",
            "3 idli and sambar                           | 304",
            "250 ml milk                                 | 150",
            "1 cup milk                                  | 120",
            "half kg chicken                             | 825",
            "2 tbsp ghee                                 | 144",
            "3 egg omelette                              | 285",
            "1 medium pizza                              | 1710",
            "2 slices pizza                              | 570",
            "tea with milk                               | 80",
            "coffee with milk and 2 biscuits             | 180",
            "1 glass water                               | 0",
            "1 bowl paneer butter masala and 2 naan      | 900",
            "half plate chicken biryani + raita          | 315",
            "masala dosa with sambar and chai            | 590",
            "oats with banana and milk                   | 405",
            "3 samosa and a coke                         | 920",
    })
    void calculatesCalories(String meal, int expected) {
        assertEquals(expected, kcal(meal.trim()), () -> "Wrong calories for: " + meal);
    }

    @Test
    void bareLargeNumberIsReadAsGramsAndExplained() {
        Analysis a = ai.analyze("200 rice", 2000);
        assertEquals(260, a.calories());
        assertEquals("g", a.items().get(0).unit());
        assertTrue(a.tips().stream().anyMatch(t -> t.contains("200 g")), "should tell the user it read 200 as grams");
    }

    @Test
    void rawRiceIsDenserThanCookedRice() {
        assertTrue(kcal("100g raw rice") > 2.5 * kcal("100g rice"));
    }

    @Test
    void unknownFoodsAreReportedNotCounted() {
        Analysis a = ai.analyze("pizza and xyzfood", 2000);
        assertEquals(285, a.calories());
        assertTrue(a.unrecognized().contains("xyzfood"));
    }

    @Test
    void healthyMealScoresHigherThanJunk() {
        int healthy = ai.analyze("200g grilled chicken, brown rice and salad", 2000).healthScore();
        int junk = ai.analyze("3 samosa, fries and a coke", 2000).healthScore();
        assertTrue(healthy >= 8, "healthy score was " + healthy);
        assertTrue(junk <= 3, "junk score was " + junk);
    }

    @Test
    void misspellingsAreUnderstood() {
        assertEquals(kcal("2 roti"), kcal("2 chapathi"));
        assertEquals(kcal("100g paneer"), kcal("100g panner"));
        assertEquals(kcal("omelette"), kcal("omlette"));
    }
}
