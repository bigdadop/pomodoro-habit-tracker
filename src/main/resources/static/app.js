// Pomodoro Habit Tracker - the code that runs in the browser.
//
// The Java application does the real work: it saves the data, calculates the
// streaks and runs the timer. This file only asks the Java API for data, shows
// it on the page, and sends the user's clicks back to the API.

"use strict";

const $ = (id) => document.getElementById(id);

const state = {
  habits: [],             // the last habit list from GET /api/habits
  timer: null,            // the last timer state from GET /api/timer
  timerReceivedAt: 0,     // when we received it (Date.now()), to count down between updates
  editingHabitId: null,   // id of the habit being edited, or null when adding a new one
  checkingFinish: false,  // true while we ask the server whether the timer has finished
  offline: false,         // true when the Java application cannot be reached
  lastSeenDate: null,     // this computer's date at the last full refresh
};

// ---------------------------------------------------------------------------
// Talking to the Java API
// ---------------------------------------------------------------------------

/**
 * Sends a request to the Java API and returns its JSON answer.
 * Throws an Error with the server's message if something went wrong.
 */
async function api(method, url, body) {
  const options = { method, headers: {} };
  if (body !== undefined) {
    options.headers["Content-Type"] = "application/json";
    options.body = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(url, options);
  } catch (networkError) {
    throw new Error("Cannot reach the Java application. Is it still running?");
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.error || `Request failed (HTTP ${response.status}).`);
  }
  return data;
}

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------

