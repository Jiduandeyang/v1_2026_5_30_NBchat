let adminUsersPage = 1;
let adminUserQuery = "";

function adminFormatDate(value) {
    return formatDate(value);
}

function adminPageLabel(page) {
    if (!page) return "";
    const totalPages = Math.max(1, Math.ceil((page.total || 0) / (page.size || 20)));
    return `第 ${page.page} / ${totalPages} 页`;
}

function renderAdminStats(stats = {}) {
    const target = $("#adminStats");
    if (!target) return;
    const cards = [
        ["用户", stats.totalUsers || 0],
        ["消息", stats.totalMessages || 0],
        ["群聊", stats.totalGroups || 0],
        ["动态", stats.totalMoments || 0],
        ["今日活跃", stats.activeUsersToday || 0],
        ["本周新增", stats.newUsersThisWeek || 0],
        ["今日消息", stats.messagesToday || 0],
        ["禁用账号", stats.disabledUsers || 0]
    ];
    target.innerHTML = cards.map(([label, value]) => `
        <article class="admin-stat-card">
            <strong>${escapeHtml(value)}</strong>
            <span>${escapeHtml(label)}</span>
        </article>
    `).join("");
}

function renderAdminUsers(page) {
    const target = $("#adminUsersTable");
    if (!target) return;
    target.innerHTML = `
        <table class="admin-table">
            <thead><tr><th>ID</th><th>用户</th><th>QQ 邮箱</th><th>角色</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead>
            <tbody>
                ${(page.rows || []).map(user => `
                    <tr>
                        <td>${user.id}</td>
                        <td><strong>${escapeHtml(user.nickname || user.username)}</strong><span>${escapeHtml(user.username)}</span></td>
                        <td>${escapeHtml(user.qqEmail || "")}</td>
                        <td><b class="role-pill">${escapeHtml(user.role)}</b></td>
                        <td>${user.disabled ? "<b class=\"admin-danger-text\">已禁用</b>" : "正常"}</td>
                        <td>${adminFormatDate(user.createdAt)}</td>
                        <td class="admin-actions">
                            <button class="small-button" type="button" data-admin-role="${user.id}" data-role="${user.role === "ADMIN" ? "USER" : "ADMIN"}">${user.role === "ADMIN" ? "撤销管理" : "设为管理"}</button>
                            <button class="small-button ${user.disabled ? "" : "danger-button"}" type="button" data-admin-disable="${user.id}" data-disabled="${!user.disabled}">${user.disabled ? "解禁" : "禁用"}</button>
                        </td>
                    </tr>
                `).join("")}
            </tbody>
        </table>
        <div class="admin-pagination">
            <span>${adminPageLabel(page)}</span>
            <button class="small-button" type="button" data-admin-users-page="${Math.max(1, page.page - 1)}">上一页</button>
            <button class="small-button" type="button" data-admin-users-page="${page.page + 1}" ${page.page * page.size >= page.total ? "disabled" : ""}>下一页</button>
        </div>
    `;
}

function renderAdminGroups(page) {
    const target = $("#adminGroupsTable");
    if (!target) return;
    target.innerHTML = `
        <table class="admin-table">
            <thead><tr><th>ID</th><th>群名</th><th>群主</th><th>成员</th><th>消息</th><th>创建时间</th><th>操作</th></tr></thead>
            <tbody>
                ${(page.rows || []).map(group => `
                    <tr>
                        <td>${group.conversationId}</td>
                        <td><strong>${escapeHtml(group.name)}</strong></td>
                        <td>${escapeHtml(group.ownerName)}</td>
                        <td>${group.memberCount}</td>
                        <td>${group.messageCount}</td>
                        <td>${adminFormatDate(group.createdAt)}</td>
                        <td><button class="small-button danger-button" type="button" data-admin-disband-group="${group.conversationId}">解散</button></td>
                    </tr>
                `).join("")}
            </tbody>
        </table>
        <div class="admin-pagination"><span>${adminPageLabel(page)}</span></div>
    `;
}

function renderAdminMoments(page) {
    const target = $("#adminMomentsTable");
    if (!target) return;
    target.innerHTML = `
        <table class="admin-table">
            <thead><tr><th>ID</th><th>作者</th><th>内容</th><th>可见性</th><th>媒体/赞/评</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
                ${(page.rows || []).map(moment => `
                    <tr>
                        <td>${moment.id}</td>
                        <td>${escapeHtml(moment.authorName)}</td>
                        <td class="admin-text-cell">${escapeHtml(moment.text || "")}</td>
                        <td>${escapeHtml(moment.visibility)}</td>
                        <td>${moment.mediaCount}/${moment.likeCount}/${moment.commentCount}</td>
                        <td>${moment.deleted ? "<b class=\"admin-danger-text\">已删除</b>" : "正常"}</td>
                        <td><button class="small-button danger-button" type="button" data-admin-delete-moment="${moment.id}" ${moment.deleted ? "disabled" : ""}>删除</button></td>
                    </tr>
                `).join("")}
            </tbody>
        </table>
        <div class="admin-pagination"><span>${adminPageLabel(page)}</span></div>
    `;
}

