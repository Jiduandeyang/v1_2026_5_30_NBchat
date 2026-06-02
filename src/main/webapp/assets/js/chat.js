function socketBasePath() {
    return location.pathname.replace(/\/[^/]*$/, "");
}

function connectChatSocket() {
    if (AppState.socket && AppState.socket.readyState !== WebSocket.CLOSED) return AppState.socket;
    const protocol = location.protocol === "https:" ? "wss" : "ws";
    AppState.socket = new WebSocket(`${protocol}://${location.host}${socketBasePath()}/ws/chat`);
    AppState.socket.addEventListener("message", async event => {
        const message = JSON.parse(event.data);
        if (message.event === "REACTION") {
            if (message.conversationId === AppState.conversationId) await loadHistory(historyQuery);
            return;
        }
        if (message.event === "RECALL") {
            if (message.conversationId === AppState.conversationId) replaceMessage(message.message);
            await loadConversations({selectFirst: false, reloadHistory: false});
            return;
        }
        if (message.event === "ERROR") {
            if (!message.conversationId || message.conversationId === AppState.conversationId) {
                renderFailedMessage(message.content || "", message.message || "消息发送失败");
                scrollMessagesToBottom();
            }
            return;
        }
        if (message.conversationId === AppState.conversationId) {
            appendMessage(message);
            scrollMessagesToBottom();
        }
        await loadConversations({selectFirst: false, reloadHistory: false});
    });
    AppState.socket.addEventListener("close", () => {
        AppState.socket = null;
    });
    return AppState.socket;
}

function waitForOpen(socket) {
    if (socket.readyState === WebSocket.OPEN) return Promise.resolve();
    return new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error("WebSocket timeout")), 2500);
        socket.addEventListener("open", () => {
            clearTimeout(timer);
            resolve();
        }, {once: true});
        socket.addEventListener("error", () => {
            clearTimeout(timer);
            reject(new Error("WebSocket error"));
        }, {once: true});
    });
}

function conversationName(conversation) {
    return conversation?.peerName || conversation?.title || (conversation?.type === "GROUP" ? "群聊" : "私聊");
}

function conversationPreview(conversation) {
    if (!conversation?.lastMessage) return "还没有聊天记录";
    if (conversation.lastMessageType === "IMAGE") return "[图片]";
    if (conversation.lastMessageType === "VOICE") return "[语音]";
    return conversation.lastMessage;
}

let fxParticles = [];
let fxAnimationFrame = null;
let replyDraft = null;
let voiceRecorder = null;
let voiceChunks = [];
let voiceStream = null;
let burnMode = false;
const EMOJI_CHOICES = ["😀", "😂", "😊", "😍", "😎", "😭", "👍", "👏", "🙏", "❤️", "🔥", "✨", "🎉", "🤝", "💡", "📌"];
const CHAT_COLUMN_KEY = "chatColumnWidths";
let mentionAutocomplete = {
    visible: false,
    query: "",
    rangeStart: -1,
    members: []
};

function clampNumber(value, min, max) {
    return Math.max(min, Math.min(max, value));
}

function applyChatColumnWidths(widths = {}) {
    const board = $(".chat-board");
    if (!board) return;
    if (widths.left) board.style.setProperty("--conversation-column", `${widths.left}px`);
    if (widths.right) board.style.setProperty("--inspector-column", `${widths.right}px`);
}

function savedChatColumnWidths() {
    try {
        return JSON.parse(localStorage.getItem(CHAT_COLUMN_KEY) || "{}");
    } catch (error) {
        return {};
    }
}

function persistChatColumnWidths(widths) {
    localStorage.setItem("chatColumnWidths", JSON.stringify(widths));
}

function initChatColumnResizers() {
    const board = $(".chat-board");
    if (!board) return;
    applyChatColumnWidths(savedChatColumnWidths());
    $$("[data-chat-resizer]").forEach(handle => {
        handle.addEventListener("pointerdown", event => {
            if (window.innerWidth <= 980) return;
            event.preventDefault();
            const side = handle.dataset.chatResizer;
            const startX = event.clientX;
            const start = savedChatColumnWidths();
            const conversationStart = $(".conversation-panel")?.getBoundingClientRect().width || 340;
            const inspectorStart = $(".inspector-panel")?.getBoundingClientRect().width || 320;
            const shellWidth = board.getBoundingClientRect().width;
            handle.classList.add("active");
            board.classList.add("is-resizing");
            handle.setPointerCapture?.(event.pointerId);

            const move = moveEvent => {
                const delta = moveEvent.clientX - startX;
                const next = {...start};
                if (side === "left") {
                    next.left = clampNumber(conversationStart + delta, 280, Math.min(460, shellWidth * .38));
                } else {
                    next.right = clampNumber(inspectorStart - delta, 260, Math.min(420, shellWidth * .34));
                }
                applyChatColumnWidths(next);
            };

            const stop = () => {
                const next = {
                    left: Math.round($(".conversation-panel")?.getBoundingClientRect().width || conversationStart),
                    right: Math.round($(".inspector-panel")?.getBoundingClientRect().width || inspectorStart)
                };
                persistChatColumnWidths(next);
                handle.classList.remove("active");
                board.classList.remove("is-resizing");
                window.removeEventListener("pointermove", move);
                window.removeEventListener("pointerup", stop);
                window.removeEventListener("pointercancel", stop);
            };

            window.addEventListener("pointermove", move);
            window.addEventListener("pointerup", stop, {once: true});
            window.addEventListener("pointercancel", stop, {once: true});
        });
    });
}

function fxCanvasContext() {
    const canvas = $("#chatFxCanvas");
    if (!canvas) return null;
    const ratio = window.devicePixelRatio || 1;
    const width = Math.floor(window.innerWidth * ratio);
    const height = Math.floor(window.innerHeight * ratio);
    if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width;
        canvas.height = height;
        canvas.style.width = `${window.innerWidth}px`;
        canvas.style.height = `${window.innerHeight}px`;
    }
    const context = canvas.getContext("2d");
    context.setTransform(ratio, 0, 0, ratio, 0, 0);
    return context;
}

