package com.onlineexamsystem.onlineexamsystem.student;

import com.onlineexamsystem.onlineexamsystem.admin.Exam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
public class StudentController {
    @Autowired
    private JdbcTemplate jdbc;

    // The logged-in student's id in the users table. auth.getName() is the username from the JWT.
    private int currentUserId(Authentication auth) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Integer.class, auth.getName());
    }

    // Returns null if the student has not started this exam
    private Attempt findAttempt(int userId, int examId) {
        List<Attempt> rows = jdbc.query("SELECT id, user, exam, score, open FROM attempts WHERE user = ? AND exam = ?",
                (rs, rowNum) -> new Attempt(
                        rs.getInt("id"),
                        rs.getInt("user"),
                        rs.getInt("exam"),
                        rs.getObject("score", Integer.class),
                        rs.getBoolean("open")
                ), userId, examId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // The ids of the questions picked for an attempt
    private List<Integer> questionIds(int attemptId) {
        return jdbc.queryForList("SELECT question FROM attempt_question WHERE id = ? ORDER BY question", Integer.class, attemptId);
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    // Exams the student can take: not started yet, or started and still open
    @GetMapping("/exam")
    public List<Exam> listExams(Authentication auth) {
        return jdbc.query(
                "SELECT e.id, e.title, e.num_questions FROM exams e "
                        + "WHERE NOT EXISTS (SELECT 1 FROM attempts a WHERE a.exam = e.id AND a.user = ? AND a.open = 0) "
                        + "ORDER BY e.id",
                (rs, rowNum) -> new Exam(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getInt("num_questions")
                ), currentUserId(auth));
    }

    // Creates the attempt and picks num_questions random questions from the exam's pool.
    // Starting an exam that is already open returns the same attempt; a closed exam cannot be started again.
    @PostMapping("/exam/{examId}/start")
    @Transactional
    public ResponseEntity<?> startExam(@PathVariable int examId, Authentication auth) {
        int userId = currentUserId(auth);

        Attempt existing = findAttempt(userId, examId);
        if (existing != null) {
            if (existing.getOpen()) {
                return ResponseEntity.ok(Map.of("attempt", existing, "questions", questionIds(existing.getId())));
            }
            return error(HttpStatus.CONFLICT, "You have already completed exam " + examId + " and cannot start it again");
        }

        List<Exam> exams = jdbc.query("SELECT id, title, num_questions FROM exams WHERE id = ?",
                (rs, rowNum) -> new Exam(
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getInt("num_questions")
                ), examId);
        if (exams.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "Exam " + examId + " not found");
        }
        Exam exam = exams.get(0);

        Integer poolSize = jdbc.queryForObject("SELECT COUNT(*) FROM exam_questions WHERE exam = ?", Integer.class, examId);
        if (poolSize == null || poolSize < exam.getNumQuestions()) {
            return error(HttpStatus.CONFLICT, "Exam " + examId + " is not ready yet: it has " + poolSize
                    + " of the " + exam.getNumQuestions() + " questions it needs");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO attempts (user, exam, score, open) VALUES (?, ?, NULL, 1)", Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, userId);
            ps.setInt(2, examId);
            return ps;
        }, keyHolder);
        int attemptId = keyHolder.getKey().intValue();

        List<Integer> picked = jdbc.queryForList(
                "SELECT question FROM exam_questions WHERE exam = ? ORDER BY RAND() LIMIT ?",
                Integer.class, examId, exam.getNumQuestions());
        for (Integer questionId : picked) {
            jdbc.update("INSERT INTO attempt_question (id, question, chosen) VALUES (?, ?, NULL)", attemptId, questionId);
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("attempt", findAttempt(userId, examId), "questions", questionIds(attemptId)));
    }

    // The ids of the questions picked for the student's open attempt
    @GetMapping("/exam/{examId}")
    public ResponseEntity<?> getQuestionIds(@PathVariable int examId, Authentication auth) {
        Attempt attempt = findAttempt(currentUserId(auth), examId);
        if (attempt == null) {
            return error(HttpStatus.CONFLICT, "You have not started exam " + examId);
        }
        if (!attempt.getOpen()) {
            return error(HttpStatus.CONFLICT, "Exam " + examId + " is closed");
        }
        return ResponseEntity.ok(questionIds(attempt.getId()));
    }

    // The question and its options, plus the student's current answer. The correct answer is not included.
    @GetMapping("/exam/{examId}/{questionId}")
    public ResponseEntity<?> getQuestion(@PathVariable int examId, @PathVariable int questionId, Authentication auth) {
        Attempt attempt = findAttempt(currentUserId(auth), examId);
        if (attempt == null) {
            return error(HttpStatus.CONFLICT, "You have not started exam " + examId);
        }
        if (!attempt.getOpen()) {
            return error(HttpStatus.CONFLICT, "Exam " + examId + " is closed");
        }

        List<Map<String, Object>> rows = jdbc.query(
                "SELECT q.id, q.text, q.option_a, q.option_b, q.option_c, q.option_d, aq.chosen "
                        + "FROM attempt_question aq JOIN questions q ON q.id = aq.question "
                        + "WHERE aq.id = ? AND aq.question = ?",
                (rs, rowNum) -> {
                    // LinkedHashMap rather than Map.of because "chosen" is null until the student answers
                    Map<String, Object> question = new LinkedHashMap<>();
                    question.put("id", rs.getInt("id"));
                    question.put("text", rs.getString("text"));
                    question.put("optionA", rs.getString("option_a"));
                    question.put("optionB", rs.getString("option_b"));
                    question.put("optionC", rs.getString("option_c"));
                    question.put("optionD", rs.getString("option_d"));
                    question.put("chosen", rs.getString("chosen"));
                    return question;
                }, attempt.getId(), questionId);
        if (rows.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "Question " + questionId + " is not part of your attempt at exam " + examId);
        }
        return ResponseEntity.ok(rows.get(0));
    }

    // Saves the student's answer, e.g. {"chosen": "B"}. Answering again replaces the earlier answer.
    @PostMapping("/exam/{examId}/{questionId}")
    public ResponseEntity<?> answerQuestion(@PathVariable int examId, @PathVariable int questionId,
                                            @RequestBody AttemptQuestion answer, Authentication auth) {
        Attempt attempt = findAttempt(currentUserId(auth), examId);
        if (attempt == null) {
            return error(HttpStatus.CONFLICT, "You have not started exam " + examId);
        }
        if (!attempt.getOpen()) {
            return error(HttpStatus.CONFLICT, "Exam " + examId + " is closed");
        }

        String chosen = answer.getChosen() == null ? null : answer.getChosen().toUpperCase();
        if (chosen == null || !List.of("A", "B", "C", "D").contains(chosen)) {
            return error(HttpStatus.BAD_REQUEST, "chosen must be one of A, B, C or D");
        }

        int updated = jdbc.update("UPDATE attempt_question SET chosen = ? WHERE id = ? AND question = ?",
                chosen, attempt.getId(), questionId);
        if (updated == 0) {
            return error(HttpStatus.NOT_FOUND, "Question " + questionId + " is not part of your attempt at exam " + examId);
        }
        return ResponseEntity.ok(new AttemptQuestion(attempt.getId(), questionId, chosen));
    }

    // Scores the attempt (one point per correct answer) and closes it for good
    @PostMapping("/exam/{examId}/close")
    public ResponseEntity<?> closeExam(@PathVariable int examId, Authentication auth) {
        int userId = currentUserId(auth);
        Attempt attempt = findAttempt(userId, examId);
        if (attempt == null) {
            return error(HttpStatus.CONFLICT, "You have not started exam " + examId);
        }
        if (!attempt.getOpen()) {
            return error(HttpStatus.CONFLICT, "Exam " + examId + " is already closed");
        }

        jdbc.update("UPDATE attempts SET open = 0, score = ("
                        + "SELECT COUNT(*) FROM attempt_question aq JOIN questions q ON q.id = aq.question "
                        + "WHERE aq.id = ? AND aq.chosen = q.correct"
                        + ") WHERE id = ?",
                attempt.getId(), attempt.getId());
        return ResponseEntity.ok(findAttempt(userId, examId));
    }
}
