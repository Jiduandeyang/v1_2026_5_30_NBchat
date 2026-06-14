let selectedFriendGroupId = "";

function friendName(friend) {
    return friend.nickname || friend.username || `User ${friend.id}`;
}

function groupNameById(groupId) {
    return AppState.groups.find(group => group.id === groupId)?.name || "未分组";
}

function groupOptionsFor(friend) {
    return AppState.groups
        .map(group => `<option value="${group.id}" ${friend.groupId === group.id ? "selected" : ""}>${escapeHtml(group.name)}</option>`)
        .join("");
}

function renderFriendGroupFilters() {
    const target = $("#friendGroupFilters");
    if (!target) return;
    target.innerHTML = `
        <button class="small-button ${selectedFriendGroupId === "" ? "active" : ""}" type="button" data-group-filter="">全部</button>
        <button class="small-button ${selectedFriendGroupId === "ungrouped" ? "active" : ""}" type="button" data-group-filter="ungrouped">未分组</button>
        ${AppState.groups.map(group => `
            <button class="small-button ${selectedFriendGroupId === String(group.id) ? "active" : ""}" type="button" data-group-filter="${group.id}">
                ${escapeHtml(group.name)}
            </button>
        `).join("")}
    `;
}

function filteredFriends() {
    return AppState.friends.filter(friend => {
        if (!selectedFriendGroupId) return true;
        if (selectedFriendGroupId === "ungrouped") return !friend.groupId;
        return String(friend.groupId || "") === selectedFriendGroupId;
    });
}

function renderFriendList() {
    const list = $("#friendList");
    if (!list) return;
    renderFriendGroupFilters();
    const friends = filteredFriends();
    list.innerHTML = friends.map(friend => `
        <div class="list-item">
            ${avatarHtml(friendName(friend), friend.avatarUrl, friend.role)}
            <span>
                <strong>${escapeHtml(friendName(friend))}</strong>
                <span>ID ${friend.id} · ${escapeHtml(groupNameById(friend.groupId))} · @${escapeHtml(friend.username)}</span>
            </span>
            <span class="list-actions">
                <select class="compact-select" data-move-friend="${friend.id}">
                    <option value="">移动分组</option>
                    ${groupOptionsFor(friend)}
                </select>
                <button class="small-button heart-button ${friend.closeFriend ? "active" : ""}" type="button" data-toggle-close-friend="${friend.id}" data-close-friend="${friend.closeFriend ? "false" : "true"}">♥</button>
                <button class="small-button" type="button" data-start-private="${friend.id}">私聊</button>
                <button class="small-button danger-button" type="button" data-delete-friend="${friend.id}">删除</button>
            </span>
        </div>
    `).join("") || `<div class="empty-state"><i data-lucide="user-plus"></i><strong>暂无好友</strong><span>可以搜索用户并发送验证信息。</span></div>`;
    refreshIcons();
}

async function loadFriends() {
    AppState.friends = await ChatApi.get("/friends");
    renderFriendList();
    updateDashboardMetrics?.();
}

window.loadFriends = loadFriends;

function renderFriendGroups() {
    const target = $("#friendGroupList");
    if (!target) return;
    target.innerHTML = AppState.groups.map(group => {
        const count = AppState.friends.filter(friend => friend.groupId === group.id).length;
        return `
            <div class="list-item">
                ${avatarHtml(group.name, null, "GROUP")}
                <span><strong>${escapeHtml(group.name)}</strong><span>${count} 位联系人</span></span>
                <span class="list-actions">
                    <button class="small-button" type="button" data-rename-group="${group.id}">重命名</button>
                </span>
            </div>
        `;
    }).join("") || `<div class="empty-state"><i data-lucide="folder-plus"></i><strong>暂无分组</strong><span>创建分组后可以移动好友。</span></div>`;
    refreshIcons();
}

async function loadFriendGroups() {
    AppState.groups = await ChatApi.get("/friend-groups");
    renderFriendGroupFilters();
    renderFriendGroups();
    renderFriendList();
    updateDashboardMetrics?.();
}