function animateFxParticles() {
    const context = fxCanvasContext();
    if (!context) return;
    context.clearRect(0, 0, window.innerWidth, window.innerHeight);
    fxParticles = fxParticles.filter(particle => particle.life > 0);
    fxParticles.forEach(particle => {
        particle.x += particle.vx;
        particle.y += particle.vy;
        particle.vy += .12;
        particle.life -= 1;
        context.globalAlpha = Math.max(0, particle.life / particle.maxLife);
        context.fillStyle = particle.color;
        context.beginPath();
        context.arc(particle.x, particle.y, particle.size, 0, Math.PI * 2);
        context.fill();
    });
    context.globalAlpha = 1;
    if (fxParticles.length) {
        fxAnimationFrame = requestAnimationFrame(animateFxParticles);
    } else {
        fxAnimationFrame = null;
        context.clearRect(0, 0, window.innerWidth, window.innerHeight);
    }
}

function triggerSendParticles(source) {
    const rect = source?.getBoundingClientRect?.();
    if (!rect || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const colors = ["#536dfe", "#79e0a7", "#ffb86b", "#ff7da8", "#8be3cc"];
    const originX = rect.left + rect.width / 2;
    const originY = rect.top + rect.height / 2;
    for (let index = 0; index < 34; index += 1) {
        const angle = -Math.PI / 2 + (Math.random() - .5) * 2.4;
        const speed = 2.5 + Math.random() * 5.5;
        fxParticles.push({
            x: originX,
            y: originY,
            vx: Math.cos(angle) * speed,
            vy: Math.sin(angle) * speed,
            size: 2 + Math.random() * 3.5,
            color: colors[index % colors.length],
            life: 28 + Math.random() * 18,
            maxLife: 46
        });
    }
    if (!fxAnimationFrame) fxAnimationFrame = requestAnimationFrame(animateFxParticles);
}

function renderConversationList() {
    const target = $("#conversationList");
    if (!target) return;
    const keyword = ($("#conversationSearch")?.value || "").trim().toLowerCase();
    const rows = AppState.conversations.filter(item => {
        const haystack = `${conversationName(item)} ${conversationPreview(item)} ${item.type}`.toLowerCase();
        return !keyword || haystack.includes(keyword);
    });

    target.innerHTML = rows.map(item => `
        <button class="conversation-item ${item.id === AppState.conversationId ? "active" : ""}" type="button" data-conversation="${item.id}">
            ${avatarHtml(conversationName(item), item.peerAvatarUrl, item.type)}
            <span class="conversation-copy">
                <span class="conversation-title-row">
                    <strong>${escapeHtml(conversationName(item))}</strong>
                    <span class="tag">${item.type === "GROUP" ? "群聊" : "私聊"}</span>
                </span>
                <p>${escapeHtml(conversationPreview(item))}</p>
            </span>
            <span class="conversation-meta">
                <time>${formatTime(item.lastSentAt)}</time>
                ${item.unreadCount ? `<b class="badge">${item.unreadCount}</b>` : ""}
            </span>
        </button>
    `).join("") || `<div class="empty-state"><i data-lucide="message-circle"></i><strong>暂无会话</strong><span>可以输入好友 ID 创建私聊。</span></div>`;
    refreshIcons();
}

async function loadConversations(options = {}) {
    const {selectFirst = true, reloadHistory = true} = options;
    AppState.conversations = await ChatApi.get("/chat/conversations");
    renderConversationList();

    const current = AppState.conversations.find(item => item.id === AppState.conversationId);
    if (current) {
        AppState.selectedConversation = current;
        renderChatHeader();
        if (reloadHistory) await loadHistory();
    } else if (selectFirst && AppState.conversations.length) {
        await selectConversation(AppState.conversations[0].id);
    } else {
        AppState.conversationId = null;
        AppState.selectedConversation = null;
        renderChatHeader();
    }
    updateDashboardMetrics();
}

window.loadConversations = loadConversations;

function renderChatHeader() {
    const conversation = AppState.selectedConversation;
    $("#chatTitle") && ($("#chatTitle").textContent = conversation ? conversationName(conversation) : "选择一个会话");
    $("#chatStatus") && ($("#chatStatus").innerHTML = conversation
        ? `<em></em> ${conversation.type === "GROUP" ? "群聊在线" : "一对一私聊"}`
        : `<em></em> 等待连接`);
    paintAvatar($("#chatAvatar"), conversation ? {
        nickname: conversationName(conversation),
        avatarUrl: conversation.peerAvatarUrl
    } : {nickname: "?"}, conversation?.type);
    const aiHint = $("#aiHelperHint");
    if (aiHint) {
        aiHint.hidden = conversation?.type !== "GROUP";
    }
    const groupButton = $("#groupManageButton");
    if (groupButton) {
        groupButton.title = conversation?.type === "GROUP" ? "管理群聊" : "创建群聊";
    }
    const leaveGroupButton = $("#leaveGroupButton");
    if (leaveGroupButton) {
        leaveGroupButton.hidden = conversation?.type !== "GROUP";
    }
}

function selectedGroupFriendIds() {
    return $$("[data-group-friend]:checked").map(input => Number(input.value)).filter(Boolean);
}

function currentMemberRole(members = []) {
    const currentUserId = AppState.me?.id;
    return members.find(member => member.userId === currentUserId)?.role || "";
}

function canManageGroupMembers(role) {
    return role === "OWNER" || role === "ADMIN";
}

function canAssignGroupAdmins(role) {
    return role === "OWNER";
}

function updateGroupManageActions(members = []) {
    const isGroup = AppState.selectedConversation?.type === "GROUP";
    const role = currentMemberRole(members);
    const canManage = !isGroup || canManageGroupMembers(role);

    $("#saveGroupNameButton") && ($("#saveGroupNameButton").hidden = !isGroup || !canManage);
    $("#inviteGroupMembersButton") && ($("#inviteGroupMembersButton").hidden = !isGroup || !canManage);
    $("#createGroupFromDialogButton") && ($("#createGroupFromDialogButton").hidden = isGroup);
    $("#groupMemberSection") && ($("#groupMemberSection").hidden = !isGroup);
}

function renderGroupFriendPicker(existingMembers = []) {
    const target = $("#groupFriendPicker");
    if (!target) return;
    const existingIds = new Set(existingMembers.map(member => member.userId));
    const inviteableFriends = AppState.friends.filter(friend => !existingIds.has(friend.id));
    target.innerHTML = inviteableFriends.map(friend => `
        <label class="group-friend-row">
            <input type="checkbox" data-group-friend value="${friend.id}">
            ${avatarHtml(friend.nickname || friend.username, friend.avatarUrl, friend.role, "avatar-sm")}
            <span>
                <strong>${escapeHtml(friend.nickname || friend.username)}</strong>
                <small>ID ${friend.id}${friend.closeFriend ? " · 亲密好友" : ""}</small>
            </span>
        </label>
    `).join("") || `<div class="empty-state"><span>暂无可邀请好友</span></div>`;
}

function renderGroupMembers(members = []) {
    const target = $("#groupMemberList");
    if (!target) return;
    const role = currentMemberRole(members);
    const canManage = canManageGroupMembers(role);
    const canAssignAdmins = canAssignGroupAdmins(role);
    const owner = members.find(member => member.role === "OWNER");
    if (owner && $("#groupOwnerName")) {
        $("#groupOwnerName").textContent = owner.nickname || owner.username;
    }
    target.innerHTML = members.map(member => `
        <div class="group-member-row">
            ${avatarHtml(member.nickname || member.username, member.avatarUrl, null, "avatar-sm")}
            <span>
                <strong>${escapeHtml(member.nickname || member.username)}</strong>
                <small>ID ${member.userId}</small>
            </span>
            <b class="role-pill">${escapeHtml(member.role)}</b>
            ${member.role !== "OWNER" && canAssignAdmins ? `<button class="small-button" type="button" data-set-group-role="${member.userId}" data-role="${member.role === "ADMIN" ? "MEMBER" : "ADMIN"}">${member.role === "ADMIN" ? "取消管理" : "设为管理"}</button>` : ""}
            ${member.role !== "OWNER" && canManage ? `<button class="small-button danger-button" type="button" data-remove-group-member="${member.userId}">移除</button>` : ""}
        </div>
    `).join("") || `<div class="empty-state"><span>创建后显示成员</span></div>`;
}

function quoteHtml(message) {
    if (!message.replyToMessageId) return "";
    return `
        <button class="quote-card" type="button" data-jump-message="${message.replyToMessageId}">
            <strong>${escapeHtml(message.replySenderName || "原消息")}</strong>
            <span>${escapeHtml(message.replyPreview || "引用的消息")}</span>
        </button>
    `;
}

function renderReactionBar(message) {
    const quick = ["👍", "😂", "❤️"];
    const existing = message.reactions || [];
    const summary = existing.map(reaction => `
        <button class="reaction-chip ${reaction.mine ? "active" : ""}" type="button" data-react="${message.id}" data-emoji="${escapeHtml(reaction.emoji)}">
            <span>${escapeHtml(reaction.emoji)}</span><b>${reaction.count}</b>
        </button>
    `).join("");
    const quickRows = quick
        .filter(emoji => !existing.some(reaction => reaction.emoji === emoji))
        .map(emoji => `<button class="reaction-quick" type="button" data-react="${message.id}" data-emoji="${emoji}">${emoji}</button>`)
        .join("");
    return `<div class="reaction-bar">${summary}${quickRows}</div>`;
}

function renderBurnMessage(message) {
    return `
        <div class="burn-message" data-burn-message data-burn-message-id="${message.id}">
            <div class="burn-secret">${escapeHtml(message.content)}</div>
            <canvas class="burn-canvas" aria-label="Scratch to reveal"></canvas>
            <span class="burn-hint">按住拖动刮开，显示后自动隐藏</span>
        </div>
    `;
}

async function refreshGroupMembers() {
    if (AppState.selectedConversation?.type !== "GROUP") {
        renderGroupMembers([]);
        updateGroupManageActions([]);
        return;
    }
    const members = await ChatApi.get(`/chat/groups/${AppState.conversationId}/members`);
    renderGroupFriendPicker(members);
    renderGroupMembers(members);
    updateGroupManageActions(members);
    return members;
}

async function openGroupManageDialog() {
    const isGroup = AppState.selectedConversation?.type === "GROUP";
    renderGroupFriendPicker();
    $("#groupManageTitle") && ($("#groupManageTitle").textContent = isGroup ? "群聊管理" : "创建群聊");
    $("#groupNameInput") && ($("#groupNameInput").value = isGroup ? conversationName(AppState.selectedConversation) : "");
    $("#groupOwnerName") && ($("#groupOwnerName").textContent = AppState.me?.nickname || AppState.me?.username || "当前用户");
    updateGroupManageActions([]);
    if (isGroup) await refreshGroupMembers();
    const dialog = $("#groupManageDialog");
    if (dialog && !dialog.open) dialog.showModal();
    refreshIcons();
}

window.openGroupManageDialog = openGroupManageDialog;

async function createGroupFromDialog() {
    const name = $("#groupNameInput")?.value.trim();
    const memberIds = selectedGroupFriendIds();
    if (!name) return toast("请输入群聊名称");
    const conversationId = await ChatApi.post("/chat/groups", {name, memberIds});
    $("#groupManageDialog")?.close();
    await loadConversations({selectFirst: false});
    await selectConversation(conversationId);
    toast("群聊已创建，普通好友会收到邀请");
}

async function saveGroupName() {
    const name = $("#groupNameInput")?.value.trim();
    if (!AppState.conversationId || !name) return toast("请输入群聊名称");
    await ChatApi.put(`/chat/groups/${AppState.conversationId}/name`, {name});
    await loadConversations({selectFirst: false});
    toast("群聊名称已保存");
}

async function inviteGroupMembersFromDialog() {
    const memberIds = selectedGroupFriendIds();
    if (!AppState.conversationId || !memberIds.length) return toast("请选择要邀请的好友");
    await ChatApi.post(`/chat/groups/${AppState.conversationId}/members`, {memberIds});
    await refreshGroupMembers();
    await loadConversations({selectFirst: false, reloadHistory: false});
    toast("邀请已处理，亲密好友会直接入群");
}

function canRecallMessage(message, mine) {
    if (!mine || message.type === "RECALLED" || message.recalledAt) return false;
    const sentAt = Array.isArray(message.sentAt)
        ? new Date(message.sentAt[0], message.sentAt[1] - 1, message.sentAt[2], message.sentAt[3] || 0, message.sentAt[4] || 0, message.sentAt[5] || 0)
        : new Date(String(message.sentAt || "").replace(" ", "T"));
    return Number.isNaN(sentAt.getTime()) || Date.now() - sentAt.getTime() <= 2 * 60 * 1000;
}

function renderMessageActions(message, mine) {
    if (message.type === "SYSTEM") return "";
    return `
        <span class="message-actions">
            <button class="meta-action" type="button" data-reply-message="${message.id}" title="引用回复"><i data-lucide="reply"></i></button>
            ${canRecallMessage(message, mine) ? `<button class="meta-action recall-button" type="button" data-recall-message="${message.id}" title="2 分钟内撤回"><i data-lucide="rotate-ccw"></i></button>` : ""}
        </span>
    `;
}

function messageBody(message) {
    if (message.type === "SYSTEM") {
        return `<div class="system-message">${escapeHtml(message.content)}</div>`;
    }
    if (message.type === "RECALLED" || message.recalledAt) {
        return `<div class="message-text recalled-text">${escapeHtml(message.content || "消息已撤回")}</div>`;
    }
    if (message.type === "AI") {
        return `<div class="message-text ai-answer">${escapeHtml(message.content)}</div>`;
    }
    if (message.type === "IMAGE") {
        const url = escapeHtml(message.mediaUrl || message.content);
        return `<a href="${url}" target="_blank" rel="noreferrer"><img class="chat-image loading" src="${url}" alt="聊天图片"></a>`;
    }
    if (message.type === "VOICE") {
        const url = escapeHtml(message.mediaUrl || message.content);
        return `<div class="voice-message"><span class="play-icon"><i data-lucide="play"></i></span><audio controls preload="metadata" data-voice-audio src="${url}"></audio></div>`;
    }
    if (message.type === "BURN") {
        return renderBurnMessage(message);
    }
    return `<div class="message-text">${escapeHtml(message.content)}</div>`;
}

function appendMessage(message) {
    const target = $("#messageList");
    if (!target) return;
    target.querySelector(".empty-state")?.remove();
    if (message.type === "AI") hideAssistantThinking();
    const mine = AppState.me && message.senderId === AppState.me.id;
    const node = document.createElement("div");
    node.className = `message-row spring-entry ${mine ? "mine" : ""} ${message.type === "AI" ? "ai" : ""} ${message.type === "SYSTEM" ? "system" : ""}`;
    node.dataset.messageId = message.id;
    node.dataset.senderName = message.senderName || "";
    node.dataset.messagePreview = message.type === "VOICE" ? "语音消息" : (message.content || "").slice(0, 120);
    const senderName = message.type === "AI" ? "千问小助手" : (message.senderName || "");
    if (message.type === "SYSTEM") {
        node.innerHTML = messageBody(message);
        target.appendChild(node);
        refreshIcons();
        return;
    }
    node.innerHTML = `
        ${mine ? "" : avatarHtml(senderName, null, message.type === "AI" ? "ADMIN" : null, "avatar-sm")}
        <div class="message-bubble">
            ${mine ? "" : `<strong class="message-sender">${escapeHtml(senderName)}</strong>`}
            ${quoteHtml(message)}
            ${messageBody(message)}
            <div class="message-meta"><time>${formatTime(message.sentAt)}</time><span>${escapeHtml(message.type || "TEXT")}</span>${renderMessageActions(message, mine)}</div>
            ${message.type === "RECALLED" || message.recalledAt ? "" : renderReactionBar(message)}
        </div>
    `;
    target.appendChild(node);
    markMentionPulse(node, message);
    wireImageReveal(node);
    wireBurnCanvases(node);
    wireVoiceAudio(node);
    refreshIcons();
}

function replaceMessage(message) {
    const old = $(`[data-message-id="${message.id}"]`);
    if (!old) {
        appendMessage(message);
        return;
    }
    const target = $("#messageList");
    const next = old.nextSibling;
    old.remove();
    appendMessage(message);
    const inserted = target?.lastElementChild;
    if (inserted && next) target.insertBefore(inserted, next);
}

function renderFailedMessage(content, reason) {
    const target = $("#messageList");
    if (!target) return;
    target.querySelector(".empty-state")?.remove();
    const node = document.createElement("div");
    node.className = "message-row mine failed-message";
    node.innerHTML = `
        <div class="message-bubble">
            <div class="message-text">${escapeHtml(content || "消息未发送")}</div>
            <div class="message-meta failed-meta">
                <span class="send-failed-mark" title="${escapeHtml(reason)}">!</span>
                <span>${escapeHtml(reason)}</span>
            </div>
        </div>
    `;
    target.appendChild(node);
}

function setReplyDraft(message) {
    replyDraft = message;
    const preview = $("#replyPreview");
    if (!preview) return;
    $("#replyPreviewSender").textContent = message.senderName || "原消息";
    $("#replyPreviewText").textContent = message.preview || "";
    preview.hidden = false;
    $("#messageForm input[name='content']")?.focus();
    refreshIcons();
}

function clearReplyDraft() {
    replyDraft = null;
    const preview = $("#replyPreview");
    if (preview) preview.hidden = true;
}

async function sendReaction(messageId, emoji, remove = false) {
    try {
        const socket = connectChatSocket();
        await waitForOpen(socket);
        socket.send(JSON.stringify({event: "REACTION", messageId, emoji, remove}));
        return;
    } catch (error) {
        if (remove) {
            await ChatApi.delete(`/chat/messages/${messageId}/reactions/${encodeURIComponent(emoji)}`);
        } else {
            await ChatApi.post(`/chat/messages/${messageId}/reactions`, {emoji});
        }
        await loadHistory(historyQuery);
    }
}

async function recallMessage(messageId) {
    try {
        const socket = connectChatSocket();
        await waitForOpen(socket);
        socket.send(JSON.stringify({event: "RECALL", messageId}));
        return;
    } catch (error) {
        const message = await ChatApi.post(`/chat/messages/${messageId}/recall`);
        replaceMessage(message);
    }
}

async function markBurnMessageRead(messageId) {
    if (!messageId) return;
    await ChatApi.post(`/chat/messages/${messageId}/burn-read`).catch(() => {});
}

function markMentionPulse(node, message) {
    if (!node || !String(message?.content || "").includes("@")) return;
    node.classList.add("mention-pulse");
    setTimeout(() => node.classList.remove("mention-pulse"), 2200);
}

function wireImageReveal(scope) {
    scope?.querySelectorAll?.(".chat-image.loading").forEach(image => {
        const reveal = () => image.classList.remove("loading");
        if (image.complete) {
            requestAnimationFrame(reveal);
        } else {
            image.addEventListener("load", reveal, {once: true});
            image.addEventListener("error", reveal, {once: true});
        }
    });
}

function wireBurnCanvases(scope) {
    scope?.querySelectorAll?.(".burn-canvas").forEach(canvas => {
        if (canvas.dataset.ready) return;
        canvas.dataset.ready = "true";
        const wrapper = canvas.closest("[data-burn-message]");
        const resize = () => {
            const rect = wrapper.getBoundingClientRect();
            const ratio = window.devicePixelRatio || 1;
            canvas.width = Math.max(1, Math.floor(rect.width * ratio));
            canvas.height = Math.max(1, Math.floor(rect.height * ratio));
            canvas.style.width = `${rect.width}px`;
            canvas.style.height = `${rect.height}px`;
            const context = canvas.getContext("2d");
            context.setTransform(ratio, 0, 0, ratio, 0, 0);
            context.fillStyle = "#232a44";
            context.fillRect(0, 0, rect.width, rect.height);
            context.fillStyle = "rgba(255,255,255,.82)";
            context.font = "700 13px system-ui";
            context.fillText("刮开查看", 16, Math.max(28, rect.height / 2));
        };
        resize();
        let revealed = false;
        let scratching = false;
        const scratch = event => {
            const rect = canvas.getBoundingClientRect();
            const point = event.touches?.[0] || event;
            const context = canvas.getContext("2d");
            context.globalCompositeOperation = "destination-out";
            context.beginPath();
            context.arc(point.clientX - rect.left, point.clientY - rect.top, 18, 0, Math.PI * 2);
            context.fill();
            context.globalCompositeOperation = "source-over";
            if (!revealed && burnRevealRatio(canvas) > .18) {
                revealed = true;
                wrapper.classList.add("revealed");
                markBurnMessageRead(Number(wrapper.dataset.burnMessageId));
                setTimeout(() => wrapper.classList.add("burned"), 8500);
            }
        };
        canvas.addEventListener("pointermove", event => {
            if (scratching) scratch(event);
        });
        canvas.addEventListener("pointerdown", event => {
            scratching = true;
            scratch(event);
        });
        canvas.addEventListener("pointerup", () => scratching = false);
        canvas.addEventListener("pointerleave", () => scratching = false);
        canvas.addEventListener("pointercancel", () => scratching = false);
        canvas.addEventListener("mousedown", event => {
            scratching = true;
            scratch(event);
        });
        canvas.addEventListener("mousemove", event => {
            if (scratching) scratch(event);
        });
        window.addEventListener("mouseup", () => scratching = false);
        canvas.addEventListener("touchstart", event => {
            scratching = true;
            scratch(event);
        }, {passive: true});
        canvas.addEventListener("touchmove", event => {
            if (scratching) scratch(event);
        }, {passive: true});
        canvas.addEventListener("touchend", () => scratching = false);
    });
}

function burnRevealRatio(canvas) {
    const data = canvas.getContext("2d").getImageData(0, 0, canvas.width, canvas.height).data;
    let transparent = 0;
    for (let index = 3; index < data.length; index += 40) {
        if (data[index] < 12) transparent += 1;
    }
    return transparent / Math.max(1, data.length / 40);
}

function scrollMessagesToBottom() {
    const target = $("#messageList");
    if (target) target.scrollTop = target.scrollHeight;
}

function isAiMention(content) {
    return AppState.selectedConversation?.type === "GROUP" && content.includes("@千问小助手");
}

function showAssistantThinking() {
    const target = $("#messageList");
    if (!target || $("#assistantThinking")) return;
    const node = document.createElement("div");
    node.id = "assistantThinking";
    node.className = "message-row ai thinking";
    node.innerHTML = `
        ${avatarHtml("千问小助手", null, "ADMIN", "avatar-sm")}
        <div class="message-bubble">
            <strong class="message-sender">千问小助手</strong>
            <div class="typing-dots"><span></span><span></span><span></span></div>
            <div class="message-meta"><span>正在分析群聊内容</span></div>
        </div>
    `;
    target.appendChild(node);
    scrollMessagesToBottom();
}

function hideAssistantThinking() {
    $("#assistantThinking")?.remove();
}

let historyQuery = "";
let historyOldestId = null;
let historyCanLoadMore = false;

function historyUrl(query, beforeId) {
    const params = new URLSearchParams();
    params.set("limit", "50");
    if (query) params.set("q", query);
    if (beforeId) params.set("beforeId", beforeId);
    return `/chat/conversations/${AppState.conversationId}/history?${params.toString()}`;
}

function renderLoadMoreHistoryButton(target) {
    if (!historyCanLoadMore || !historyOldestId) return;
    const button = document.createElement("button");
    button.className = "history-load-more";
    button.type = "button";
    button.dataset.loadOlderHistory = "true";
    button.textContent = "加载更早消息";
    target.prepend(button);
}

function appendHistoryMessages(target, messages) {
    let lastDate = "";
    messages.forEach(message => {
        const currentDate = formatDate(message.sentAt);
        if (currentDate !== lastDate) {
            const pill = document.createElement("div");
            pill.className = "date-pill";
            pill.textContent = currentDate;
            target.appendChild(pill);
            lastDate = currentDate;
        }
        appendMessage(message);
    });
}

async function loadHistory(query = "") {
    if (!AppState.conversationId) return;
    historyQuery = query;
    const messages = await ChatApi.get(historyUrl(query, null));
    const target = $("#messageList");
    target.innerHTML = "";
    historyOldestId = messages[0]?.id || null;
    historyCanLoadMore = messages.length === 50;
    renderLoadMoreHistoryButton(target);
    appendHistoryMessages(target, messages);
    if (!messages.length) {
        target.innerHTML = `<div class="empty-state"><i data-lucide="message-square-plus"></i><strong>还没有消息</strong><span>发一句话开始聊天。</span></div>`;
    }
    AppState.messageCount = messages.length;
    const conversation = AppState.conversations.find(item => item.id === AppState.conversationId);
    if (conversation) {
        conversation.unreadCount = 0;
        renderConversationList();
    }
    updateDashboardMetrics();
    scrollMessagesToBottom();
}

window.loadHistory = loadHistory;

async function loadOlderHistory() {
    if (!AppState.conversationId || !historyOldestId) return;
    const target = $("#messageList");
    const previousHeight = target.scrollHeight;
    target.querySelector("[data-load-older-history]")?.remove();
    const messages = await ChatApi.get(historyUrl(historyQuery, historyOldestId));
    historyOldestId = messages[0]?.id || historyOldestId;
    historyCanLoadMore = messages.length === 50;

    const holder = document.createElement("div");
    holder.style.display = "contents";
    const originalAppend = target.appendChild.bind(target);
    target.appendChild = node => holder.appendChild(node);
    appendHistoryMessages(target, messages);
    target.appendChild = originalAppend;
    target.prepend(...Array.from(holder.childNodes));
    renderLoadMoreHistoryButton(target);
    target.scrollTop += target.scrollHeight - previousHeight;
    AppState.messageCount += messages.length;
    updateDashboardMetrics();
}

async function selectConversation(id) {
    AppState.conversationId = Number(id);
    AppState.selectedConversation = AppState.conversations.find(item => item.id === AppState.conversationId) || null;
    renderConversationList();
    renderChatHeader();
    await loadHistory();
    await loadChatHeatmap();
    connectChatSocket();
}

window.selectConversation = selectConversation;

async function loadChatHeatmap() {
    if (!AppState.conversationId) return renderChatHeatmap([]);
    const days = await ChatApi.get(`/chat/conversations/${AppState.conversationId}/heatmap`);
    renderChatHeatmap(days);
}

function renderChatHeatmap(days = []) {
    const target = $("#chatHeatmap");
    if (!target) return;
    const counts = new Map(days.map(item => [heatmapDayKey(item.day), item.count]));
    const today = new Date();
    const cells = [];
    let total = 0;
    for (let offset = 364; offset >= 0; offset -= 1) {
        const day = new Date(today);
        day.setDate(today.getDate() - offset);
        const key = day.toISOString().slice(0, 10);
        const count = counts.get(key) || 0;
        total += count;
        const level = count === 0 ? 0 : Math.min(4, Math.ceil(Math.log2(count + 1)));
        cells.push(`<span class="heatmap-cell level-${level}" title="${key} · ${count} 条"></span>`);
    }
    target.innerHTML = cells.join("");
    $("#heatmapTotal") && ($("#heatmapTotal").textContent = `${total} 条`);
}

function heatmapDayKey(value) {
    if (Array.isArray(value)) {
        const [year, month, day] = value;
        return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
    }
    return String(value || "");
}

async function sendMessage(payload) {
    if (!AppState.conversationId) {
        toast("请先选择一个会话");
        return;
    }
    try {
        const socket = connectChatSocket();
        await waitForOpen(socket);
        socket.send(JSON.stringify(payload));
    } catch (error) {
        try {
            const saved = await ChatApi.post("/chat/messages", payload);
            appendMessage(saved);
            scrollMessagesToBottom();
        } catch (sendError) {
            renderFailedMessage(payload.content, sendError.message || "消息发送失败");
            scrollMessagesToBottom();
            return;
        }
    }
    await loadConversations({selectFirst: false, reloadHistory: false});
    await loadChatHeatmap();
}

async function uploadChatImage(file) {
    const formData = new FormData();
    formData.append("kind", "CHAT_IMAGE");
    formData.append("file", file);
    return ChatApi.request("/media", {method: "POST", body: formData});
}

async function uploadVoiceMessage(blob) {
    const formData = new FormData();
    formData.append("kind", "VOICE_MESSAGE");
    formData.append("file", blob, "voice-message.webm");
    return ChatApi.request("/media", {method: "POST", body: formData});
}

function preferredVoiceMimeType() {
    const candidates = [
        "audio/webm;codecs=opus",
        "audio/ogg;codecs=opus",
        "audio/webm",
        "audio/ogg"
    ];
    return candidates.find(type => MediaRecorder.isTypeSupported(type)) || "";
}

function wireVoiceAudio(root = document) {
    root.querySelectorAll?.("audio[data-voice-audio]:not([data-voice-wired])").forEach(audio => {
        audio.dataset.voiceWired = "true";
        audio.addEventListener("loadedmetadata", () => {
            if (Number.isFinite(audio.duration) && audio.duration > 0) return;
            const restore = () => {
                audio.currentTime = 0;
                audio.removeEventListener("timeupdate", restore);
            };
            audio.addEventListener("timeupdate", restore);
            try {
                audio.currentTime = Number.MAX_SAFE_INTEGER;
            } catch (error) {
                audio.load();
            }
        });
        audio.addEventListener("error", () => toast("语音文件加载失败，请刷新后重试"));
    });
}

async function startVoiceRecording() {
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === "undefined") {
        return toast("当前浏览器不支持录音");
    }
    if (!AppState.conversationId) return toast("请先选择一个会话");
    voiceStream = await navigator.mediaDevices.getUserMedia({audio: true});
    voiceChunks = [];
    const mimeType = preferredVoiceMimeType();
    voiceRecorder = mimeType ? new MediaRecorder(voiceStream, {mimeType}) : new MediaRecorder(voiceStream);
    voiceRecorder.addEventListener("dataavailable", event => {
        if (event.data.size > 0) voiceChunks.push(event.data);
    });
    voiceRecorder.addEventListener("stop", async () => {
        const blob = new Blob(voiceChunks, {type: voiceRecorder.mimeType || "audio/webm"});
        voiceStream?.getTracks().forEach(track => track.stop());
        voiceStream = null;
        $("#messageForm")?.classList.remove("composer-recording");
        if (blob.size < 800) return toast("录音时间太短");
        const media = await uploadVoiceMessage(blob);
        await sendMessage({
            conversationId: AppState.conversationId,
            type: "VOICE",
            content: media.url,
            mediaId: media.id,
            replyToMessageId: replyDraft?.id || null
        });
        clearReplyDraft();
        toast("语音消息已发送");
    }, {once: true});
    voiceRecorder.start(250);
    $("#messageForm")?.classList.add("composer-recording");
    toast("正在录音，松开发送", 0);
}

