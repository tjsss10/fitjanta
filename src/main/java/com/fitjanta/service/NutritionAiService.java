package com.fitjanta.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FitJanta AI Nutrition engine.
 *
 * Reads a meal written in plain language, for example
 *   "2 roti, 1 bowl dal tadka and a glass of buttermilk"
 * and returns every recognised food with its quantity, calories and macros,
 * a 1-10 health score and coaching tips.
 *
 * It runs fully offline using a built-in nutrition database of common
 * Indian and international foods (values per typical serving).
 */
@Service
public class NutritionAiService {

    // ------------------------------------------------------------------
    // Data types
    // ------------------------------------------------------------------

    /** Nutrition for ONE serving of a food. */
    public record FoodInfo(String name, String unit, int grams,
                           double kcal, double protein, double carbs, double fat, double fiber,
                           String tag, List<String> aliases) {
    }

    public record ItemResult(String foodName, double quantity, String unit, int grams,
                             int calories, double protein, double carbs, double fat, double fiber,
                             String tag) {
    }

    public record Analysis(List<ItemResult> items, List<String> unrecognized,
                           int calories, double protein, double carbs, double fat, double fiber,
                           int healthScore, String verdict, List<String> tips,
                           int goalPercent) {
    }

    private record AliasEntry(String alias, FoodInfo food, Pattern pattern) {
    }

    private record Match(int start, int end, FoodInfo food) {
    }

    // ------------------------------------------------------------------
    // Nutrition database (per serving)
    // ------------------------------------------------------------------

    private final List<FoodInfo> foods = new ArrayList<>();
    private final List<AliasEntry> aliasIndex = new ArrayList<>();

    private void f(String name, String unit, int grams,
                   double kcal, double protein, double carbs, double fat, double fiber,
                   String tag, String... aliases) {
        foods.add(new FoodInfo(name, unit, grams, kcal, protein, carbs, fat, fiber, tag, List.of(aliases)));
    }

