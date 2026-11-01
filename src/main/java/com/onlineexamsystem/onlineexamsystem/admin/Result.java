package com.onlineexamsystem.onlineexamsystem.admin;

// One student's attempt at one exam, as shown to the admin.
// score is null while the attempt is still open; total is how many questions were picked for it.
// rank is the student's position among the submitted attempts at the same exam (ties share a rank), null while open.
public class Result {

    private Integer attemptId;
    private String username;
    private Integer examId;
    private String examTitle;
    private Integer score;
    private Integer total;
    private boolean open;
    private Integer rank;

    public Result(Integer attemptId, String username, Integer examId, String examTitle, Integer score, Integer total, boolean open, Integer rank) {
        this.attemptId = attemptId;
        this.username = username;
        this.examId = examId;
        this.examTitle = examTitle;
        this.score = score;
        this.total = total;
        this.open = open;
        this.rank = rank;
    }

    public Integer getAttemptId() {
        return attemptId;
    }

    public String getUsername() {
        return username;
    }

    public Integer getExamId() {
        return examId;
    }

    public String getExamTitle() {
        return examTitle;
    }

    public Integer getScore() {
        return score;
    }

    public Integer getTotal() {
        return total;
    }

    public boolean getOpen() {
        return open;
    }

    public Integer getRank() {
        return rank;
    }
}
