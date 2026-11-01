package com.onlineexamsystem.onlineexamsystem.admin;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final String EXAM_COLUMNS = "id, title, num_questions";
    private static final String QUESTION_COLUMNS = "id, text, option_a, option_b, option_c, option_d, correct";
    @Autowired
    private JdbcTemplate jdbc;

    // The BCrypt encoder from SecurityConfig, used to hash new students' passwords
    @Autowired
    private PasswordEncoder passwordEncoder;

    private Exam findExam(int id) {
        List<Exam> rows = jdbc.query("SELECT " + EXAM_COLUMNS + " FROM exams WHERE id = ?", (rs, rowNum) -> new Exam(
                rs.getInt("id"),
                rs.getString("title"),
                rs.getInt("num_questions")
        ), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // Returns null if there is no question with this id
    private Question findQuestion(int id) {
        List<Question> rows = jdbc.query("SELECT " + QUESTION_COLUMNS + " FROM questions WHERE id = ?", (rs, rowNum) -> new Question(
                rs.getInt("id"),
                rs.getString("text"),
                rs.getString("option_a"),
                rs.getString("option_b"),
                rs.getString("option_c"),
                rs.getString("option_d"),
                rs.getString("correct")
        ), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // Runs an INSERT and returns the auto-generated id
    private int insert(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    @GetMapping("/exam")
    public List<Exam> listExams() {
        return jdbc.query("SELECT " + EXAM_COLUMNS + " FROM exams ORDER BY id", (rs, rowNum) -> new Exam(
                rs.getInt("id"),
                rs.getString("title"),
                rs.getInt("num_questions")
        ));
    }

    @GetMapping("/exam/{id}")
    public ResponseEntity<?> getExam(@PathVariable int id) {
        Exam exam = findExam(id);
        if (exam == null) {
            return error(HttpStatus.NOT_FOUND, "Exam " + id + " not found");
        }

        List<Question> questions = jdbc.query(
                "SELECT q.id, q.text, q.option_a, q.option_b, q.option_c, q.option_d, q.correct "
                        + "FROM questions q JOIN exam_questions eq ON eq.question = q.id "
                        + "WHERE eq.exam = ? ORDER BY q.id",
                (rs, rowNum) -> new Question(
                        rs.getInt("id"),
                        rs.getString("text"),
                        rs.getString("option_a"),
                        rs.getString("option_b"),
                        rs.getString("option_c"),
                        rs.getString("option_d"),
                        rs.getString("correct")
                ), id);
        return ResponseEntity.ok(Map.of("exam", exam, "questions", questions));
    }

    @PostMapping("/exam")
    public ResponseEntity<Exam> createExam(@Valid @RequestBody Exam exam) {

        int id = insert("INSERT INTO exams (title, num_questions) VALUES (?, ?)", exam.getTitle(), exam.getNumQuestions());
        return ResponseEntity.status(HttpStatus.CREATED).body(findExam(id));
    }

    @PutMapping("/exam/{id}")
    public ResponseEntity<?> updateExam(@PathVariable int id, @Valid @RequestBody Exam exam) {
        int updated = jdbc.update("UPDATE exams SET title = ?, num_questions = ? WHERE id = ?",
                exam.getTitle(), exam.getNumQuestions(), id);
        if (updated == 0) {
            return error(HttpStatus.NOT_FOUND, "Exam " + id + " not found");
        }
        return ResponseEntity.ok(findExam(id));
    }

    // Also removes the exam's question assignments; the questions themselves are kept
    @DeleteMapping("/exam/{id}")
    @Transactional
    public ResponseEntity<?> deleteExam(@PathVariable int id) {
        jdbc.update("DELETE FROM exam_questions WHERE exam = ?", id);
        int deleted = jdbc.update("DELETE FROM exams WHERE id = ?", id);
        if (deleted == 0) {
            return error(HttpStatus.NOT_FOUND, "Exam " + id + " not found");
        }
        return ResponseEntity.noContent().build();
    }

    // ---------- Assigning questions to exams ----------

    @PostMapping("/exam/{examId}/question/{questionId}")
    public ResponseEntity<?> assignQuestion(@PathVariable int examId, @PathVariable int questionId) {
        if (findExam(examId) == null) {
            return error(HttpStatus.NOT_FOUND, "Exam " + examId + " not found");
        }
        if (findQuestion(questionId) == null) {
            return error(HttpStatus.NOT_FOUND, "Question " + questionId + " not found");
        }
        Integer assigned = jdbc.queryForObject(
                "SELECT COUNT(*) FROM exam_questions WHERE exam = ? AND question = ?", Integer.class, examId, questionId);
        if (assigned != null && assigned > 0) {
            return error(HttpStatus.CONFLICT, "Question " + questionId + " is already assigned to exam " + examId);
        }

        jdbc.update("INSERT INTO exam_questions (exam, question) VALUES (?, ?)", examId, questionId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("exam", examId, "question", questionId));
    }

    @DeleteMapping("/exam/{examId}/question/{questionId}")
    public ResponseEntity<?> unassignQuestion(@PathVariable int examId, @PathVariable int questionId) {
        int deleted = jdbc.update("DELETE FROM exam_questions WHERE exam = ? AND question = ?", examId, questionId);
        if (deleted == 0) {
            return error(HttpStatus.NOT_FOUND, "Question " + questionId + " is not assigned to exam " + examId);
        }
        return ResponseEntity.noContent().build();
    }

    // ---------- Questions ----------

    @GetMapping("/question")
    public List<Question> listQuestions() {
        return jdbc.query("SELECT " + QUESTION_COLUMNS + " FROM questions ORDER BY id", (rs, rowNum) -> new Question(
                rs.getInt("id"),
                rs.getString("text"),
                rs.getString("option_a"),
                rs.getString("option_b"),
                rs.getString("option_c"),
                rs.getString("option_d"),
                rs.getString("correct")
        ));
    }

    @GetMapping("/question/{id}")
    public ResponseEntity<?> getQuestion(@PathVariable int id) {
        Question question = findQuestion(id);
        if (question == null) {
            return error(HttpStatus.NOT_FOUND, "Question " + id + " not found");
        }
        return ResponseEntity.ok(question);
    }

    @PostMapping("/question")
    public ResponseEntity<Question> createQuestion(@Valid @RequestBody Question question) {
        int id = insert("INSERT INTO questions (text, option_a, option_b, option_c, option_d, correct) VALUES (?, ?, ?, ?, ?, ?)",
                question.getText(), question.getOptionA(), question.getOptionB(), question.getOptionC(), question.getOptionD(), question.getCorrect());
        return ResponseEntity.status(HttpStatus.CREATED).body(findQuestion(id));
    }

    @PutMapping("/question/{id}")
    public ResponseEntity<?> updateQuestion(@PathVariable int id, @Valid @RequestBody Question question) {
        int updated = jdbc.update(
                "UPDATE questions SET text = ?, option_a = ?, option_b = ?, option_c = ?, option_d = ?, correct = ? WHERE id = ?",
                question.getText(), question.getOptionA(), question.getOptionB(), question.getOptionC(), question.getOptionD(), question.getCorrect(), id);
        if (updated == 0) {
            return error(HttpStatus.NOT_FOUND, "Question " + id + " not found");
        }
        return ResponseEntity.ok(findQuestion(id));
    }

    // Also removes the question from every exam it was assigned to
    @DeleteMapping("/question/{id}")
    @Transactional
    public ResponseEntity<?> deleteQuestion(@PathVariable int id) {
        jdbc.update("DELETE FROM exam_questions WHERE question = ?", id);
        int deleted = jdbc.update("DELETE FROM questions WHERE id = ?", id);
        if (deleted == 0) {
            return error(HttpStatus.NOT_FOUND, "Question " + id + " not found");
        }
        return ResponseEntity.noContent().build();
    }

    // ---------- Students ----------

    // Lists student accounts; passwords are never returned
    @GetMapping("/student")
    public List<Map<String, Object>> listStudents() {
        return jdbc.query("SELECT id, username FROM users WHERE role = 'STUDENT' ORDER BY id",
                (rs, rowNum) -> Map.of(
                        "id", rs.getInt("id"),
                        "username", rs.getString("username")
                ));
    }

    // Creates a student account from {"username": "...", "password": "..."}
    @PostMapping("/student")
    public ResponseEntity<?> createStudent(@RequestBody Student student) {
        Integer taken = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, student.getUsername());
        if (taken != null && taken > 0) {
            return error(HttpStatus.CONFLICT, "Username " + student.getUsername() + " is already taken");
        }

        int id = insert("INSERT INTO users (username, password, role) VALUES (?, ?, 'STUDENT')",
                student.getUsername(), passwordEncoder.encode(student.getPassword()));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "username", student.getUsername()));
    }

    // ---------- Results ----------

    // Every student's attempts with their score, grouped by exam
    @GetMapping("/result")
    public List<Result> listResults() {
        return jdbc.query(
                "SELECT a.id, u.username, e.id AS exam_id, e.title, a.score, a.open, "
                        + "(SELECT COUNT(*) FROM attempt_question aq WHERE aq.id = a.id) AS total "
                        + "FROM attempts a "
                        + "JOIN users u ON u.id = a.user "
                        + "JOIN exams e ON e.id = a.exam "
                        + "ORDER BY e.title, u.username",
                (rs, rowNum) -> new Result(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getInt("exam_id"),
                        rs.getString("title"),
                        rs.getObject("score", Integer.class),
                        rs.getInt("total"),
                        rs.getBoolean("open")
                ));
    }

    // One student's attempt with every question, the correct answer and what they chose
    @GetMapping("/result/{attemptId}")
    public ResponseEntity<?> getResult(@PathVariable int attemptId) {
        List<Result> rows = jdbc.query(
                "SELECT a.id, u.username, e.id AS exam_id, e.title, a.score, a.open, "
                        + "(SELECT COUNT(*) FROM attempt_question aq WHERE aq.id = a.id) AS total "
                        + "FROM attempts a "
                        + "JOIN users u ON u.id = a.user "
                        + "JOIN exams e ON e.id = a.exam "
                        + "WHERE a.id = ?",
                (rs, rowNum) -> new Result(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getInt("exam_id"),
                        rs.getString("title"),
                        rs.getObject("score", Integer.class),
                        rs.getInt("total"),
                        rs.getBoolean("open")
                ), attemptId);
        if (rows.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "Attempt " + attemptId + " not found");
        }

        List<AnswerDetail> answers = jdbc.query(
                "SELECT q.id, q.text, q.option_a, q.option_b, q.option_c, q.option_d, q.correct, aq.chosen "
                        + "FROM attempt_question aq JOIN questions q ON q.id = aq.question "
                        + "WHERE aq.id = ? ORDER BY q.id",
                (rs, rowNum) -> new AnswerDetail(
                        rs.getInt("id"),
                        rs.getString("text"),
                        rs.getString("option_a"),
                        rs.getString("option_b"),
                        rs.getString("option_c"),
                        rs.getString("option_d"),
                        rs.getString("correct"),
                        rs.getString("chosen")
                ), attemptId);
        return ResponseEntity.ok(Map.of("result", rows.get(0), "answers", answers));
    }

}