window.loadFriendGroups = loadFriendGroups;

function renderRequests(received, sent) {
    const target = $("#requestList");
    if (!target) return;
    const receivedRows = received.map(request => `
        <div class="list-item request-card">
            ${avatarHtml(request.senderName, null)}
            <span>
                <strong>${escapeHtml(request.senderName)}</strong>
                <span>${escapeHtml(request.message || "请求添加你为好友")} · ${escapeHtml(request.status)}</span>
            </span>
            <span class="list-actions">
                ${request.status === "PENDING" ? `<button class="small-button" type="button" data-accept="${request.id}">接受</button>` : ""}
                ${request.status === "PENDING" ? `<button class="small-button danger-button" type="button" data-reject="${request.id}">拒绝</button>` : ""}
            </span>
        </div>
    `);
    const sentRows = sent.map(request => `
        <div class="list-item request-card">
            ${avatarHtml(request.receiverName, null)}
            <span>
                <strong>发给 ${escapeHtml(request.receiverName)}</strong>
                <span>${escapeHtml(request.message || "好友验证")} · ${escapeHtml(request.status)}</span>
            </span>
            <span class="list-actions">
                <button class="small-button" type="button" data-resend-request="${request.receiverId}">重发</button>
            </span>
        </div>
    `);
    target.innerHTML = [...receivedRows, ...sentRows].join("") || `<div class="empty-state"><i data-lucide="mail-check"></i><strong>暂无验证消息</strong></div>`;
    refreshIcons();
}

async function loadRequests() {
    const [received, sent] = await Promise.all([
        ChatApi.get("/friend-requests?mode=received"),
        ChatApi.get("/friend-requests?mode=sent")
    ]);
    renderRequests(received, sent);
    loadGroupInvitations().catch(() => {});
}

function renderGroupInvitations(invitations = []) {
    const target = $("#groupInviteList");
    if (!target) return;
    const pending = invitations.filter(inv => inv.status === "PENDING");
    target.innerHTML = pending.map(inv => `
        <div class="list-item request-card">
            ${avatarHtml(inv.groupName || "群聊", null, "GROUP")}
            <span>
                <strong>${escapeHtml(inv.groupName)}</strong>
                <span>${escapeHtml(inv.inviterName)} 邀请你加入群聊 · ${escapeHtml(inv.status)}</span>
            </span>
            <span class="list-actions">
                <button class="small-button" type="button" data-accept-group-invite="${inv.id}">加入</button>
                <button class="small-button danger-button" type="button" data-reject-group-invite="${inv.id}">拒绝</button>
            </span>
        </div>
    `).join("") || `<div class="empty-state"><span>暂无群聊邀请</span></div>`;
    refreshIcons();
}

async function loadGroupInvitations() {
    const invitations = await ChatApi.get("/chat/group-invitations?mode=received");
    renderGroupInvitations(invitations);
}

function renderSearchResults(users) {
    const target = $("#friendResults");
    if (!target) return;
    target.innerHTML = users.map(user => `
        <div class="list-item">
            ${avatarHtml(friendName(user), user.avatarUrl, user.role)}
            <span>
                <strong>${escapeHtml(friendName(user))}</strong>
                <span>ID ${user.id} · ${escapeHtml(user.qqEmail || "")}</span>
            </span>
            <span class="list-actions">
                <button class="small-button" type="button" data-send-request="${user.id}">加好友</button>
                <button class="small-button" type="button" data-start-private="${user.id}">私聊</button>
            </span>
        </div>
    `).join("") || `<div class="empty-state"><span>没有找到匹配用户</span></div>`;
    refreshIcons();
}

$("#friendSearchForm")?.addEventListener("submit", async event => {
    event.preventDefault();
    const q = event.currentTarget.elements.q.value.trim();
    if (!q) return toast("请输入用户名或 QQ 邮箱");
    const users = await ChatApi.get(`/users/search?q=${encodeURIComponent(q)}`);
    renderSearchResults(users);
});