    public NutritionAiService() {
        // ---- Breads & grains ----
        f("Roti", "piece", 40, 120, 3.5, 18, 3.7, 2.5, "grain", "roti", "chapati", "chapatti", "chapathi", "chappati", "phulka", "fulka", "rotis", "poli");
        f("Plain Paratha", "piece", 80, 260, 5, 36, 10, 3, "grain", "paratha", "parantha");
        f("Aloo Paratha", "piece", 120, 320, 7, 45, 12, 4, "grain", "aloo paratha", "alu paratha", "potato paratha");
        f("Paneer Paratha", "piece", 120, 340, 12, 38, 15, 3, "grain", "paneer paratha");
        f("Naan", "piece", 90, 260, 8, 45, 5, 2, "grain", "naan", "nan", "butter naan");
        f("Thepla", "piece", 40, 120, 3, 17, 4.5, 2, "grain", "thepla");
        f("Puri", "piece", 25, 100, 1.5, 11, 5.5, 0.5, "fried", "puri", "poori");
        f("Bhatura", "piece", 80, 300, 6, 40, 13, 1.5, "fried", "bhatura", "bhature");
        f("Steamed Rice", "bowl", 150, 195, 4, 43, 0.4, 0.6, "grain", "rice", "chawal", "steamed rice", "white rice", "plain rice");
        f("Brown Rice", "bowl", 150, 170, 4, 36, 1.4, 2.7, "whole", "brown rice");
        // Uncooked rice is ~3x denser in calories than cooked rice (365 vs 130 kcal / 100 g)
        f("Raw Rice (uncooked)", "serving", 100, 365, 7, 80, 0.7, 1.3, "grain", "raw rice", "uncooked rice", "dry rice", "raw chawal");
        f("Jeera Rice", "bowl", 150, 240, 4, 42, 6, 1, "grain", "jeera rice", "fried rice");
        f("Chicken Biryani", "plate", 250, 490, 22, 55, 19, 2, "grain", "chicken biryani", "biryani", "biriyani", "briyani", "chiken biryani");
        f("Veg Biryani", "plate", 250, 400, 9, 60, 13, 4, "grain", "veg biryani", "vegetable biryani", "pulao", "pulav");
        f("Khichdi", "bowl", 200, 240, 9, 40, 5, 4, "whole", "khichdi", "khichri");
        f("Poha", "plate", 150, 250, 5, 40, 8, 2, "grain", "poha", "pohe");
        f("Upma", "plate", 150, 250, 6, 35, 9, 3, "grain", "upma");
        f("Oats", "bowl", 40, 150, 5, 27, 3, 4, "whole", "oats", "oatmeal", "porridge", "daliya", "dalia");
        f("Cornflakes", "bowl", 30, 110, 2, 25, 0.2, 1, "processed", "cornflakes", "corn flakes", "cereal");
        f("White Bread", "slice", 30, 80, 3, 14, 1, 1, "processed", "bread", "white bread", "toast");
        f("Brown Bread", "slice", 30, 70, 3.5, 12, 1, 2, "whole", "brown bread", "multigrain bread", "whole wheat bread");
        f("Quinoa", "bowl", 150, 180, 6.6, 32, 2.9, 4.2, "whole", "quinoa");
        f("Pasta", "plate", 200, 350, 12, 55, 9, 3, "grain", "pasta", "spaghetti", "macaroni");
        f("Noodles", "plate", 200, 380, 8, 52, 15, 2, "processed", "noodles", "maggi", "chowmein", "chow mein", "hakka noodles");

        // ---- Dals, curries & sabzi ----
        f("Dal Tadka", "bowl", 150, 180, 9, 24, 5, 6, "protein", "dal", "daal", "dhal", "dal tadka", "dal fry", "toor dal", "moong dal", "masoor dal", "lentils");
        f("Dal Makhani", "bowl", 150, 280, 10, 25, 15, 6, "protein", "dal makhani", "daal makhani");
        f("Rajma", "bowl", 150, 210, 10, 30, 5, 8, "protein", "rajma", "kidney beans");
        f("Chole", "bowl", 150, 240, 10, 32, 8, 9, "protein", "chole", "chhole", "chana masala", "chickpeas", "chana");
        f("Sambar", "bowl", 150, 130, 6, 18, 4, 5, "veg", "sambar", "sambhar");
        f("Paneer Butter Masala", "bowl", 150, 380, 14, 12, 30, 2, "dairy", "paneer butter masala", "butter paneer", "paneer makhani", "shahi paneer");
        f("Palak Paneer", "bowl", 150, 280, 13, 10, 20, 4, "dairy", "palak paneer");
        f("Paneer Tikka", "plate", 150, 320, 20, 8, 23, 2, "protein", "paneer tikka");
        f("Paneer", "serving", 50, 160, 9, 2, 13, 0, "protein", "paneer", "panner", "cottage cheese");
        f("Mixed Veg Sabzi", "bowl", 150, 150, 4, 15, 8, 5, "veg", "sabzi", "sabji", "subzi", "mix veg", "mixed veg", "vegetables", "veggies", "curry");
        f("Aloo Sabzi", "bowl", 150, 190, 3, 25, 9, 3, "veg", "aloo sabzi", "aloo sabji", "potato curry", "aloo");
        f("Bhindi Fry", "bowl", 150, 150, 3, 12, 10, 5, "veg", "bhindi", "okra", "lady finger");
        f("Baingan Bharta", "bowl", 150, 140, 3, 14, 8, 6, "veg", "baingan", "bharta", "brinjal");
        f("Kadhi", "bowl", 150, 160, 5, 12, 10, 1, "dairy", "kadhi", "kadi");

        // ---- South Indian & snacks ----
        f("Idli", "piece", 40, 58, 2, 12, 0.2, 0.5, "whole", "idli", "idly");
        f("Plain Dosa", "piece", 100, 170, 4, 28, 5, 1, "grain", "dosa", "plain dosa");
        f("Masala Dosa", "piece", 180, 380, 7, 50, 16, 4, "grain", "masala dosa");
        f("Uttapam", "piece", 150, 250, 6, 38, 8, 3, "grain", "uttapam", "uthappam");
        f("Medu Vada", "piece", 50, 150, 4, 15, 8, 2, "fried", "vada", "medu vada", "wada");
        f("Dhokla", "piece", 40, 65, 2.5, 10, 2, 0.5, "whole", "dhokla");
        f("Samosa", "piece", 60, 260, 4, 28, 15, 2, "fried", "samosa", "samosas");
        f("Kachori", "piece", 60, 280, 5, 27, 17, 2, "fried", "kachori");
        f("Pakora", "plate", 100, 300, 6, 25, 20, 3, "fried", "pakora", "pakoda", "bhajiya", "bhaji");
        f("Vada Pav", "piece", 150, 290, 6, 40, 12, 3, "fried", "vada pav", "wada pav", "vadapav");
        f("Pav Bhaji", "plate", 300, 450, 10, 60, 18, 7, "processed", "pav bhaji");
        f("Misal Pav", "plate", 300, 500, 17, 60, 22, 10, "protein", "misal", "misal pav");
        f("Momos", "plate", 180, 250, 10, 35, 8, 2, "grain", "momos", "momo", "dumplings");
        f("Bhel Puri", "plate", 120, 280, 6, 45, 9, 4, "processed", "bhel", "bhel puri", "chaat", "pani puri", "golgappa");

        // ---- Eggs, meat & protein ----
        f("Boiled Egg", "piece", 50, 78, 6.3, 0.6, 5.3, 0, "protein", "egg", "boiled egg", "anda");
        f("Omelette", "piece", 120, 190, 13, 2, 15, 0, "protein", "omelette", "omelet", "omlette", "omlet", "egg bhurji", "scrambled eggs");
        // "3 egg omelette" counts eggs, not omelettes
        f("Egg Omelette", "egg", 60, 95, 6.5, 1, 7.5, 0, "protein", "egg omelette", "eggs omelette", "egg omelet", "egg omlette", "eggs omlette");
        f("Egg Curry", "bowl", 200, 280, 14, 10, 20, 2, "protein", "egg curry", "anda curry");
        f("Grilled Chicken", "serving", 100, 165, 31, 0, 3.6, 0, "protein", "chicken breast", "grilled chicken", "boiled chicken", "chicken", "chiken");
        // ~150 kcal / 100 g for a home-style chicken gravy
        f("Chicken Curry", "bowl", 200, 300, 25, 8, 18, 2, "protein", "chicken curry", "chicken masala", "chicken gravy", "gravy chicken", "chicken rassa", "chicken sabzi", "chiken curry", "chicken kurma", "chicken korma");
        f("Butter Chicken", "bowl", 200, 440, 28, 10, 32, 2, "protein", "butter chicken", "chicken makhani");
        f("Tandoori Chicken", "plate", 200, 300, 40, 4, 13, 1, "protein", "tandoori chicken", "chicken tikka", "tandoori");
        f("Fish Curry", "bowl", 200, 250, 22, 6, 15, 1, "protein", "fish curry");
        f("Fish Fry", "piece", 100, 230, 20, 8, 13, 0, "fried", "fish fry", "fried fish", "fish");
        f("Mutton Curry", "bowl", 200, 400, 28, 8, 28, 1, "protein", "mutton", "mutton curry", "lamb curry");
        f("Soya Chunks", "bowl", 50, 170, 26, 16, 0.3, 6, "protein", "soya", "soya chunks", "soy chunks", "nutrela");
        f("Tofu", "serving", 100, 76, 8, 1.9, 4.8, 0.3, "protein", "tofu");
        f("Sprouts", "bowl", 100, 100, 7, 18, 0.5, 4, "protein", "sprouts", "moong sprouts");
        f("Whey Protein", "scoop", 30, 120, 24, 3, 1.5, 0, "protein", "whey", "protein shake", "protein powder", "whey protein");

        // ---- Dairy & drinks ----
        f("Milk", "glass", 250, 150, 8, 12, 8, 0, "dairy", "milk", "doodh");
        f("Curd", "bowl", 100, 60, 3.5, 4.7, 3.3, 0, "dairy", "curd", "dahi", "yogurt", "yoghurt");
        f("Greek Yogurt", "bowl", 100, 97, 9, 4, 5, 0, "protein", "greek yogurt", "hung curd");
        f("Raita", "bowl", 100, 70, 3, 6, 3, 1, "dairy", "raita");
        f("Buttermilk", "glass", 200, 40, 2, 5, 1, 0, "drink", "buttermilk", "chaas", "chhaas", "mattha");
        f("Lassi", "glass", 250, 220, 7, 30, 8, 0, "sweet", "lassi", "sweet lassi", "mango lassi");
        f("Masala Chai", "cup", 150, 80, 2, 11, 3, 0, "drink", "tea", "chai", "masala chai");
        f("Coffee with Milk", "cup", 150, 90, 3, 12, 3, 0, "drink", "coffee", "cappuccino", "latte", "cold coffee");
        f("Black Coffee", "cup", 150, 5, 0.3, 1, 0, 0, "drink", "black coffee", "green tea", "black tea");
        f("Fruit Juice", "glass", 250, 110, 1, 26, 0.3, 0.5, "sweet", "juice", "orange juice", "fruit juice", "mango juice");
        f("Coconut Water", "glass", 250, 45, 1.7, 9, 0.5, 2.6, "drink", "coconut water", "nariyal pani");
        f("Water", "glass", 250, 0, 0, 0, 0, 0, "drink", "water", "paani", "pani");
        f("Lemon Water", "glass", 250, 30, 0.1, 8, 0, 0.1, "drink", "lemon water", "nimbu pani", "lemonade", "shikanji");
        f("Soft Drink", "can", 330, 140, 0, 39, 0, 0, "sweet", "coke", "cola", "pepsi", "soft drink", "soda", "sprite", "cold drink");

        // ---- Fruits, veg & nuts ----
        f("Banana", "piece", 118, 105, 1.3, 27, 0.4, 3.1, "fruit", "banana", "kela");
        f("Apple", "piece", 180, 95, 0.5, 25, 0.3, 4.4, "fruit", "apple", "seb");
        f("Orange", "piece", 130, 62, 1.2, 15, 0.2, 3.1, "fruit", "orange", "santra");
        f("Mango", "piece", 200, 200, 2.8, 50, 1.3, 5.4, "fruit", "mango", "aam");
        f("Papaya", "bowl", 150, 65, 0.7, 16, 0.4, 2.5, "fruit", "papaya");
        f("Grapes", "bowl", 100, 69, 0.7, 18, 0.2, 0.9, "fruit", "grapes", "angoor");
        f("Watermelon", "bowl", 150, 45, 0.9, 11, 0.2, 0.6, "fruit", "watermelon", "tarbooz");
        f("Pomegranate", "bowl", 100, 83, 1.7, 19, 1.2, 4, "fruit", "pomegranate", "anar");
        f("Fruit Bowl", "bowl", 150, 90, 1.2, 22, 0.4, 3, "fruit", "fruits", "fruit salad", "fruit bowl");
        f("Green Salad", "bowl", 100, 25, 1.5, 5, 0.2, 2, "veg", "salad", "green salad", "cucumber", "kheera", "tomato");
        f("Boiled Potato", "piece", 150, 130, 3, 30, 0.2, 2.5, "veg", "boiled potato", "potato");
        f("Sweet Potato", "piece", 130, 112, 2, 26, 0.1, 3.9, "whole", "sweet potato", "shakarkandi");
        f("Almonds", "handful", 28, 165, 6, 6, 14, 3.5, "whole", "almonds", "badam");
        f("Peanuts", "handful", 30, 170, 7.7, 4.8, 14.8, 2.5, "protein", "peanuts", "groundnuts", "mungfali");
        f("Mixed Nuts", "handful", 30, 175, 5, 6, 15, 2.5, "whole", "nuts", "dry fruits", "cashews", "kaju", "walnuts");
        f("Dates", "piece", 8, 20, 0.2, 5.3, 0, 0.6, "fruit", "dates", "khajur");
        f("Peanut Butter", "tbsp", 16, 95, 4, 3, 8, 1, "protein", "peanut butter");

        // ---- Fast food & sweets ----
        f("Pizza", "slice", 107, 285, 12, 36, 10, 2.5, "processed", "pizza");
        f("Burger", "piece", 200, 350, 15, 35, 16, 2, "processed", "burger");
        f("French Fries", "serving", 100, 312, 3.4, 41, 15, 3.8, "fried", "fries", "french fries", "chips");
        f("Veg Sandwich", "piece", 150, 250, 8, 32, 10, 3, "grain", "sandwich", "veg sandwich", "grilled sandwich");
        f("Gulab Jamun", "piece", 50, 150, 2, 22, 6, 0.3, "sweet", "gulab jamun");
        f("Jalebi", "serving", 50, 200, 1, 30, 9, 0, "sweet", "jalebi");
        f("Ladoo", "piece", 40, 180, 3, 22, 9, 1, "sweet", "ladoo", "laddu", "laddoo");
        f("Kheer", "bowl", 150, 230, 6, 35, 7, 0.5, "sweet", "kheer", "payasam", "rice pudding");
        f("Ice Cream", "scoop", 70, 140, 2.4, 16, 7.5, 0.5, "sweet", "ice cream", "icecream", "kulfi");
        f("Chocolate", "serving", 40, 210, 3, 24, 12, 2, "sweet", "chocolate", "dairy milk", "kitkat");
        f("Biscuit", "piece", 10, 45, 0.6, 7, 1.7, 0.2, "processed", "biscuit", "biscuits", "cookie", "cookies", "parle g");
        f("Cake", "slice", 80, 300, 4, 42, 13, 1, "sweet", "cake", "pastry", "brownie");
        f("Butter", "tbsp", 10, 72, 0.1, 0, 8, 0, "processed", "butter", "ghee");
        f("Honey", "tbsp", 21, 64, 0, 17, 0, 0, "sweet", "honey", "shahad");
        f("Sugar", "tsp", 4, 16, 0, 4, 0, 0, "sweet", "sugar", "cheeni");

        // Build the alias index, longest alias first, so "aloo paratha"
        // wins over "paratha" and "chicken biryani" wins over "chicken".
        for (FoodInfo food : foods) {
            for (String alias : food.aliases()) {
                Pattern p = Pattern.compile("(?<![a-z])" + Pattern.quote(alias) + "(?:s|es)?(?![a-z])");
                aliasIndex.add(new AliasEntry(alias, food, p));
            }
        }
        aliasIndex.sort((a, b) -> Integer.compare(b.alias().length(), a.alias().length()));
    }