/** Makes user text safe to put into HTML, so a habit called "<b>" is shown as text. */
function escapeHtml(text) {
  return String(text)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

/** plural(1, "day") -> "1 day", plural(3, "day") -> "3 days" */
function plural(count, word) {
  return `${count} ${word}${count === 1 ? "" : "s"}`;
}

/** 1500 -> "25:00" */
function formatTime(totalSeconds) {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

/** Formats a date such as "2026-10-24" (read as a local date, not UTC). */
function formatDate(isoDate, options) {
  const [year, month, day] = isoDate.split("-").map(Number);
  return new Date(year, month - 1, day).toLocaleDateString(undefined, options);
}

/** Today's date on this computer, as "yyyy-mm-dd". */
function localIsoDate() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
}

let messageTimeout = null;

/** Shows a green (success) or red (error) message at the top of the page. */
function showMessage(text, type = "success", stayVisible = false) {
  const box = $("message");
  box.textContent = text;
  box.className = type === "error" ? "message error" : "message";
  box.hidden = false;
  clearTimeout(messageTimeout);
  if (!stayVisible) {
    messageTimeout = setTimeout(() => { box.hidden = true; }, type === "error" ? 8000 : 4000);
  }
}

function setOffline(message) {
  if (!state.offline) {
    state.offline = true;
    showMessage(message, "error", true);   // stays until the app is reachable again
  }
}

function setOnline() {
  if (state.offline) {
    state.offline = false;
    showMessage("Connected to the Java application again.");
  }
}

/** A short beep when the timer finishes (uses the browser's built-in audio, no sound file). */
function playBeep() {
  try {
    const audio = new (window.AudioContext || window.webkitAudioContext)();
    const oscillator = audio.createOscillator();
    const volume = audio.createGain();
    oscillator.frequency.value = 880;
    volume.gain.value = 0.15;
    oscillator.connect(volume);
    volume.connect(audio.destination);
    oscillator.start();
    oscillator.stop(audio.currentTime + 0.5);
    oscillator.onended = () => audio.close();
  } catch (error) {
    // Sound is optional; some browsers block it.
  }
}

// ---------------------------------------------------------------------------
// Loading and showing data
// ---------------------------------------------------------------------------

/** Loads habits, dashboard numbers and the timer, then redraws the page. */
async function loadAll() {
  try {
    const [habits, dashboard, timer] = await Promise.all([
      api("GET", "/api/habits"),
      api("GET", "/api/dashboard"),
      api("GET", "/api/timer"),
    ]);
    setOnline();
    state.habits = habits;
    state.lastSeenDate = localIsoDate();
    renderDashboard(dashboard);
    renderTodayList(habits);
    renderHabitList(habits);
    renderHabitSelect(habits);
    setTimerState(timer);
  } catch (error) {
    setOffline(error.message);
  }
}

function renderDashboard(data) {
  $("today-label").textContent = formatDate(data.today,
    { weekday: "long", day: "numeric", month: "long", year: "numeric" });

  $("stat-total").textContent = data.totalHabits;
  $("stat-done").textContent = `${data.completedToday} / ${data.totalHabits}`;
  const percent = data.totalHabits === 0 ? 0 : (data.completedToday / data.totalHabits) * 100;
  $("stat-done-bar").style.width = `${percent}%`;
  $("stat-streaks").textContent = data.activeStreaks;
  $("stat-best").textContent = `Best streak: ${plural(data.bestCurrentStreak, "day")}`;
  $("stat-sessions").textContent = data.totalFocusSessions;
  $("stat-sessions-today").textContent = `Today: ${data.focusSessionsToday} (${data.focusMinutesToday} min)`;

  // If the app's "today" is not this computer's date, explain why (usually Docker's time zone).
  const warning = $("date-warning");
  if (data.today !== localIsoDate()) {
    const appDate = formatDate(data.today, { weekday: "short", day: "numeric", month: "short" });
    const computerDate = formatDate(localIsoDate(), { weekday: "short", day: "numeric", month: "short" });
    warning.textContent = `Note: the app's date is ${appDate} (time zone ${data.timeZone}), `
      + `but your computer says ${computerDate}. If you use Docker, set TZ as explained in the README.`;
    warning.hidden = false;
  } else {
    warning.hidden = true;
  }

  renderSessions(data.recentSessions);
}

function renderSessions(sessions) {
  $("sessions-body").innerHTML = sessions.map((session) => `
    <tr>
      <td>${session.habitName ? escapeHtml(session.habitName) : '<span class="muted">No habit</span>'}</td>
      <td>${session.durationMinutes} min</td>
      <td>${new Date(session.completedAt).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })}</td>
    </tr>`).join("");
  $("sessions-wrap").hidden = sessions.length === 0;
  $("sessions-empty").hidden = sessions.length > 0;
}

/** The "Mark done" button, or a "Done" label with an "Undo" button. */
function completionControls(habit) {
  const name = escapeHtml(habit.name);
  if (habit.completedToday) {
    return `<span class="badge">✓ Done</span>
      <button type="button" class="btn small" data-action="uncomplete" data-id="${habit.id}"
              aria-label="Undo: ${name} is not done today">Undo</button>`;
  }
  return `<button type="button" class="btn small primary" data-action="complete" data-id="${habit.id}"
                  aria-label="Mark ${name} as done today">Mark done</button>`;
}

function renderTodayList(habits) {
  $("today-list").innerHTML = habits.map((habit) => `
    <li class="today-item">
      <div>
        <span class="habit-name">${escapeHtml(habit.name)}</span>
        <span class="streak">${habit.currentStreak > 0
          ? `🔥 ${plural(habit.currentStreak, "day")} streak`
          : "No active streak yet"}</span>
      </div>
      <div class="habit-actions">${completionControls(habit)}</div>
    </li>`).join("");
  $("today-empty").hidden = habits.length > 0;
}

/** One small square of the "last 7 days" row. */
function dayBox(day, isToday) {
  const label = formatDate(day.date, { weekday: "short", day: "numeric", month: "short" });
  const classes = ["day", day.completed ? "done" : "", isToday ? "is-today" : ""].join(" ").trim();
  return `<span class="${classes}" title="${label}: ${day.completed ? "done" : "not done"}">`
    + `${formatDate(day.date, { weekday: "narrow" })}</span>`;
}

function renderHabitList(habits) {
  $("habit-count").textContent = `(${habits.length})`;
  $("habit-list").innerHTML = habits.map((habit) => {
    const daysDone = habit.lastSevenDays.filter((day) => day.completed).length;
    const added = habit.createdOn
      ? ` · Added ${formatDate(habit.createdOn, { day: "numeric", month: "short", year: "numeric" })}`
      : "";
    return `
    <li class="habit-item">
      <div class="habit-main">
        <span class="habit-name">${escapeHtml(habit.name)}</span>
        ${habit.description ? `<p class="habit-description">${escapeHtml(habit.description)}</p>` : ""}
        <div class="week" role="img" aria-label="Done on ${daysDone} of the last 7 days">
          ${habit.lastSevenDays.map((day, index) => dayBox(day, index === 6)).join("")}
        </div>
        <p class="habit-meta">
          Current streak: <strong>${plural(habit.currentStreak, "day")}</strong>
          · Best: ${plural(habit.longestStreak, "day")}
          · Done on ${plural(habit.totalCompletions, "day")} in total
          · Focus: ${plural(habit.focusSessions, "session")} (${habit.focusMinutes} min)${added}
        </p>
      </div>
      <div class="habit-actions">
        ${completionControls(habit)}
        <button type="button" class="btn small" data-action="edit" data-id="${habit.id}">Edit</button>
        <button type="button" class="btn small danger" data-action="delete" data-id="${habit.id}">Delete</button>
      </div>
    </li>`;
  }).join("");
  $("habits-empty").hidden = habits.length > 0;
}

/** Fills the "Habit for this focus session" drop-down list. */
function renderHabitSelect(habits) {
  const select = $("timer-habit");
  const previousChoice = select.value;
  select.innerHTML = '<option value="">No habit</option>'
    + habits.map((habit) => `<option value="${habit.id}">${escapeHtml(habit.name)}</option>`).join("");
  if (habits.some((habit) => String(habit.id) === previousChoice)) {
    select.value = previousChoice;
  }
}

// ---------------------------------------------------------------------------
// Timer
// ---------------------------------------------------------------------------

/** Stores a new timer state from the server and redraws the timer. */
function setTimerState(timer) {
  const previous = state.timer;
  state.timer = timer;
  state.timerReceivedAt = Date.now();

  // The timer has just reached 00:00 while this page was open.
  if (previous && previous.status === "RUNNING" && timer.status === "FINISHED") {
    timerFinished(timer);
  }
  renderTimerControls(timer);
  renderTimerDisplay();
}

/** Seconds left right now: the server's value minus the time since we received it. */
function remainingSeconds() {
  const timer = state.timer;
  if (timer.status !== "RUNNING") {
    return timer.remainingSeconds;
  }
  const secondsSinceUpdate = (Date.now() - state.timerReceivedAt) / 1000;
  return Math.max(0, Math.ceil(timer.remainingSeconds - secondsSinceUpdate));
}

/** Runs four times a second to update the countdown on the page. */
function renderTimerDisplay() {
  const timer = state.timer;
  if (!timer) {
    return;
  }
  const remaining = remainingSeconds();
  $("timer-display").textContent = formatTime(remaining);
  const percentDone = ((timer.durationSeconds - remaining) / timer.durationSeconds) * 100;
  $("timer-bar").style.width = `${percentDone}%`;

  const inProgress = timer.status === "RUNNING" || timer.status === "PAUSED";
  document.title = inProgress
    ? `${formatTime(remaining)} · ${timer.modeLabel} - Pomodoro Habit Tracker`
    : "Pomodoro Habit Tracker";

  // Our countdown reached 00:00: ask the Java application, which saves the finished session.
  if (timer.status === "RUNNING" && remaining === 0 && !state.checkingFinish) {
    state.checkingFinish = true;
    syncTimer().finally(() => { state.checkingFinish = false; });
  }
}

function renderTimerControls(timer) {
  const status = timer.status;
  const inProgress = status === "RUNNING" || status === "PAUSED";

  // The mode buttons are created once from the modes the Java application sends.
  const modeBox = $("mode-buttons");
  if (modeBox.children.length === 0) {
    modeBox.innerHTML = timer.modes.map((mode) => `
      <button type="button" class="mode" data-mode="${mode.mode}">
        <span class="mode-name">${escapeHtml(mode.label)}</span>
        <span class="mode-minutes">${mode.minutes} min</span>
      </button>`).join("");
  }
  for (const button of modeBox.querySelectorAll("button")) {
    button.setAttribute("aria-pressed", String(button.dataset.mode === timer.mode));
    button.disabled = inProgress;
  }

  $("btn-start").disabled = inProgress;
  $("btn-pause").disabled = status !== "RUNNING";
  $("btn-resume").disabled = status !== "PAUSED";
  $("btn-reset").disabled = status === "IDLE";

  // A habit can only be chosen for a focus session that has not started yet.
  const select = $("timer-habit");
  select.disabled = inProgress || timer.mode !== "FOCUS";
  if (timer.mode !== "FOCUS") {
    select.value = "";
  } else if (inProgress) {
    select.value = timer.habitId === null ? "" : String(timer.habitId);
  }

  $("timer-status").textContent = timerStatusText(timer);
}

function timerStatusText(timer) {
  const isFocus = timer.mode === "FOCUS";
  switch (timer.status) {
    case "IDLE":
      return isFocus ? "Ready to focus. Pick a habit (optional) and press Start."
        : `Ready for a ${timer.modeLabel.toLowerCase()}.`;
    case "RUNNING":
      if (!isFocus) {
        return "Break time - relax!";
      }
      return timer.habitName ? `Focusing on "${timer.habitName}"...` : "Focusing...";
    case "PAUSED":
      return "Paused. Press Resume to continue.";
    case "FINISHED":
      if (isFocus && timer.lastSavedSession) {
        const time = new Date(timer.lastSavedSession.completedAt)
          .toLocaleTimeString(undefined, { timeStyle: "short" });
        return `Done! Focus session saved at ${time}. Time for a break.`;
      }
      return isFocus ? "Focus session finished." : "Break is over. Ready for the next focus session?";
    default:
      return "";
  }
}

function timerFinished(timer) {
  playBeep();
  if (timer.mode === "FOCUS") {
    showMessage("Focus session complete and saved. Take a short break!");
  } else {
    showMessage("Break is over. Ready for the next focus session?");
  }
  loadAll();   // update the numbers and the recent sessions
}

/** Asks the Java application for the real timer state. */
async function syncTimer() {
  try {
    const timer = await api("GET", "/api/timer");
    setOnline();
    setTimerState(timer);
  } catch (error) {
    setOffline(error.message);
  }
}

/** Sends a timer action (start, pause, resume, reset or mode) to the Java application. */
async function timerAction(action, body) {
  try {
    const result = await api("POST", `/api/timer/${action}`, body);
    setTimerState(result.timer);
    if (action !== "mode") {
      showMessage(result.message);
    }
  } catch (error) {
    showMessage(error.message, "error");
    syncTimer();
  }
}

$("btn-start").addEventListener("click", () => {
  const habitId = $("timer-habit").value;
  timerAction("start", { habitId: habitId ? Number(habitId) : null });
});
$("btn-pause").addEventListener("click", () => timerAction("pause"));
$("btn-resume").addEventListener("click", () => timerAction("resume"));
$("btn-reset").addEventListener("click", () => {
  const timer = state.timer;
  const unfinishedFocus = timer.mode === "FOCUS" && (timer.status === "RUNNING" || timer.status === "PAUSED");
  if (unfinishedFocus && !confirm("Reset the timer? This unfinished focus session will not be saved.")) {
    return;
  }
  timerAction("reset");
});
$("mode-buttons").addEventListener("click", (event) => {
  const button = event.target.closest("button[data-mode]");
  if (button && !button.disabled) {
    timerAction("mode", { mode: button.dataset.mode });
  }
});

// ---------------------------------------------------------------------------
// Habits: add, edit, delete, mark as done
// ---------------------------------------------------------------------------

function showFormError(text) {
  $("form-error").textContent = text;
  $("form-error").hidden = false;
}

function resetForm() {
  state.editingHabitId = null;
  $("habit-form").reset();
  $("form-title").textContent = "Add a new habit";
  $("form-submit").textContent = "Add habit";
  $("form-cancel").hidden = true;
  $("form-error").hidden = true;
}

function startEditing(habit) {
  state.editingHabitId = habit.id;
  showView("habits");
  $("form-title").textContent = `Edit habit "${habit.name}"`;
  $("habit-name").value = habit.name;
  $("habit-description").value = habit.description;
  $("form-submit").textContent = "Save changes";
  $("form-cancel").hidden = false;
  $("form-error").hidden = true;
  $("habit-form").scrollIntoView({ block: "center" });
  $("habit-name").focus();
}

$("habit-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const name = $("habit-name").value.trim();
  const description = $("habit-description").value.trim();
  if (name === "") {
    showFormError("Please enter a habit name.");
    $("habit-name").focus();
    return;
  }

  try {
    const result = state.editingHabitId === null
      ? await api("POST", "/api/habits", { name, description })
      : await api("PUT", `/api/habits/${state.editingHabitId}`, { name, description });
    resetForm();
    showMessage(result.message);
    await loadAll();
  } catch (error) {
    showFormError(error.message);   // for example "You already have a habit called ..."
  }
});

