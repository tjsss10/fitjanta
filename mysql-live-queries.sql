-- =====================================================================
--  FitJanta: watch MySQL change live while you use the app on the phone
--  Open this file in MySQL Workbench, then run a query (Ctrl + Enter)
--  after each action on the phone to see the new rows.
-- =====================================================================

USE fitjanta;

-- 1. Sign up on the phone  ->  a new row in users (password is a BCrypt hash)
SELECT id, name, email, city, xp, level, streak, calorie_goal, bio, created_at
FROM users
ORDER BY id DESC;

-- 2. Food AI: "Add to food diary"  ->  one row per meal ...
SELECT id, user_id, meal_type, description, calories, protein, carbs, fat, fiber,
       health_score, log_date, created_at
FROM food_logs
ORDER BY id DESC;

-- ... and one row per recognised food in that meal
SELECT i.food_log_id, l.meal_type, i.food_name, i.quantity, i.unit, i.grams,
       i.calories, i.protein, i.carbs, i.fat
FROM food_log_items i
JOIN food_logs l ON l.id = i.food_log_id
ORDER BY i.id DESC;

-- 3. Log a workout  ->  activities (calories burned estimated by the server)
SELECT id, user_id, type, distance, duration, calories, date
FROM activities
ORDER BY id DESC;

-- 4. Share a photo  ->  posts (the image file is in springboot/fitjanta/uploads/posts)
SELECT p.id, u.name, p.caption, p.image_path, p.created_at
FROM posts p
JOIN users u ON u.id = p.user_id
ORDER BY p.id DESC;

-- 5. Complete a challenge  ->  challenge_completions (+ XP added to users)
SELECT c.id, u.name, ch.title, c.xp_earned, c.completed_at
FROM challenge_completions c
JOIN users u ON u.id = c.user_id
JOIN challenges ch ON ch.id = c.challenge_id
ORDER BY c.id DESC;

-- 6. Today's nutrition totals per user (what the Food AI ring shows)
SELECT u.name, COUNT(l.id) AS meals, SUM(l.calories) AS kcal_eaten, u.calorie_goal,
       ROUND(SUM(l.protein)) AS protein_g, ROUND(AVG(l.health_score), 1) AS avg_score
FROM users u
LEFT JOIN food_logs l ON l.user_id = u.id AND l.log_date = CURDATE()
GROUP BY u.id
ORDER BY kcal_eaten DESC;

-- 7. Leaderboard (same order as the app)
SELECT name, xp, level, streak FROM users ORDER BY xp DESC;