    public int databaseSize() {
        return foods.size();
    }

    // ------------------------------------------------------------------
    // Quantity & unit understanding
    // ------------------------------------------------------------------

    private static final Map<String, Double> NUMBER_WORDS = Map.ofEntries(
            Map.entry("a", 1.0), Map.entry("an", 1.0), Map.entry("one", 1.0),
            Map.entry("two", 2.0), Map.entry("three", 3.0), Map.entry("four", 4.0),
            Map.entry("five", 5.0), Map.entry("six", 6.0), Map.entry("seven", 7.0),
            Map.entry("eight", 8.0), Map.entry("nine", 9.0), Map.entry("ten", 10.0),
            Map.entry("half", 0.5), Map.entry("quarter", 0.25), Map.entry("couple", 2.0),
            Map.entry("few", 3.0), Map.entry("dozen", 12.0), Map.entry("double", 2.0));

    private static final Pattern NUMBER = Pattern.compile("(\\d+(?:\\.\\d+)?)(?:\\s*/\\s*(\\d+))?");
    private static final Pattern GRAMS = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(kg|kgs|g|gm|gms|gram|grams|ml|l|ltr|litre|liter|litres|liters)(?![a-z])");
    private static final Pattern UNIT = Pattern.compile("(?<![a-z])(bowl|bowls|katori|katoris|cup|cups|plate|plates|glass|glasses|piece|pieces|pc|pcs|slice|slices|tbsp|tablespoon|tablespoons|spoon|spoons|tsp|teaspoon|teaspoons|scoop|scoops|handful|handfuls|can|cans|bottle|bottles|serving|servings|portion|portions)(?![a-z])");