function stopVoiceRecording() {
    if (voiceRecorder && voiceRecorder.state === "recording") {
        voiceRecorder.stop();
    }
}

function ensureComposerLayer(id, className) {
    let layer = $(`#${id}`);
    if (layer) return layer;
    layer = document.createElement("div");
    layer.id = id;
    layer.className = className;
    layer.hidden = true;
    $("#messageForm")?.appendChild(layer);
    return layer;
}

function insertAtCursor(input, text, start = input.selectionStart, end = input.selectionEnd) {
    const before = input.value.slice(0, start);
    const after = input.value.slice(end);
    input.value = `${before}${text}${after}`;
    const cursor = before.length + text.length;
    input.setSelectionRange(cursor, cursor);
    input.dispatchEvent(new Event("input", {bubbles: true}));
    input.focus();
}

function emojiPicker() {
    const panel = ensureComposerLayer("emojiPicker", "emoji-picker");
    panel.innerHTML = EMOJI_CHOICES.map(emoji => `<button type="button" data-emoji-choice="${emoji}">${emoji}</button>`).join("");
    return panel;
}

async function membersForMention() {
    if (AppState.selectedConversation?.type === "GROUP" && AppState.conversationId) {
        const members = await ChatApi.get(`/chat/groups/${AppState.conversationId}/members`);
        return members.map(member => ({
            id: member.userId,
            name: member.nickname || member.username
        }));
    }
    return [
        ...AppState.friends.map(friend => ({id: friend.id, name: friend.nickname || friend.username})),
        {id: 0, name: "千问小助手"}
    ];
}