$("form-cancel").addEventListener("click", resetForm);

async function habitRequest(method, url) {
  try {
    const result = await api(method, url);
    showMessage(result.message);
    await loadAll();
  } catch (error) {
    showMessage(error.message, "error");
  }
}

// One click listener for all habit buttons (they are re-created whenever the list changes).
document.addEventListener("click", async (event) => {
  const button = event.target.closest("button[data-action]");
  if (!button) {
    return;
  }
  const id = Number(button.dataset.id);
  const habit = state.habits.find((h) => h.id === id);
  if (!habit) {
    return;
  }

  switch (button.dataset.action) {
    case "complete":
      button.disabled = true;
      await habitRequest("POST", `/api/habits/${id}/complete`);
      break;
    case "uncomplete":
      button.disabled = true;
      await habitRequest("POST", `/api/habits/${id}/uncomplete`);
      break;
    case "edit":
      startEditing(habit);
      break;
    case "delete":
      if (confirm(`Delete the habit "${habit.name}"?\n\nIts completed days and streak will be removed. `
          + "Past focus sessions stay in the history. This cannot be undone.")) {
        await habitRequest("DELETE", `/api/habits/${id}`);
        if (state.editingHabitId === id) {
          resetForm();
        }
      }
      break;
    default:
      break;
  }
});

