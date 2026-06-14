window.AppState = {
    me: null,
    conversationId: null,
    selectedConversation: null,
    conversations: [],
    friends: [],
    groups: [],
    moments: [],
    socket: null,
    messageCount: 0
};

window.$ = selector => document.querySelector(selector);
window.$$ = selector => Array.from(document.querySelectorAll(selector));

window.escapeHtml = function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;");
};

function normalizeDate(value) {
    if (!value) return null;
    if (Array.isArray(value)) {
        const [year, month, day, hour = 0, minute = 0, second = 0] = value;
        return new Date(year, month - 1, day, hour, minute, second);
    }
    const text = String(value).includes("T") ? String(value) : String(value).replace(" ", "T");
    const date = new Date(text);
    return Number.isNaN(date.getTime()) ? null : date;
}

window.formatTime = function formatTime(value) {
    const date = normalizeDate(value);
    return date ? date.toLocaleTimeString("zh-CN", {hour: "2-digit", minute: "2-digit"}) : "";
};

window.formatDate = function formatDate(value) {
    const date = normalizeDate(value);
    return date ? date.toLocaleDateString("zh-CN", {year: "numeric", month: "long", day: "numeric"}) : "今天";
};

window.avatarText = function avatarText(name) {
    const value = String(name || "U").trim();
    return escapeHtml(value.slice(0, 2).toUpperCase());
};

window.avatarClass = function avatarClass(type) {
    if (type === "GROUP") return "group";
    if (type === "ADMIN") return "admin";
    return "";
};

window.avatarHtml = function avatarHtml(name, url, type, extra = "") {
    const style = url ? ` style="background-image:url('${escapeHtml(url)}')"` : "";
    const label = url ? "" : avatarText(name);
    return `<div class="avatar ${avatarClass(type)} ${extra}"${style}>${label}</div>`;
};

window.paintAvatar = function paintAvatar(element, user, type) {
    if (!element) return;
    const keep = [];
    if (element.classList.contains("avatar-lg")) keep.push("avatar-lg");
    if (element.classList.contains("avatar-sm")) keep.push("avatar-sm");
    element.className = ["avatar", avatarClass(type || user?.role), ...keep].filter(Boolean).join(" ");
    element.style.backgroundImage = user?.avatarUrl ? `url(${user.avatarUrl})` : "";
    element.textContent = user?.avatarUrl ? "" : avatarText(user?.nickname || user?.username || user?.peerName || user?.title);
};

window.refreshIcons = function refreshIcons() {
    window.lucide?.createIcons();
};

let toastTimer;
window.toast = function toast(message, duration = 2600) {
    const target = $("#toast");
    if (!target) return;
    target.textContent = message || "";
    target.hidden = !message;
    clearTimeout(toastTimer);
    if (message && duration) {
        toastTimer = setTimeout(() => {
            target.hidden = true;
            target.textContent = "";
        }, duration);
    }
};

window.switchView = function switchView(viewId) {
    $$(".rail-button[data-view]").forEach(item => item.classList.toggle("active", item.dataset.view === viewId));
    $$(".workspace").forEach(view => view.classList.toggle("active", view.id === viewId));
    refreshIcons();
};

function applySceneMode(mode) {
    const normalized = mode === "night" ? "night" : "day";
    const scene = $(".nav-illustration");
    const toggle = $("#sceneModeToggle");
    if (!scene || !toggle) return;
    document.body.classList.toggle("night", normalized === "night");
    scene.classList.toggle("night", normalized === "night");
    toggle.dataset.sceneMode = normalized;
    toggle.title = normalized === "night" ? "Switch to day scene" : "Switch to night scene";
    toggle.innerHTML = normalized === "night" ? `<i data-lucide="sun"></i>` : `<i data-lucide="moon"></i>`;
    localStorage.setItem("sceneMode", normalized);
    refreshIcons();
}

function animateThemeSwitch() {
    document.body.classList.remove("theme-transitioning");
    void document.body.offsetWidth;
    document.body.classList.add("theme-transitioning");
    setTimeout(() => document.body.classList.remove("theme-transitioning"), 620);
}

