// Single page front end for the Online Exam System API.
// The JWT from /api/auth/login is kept in localStorage and sent as "Authorization: Bearer <token>".

const TOKEN_KEY = "examToken";

const $ = (id) => document.getElementById(id);

const views = ["login-view", "admin-view", "student-view", "attempt-view", "result-view"];

// The logged-in user's role: "ADMIN", "STUDENT" or null
let currentRole = null;

// State for the student's current attempt
let attempt = null; // { examId, title, questionIds, questions: {id -> question}, currentIndex }

// ---------- Helpers ----------

function showView(id) {
    views.forEach((view) => ($(view).hidden = view !== id));
}

function showMessage(text, type = "error") {
    const box = $("message");
    box.textContent = text;
    box.className = "message " + type;
    box.hidden = false;
    window.scrollTo({ top: 0, behavior: "smooth" });
}

function clearMessage() {
    $("message").hidden = true;
}

// Builds an element: el("button", { class: "small", onclick: fn }, "Edit")
function el(tag, attrs = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(attrs)) {
        if (key.startsWith("on")) {
            node.addEventListener(key.slice(2), value);
        } else if (key === "class") {
            node.className = value;
        } else {
            node.setAttribute(key, value);
        }
    }
    for (const child of children) {
        node.append(child);
    }
    return node;
}

function emptyRow(columns, text) {
    return el("tr", {}, el("td", { colspan: columns, class: "empty" }, text));
}

// Calls the API. Returns the parsed JSON (or null for empty responses) and throws an Error with a readable message otherwise.
async function api(method, path, body) {
    const headers = {};
    const token = localStorage.getItem(TOKEN_KEY);
    if (token) {
        headers["Authorization"] = "Bearer " + token;
    }
    if (body !== undefined) {
        headers["Content-Type"] = "application/json";
    }

    const response = await fetch(path, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
    });

    const text = await response.text();
    const data = text ? JSON.parse(text) : null;

    if (response.status === 403 && path !== "/api/auth/login") {
        // Missing, expired or invalid token
        logout();
        throw new Error("Your session has expired. Please log in again.");
    }
    if (!response.ok) {
        // Our controllers send {"error": "..."}; Spring's default errors also have an "error" field like "Bad Request"
        throw new Error((data && data.error) || "Request failed with status " + response.status);
    }
    return data;
}

// Wraps an async action so any error is shown in the message box
function handle(action) {
    return async (event) => {
        if (event && event.preventDefault) {
            event.preventDefault();
        }
        clearMessage();
        try {
            await action(event);
        } catch (error) {
            showMessage(error.message);
        }
    };
}

// ---------- Login / logout ----------

async function startSession() {
    const me = await api("GET", "/api/me");
    $("user-name").textContent = me.username;
    $("user-info").hidden = false;

    if (me.roles.includes("ROLE_ADMIN")) {
        currentRole = "ADMIN";
        showView("admin-view");
        if (location.hash.startsWith("#/")) {
            await routeAdmin();
        } else {
            location.hash = "#/exams"; // triggers routeAdmin via hashchange
        }
    } else if (me.roles.includes("ROLE_STUDENT")) {
        currentRole = "STUDENT";
        await showStudentExams();
    } else {
        showMessage("Your account has no role this app recognises.");
    }
}

function logout() {
    localStorage.removeItem(TOKEN_KEY);
    currentRole = null;
    attempt = null;
    history.replaceState(null, "", location.pathname); // drop any #/admin/page from the URL
    $("user-info").hidden = true;
    $("login-form").reset();
    showView("login-view");
}

$("login-form").addEventListener("submit", handle(async () => {
    const data = await api("POST", "/api/auth/login", {
        username: $("login-username").value,
        password: $("login-password").value,
    });
    localStorage.setItem(TOKEN_KEY, data.token);
    await startSession();
}));

$("logout-button").addEventListener("click", () => {
    clearMessage();
    logout();
});

// ---------- Admin: page router ----------
// Admin pages live in index.html and are switched by the URL hash:
//   #/exams            #/exams/new        #/exams/{id}       #/exams/{id}/edit
//   #/questions        #/questions/new    #/questions/{id}/edit
//   #/students         #/students/new
//   #/results          #/results/{examId}
//   #/attempts/{attemptId}

const adminPages = ["page-exams", "page-exam-form", "page-exam-detail", "page-questions",
    "page-question-form", "page-students", "page-student-form", "page-results", "page-attempt"];

// A message to show on the next page, e.g. "Exam created." after saving
let flashMessage = null;