// ---------------------------------------------------------------------------
// Tabs ("Dashboard" and "Manage habits")
// ---------------------------------------------------------------------------

function showView(name) {
  for (const view of ["dashboard", "habits"]) {
    $(`view-${view}`).hidden = view !== name;
  }
  for (const tab of document.querySelectorAll(".tab")) {
    if (tab.dataset.view === name) {
      tab.setAttribute("aria-current", "page");
    } else {
      tab.removeAttribute("aria-current");
    }
  }
  history.replaceState(null, "", `#${name}`);   // a page refresh stays on the same tab
}

for (const tab of document.querySelectorAll(".tab")) {
  tab.addEventListener("click", () => showView(tab.dataset.view));
}

/** The tab named in the address bar: "#habits" or the dashboard. */
function viewFromAddress() {
  return location.hash === "#habits" ? "habits" : "dashboard";
}

// Also follow the address bar (for example the browser's Back button).
window.addEventListener("hashchange", () => showView(viewFromAddress()));

// The "Add your first habit" button on the dashboard.
document.addEventListener("click", (event) => {
  const link = event.target.closest("[data-goto]");
  if (link) {
    showView(link.dataset.goto);
    $("habit-name").focus();
  }
});

// ---------------------------------------------------------------------------
// Start
// ---------------------------------------------------------------------------

showView(viewFromAddress());
loadAll();

setInterval(renderTimerDisplay, 250);   // smooth countdown on the page
setInterval(() => {
  syncTimer();                          // get the real timer state from Java every 5 seconds
  if (state.lastSeenDate && localIsoDate() !== state.lastSeenDate) {
    loadAll();                          // midnight has passed: refresh "today"
  }
}, 5000);
document.addEventListener("visibilitychange", () => {
  if (!document.hidden) {
    loadAll();                          // coming back to this tab: refresh everything
  }
});