function renderMentionSuggestions(items = []) {
    const panel = ensureComposerLayer("mentionSuggestions", "mention-suggestions");
    panel.innerHTML = items.map(item => `
        <button type="button" data-mention-name="${escapeHtml(item.name)}">
            ${avatarHtml(item.name, item.avatarUrl, item.id === 0 ? "ADMIN" : null, "avatar-sm")}
            <span><strong>${escapeHtml(item.name)}</strong><small>ID ${item.id || "AI"}</small></span>
        </button>
    `).join("");
    panel.hidden = !items.length;
    mentionAutocomplete.visible = items.length > 0;
}

async function updateMentionAutocomplete(input) {
    const cursor = input.selectionStart || 0;
    const value = input.value.slice(0, cursor);
    const match = value.match(/(^|\s)@([\u4e00-\u9fa5\w-]{0,20})$/);
    if (!match) {
        renderMentionSuggestions([]);
        return;
    }
    mentionAutocomplete.query = match[2].toLowerCase();
    mentionAutocomplete.rangeStart = cursor - match[2].length - 1;
    mentionAutocomplete.members = await membersForMention();
    const rows = mentionAutocomplete.members
        .filter(member => !mentionAutocomplete.query || member.name.toLowerCase().includes(mentionAutocomplete.query))
        .slice(0, 6);
    renderMentionSuggestions(rows);
}