function navigate(hash, message) {
    flashMessage = message || null;
    if (location.hash === hash) {
        routeAdmin();
    } else {
        location.hash = hash;
    }
}

function showAdminPage(id, tab) {
    adminPages.forEach((page) => ($(page).hidden = page !== id));
    document.querySelectorAll(".tabs a").forEach((link) => {
        link.classList.toggle("active", link.dataset.tab === tab);
    });
}

async function routeAdmin() {
    const [section = "exams", id, action] = location.hash.replace(/^#\/?/, "").split("/");

    clearMessage();
    if (flashMessage) {
        showMessage(flashMessage, "success");
        flashMessage = null;
    }

    try {
        if (section === "exams") {
            if (!id) await renderExams();
            else if (id === "new") renderExamForm(null);
            else if (action === "edit") await renderExamForm(id);
            else await renderExamDetail(id);
        } else if (section === "questions") {
            if (!id) await renderQuestions();
            else if (id === "new") renderQuestionForm(null);
            else await renderQuestionForm(id);
        } else if (section === "students") {
            if (id === "new") renderStudentForm();
            else await renderStudents();
        } else if (section === "results") {
            await renderResults(id || "");
        } else if (section === "attempts" && id) {
            await renderAttemptReview(id);
        } else {
            location.hash = "#/exams";
        }
    } catch (error) {
        showMessage(error.message);
    }
}

window.addEventListener("hashchange", () => {
    if (currentRole === "ADMIN") {
        routeAdmin();
    }
});

// ---------- Admin: exams ----------

async function renderExams() {
    showAdminPage("page-exams", "exams");
    const exams = await api("GET", "/api/admin/exam");
    const table = $("exam-table");
    table.replaceChildren();
    if (exams.length === 0) {
        table.append(emptyRow(3, "No exams yet. Use New exam to create one."));
    }
    for (const exam of exams) {
        table.append(el("tr", {},
            el("td", {}, el("a", { class: "cell-link", href: "#/exams/" + exam.id }, exam.title)),
            el("td", { class: "num" }, String(exam.numQuestions)),
            el("td", { class: "actions" },
                el("button", { class: "small", type: "button", onclick: () => navigate("#/exams/" + exam.id) }, "Manage"), " ",
                el("button", { class: "small danger", type: "button", onclick: handle(() => deleteExam(exam, "#/exams")) }, "Delete")
            )
        ));
    }
}

async function renderExamForm(id) {
    showAdminPage("page-exam-form", "exams");
    $("exam-form").reset();
    $("exam-id").value = id || "";
    const back = id ? "#/exams/" + id : "#/exams";
    $("exam-form-back").href = back;
    $("exam-form-cancel").href = back;

    if (id) {
        const data = await api("GET", "/api/admin/exam/" + id);
        $("exam-form-title").textContent = "Edit exam";
        $("exam-title").value = data.exam.title;
        $("exam-num-questions").value = data.exam.numQuestions;
    } else {
        $("exam-form-title").textContent = "New exam";
    }
    $("exam-title").focus();
}

$("exam-form").addEventListener("submit", handle(async () => {
    const id = $("exam-id").value;
    const body = {
        title: $("exam-title").value,
        numQuestions: Number($("exam-num-questions").value),
    };
    if (id) {
        await api("PUT", "/api/admin/exam/" + id, body);
        navigate("#/exams/" + id, "Exam updated.");
    } else {
        const exam = await api("POST", "/api/admin/exam", body);
        navigate("#/exams/" + exam.id, "Exam created. Now add questions to its pool.");
    }
}));

async function deleteExam(exam, afterHash) {
    if (!confirm('Delete exam "' + exam.title + '"? Its question pool is cleared too.')) {
        return;
    }
    await api("DELETE", "/api/admin/exam/" + exam.id);
    navigate(afterHash, "Exam deleted.");
}

// ---------- Admin: exam detail (question pool) ----------

let currentExam = null; // the exam shown on the detail page

async function renderExamDetail(id) {
    showAdminPage("page-exam-detail", "exams");
    const [data, allQuestions] = await Promise.all([
        api("GET", "/api/admin/exam/" + id),
        api("GET", "/api/admin/question"),
    ]);
    const exam = data.exam;
    const pool = data.questions;
    currentExam = exam;

    $("exam-detail-title").textContent = exam.title;
    $("exam-detail-edit").href = "#/exams/" + exam.id + "/edit";
    $("exam-detail-results").href = "#/results/" + exam.id;
    $("exam-detail-per-student").textContent = exam.numQuestions;
    $("exam-detail-pool-size").textContent = pool.length;

    const status = $("exam-detail-status");
    const missing = exam.numQuestions - pool.length;
    if (missing > 0) {
        status.className = "pill warn";
        status.textContent = "Needs " + missing + " more";
    } else {
        status.className = "pill ok";
        status.textContent = "Ready";
    }

    const table = $("exam-detail-questions");
    table.replaceChildren();
    if (pool.length === 0) {
        table.append(emptyRow(3, "The pool is empty. Add questions below."));
    }
    for (const question of pool) {
        table.append(el("tr", {},
            el("td", {}, question.text),
            el("td", {}, question.correct + ". " + question["option" + question.correct]),
            el("td", { class: "actions" },
                el("button", { class: "small danger", type: "button", onclick: handle(() => unassign(exam.id, question.id)) }, "Remove")
            )
        ));
    }

    // Only offer questions that aren't already in this exam
    const inPool = new Set(pool.map((q) => q.id));
    const available = allQuestions.filter((q) => !inPool.has(q.id));
    const select = $("assign-question");
    select.replaceChildren();
    for (const question of available) {
        select.append(el("option", { value: question.id }, question.text));
    }
    $("assign-form").hidden = available.length === 0;
    $("assign-empty").hidden = available.length > 0;
}

$("exam-detail-delete").addEventListener("click", handle(() => deleteExam(currentExam, "#/exams")));

$("assign-form").addEventListener("submit", handle(async () => {
    await api("POST", "/api/admin/exam/" + currentExam.id + "/question/" + $("assign-question").value);
    await renderExamDetail(currentExam.id);
}));

async function unassign(examId, questionId) {
    await api("DELETE", "/api/admin/exam/" + examId + "/question/" + questionId);
    await renderExamDetail(examId);
}

// ---------- Admin: questions ----------

async function renderQuestions() {
    showAdminPage("page-questions", "questions");
    const questions = await api("GET", "/api/admin/question");
    const table = $("question-table");
    table.replaceChildren();
    if (questions.length === 0) {
        table.append(emptyRow(3, "No questions yet. Use New question to create one."));
    }
    for (const question of questions) {
        table.append(el("tr", {},
            el("td", {}, question.text),
            el("td", {}, question.correct + ". " + question["option" + question.correct]),
            el("td", { class: "actions" },
                el("button", { class: "small", type: "button", onclick: () => navigate("#/questions/" + question.id + "/edit") }, "Edit"), " ",
                el("button", { class: "small danger", type: "button", onclick: handle(() => deleteQuestion(question)) }, "Delete")
            )
        ));
    }
}

async function renderQuestionForm(id) {
    showAdminPage("page-question-form", "questions");
    $("question-form").reset();
    $("question-id").value = id || "";

    if (id) {
        const question = await api("GET", "/api/admin/question/" + id);
        $("question-form-title").textContent = "Edit question";
        $("question-text").value = question.text;
        $("question-a").value = question.optionA;
        $("question-b").value = question.optionB;
        $("question-c").value = question.optionC;
        $("question-d").value = question.optionD;
        $("question-correct").value = question.correct;
    } else {
        $("question-form-title").textContent = "New question";
    }
    $("question-text").focus();
}

$("question-form").addEventListener("submit", handle(async () => {
    const id = $("question-id").value;
    const body = {
        text: $("question-text").value,
        optionA: $("question-a").value,
        optionB: $("question-b").value,
        optionC: $("question-c").value,
        optionD: $("question-d").value,
        correct: $("question-correct").value,
    };
    if (id) {
        await api("PUT", "/api/admin/question/" + id, body);
        navigate("#/questions", "Question updated.");
    } else {
        await api("POST", "/api/admin/question", body);
        navigate("#/questions", "Question created.");
    }
}));

async function deleteQuestion(question) {
    if (!confirm('Delete the question "' + question.text + '"? It is removed from every exam it belongs to.')) {
        return;
    }
    await api("DELETE", "/api/admin/question/" + question.id);
    navigate("#/questions", "Question deleted.");
}

// ---------- Admin: students ----------

async function renderStudents() {
    showAdminPage("page-students", "students");
    const students = await api("GET", "/api/admin/student");
    const table = $("student-table");
    table.replaceChildren();
    if (students.length === 0) {
        table.append(emptyRow(2, "No students yet. Use Add student to create one."));
    }
    for (const student of students) {
        table.append(el("tr", {},
            el("td", { class: "num" }, String(student.id)),
            el("td", {}, student.username)
        ));
    }
}

function renderStudentForm() {
    showAdminPage("page-student-form", "students");
    $("student-form").reset();
    $("student-username").focus();
}

$("student-form").addEventListener("submit", handle(async () => {
    const student = await api("POST", "/api/admin/student", {
        username: $("student-username").value,
        password: $("student-password").value,
    });
    navigate("#/students", "Student " + student.username + " created. They can now log in.");
}));

// ---------- Admin: results ----------

async function renderResults(examId) {
    showAdminPage("page-results", "results");
    const [results, exams] = await Promise.all([
        api("GET", "/api/admin/result"),
        api("GET", "/api/admin/exam"),
    ]);

    const filter = $("result-exam-filter");
    filter.replaceChildren(el("option", { value: "" }, "All exams"));
    for (const exam of exams) {
        filter.append(el("option", { value: exam.id }, exam.title));
    }
    filter.value = exams.some((e) => String(e.id) === examId) ? examId : "";

    const rows = filter.value ? results.filter((r) => String(r.examId) === filter.value) : results;
    const submitted = rows.filter((r) => !r.open);
    const inProgress = rows.length - submitted.length;
    let summary = submitted.length + " submitted, " + inProgress + " in progress";
    if (submitted.length > 0) {
        const average = submitted.reduce((sum, r) => sum + (r.total ? r.score / r.total : 0), 0) / submitted.length;
        summary += " · average score " + Math.round(average * 100) + "%";
    }
    if (!filter.value && rows.length > 0) {
        summary += " · ranks are within each exam";
    }
    $("result-summary").textContent = summary;

    const table = $("result-table");
    table.replaceChildren();
    if (rows.length === 0) {
        table.append(emptyRow(6, "No students have taken this exam yet."));
    }
    // Rows arrive grouped by exam with the best score first, so the order is already the rank list
    for (const result of rows) {
        table.append(el("tr", {},
            el("td", { class: "rank" + (result.rank !== null && result.rank <= 3 ? " top" : "") },
                result.rank === null ? "—" : "#" + result.rank),
            el("td", {}, result.username),
            el("td", {}, el("a", { class: "cell-link", href: "#/exams/" + result.examId }, result.examTitle)),
            el("td", { class: "num" }, result.open ? "—" : result.score + " / " + result.total),
            el("td", {}, result.open
                ? el("span", { class: "pill info" }, "In progress")
                : el("span", { class: "pill ok" }, "Submitted")),
            el("td", { class: "actions" },
                el("button", { class: "small", type: "button", onclick: () => navigate("#/attempts/" + result.attemptId) }, "View answers"))
        ));
    }
}

// ---------- Admin: one student's answers ----------

async function renderAttemptReview(attemptId) {
    showAdminPage("page-attempt", "results");
    const data = await api("GET", "/api/admin/result/" + attemptId);
    const result = data.result;

    $("attempt-review-back").href = "#/results/" + result.examId;
    $("attempt-review-title").textContent = result.username + " · " + result.examTitle;
    $("attempt-review-score").textContent = result.open ? "—" : result.score + " / " + result.total;
    $("attempt-review-rank").textContent = result.rank === null ? "—" : "#" + result.rank;
    const status = $("attempt-review-status");
    status.className = "pill " + (result.open ? "info" : "ok");
    status.textContent = result.open ? "In progress" : "Submitted";

    const list = $("attempt-review-answers");
    list.replaceChildren();
    if (data.answers.length === 0) {
        list.append(el("p", { class: "empty" }, "This attempt has no questions."));
    }
    data.answers.forEach((answer, index) => {
        let verdict;
        if (!answer.chosen) verdict = el("span", { class: "pill warn" }, "Skipped");
        else if (answer.chosen === answer.correct) verdict = el("span", { class: "pill ok" }, "Correct");
        else verdict = el("span", { class: "pill bad" }, "Wrong");

        const card = el("div", { class: "card review-question" },
            el("h3", {}, el("span", {}, (index + 1) + ". " + answer.text), verdict));

        for (const letter of ["A", "B", "C", "D"]) {
            const isCorrect = letter === answer.correct;
            const isChosen = letter === answer.chosen;
            let cls = "review-option";
            let tag = "";
            if (isCorrect && isChosen) { cls += " correct"; tag = "Student's answer · Correct"; }
            else if (isCorrect) { cls += " correct"; tag = "Correct answer"; }
            else if (isChosen) { cls += " wrong"; tag = "Student's answer"; }
            card.append(el("div", { class: cls },
                el("span", {}, el("strong", {}, letter + ". "), answer["option" + letter]),
                el("span", { class: "tag" }, tag)));
        }
        list.append(card);
    });
}

$("result-exam-filter").addEventListener("change", () => {
    navigate($("result-exam-filter").value ? "#/results/" + $("result-exam-filter").value : "#/results");
});
$("refresh-results").addEventListener("click", () => navigate(location.hash));

// ---------- Student: exam list ----------

async function showStudentExams() {
    attempt = null;
    showView("student-view");
    const exams = await api("GET", "/api/student/exam");
    const table = $("student-exam-table");
    table.replaceChildren();
    if (exams.length === 0) {
        table.append(emptyRow(3, "There are no exams for you to take right now."));
    }
    for (const exam of exams) {
        table.append(el("tr", {},
            el("td", {}, exam.title),
            el("td", {}, String(exam.numQuestions)),
            el("td", { class: "actions" },
                // Starting an exam that is already open just returns it, so this button also resumes
                el("button", { class: "primary small", type: "button", onclick: handle(() => startExam(exam)) }, "Start / continue")
            )
        ));
    }
}

// ---------- Student: taking an exam ----------

async function startExam(exam) {
    const data = await api("POST", "/api/student/exam/" + exam.id + "/start");

    // Load every picked question up front so the navigation can show which ones are answered
    const questions = await Promise.all(
        data.questions.map((questionId) => api("GET", "/api/student/exam/" + exam.id + "/" + questionId))
    );

    attempt = {
        examId: exam.id,
        title: exam.title,
        questionIds: data.questions,
        questions: Object.fromEntries(questions.map((q) => [q.id, q])),
        currentIndex: 0,
    };

    $("attempt-title").textContent = exam.title;
    showView("attempt-view");
    renderAttempt();
}

function renderAttempt() {
    const { questionIds, questions, currentIndex } = attempt;
    const answered = questionIds.filter((id) => questions[id].chosen).length;
    $("attempt-progress").textContent = answered + " of " + questionIds.length + " answered";

    const nav = $("question-nav");
    nav.replaceChildren();
    questionIds.forEach((id, index) => {
        const classes = ["small"];
        if (questions[id].chosen) classes.push("answered");
        if (index === currentIndex) classes.push("current");
        nav.append(el("button", {
            class: classes.join(" "),
            type: "button",
            "aria-label": "Question " + (index + 1),
            onclick: () => goToQuestion(index),
        }, String(index + 1)));
    });

    if (questionIds.length === 0) {
        $("question-panel").hidden = true;
        return;
    }

    const question = questions[questionIds[currentIndex]];
    $("question-panel").hidden = false;
    $("question-heading").textContent = "Question " + (currentIndex + 1) + " of " + questionIds.length;
    $("question-body").textContent = question.text;

    const options = $("options");
    options.replaceChildren();
    for (const letter of ["A", "B", "C", "D"]) {
        const input = el("input", {
            type: "radio",
            name: "answer",
            value: letter,
            onchange: handle(() => answer(question.id, letter)),
        });
        input.checked = question.chosen === letter;
        options.append(el("label", { class: "option" + (input.checked ? " selected" : "") },
            input,
            el("span", {}, el("strong", {}, letter + ". "), question["option" + letter])
        ));
    }

    $("prev-question").disabled = currentIndex === 0;
    $("next-question").disabled = currentIndex === questionIds.length - 1;
}

function goToQuestion(index) {
    clearMessage();
    attempt.currentIndex = index;
    renderAttempt();
}

async function answer(questionId, letter) {
    const saved = await api("POST", "/api/student/exam/" + attempt.examId + "/" + questionId, { chosen: letter });
    attempt.questions[questionId].chosen = saved.chosen;
    renderAttempt();
}

$("prev-question").addEventListener("click", () => goToQuestion(attempt.currentIndex - 1));
$("next-question").addEventListener("click", () => goToQuestion(attempt.currentIndex + 1));
$("back-to-exams").addEventListener("click", handle(showStudentExams));
$("result-back").addEventListener("click", handle(showStudentExams));

$("close-exam").addEventListener("click", handle(async () => {
    const unanswered = attempt.questionIds.filter((id) => !attempt.questions[id].chosen).length;
    const warning = unanswered > 0 ? unanswered + " question(s) are unanswered and will score 0. " : "";
    if (!confirm(warning + "Submit the exam? You cannot change your answers afterwards.")) {
        return;
    }

    await api("POST", "/api/student/exam/" + attempt.examId + "/close");
    $("result-title").textContent = attempt.title;
    attempt = null;
    showView("result-view");
}));

// ---------- Start up ----------

(async () => {
    if (!localStorage.getItem(TOKEN_KEY)) {
        showView("login-view");
        return;
    }
    try {
        await startSession();
    } catch (error) {
        showMessage(error.message);
    }
})();