function renderAdminLogs(page) {
    const target = $("#adminLogsTable");
    if (!target) return;
    target.innerHTML = `
        <table class="admin-table">
            <thead><tr><th>时间</th><th>管理员</th><th>动作</th><th>目标</th><th>详情</th></tr></thead>
            <tbody>
                ${(page.rows || []).map(log => `
                    <tr>
                        <td>${adminFormatDate(log.createdAt)}</td>
                        <td>${escapeHtml(log.adminName)}</td>
                        <td><b class="role-pill">${escapeHtml(log.action)}</b></td>
                        <td>${escapeHtml(log.targetType || "")} ${log.targetId || ""}</td>
                        <td>${escapeHtml(log.detail || "")}</td>
                    </tr>
                `).join("")}
            </tbody>
        </table>
        <div class="admin-pagination"><span>${adminPageLabel(page)}</span></div>
    `;
}

async function loadAdminDashboard() {
    if (AppState.me?.role !== "ADMIN") return;
    const [stats, users, groups, moments, logs] = await Promise.all([
        ChatApi.get("/admin/dashboard"),
        ChatApi.get(`/admin/users?q=${encodeURIComponent(adminUserQuery)}&page=${adminUsersPage}&size=12`),
        ChatApi.get("/admin/groups?page=1&size=20"),
        ChatApi.get("/admin/moments?page=1&size=20"),
        ChatApi.get("/admin/audit-logs?page=1&size=30")
    ]);
    renderAdminStats(stats);
    renderAdminUsers(users);
    renderAdminGroups(groups);
    renderAdminMoments(moments);
    renderAdminLogs(logs);
    refreshIcons();
}

window.loadAdminDashboard = loadAdminDashboard;

function selectAdminTab(tab) {
    $$("[data-admin-tab]").forEach(button => button.classList.toggle("active", button.dataset.adminTab === tab));
    $$(".admin-section").forEach(section => section.classList.remove("active"));
    $(`#admin${tab.charAt(0).toUpperCase()}${tab.slice(1)}Section`)?.classList.add("active");
}

document.addEventListener("click", async event => {
    const tab = event.target.closest("[data-admin-tab]");
    if (tab) {
        selectAdminTab(tab.dataset.adminTab);
        return;
    }
    if (event.target.closest("#adminRefreshButton")) {
        await loadAdminDashboard();
        toast("后台数据已刷新");
        return;
    }
    const pageButton = event.target.closest("[data-admin-users-page]");
    if (pageButton) {
        adminUsersPage = Number(pageButton.dataset.adminUsersPage);
        await loadAdminDashboard();
        return;
    }
    const roleButton = event.target.closest("[data-admin-role]");
    if (roleButton) {
        if (!confirm("确认切换该用户角色？")) return;
        await ChatApi.put(`/admin/users/${roleButton.dataset.adminRole}/role`, {role: roleButton.dataset.role});
        await loadAdminDashboard();
        return;
    }
    const disableButton = event.target.closest("[data-admin-disable]");
    if (disableButton) {
        if (!confirm("确认修改该用户状态？")) return;
        await ChatApi.put(`/admin/users/${disableButton.dataset.adminDisable}/disable`, {disabled: disableButton.dataset.disabled === "true"});
        await loadAdminDashboard();
        return;
    }
    const disbandButton = event.target.closest("[data-admin-disband-group]");
    if (disbandButton) {
        if (!confirm("确认解散该群聊？相关群消息会被清理。")) return;
        await ChatApi.delete(`/admin/groups/${disbandButton.dataset.adminDisbandGroup}`);
        await loadAdminDashboard();
        await window.loadConversations?.();
        return;
    }
    const deleteMomentButton = event.target.closest("[data-admin-delete-moment]");
    if (deleteMomentButton) {
        if (!confirm("确认删除该朋友圈动态？")) return;
        await ChatApi.delete(`/admin/moments/${deleteMomentButton.dataset.adminDeleteMoment}`);
        await loadAdminDashboard();
        await window.loadMoments?.();
    }
});

$("#adminUserSearchForm")?.addEventListener("submit", async event => {
    event.preventDefault();
    adminUserQuery = event.currentTarget.elements.q.value.trim();
    adminUsersPage = 1;
    await loadAdminDashboard();
});