$("#friendRequestForm")?.addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const receiverId = Number(form.elements.receiverId.value);
    if (!receiverId) return toast("请输入用户 ID");
    await ChatApi.post("/friend-requests", {receiverId, message: form.elements.message.value});
    form.reset();
    toast("验证信息已发送");
    await loadRequests();
});

$("#friendGroupForm")?.addEventListener("submit", async event => {
    event.preventDefault();
    const name = event.currentTarget.elements.name.value.trim();
    if (!name) return toast("请输入分组名称");
    await ChatApi.post("/friend-groups", {name});
    event.currentTarget.reset();
    toast("分组已创建");
    await loadFriendGroups();
});

document.addEventListener("click", async event => {
    const button = event.target.closest("button");
    if (!button) return;

    if (button.dataset.groupFilter !== undefined) {
        selectedFriendGroupId = button.dataset.groupFilter;
        renderFriendList();
        return;
    }

    const acceptId = button.dataset.accept;
    const rejectId = button.dataset.reject;
    const deleteId = button.dataset.deleteFriend;
    const startPrivateId = button.dataset.startPrivate;
    const sendRequestId = button.dataset.sendRequest;
    const resendRequestId = button.dataset.resendRequest;
    const renameGroupId = button.dataset.renameGroup;
    const toggleCloseFriendId = button.dataset.toggleCloseFriend;

    if (acceptId) await ChatApi.post(`/friend-requests/${acceptId}/accept`);
    if (rejectId) await ChatApi.post(`/friend-requests/${rejectId}/reject`);
    if (deleteId) await ChatApi.delete(`/friends/${deleteId}`);
    if (toggleCloseFriendId) {
        await ChatApi.put(`/friends/${toggleCloseFriendId}/close-friend`, {closeFriend: button.dataset.closeFriend === "true"});
        toast(button.dataset.closeFriend === "true" ? "已设为亲密好友" : "已取消亲密好友");
    }
    if (sendRequestId || resendRequestId) {
        const receiverId = Number(sendRequestId || resendRequestId);
        await ChatApi.post("/friend-requests", {receiverId, message: "你好，我想添加你为好友。"});
        toast("验证信息已发送");
    }
    if (renameGroupId) {
        const name = prompt("输入新的分组名称");
        if (name?.trim()) await ChatApi.put(`/friend-groups/${renameGroupId}`, {name: name.trim()});
    }
    if (startPrivateId) {
        switchView("chatView");
        const conversationId = await ChatApi.post("/chat/private", {friendId: Number(startPrivateId)});
        await loadConversations({selectFirst: false});
        await selectConversation(conversationId);
    }
    const acceptGroupInviteId = button.dataset.acceptGroupInvite;
    const rejectGroupInviteId = button.dataset.rejectGroupInvite;
    if (acceptGroupInviteId) {
        await ChatApi.post(`/chat/group-invitations/${acceptGroupInviteId}/accept`);
        toast("已加入群聊");
        await Promise.all([loadGroupInvitations(), window.loadConversations?.()]);
    }
    if (rejectGroupInviteId) {
        await ChatApi.post(`/chat/group-invitations/${rejectGroupInviteId}/reject`);
        toast("已拒绝群聊邀请");
        await loadGroupInvitations();
    }
    if (acceptId || rejectId || deleteId || renameGroupId || toggleCloseFriendId || acceptGroupInviteId || rejectGroupInviteId) {
        await Promise.all([loadFriends(), loadFriendGroups(), loadRequests()]);
    }
});

document.addEventListener("change", async event => {
    const select = event.target.closest("[data-move-friend]");
    if (!select || !select.value) return;
    await ChatApi.put(`/friends/${select.dataset.moveFriend}/group`, {groupId: Number(select.value)});
    toast("好友已移动到新分组");
    await Promise.all([loadFriends(), loadFriendGroups()]);
});

loadRequests().catch(() => {});