function chooseMention(name) {
    const input = $("#messageForm")?.elements.content;
    if (!input) return;
    insertAtCursor(input, `@${name} `, mentionAutocomplete.rangeStart, input.selectionStart);
    renderMentionSuggestions([]);
}

$("#conversationList")?.addEventListener("click", async event => {
    const id = event.target.closest("[data-conversation]")?.dataset.conversation;
    if (id) await selectConversation(Number(id));
});

$("#conversationSearch")?.addEventListener("input", renderConversationList);

$("#groupManageButton")?.addEventListener("click", () => {
    openGroupManageDialog().catch(error => toast(error.message || "无法打开群聊管理"));
});

$("#leaveGroupButton")?.addEventListener("click", async () => {
    if (AppState.selectedConversation?.type !== "GROUP") return;
    if (!confirm("确定退出这个群聊吗？群主需要先转让群主。")) return;
    await ChatApi.post(`/chat/groups/${AppState.conversationId}/leave`);
    toast("已退出群聊");
    AppState.conversationId = null;
    AppState.selectedConversation = null;
    await loadConversations({selectFirst: true});
});

$("#createGroupFromDialogButton")?.addEventListener("click", () => {
    createGroupFromDialog().catch(error => toast(error.message || "创建群聊失败"));
});

$("#saveGroupNameButton")?.addEventListener("click", () => {
    saveGroupName().catch(error => toast(error.message || "保存群聊名称失败"));
});