    private record Quantity(double servings, double displayQty, String displayUnit, int grams, String note) {
    }

    /** Typical weight (g) or volume (ml) of a household measure. */
    private static final Map<String, Integer> MEASURE_GRAMS = Map.ofEntries(
            Map.entry("bowl", 150), Map.entry("cup", 200), Map.entry("glass", 250),
            Map.entry("plate", 250), Map.entry("can", 330), Map.entry("bottle", 500),
            Map.entry("handful", 30), Map.entry("scoop", 30), Map.entry("slice", 30));

    private static final Set<String> DRINK_UNITS = Set.of("glass", "cup", "can", "bottle");

    /**
     * Above this, a bare number without a unit ("200 rice") is a weight in
     * grams, not a count. Nobody eats 200 bowls of rice.
     */
    private static final double MAX_PLAIN_COUNT = 15;

    private Quantity parseQuantity(String text, FoodInfo food) {
        String t = " " + text.trim() + " ";

        double sizeFactor = 1.0;
        if (t.matches(".*(?<![a-z])(large|big|bada|heavy|full)(?![a-z]).*")) sizeFactor = 1.4;
        if (t.matches(".*(?<![a-z])(small|chota|mini|light)(?![a-z]).*")) sizeFactor = 0.7;

        // 1) Explicit weight / volume: "150g", "200 ml", "0.5 kg", "half kg"
        Matcher gm = GRAMS.matcher(t);
        Double explicitGrams = null;
        String explicitUnit = "g";
        if (gm.find()) {
            explicitGrams = toGrams(Double.parseDouble(gm.group(1)), gm.group(2));
            explicitUnit = gm.group(2).startsWith("l") || gm.group(2).equals("ml") ? "ml" : "g";
        } else {
            Matcher wm = WORD_WEIGHT.matcher(t);
            if (wm.find()) {
                explicitGrams = toGrams(NUMBER_WORDS.get(wm.group(1)), wm.group(2));
                explicitUnit = wm.group(2).startsWith("l") || wm.group(2).equals("ml") ? "ml" : "g";
            }
        }
        if (explicitGrams != null) {
            return byWeight(food, explicitGrams, explicitUnit, null);
        }

        // 2) Count: digits, fractions or number words
        double count = 1.0;
        boolean numeric = false;
        Matcher nm = NUMBER.matcher(t);
        if (nm.find()) {
            count = Double.parseDouble(nm.group(1));
            if (nm.group(2) != null && Double.parseDouble(nm.group(2)) > 0) {
                count = count / Double.parseDouble(nm.group(2));
            }
            numeric = true;
        } else {
            for (String word : t.trim().split("\\s+")) {
                if (NUMBER_WORDS.containsKey(word)) {
                    count = NUMBER_WORDS.get(word);
                    break;
                }
            }
        }
        if (count <= 0) count = 1.0;

        Matcher um = UNIT.matcher(t);
        String spokenUnit = um.find() ? singular(um.group(1)) : null;

        // 3) Bare large number with no unit -> grams (or ml for drinks)
        if (spokenUnit == null && numeric && count > MAX_PLAIN_COUNT) {
            String unit = DRINK_UNITS.contains(food.unit()) ? "ml" : "g";
            return byWeight(food, count, unit,
                    "I read \"" + fmtNum(count) + "\" " + food.name().replaceAll("\\s*\\(.*\\)", "").toLowerCase(Locale.ROOT)
                            + " as " + fmtNum(count) + " " + unit + " (add a unit like \"2 bowls\" to count servings).");
        }

        // 4) Whole pizza: "1 medium pizza" = 6 slices, "large pizza" = 8
        if (food.name().equals("Pizza") && spokenUnit == null
                && t.matches(".*(?<![a-z])(whole|full|medium|large|big|small)(?![a-z]).*")) {
            int slices = t.matches(".*(?<![a-z])(large|big)(?![a-z]).*") ? 8
                    : t.matches(".*(?<![a-z])small(?![a-z]).*") ? 4 : 6;
            double servings = count * slices;
            return new Quantity(servings, round1(servings), "slice", (int) Math.round(servings * food.grams()),
                    "A " + (slices == 8 ? "large" : slices == 4 ? "small" : "medium") + " pizza is counted as " + slices + " slices.");
        }

        // 5) Units
        String unit = food.unit();
        double servings = count;
        if (spokenUnit != null) {
            if (spokenUnit.equals("tbsp")) {
                servings = food.unit().equals("tbsp") ? count : count * 15 / food.grams();
                unit = "tbsp";
            } else if (spokenUnit.equals("tsp")) {
                servings = food.unit().equals("tsp") ? count : count * 5 / food.grams();
                unit = "tsp";
            } else if (spokenUnit.equals(food.unit()) || spokenUnit.equals("serving")) {
                unit = food.unit();
            } else if (spokenUnit.equals("piece")) {
                // "1 piece" of a bowl/plate food = one serving
                unit = food.unit();
            } else if (isSameMeasure(spokenUnit, food.unit())) {
                unit = spokenUnit;
            } else if (MEASURE_GRAMS.containsKey(spokenUnit)) {
                // Different measure: convert through weight, e.g. "1 plate idli" -> ~4 idli
                int measure = MEASURE_GRAMS.get(spokenUnit);
                if (spokenUnit.equals("plate") && food.unit().equals("piece")) measure = 160;
                servings = count * measure / food.grams();
                unit = spokenUnit;
            } else {
                unit = spokenUnit;
            }
        }

        servings *= sizeFactor;
        int grams = (int) Math.round(servings * food.grams());
        return new Quantity(servings, round1(count), unit, grams, null);
    }