window.updateIdentity = function updateIdentity() {
    const me = AppState.me;
    if (!me) return;
    const displayName = me.nickname || me.username;
    $("#meName") && ($("#meName").textContent = displayName);
    $("#summaryName") && ($("#summaryName").textContent = displayName);
    $("#summaryRole") && ($("#summaryRole").textContent = me.role === "ADMIN" ? "系统管理员" : "Jakarta EE 开发者");
    $("#summarySignature") && ($("#summarySignature").textContent = me.signature || "专注于企业级应用开发");
    paintAvatar($("#meAvatar"), me);
    paintAvatar($("#summaryAvatar"), me);
    paintAvatar($("#profileAvatar"), me);
    const cover = $("#summaryCover");
    if (cover) {
        cover.style.backgroundImage = me.backgroundUrl ? `url(${me.backgroundUrl})` : "";
        cover.style.backgroundSize = me.backgroundUrl ? "cover" : "";
        cover.style.backgroundPosition = me.backgroundUrl ? "center" : "";
    }
    $("#adminNavButton") && ($("#adminNavButton").hidden = AppState.me.role !== "ADMIN");
};

window.updateDashboardMetrics = function updateDashboardMetrics() {
    $("#friendCount") && ($("#friendCount").textContent = AppState.friends.length);
    $("#groupCount") && ($("#groupCount").textContent = Math.max(AppState.groups.length, AppState.conversations.filter(item => item.type === "GROUP").length));
    $("#messageCount") && ($("#messageCount").textContent = AppState.messageCount);
    renderRightGroups();
    refreshIcons();
};

function renderRightGroups() {
    const target = $("#rightGroupList");
    if (!target) return;
    const groupConversations = AppState.conversations.filter(item => item.type === "GROUP");
    const rows = groupConversations.length
        ? groupConversations.map(item => ({
            id: item.id,
            type: "conversation",
            title: item.title || "群聊",
            subtitle: `${item.unreadCount || 0} 条未读消息`,
            count: item.unreadCount || ""
        }))
        : AppState.groups.map(item => ({
            id: item.id,
            type: "group",
            title: item.name,
            subtitle: "好友分组",
            count: ""
        }));

    target.innerHTML = rows.slice(0, 5).map(item => `
        <button class="mini-row" type="button" data-side-conversation="${item.type === "conversation" ? item.id : ""}">
            ${avatarHtml(item.title, null, "GROUP", "avatar-sm")}
            <span><strong>${escapeHtml(item.title)}</strong><span>${escapeHtml(item.subtitle)}</span></span>
            ${item.count ? `<b class="badge">${item.count}</b>` : ""}
        </button>
    `).join("") || `<div class="empty-state"><span>暂无群组</span></div>`;
}

function bindAppShell() {
    $$(".rail-button[data-view]").forEach(button => {
        button.addEventListener("click", () => {
            switchView(button.dataset.view);
            if (button.dataset.view === "adminView") {
                window.loadAdminDashboard?.();
            }
        });
    });
    applySceneMode(localStorage.getItem("sceneMode") || "day");
    $("#sceneModeToggle")?.addEventListener("click", event => {
        const current = event.currentTarget.dataset.sceneMode === "night" ? "night" : "day";
        animateThemeSwitch();
        applySceneMode(current === "night" ? "day" : "night");
    });

    $("#logoutButton")?.addEventListener("click", async () => {
        await ChatApi.post("/auth/logout");
        location.href = "index.html";
    });
    $("#rightGroupList")?.addEventListener("click", event => {
        const id = event.target.closest("[data-side-conversation]")?.dataset.sideConversation;
        if (id) {
            switchView("chatView");
            window.selectConversation?.(Number(id));
        }
    });
    $$("[data-dashboard-jump]").forEach(item => {
        item.addEventListener("click", () => {
            const target = item.dataset.dashboardJump;
            if (target === "groups") {
                switchView("friendsView");
                $("#friendGroupForm input")?.focus();
                return;
            }
            switchView(target);
        });
    });
}

async function boot() {
    bindAppShell();
    try {
        AppState.me = await ChatApi.get("/users/me");
        updateIdentity();
        await Promise.all([
            window.loadFriends?.(),
            window.loadFriendGroups?.(),
            window.loadConversations?.(),
            window.loadMoments?.(),
            window.loadProfile?.(),
            AppState.me.role === "ADMIN" ? window.loadAdminDashboard?.() : Promise.resolve()
        ]);
        updateDashboardMetrics();
        refreshIcons();
    } catch (error) {
        console.error(error);
        location.href = "index.html";
    }
}

document.addEventListener("DOMContentLoaded", boot);