$("#inviteGroupMembersButton")?.addEventListener("click", () => {
    inviteGroupMembersFromDialog().catch(error => toast(error.message || "邀请好友失败"));
});

$("#groupMemberList")?.addEventListener("click", async event => {
    const roleButton = event.target.closest("[data-set-group-role]");
    const removeButton = event.target.closest("[data-remove-group-member]");
    if (roleButton) {
        await ChatApi.put(`/chat/groups/${AppState.conversationId}/members/${roleButton.dataset.setGroupRole}/role`, {role: roleButton.dataset.role});
        await refreshGroupMembers();
        toast("成员角色已更新");
    }
    if (removeButton) {
        await ChatApi.delete(`/chat/groups/${AppState.conversationId}/members/${removeButton.dataset.removeGroupMember}`);
        await refreshGroupMembers();
        toast("成员已移除");
    }
});

$("#messageList")?.addEventListener("click", event => {
    const reaction = event.target.closest("[data-react]");
    if (reaction) {
        sendReaction(Number(reaction.dataset.react), reaction.dataset.emoji, reaction.classList.contains("active"))
            .catch(error => toast(error.message || "表情回应失败"));
        return;
    }
    const replyButton = event.target.closest("[data-reply-message]");
    if (replyButton) {
        const row = replyButton.closest("[data-message-id]");
        if (row) {
            setReplyDraft({
                id: Number(row.dataset.messageId),
                senderName: row.dataset.senderName || "原消息",
                preview: row.dataset.messagePreview || ""
            });
        }
        return;
    }
    const recallButton = event.target.closest("[data-recall-message]");
    if (recallButton) {
        recallMessage(Number(recallButton.dataset.recallMessage))
            .catch(error => toast(error.message || "消息已超过 2 分钟，无法撤回"));
        return;
    }
    const jump = event.target.closest("[data-jump-message]");
    if (jump) {
        const row = $(`[data-message-id="${jump.dataset.jumpMessage}"]`);
        row?.scrollIntoView({block: "center", behavior: "smooth"});
        row?.classList.add("mention-pulse");
        setTimeout(() => row?.classList.remove("mention-pulse"), 1800);
        return;
    }
    if (event.target.closest("[data-load-older-history]")) {
        loadOlderHistory().catch(error => toast(error.message || "加载历史消息失败"));
    }
});