    private static final Pattern WORD_WEIGHT = Pattern.compile(
            "(?<![a-z])(half|quarter|one|two|three|a)\\s*(kg|kgs|litre|liter|ltr|l)(?![a-z])");

    private static double toGrams(double value, String unit) {
        return switch (unit) {
            case "kg", "kgs", "l", "ltr", "litre", "liter", "litres", "liters" -> value * 1000;
            default -> value;
        };
    }

    private static boolean isSameMeasure(String a, String b) {
        // A bowl, katori and cup of dal/rice are used interchangeably
        Set<String> containers = Set.of("bowl", "cup");
        return containers.contains(a) && containers.contains(b);
    }

    private Quantity byWeight(FoodInfo food, double grams, String unit, String note) {
        double servings = grams / food.grams();
        return new Quantity(servings, Math.round(grams), unit, (int) Math.round(grams), note);
    }

    private static String fmtNum(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static String singular(String unit) {
        return switch (unit) {
            case "katori", "katoris", "bowls" -> "bowl";
            case "cups" -> "cup";
            case "plates" -> "plate";
            case "glasses" -> "glass";
            case "pieces", "pc", "pcs" -> "piece";
            case "slices" -> "slice";
            case "scoops" -> "scoop";
            case "handfuls" -> "handful";
            case "cans" -> "can";
            case "bottles" -> "bottle";
            case "servings", "portions", "portion" -> "serving";
            case "tablespoon", "tablespoons", "spoon", "spoons" -> "tbsp";
            case "teaspoon", "teaspoons" -> "tsp";
            default -> unit;
        };
    }

    // ------------------------------------------------------------------
    // Analysis
    // ------------------------------------------------------------------

    private static final Pattern SPLIT = Pattern.compile(
            "\\s*(?:,|;|\\+|&|\\n|(?<![a-z])and(?![a-z])|(?<![a-z])with(?![a-z])|(?<![a-z])plus(?![a-z])|(?<![a-z])along(?![a-z]))\\s*");

    private static final Set<String> FILLER = Set.of(
            "i", "had", "have", "ate", "eaten", "eat", "some", "of", "the", "for", "my", "today", "in",
            "breakfast", "lunch", "dinner", "snack", "snacks", "morning", "evening", "night", "also", "then",
            "just", "only", "around", "about", "approx", "nearly", "little", "bit", "drank", "drink", "took",
            "large", "big", "small", "medium", "full", "light", "homemade", "home", "made", "fresh", "hot", "cold",
            "x", "nos", "no", "time", "times", "extra", "more", "less", "bada", "chota", "mini",
            "cooked", "boiled", "steamed", "fried", "plain", "whole", "piece", "pieces", "bowl", "bowls", "plate",
            "plates", "glass", "glasses", "cup", "cups", "slice", "slices", "serving", "servings", "katori");

    public Analysis analyze(String input, int dailyGoal) {
        String text = normalize(input);

        List<ItemResult> items = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        for (String chunk : SPLIT.split(text)) {
            chunk = chunk.trim();
            if (chunk.isEmpty()) continue;

            List<Match> matches = findFoods(chunk);

            if (matches.isEmpty()) {
                String leftover = leftoverWords(chunk);
                if (!leftover.isBlank()) unknown.add(leftover);
                continue;
            }

            for (int i = 0; i < matches.size(); i++) {
                Match m = matches.get(i);
                int qtyStart = i == 0 ? 0 : matches.get(i - 1).end();
                String before = chunk.substring(qtyStart, m.start());
                String after = (i == matches.size() - 1) ? chunk.substring(m.end()) : "";
                // Quantity normally comes before the food ("2 roti"), but also allow "roti 2"
                String qtyText = hasQuantity(before) || after.isBlank() ? before : before + " " + after;
                Quantity q = parseQuantity(qtyText + " " + chunk.substring(m.start(), m.end()), m.food());
                ItemResult item = toItem(m.food(), q);
                if (q.note() != null) notes.add(q.note());
                if (item.calories() > 2500) {
                    notes.add(item.foodName() + " comes to " + item.calories()
                            + " kcal. That's a very large amount, so double-check the quantity.");
                }
                items.add(item);
            }
        }

        items = mergeDuplicates(items);

        int kcal = items.stream().mapToInt(ItemResult::calories).sum();
        double protein = round1(items.stream().mapToDouble(ItemResult::protein).sum());
        double carbs = round1(items.stream().mapToDouble(ItemResult::carbs).sum());
        double fat = round1(items.stream().mapToDouble(ItemResult::fat).sum());
        double fiber = round1(items.stream().mapToDouble(ItemResult::fiber).sum());

        int score = healthScore(items, kcal, protein, carbs, fat, fiber);
        List<String> tips = new ArrayList<>(notes);
        tips.addAll(coachTips(items, unknown, kcal, protein, carbs, fat, fiber, score, dailyGoal));
        if (tips.size() > 6) tips = tips.subList(0, 6);
        int goalPercent = dailyGoal > 0 ? (int) Math.round(kcal * 100.0 / dailyGoal) : 0;

        return new Analysis(items, unknown, kcal, protein, carbs, fat, fiber, score, verdict(score, items.isEmpty()), tips, goalPercent);
    }

    private String normalize(String input) {
        if (input == null) return "";
        return input.toLowerCase(Locale.ROOT)
                .replace("½", " 0.5 ").replace("¼", " 0.25 ").replace("¾", " 0.75 ")
                .replaceAll("(\\d)\\s*x(?![a-z])", "$1 ")      // "2x roti" -> "2 roti"
                // "tea with milk" is one drink; don't split it and count the milk twice
                .replaceAll("(tea|chai|coffee)\\s+(with|and)\\s+milk", "$1")
                .replaceAll("milk\\s+(tea|chai|coffee)", "$1")
                .replaceAll("(\\d),(\\d{3})", "$1$2")           // "1,000" -> "1000"
                .replaceAll("[^a-z0-9./,;+&\\n ]", " ")
                .replaceAll("[ \\t]+", " ")
                .trim();
    }

    private List<Match> findFoods(String chunk) {
        boolean[] used = new boolean[chunk.length()];
        List<Match> found = new ArrayList<>();
        for (AliasEntry entry : aliasIndex) {
            Matcher m = entry.pattern().matcher(chunk);
            while (m.find()) {
                boolean overlap = false;
                for (int i = m.start(); i < m.end(); i++) {
                    if (used[i]) { overlap = true; break; }
                }
                if (overlap) continue;
                for (int i = m.start(); i < m.end(); i++) used[i] = true;
                found.add(new Match(m.start(), m.end(), entry.food()));
            }
        }
        found.sort(Comparator.comparingInt(Match::start));
        return found;
    }

    private boolean hasQuantity(String text) {
        if (NUMBER.matcher(text).find()) return true;
        for (String w : text.trim().split("\\s+")) {
            if (NUMBER_WORDS.containsKey(w)) return true;
        }
        return false;
    }

    private String leftoverWords(String chunk) {
        StringBuilder sb = new StringBuilder();
        for (String w : chunk.split("\\s+")) {
            if (w.isBlank() || FILLER.contains(w) || NUMBER_WORDS.containsKey(w)) continue;
            if (w.matches("[0-9./]+")) continue;
            if (UNIT.matcher(w).matches() || GRAMS.matcher(w).matches()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(w);
        }
        return sb.toString();
    }

    private ItemResult toItem(FoodInfo food, Quantity q) {
        double s = q.servings();
        return new ItemResult(
                food.name(), q.displayQty(), q.displayUnit(), q.grams(),
                (int) Math.round(food.kcal() * s),
                round1(food.protein() * s), round1(food.carbs() * s),
                round1(food.fat() * s), round1(food.fiber() * s),
                food.tag());
    }

    private List<ItemResult> mergeDuplicates(List<ItemResult> items) {
        Map<String, ItemResult> merged = new LinkedHashMap<>();
        for (ItemResult it : items) {
            String key = it.foodName() + "|" + it.unit();
            ItemResult prev = merged.get(key);
            if (prev == null) {
                merged.put(key, it);
            } else {
                merged.put(key, new ItemResult(it.foodName(), round1(prev.quantity() + it.quantity()), it.unit(),
                        prev.grams() + it.grams(), prev.calories() + it.calories(),
                        round1(prev.protein() + it.protein()), round1(prev.carbs() + it.carbs()),
                        round1(prev.fat() + it.fat()), round1(prev.fiber() + it.fiber()), it.tag()));
            }
        }
        return new ArrayList<>(merged.values());
    }

    // ------------------------------------------------------------------
    // Scoring & coaching
    // ------------------------------------------------------------------

    private int healthScore(List<ItemResult> items, int kcal, double protein, double carbs, double fat, double fiber) {
        if (items.isEmpty() || kcal == 0) return 0;

        double score = 6;
        double proteinPct = protein * 4 * 100 / kcal;
        double fatPct = fat * 9 * 100 / kcal;
        double fiberPer500 = fiber * 500 / kcal;

        if (proteinPct >= 20) score += 2; else if (proteinPct >= 12) score += 1; else if (proteinPct < 8) score -= 1;
        if (fiberPer500 >= 8) score += 1.5; else if (fiberPer500 >= 4) score += 0.5;
        if (fatPct > 45) score -= 1.5; else if (fatPct > 35) score -= 0.5;

        long fried = items.stream().filter(i -> i.tag().equals("fried")).count();
        long sweet = items.stream().filter(i -> i.tag().equals("sweet")).count();
        long processed = items.stream().filter(i -> i.tag().equals("processed")).count();
        boolean plants = items.stream().anyMatch(i -> i.tag().equals("fruit") || i.tag().equals("veg") || i.tag().equals("whole"));

        score -= Math.min(3, fried * 1.2);
        score -= Math.min(2.5, sweet * 1.0);
        score -= Math.min(2, processed * 0.7);
        if (plants) score += 1;
        if (kcal > 1100) score -= 1;

        return (int) Math.max(1, Math.min(10, Math.round(score)));
    }

    private String verdict(int score, boolean empty) {
        if (empty) return "No foods recognised";
        if (score >= 8) return "Excellent choice";
        if (score >= 6) return "Well balanced";
        if (score >= 4) return "Could be better";
        return "Indulgent meal";
    }

    private List<String> coachTips(List<ItemResult> items, List<String> unknown, int kcal, double protein,
                                   double carbs, double fat, double fiber, int score, int goal) {
        List<String> tips = new ArrayList<>();
        if (items.isEmpty()) {
            tips.add("Try writing it like \"2 roti, 1 bowl dal and salad\" so I can recognise each food.");
            return tips;
        }

        double proteinPct = protein * 4 * 100 / Math.max(kcal, 1);
        double carbPct = carbs * 4 * 100 / Math.max(kcal, 1);
        double fatPct = fat * 9 * 100 / Math.max(kcal, 1);

        if (proteinPct >= 20) {
            tips.add(String.format("Great protein: %.0f g (%.0f%% of calories) helps muscle recovery.", protein, proteinPct));
        } else if (proteinPct < 12) {
            tips.add(String.format("Protein is low at %.0f g. Add dal, curd, paneer, eggs or sprouts.", protein));
        }

        if (carbPct > 65) {
            tips.add(String.format("Carbs make up %.0f%% of this meal. Swap one serving of rice or roti for salad or protein.", carbPct));
        }
        if (fatPct > 40) {
            tips.add(String.format("Fat is high (%.0f%% of calories). Go easy on butter, ghee and gravy.", fatPct));
        }

        if (fiber >= 8) {
            tips.add(String.format("Nice fibre (%.0f g). It keeps you full for longer.", fiber));
        } else if (kcal > 300 && fiber < 3) {
            tips.add("Very little fibre. A bowl of salad, fruit or sabzi would balance this meal.");
        }

        List<String> fried = items.stream().filter(i -> i.tag().equals("fried")).map(ItemResult::foodName).toList();
        if (!fried.isEmpty()) {
            tips.add("Fried item: " + String.join(", ", fried) + ". Try a baked or roasted version next time.");
        }
        List<String> sweet = items.stream().filter(i -> i.tag().equals("sweet")).map(ItemResult::foodName).toList();
        if (!sweet.isEmpty()) {
            tips.add("Added sugar from " + String.join(", ", sweet) + ". Keep sweets to a small portion.");
        }

        if (goal > 0) {
            int pct = (int) Math.round(kcal * 100.0 / goal);
            if (pct >= 45) {
                tips.add("This meal alone is " + pct + "% of your " + goal + " kcal daily goal. Keep the next meal light.");
            }
        }

        if (!unknown.isEmpty()) {
            tips.add("I couldn't recognise: " + String.join(", ", unknown) + ". Those aren't counted.");
        }

        if (tips.isEmpty()) {
            tips.add(score >= 7 ? "Balanced plate. Keep it up!" : "Decent meal. Add a vegetable or protein to make it even better.");
        }
        return tips.size() > 5 ? tips.subList(0, 5) : tips;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