$("#messageList")?.addEventListener("contextmenu", event => {
    const row = event.target.closest("[data-message-id]");
    if (!row) return;
    event.preventDefault();
    setReplyDraft({
        id: Number(row.dataset.messageId),
        senderName: row.dataset.senderName || "原消息",
        preview: row.dataset.messagePreview || ""
    });
});

$("#clearReplyButton")?.addEventListener("click", clearReplyDraft);

$("#privateForm")?.addEventListener("submit", async event => {
    event.preventDefault();
    const friendId = Number(event.currentTarget.elements.friendId.value);
    if (!friendId) return toast("请输入好友 ID");
    const conversationId = await ChatApi.post("/chat/private", {friendId});
    event.currentTarget.reset();
    await loadConversations({selectFirst: false});
    await selectConversation(conversationId);
});

$("#messageForm")?.addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const content = form.elements.content.value.trim();
    if (!content) return;
    triggerSendParticles(form.querySelector(".send-button"));
    const mentionsAssistant = isAiMention(content);
    if (mentionsAssistant) showAssistantThinking();
    await sendMessage({
        conversationId: AppState.conversationId,
        type: burnMode ? "BURN" : "TEXT",
        content,
        mediaId: null,
        replyToMessageId: replyDraft?.id || null
    });
    form.reset();
    clearReplyDraft();
    if (mentionsAssistant) {
        setTimeout(() => loadHistory().catch(() => hideAssistantThinking()), 1800);
    }
});

$("#imageButton")?.addEventListener("click", () => $("#chatImageInput")?.click());

$("#chatImageInput")?.addEventListener("change", async event => {
    const file = event.currentTarget.files?.[0];
    if (!file) return;
    if (!AppState.conversationId) {
        event.currentTarget.value = "";
        return toast("请先选择一个会话");
    }
    try {
        toast("图片上传中...", 0);
        const media = await uploadChatImage(file);
        await sendMessage({
            conversationId: AppState.conversationId,
            type: "IMAGE",
            content: media.url,
            mediaId: media.id,
            replyToMessageId: replyDraft?.id || null
        });
        clearReplyDraft();
        toast("图片已发送");
    } finally {
        event.currentTarget.value = "";
    }
});

$("#micButton")?.addEventListener("pointerdown", event => {
    event.preventDefault();
    startVoiceRecording().catch(error => toast(error.message || "无法开始录音"));
});

["pointerup", "pointerleave", "pointercancel"].forEach(type => {
    $("#micButton")?.addEventListener(type, stopVoiceRecording);
});

$("#emojiButton")?.addEventListener("click", () => {
    const panel = emojiPicker();
    panel.hidden = !panel.hidden;
});

$("#messageForm")?.elements.content?.addEventListener("input", event => {
    updateMentionAutocomplete(event.currentTarget).catch(() => renderMentionSuggestions([]));
});

$("#messageForm")?.addEventListener("click", event => {
    const emoji = event.target.closest("[data-emoji-choice]");
    if (emoji) {
        const input = $("#messageForm")?.elements.content;
        if (input) insertAtCursor(input, emoji.dataset.emojiChoice);
        $("#emojiPicker") && ($("#emojiPicker").hidden = true);
        return;
    }
    const mention = event.target.closest("[data-mention-name]");
    if (mention) chooseMention(mention.dataset.mentionName);
});

$("#burnButton")?.addEventListener("click", () => {
    burnMode = !burnMode;
    $("#burnButton")?.classList.toggle("active", burnMode);
    $("#messageForm input[name='content']")?.setAttribute("placeholder", burnMode ? "输入阅后即焚消息..." : "输入消息...");
    toast(burnMode ? "阅后即焚模式已开启" : "阅后即焚模式已关闭");
});

$("#historySearchButton")?.addEventListener("click", async () => {
    if (!AppState.conversationId) return toast("请先选择一个会话");
    const keyword = prompt("输入要搜索的聊天内容");
    if (keyword !== null) await loadHistory(keyword.trim());
});

$("#exportHistory")?.addEventListener("click", () => {
    if (!AppState.conversationId) return toast("请先选择一个会话");
    location.href = `api/chat/conversations/${AppState.conversationId}/export`;
});

$("#refreshConversations")?.addEventListener("click", () => loadConversations({selectFirst: false}));

initChatColumnResizers();
