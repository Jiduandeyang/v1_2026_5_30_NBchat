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
        if (message.event === "GROUP_INVITATION") {
            await window.loadRequests?.();
            toast(message.message || "你收到一个群聊邀请");
            switchView("friendsView");
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
    if (!conversation) return "";
    if (conversation.type === "GROUP") return conversation.remark || conversation.title || "群聊";
    return conversation.peerName || conversation.title || "私聊";
}

function conversationPreview(conversation) {
    if (!conversation?.lastMessage) return "还没有聊天记录";
    if (conversation.lastMessageType === "IMAGE") return "[图片]";
    if (conversation.lastMessageType === "VOICE" || conversation.lastMessageType === "VOICE_MESSAGE") return "[语音]";
    if (conversation.lastMessageType === "TIME_CAPSULE") return "[时光胶囊]";
    return conversation.lastMessage;
}

function mobileRealtimeUnsupported() {
    return /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent || "") || Math.min(screen.width, screen.height) <= 820;
}

let fxParticles = [];
let fxAnimationFrame = null;
let replyDraft = null;
let voiceAudioContext = null;
let voiceSource = null;
let voiceProcessor = null;
let voiceSilentGain = null;
let voicePcmChunks = [];
let voiceStream = null;
let voicePointerDown = false;
let voiceRecordingStartedAt = 0;
let voiceStopTimer = null;
let voiceMaxAmplitude = 0;
const MIN_VOICE_RECORDING_MS = 900;
const MIN_VOICE_BLOB_BYTES = 1200;
const MIN_VOICE_AMPLITUDE = 0.0015;
let burnMode = false;
let convFilter = "";
let selectMode = false;
let selectedMessageIds = new Set();
let timeCapsuleRefreshTimer = null;
const EMOJI_CHOICES = ["😀", "😂", "😊", "😍", "😎", "😭", "👍", "👏", "🙏", "❤️", "🔥", "✨", "🎉", "🤝", "💡", "📌"];
const CHAT_COLUMN_KEY = "chatColumnWidths";
const GROUP_BACKGROUNDS = ["soft-blue", "mint", "neutral", "midnight"];
let selectedGroupBackgroundKey = "soft-blue";
let selectedGroupBackgroundUrl = "";
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
        if (convFilter === "unread" && !item.unreadCount) return false;
        if (convFilter && convFilter !== "unread" && item.type !== convFilter) return false;
        const haystack = `${conversationName(item)} ${conversationPreview(item)} ${item.type}`.toLowerCase();
        return !keyword || haystack.includes(keyword);
    });

    target.innerHTML = rows.map(item => {
        const tags = [item.type === "GROUP" ? "群聊" : "私聊"];
        if (item.muted) tags.push("免打扰");
        if (item.lastMessageType === "VOICE" || item.lastMessageType === "VOICE_MESSAGE") tags.push("语音");
        if (item.lastMessageType === "IMAGE") tags.push("图片");
        if (item.lastMessageType === "TIME_CAPSULE") tags.push("胶囊");
        return `
        <button class="conversation-item ${item.id === AppState.conversationId ? "active" : ""}" type="button" data-conversation="${item.id}">
            ${avatarHtml(conversationName(item), item.peerAvatarUrl, item.type)}
            <span class="conversation-copy">
                <span class="conversation-title-row">
                    <strong>${escapeHtml(conversationName(item))}</strong>
                    ${tags.map(t => `<span class="tag">${t}</span>`).join("")}
                </span>
                <p>${escapeHtml(conversationPreview(item))}</p>
            </span>
            <span class="conversation-meta">
                <time>${formatTime(item.lastSentAt)}</time>
                ${item.unreadCount ? `<b class="badge">${item.unreadCount}</b>` : ""}
            </span>
        </button>
    `}).join("") || `<div class="empty-state"><i data-lucide="message-circle"></i><strong>暂无会话</strong><span>可以输入好友 ID 创建私聊。</span></div>`;
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
        renderGroupSettings(null);
    }
    updateDashboardMetrics();
}

window.loadConversations = loadConversations;

function renderChatHeader() {
    const conversation = AppState.selectedConversation;
    const hasConversation = Boolean(conversation);
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
    const privateCallOnly = conversation?.type !== "PRIVATE";
    $("#voiceButton") && ($("#voiceButton").hidden = privateCallOnly);
    $("#videoButton") && ($("#videoButton").hidden = privateCallOnly);
    renderPrivateProfileCard(conversation);
    const leaveGroupButton = $("#leaveGroupButton");
    if (leaveGroupButton) {
        leaveGroupButton.hidden = conversation?.type !== "GROUP";
    }
    const actions = $(".chat-actions");
    if (actions) {
        actions.hidden = !hasConversation;
    }
    const composer = $("#messageForm");
    if (composer) {
        composer.classList.toggle("composer-disabled", !hasConversation);
    }
}

function renderPrivateProfileCard(conversation) {
    const card = $("#privateProfileCard");
    if (!card) return;
    const isPrivate = conversation?.type === "PRIVATE";
    card.hidden = !isPrivate;
    if (!isPrivate) return;

    const name = conversationName(conversation);
    $("#summaryName") && ($("#summaryName").textContent = name);
    $("#summaryRole") && ($("#summaryRole").textContent = "一对一私聊");
    $("#summarySignature") && ($("#summarySignature").textContent = conversation.peerId
        ? `用户 ID：${conversation.peerId}`
        : "只在私聊中显示用户资料");
    paintAvatar($("#summaryAvatar"), {
        nickname: name,
        avatarUrl: conversation.peerAvatarUrl
    });
    const cover = $("#summaryCover");
    if (cover) {
        cover.style.backgroundImage = "";
        cover.style.backgroundSize = "";
        cover.style.backgroundPosition = "";
    }
}

function normalizeGroupBackgroundKey(backgroundKey) {
    return GROUP_BACKGROUNDS.includes(backgroundKey) ? backgroundKey : "soft-blue";
}

function cssUrl(value) {
    return String(value || "").replace(/\\/g, "\\\\").replace(/"/g, '\\"');
}

function applyChatBackground(backgroundKey, backgroundUrl = "") {
    const page = document.body;
    if (!page) return;
    page.classList.remove("chat-bg-custom");
    page.style.removeProperty("--chat-global-background");
    if (backgroundUrl) {
        page.classList.add("chat-bg-custom");
        page.style.setProperty("--chat-global-background", `url("${cssUrl(backgroundUrl)}") center/cover fixed`);
        return;
    }
    if (backgroundKey) {
        const bgMap = {"soft-blue":"linear-gradient(135deg, #e8f1ff, #f5edff 58%, #eef8f4)","mint":"linear-gradient(135deg, #dff8ef, #eef7ff 62%, #fff7e8)","neutral":"#eef2f7","midnight":"linear-gradient(135deg, #1a2238, #2a3552)"};
        const bg = bgMap[backgroundKey] || bgMap["soft-blue"];
        page.classList.add("chat-bg-custom");
        page.style.setProperty("--chat-global-background", bg);
    }
}

function renderGroupBackgroundPreview(backgroundUrl = "") {
    const preview = $("#groupBackgroundPreview");
    if (!preview) return;
    preview.classList.toggle("has-image", Boolean(backgroundUrl));
    preview.style.backgroundImage = backgroundUrl
        ? `linear-gradient(rgba(248, 250, 255, .18), rgba(248, 250, 255, .18)), url("${cssUrl(backgroundUrl)}")`
        : "";
}

function renderGroupSettings(settings) {
    const card = $("#groupSettingsCard");
    if (!card) return;
    const isGroup = AppState.selectedConversation?.type === "GROUP";
    card.hidden = !isGroup;
    if (!isGroup || !settings) {
        selectedGroupBackgroundKey = "soft-blue";
        selectedGroupBackgroundUrl = "";
        renderGroupBackgroundPreview("");
        document.body.classList.remove("chat-bg-custom");
        document.body.style.removeProperty("--chat-global-background");
        return;
    }
    selectedGroupBackgroundKey = normalizeGroupBackgroundKey(settings.backgroundKey);
    selectedGroupBackgroundUrl = settings.backgroundUrl || "";
    $("#groupRemarkInput") && ($("#groupRemarkInput").value = settings.remark || "");
    $("#groupMutedInput") && ($("#groupMutedInput").checked = Boolean(settings.muted));
    $$("[data-chat-background]").forEach(button => {
        button.classList.toggle("active", button.dataset.chatBackground === selectedGroupBackgroundKey);
    });
    renderGroupBackgroundPreview(selectedGroupBackgroundUrl);
    applyChatBackground(selectedGroupBackgroundKey, selectedGroupBackgroundUrl);
}

async function loadGroupSettings() {
    if (AppState.selectedConversation?.type !== "GROUP" || !AppState.conversationId) {
        renderGroupSettings(null);
        return;
    }
    let settings;
    try {
        settings = await ChatApi.get(`/chat/groups/${AppState.conversationId}/settings`);
    } catch (error) {
        renderGroupSettings(null);
        throw error;
    }
    AppState.selectedConversation = {
        ...AppState.selectedConversation,
        remark: settings.remark,
        muted: settings.muted,
        backgroundKey: settings.backgroundKey,
        backgroundUrl: settings.backgroundUrl
    };
    renderGroupSettings(settings);
    renderChatHeader();
    renderConversationList();
}

async function saveGroupSettings() {
    if (AppState.selectedConversation?.type !== "GROUP" || !AppState.conversationId) return;
    const settings = await ChatApi.put(`/chat/groups/${AppState.conversationId}/settings`, {
        remark: $("#groupRemarkInput")?.value || "",
        muted: Boolean($("#groupMutedInput")?.checked),
        backgroundKey: selectedGroupBackgroundKey,
        backgroundUrl: selectedGroupBackgroundUrl
    });
    AppState.conversations = AppState.conversations.map(item =>
        item.id === AppState.conversationId
            ? {...item, remark: settings.remark, muted: settings.muted, backgroundKey: settings.backgroundKey, backgroundUrl: settings.backgroundUrl}
            : item
    );
    AppState.selectedConversation = AppState.conversations.find(item => item.id === AppState.conversationId) || AppState.selectedConversation;
    renderGroupSettings(settings);
    renderChatHeader();
    renderConversationList();
    toast("群聊设置已保存");
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
    await window.loadRequests?.();
    toast("邀请已发送：亲密好友直接入群，普通好友已收到群邀请");
}

function canRecallMessage(message, mine) {
    if (!mine || message.type === "RECALLED" || message.recalledAt) return false;
    const sentAt = Array.isArray(message.sentAt)
        ? new Date(message.sentAt[0], message.sentAt[1] - 1, message.sentAt[2], message.sentAt[3] || 0, message.sentAt[4] || 0, message.sentAt[5] || 0)
        : new Date(String(message.sentAt || "").replace(" ", "T"));
    return Number.isNaN(sentAt.getTime()) || Date.now() - sentAt.getTime() <= 2 * 60 * 1000;
}

function dateFromApiValue(value) {
    if (!value) return null;
    if (Array.isArray(value)) {
        return new Date(value[0], value[1] - 1, value[2], value[3] || 0, value[4] || 0, value[5] || 0);
    }
    const date = new Date(String(value).replace(" ", "T"));
    return Number.isNaN(date.getTime()) ? null : date;
}

function renderTimeCapsuleMessage(message) {
    const unlockAt = dateFromApiValue(message.unlockAt);
    const locked = unlockAt && unlockAt.getTime() > Date.now();
    if (!locked) {
        return `<div class="time-capsule-message opened"><b>时光胶囊已开启</b><p>${escapeHtml(message.content)}</p></div>`;
    }
    const seconds = Math.max(1, Math.ceil((unlockAt.getTime() - Date.now()) / 1000));
    scheduleTimeCapsuleRefresh(seconds);
    return `
        <div class="time-capsule-message locked" data-capsule-unlock="${unlockAt.toISOString()}">
            <b><i data-lucide="lock-keyhole"></i> 时光胶囊</b>
            <p>这条消息将在 ${unlockAt.toLocaleString()} 打开</p>
            <span data-capsule-countdown="${unlockAt.toISOString()}">${formatCountdown(seconds)}</span>
        </div>
    `;
}

function formatCountdown(totalSeconds) {
    const days = Math.floor(totalSeconds / 86400);
    const hours = Math.floor((totalSeconds % 86400) / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    if (days > 0) return `${days} 天 ${hours} 小时后开启`;
    if (hours > 0) return `${hours} 小时 ${minutes} 分钟后开启`;
    return `${Math.max(1, minutes)} 分钟内开启`;
}

function scheduleTimeCapsuleRefresh(seconds) {
    const delay = Math.min(Math.max(seconds * 1000 + 600, 1000), 2147483000);
    if (timeCapsuleRefreshTimer) return;
    timeCapsuleRefreshTimer = setTimeout(() => {
        timeCapsuleRefreshTimer = null;
        if (AppState.conversationId) loadHistory(historyQuery).catch(() => {});
    }, delay);
}

function renderMessageActions(message, mine) {
    if (message.type === "SYSTEM") return "";
    const isGroup = AppState.selectedConversation?.type === "GROUP";
    const readByHtml = isGroup ? `<span class="read-by-text" data-read-by="${message.id}"></span>` : "";
    return `
        <span class="message-actions">
            ${readByHtml}
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
    if (message.type === "VOICE" || message.type === "VOICE_MESSAGE") {
        const url = escapeHtml(message.mediaUrl || message.content);
        return `<div class="voice-message"><span class="play-icon"><i data-lucide="play"></i></span><audio controls preload="metadata" data-voice-audio src="${url}"></audio></div>`;
    }
    if (message.type === "POLL") {
        const poll = renderPoll(message);
        if (poll) return poll;
    }
    if (message.type === "BURN") {
        return renderBurnMessage(message);
    }
    if (message.type === "TIME_CAPSULE") {
        return renderTimeCapsuleMessage(message);
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
    node.className = `message-row spring-entry ${mine ? "mine" : ""} ${message.type === "AI" ? "ai" : ""} ${message.type === "SYSTEM" ? "system" : ""} ${selectMode ? "selecting" : ""}`;
    node.dataset.messageId = message.id;
    node.dataset.senderName = message.senderName || "";
    node.dataset.messagePreview = (message.type === "VOICE" || message.type === "VOICE_MESSAGE") ? "语音消息" : (message.content || "").slice(0, 120);
    const senderName = message.type === "AI" ? "千问小助手" : (message.senderName || "");
    if (message.type === "SYSTEM") {
        node.innerHTML = messageBody(message);
        target.appendChild(node);
        refreshIcons();
        return;
    }
    node.innerHTML = `
        ${renderMessageCheckbox(message.id)}
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
    loadReadByStatus();
    scrollMessagesToBottom();
}

window.loadHistory = loadHistory;

async function loadReadByStatus() {
    if (AppState.selectedConversation?.type !== "GROUP") return;
    const items = $$("[data-read-by]");
    for (const el of items) {
        const messageId = el.dataset.readBy;
        ChatApi.get(`/chat/messages/${messageId}/read-by`).then(names => {
            if (names && names.length) el.textContent = `已读 ${names.slice(0, 3).join(", ")}${names.length > 3 ? " 等" : ""}`;
        }).catch(() => {});
    }
}

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
    const oldId = AppState.conversationId;
    if (oldId) saveComposerDraft(oldId);
    AppState.conversationId = Number(id);
    AppState.selectedConversation = AppState.conversations.find(item => item.id === AppState.conversationId) || null;
    renderConversationList();
    renderChatHeader();
    const saved = localStorage.getItem(`draft_${id}`) || "";
    $("#messageForm")?.elements.content && ($("#messageForm").elements.content.value = saved);
    await loadHistory();
    await loadGroupSettings();
    await loadGroupAnnouncement();
    await loadMoodWeather();
    connectChatSocket();
}

window.selectConversation = selectConversation;

async function loadMoodWeather() {
    if (AppState.selectedConversation?.type !== "GROUP" || !AppState.conversationId) {
        renderMoodWeather(null);
        return;
    }
    const weather = await ChatApi.get(`/chat/conversations/${AppState.conversationId}/mood-weather`);
    renderMoodWeather(weather);
}

function renderMoodWeather(weather) {
    const card = $("#moodWeatherCard");
    if (!card) return;
    if (!weather) {
        card.hidden = true;
        return;
    }
    const icons = {sunny: "☀", breeze: "🌤", cloudy: "☁", fog: "🌫", storm: "⛈", quiet: "🌙"};
    card.hidden = false;
    card.dataset.mood = weather.code || "cloudy";
    $("#moodWeatherIcon") && ($("#moodWeatherIcon").textContent = icons[weather.code] || "☁");
    $("#moodWeatherTitle") && ($("#moodWeatherTitle").textContent = weather.title || "群聊天气");
    $("#moodWeatherSummary") && ($("#moodWeatherSummary").textContent = weather.summary || "");
    $("#moodWeatherSuggestion") && ($("#moodWeatherSuggestion").textContent = weather.suggestion || "");
    $("#moodWeatherEnergy") && ($("#moodWeatherEnergy").textContent = `${weather.energy || 0}%`);
    $("#moodWeatherEnergyBar") && ($("#moodWeatherEnergyBar").style.width = `${Math.max(0, Math.min(100, weather.energy || 0))}%`);
    const signals = $("#moodWeatherSignals");
    if (signals) {
        signals.innerHTML = (weather.signals || []).map(signal => `<span>${escapeHtml(signal)}</span>`).join("");
    }
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
    await loadMoodWeather().catch(() => {});
}

async function uploadChatImage(file) {
    const formData = new FormData();
    formData.append("kind", "CHAT_IMAGE");
    formData.append("file", file);
    return ChatApi.request("/media", {method: "POST", body: formData});
}

async function uploadGroupBackground(file) {
    const formData = new FormData();
    formData.append("kind", "BACKGROUND");
    formData.append("file", file);
    return ChatApi.request("/media", {method: "POST", body: formData});
}

async function uploadVoiceMessage(blob) {
    const formData = new FormData();
    formData.append("kind", "VOICE_MESSAGE");
    formData.append("file", blob, voiceMessageFileName(blob.type));
    return ChatApi.request("/media", {method: "POST", body: formData});
}

function voiceMessageFileName(type = "") {
    const normalized = type.split(";", 1)[0].toLowerCase();
    if (normalized === "audio/wav" || normalized === "audio/x-wav") return "voice-message.wav";
    return "voice-message.wav";
}

function encodeWav(samples, sampleRate) {
    const buffer = new ArrayBuffer(44 + samples.length * 2);
    const view = new DataView(buffer);
    const writeString = (offset, value) => {
        for (let i = 0; i < value.length; i++) view.setUint8(offset + i, value.charCodeAt(i));
    };
    writeString(0, "RIFF");
    view.setUint32(4, 36 + samples.length * 2, true);
    writeString(8, "WAVE");
    writeString(12, "fmt ");
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true);
    view.setUint16(22, 1, true);
    view.setUint32(24, sampleRate, true);
    view.setUint32(28, sampleRate * 2, true);
    view.setUint16(32, 2, true);
    view.setUint16(34, 16, true);
    writeString(36, "data");
    view.setUint32(40, samples.length * 2, true);
    let offset = 44;
    for (const sample of samples) {
        const value = Math.max(-1, Math.min(1, sample));
        view.setInt16(offset, value < 0 ? value * 0x8000 : value * 0x7FFF, true);
        offset += 2;
    }
    return new Blob([buffer], {type: "audio/wav"});
}

async function createVoiceProcessor(stream) {
    const AudioContextCtor = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextCtor) {
        throw new Error("当前浏览器不支持录音编码");
    }
    voiceAudioContext = new AudioContextCtor();
    voiceSource = voiceAudioContext.createMediaStreamSource(stream);
    voiceProcessor = voiceAudioContext.createScriptProcessor(4096, 1, 1);
    voiceSilentGain = voiceAudioContext.createGain();
    voiceSilentGain.gain.value = 0;
    voicePcmChunks = [];
    voiceMaxAmplitude = 0;

    const sampleRate = voiceAudioContext.sampleRate;
    voiceProcessor.onaudioprocess = event => {
        const input = event.inputBuffer.getChannelData(0);
        const chunk = new Float32Array(input.length);
        let peak = 0;
        for (let i = 0; i < input.length; i++) {
            const value = input[i];
            chunk[i] = value;
            peak = Math.max(peak, Math.abs(value));
        }
        voiceMaxAmplitude = Math.max(voiceMaxAmplitude, peak);
        voicePcmChunks.push({chunk, sampleRate});
    };

    voiceSource.connect(voiceProcessor);
    voiceProcessor.connect(voiceSilentGain);
    voiceSilentGain.connect(voiceAudioContext.destination);
    if (voiceAudioContext.state === "suspended") {
        await voiceAudioContext.resume();
    }
}

async function stopVoiceProcessor() {
    if (voiceStopTimer) {
        clearTimeout(voiceStopTimer);
        voiceStopTimer = null;
    }
    if (voiceProcessor) {
        voiceProcessor.disconnect();
        voiceProcessor.onaudioprocess = null;
    }
    if (voiceSource) voiceSource.disconnect();
    if (voiceSilentGain) voiceSilentGain.disconnect();
    if (voiceAudioContext && voiceAudioContext.state !== "closed") {
        try { await voiceAudioContext.close(); } catch {}
    }
    voiceProcessor = null;
    voiceSource = null;
    voiceSilentGain = null;
    voiceAudioContext = null;
}

function cacheBustedVoiceUrl(url) {
    if (!url) return url;
    const separator = url.includes("?") ? "&" : "?";
    return `${url}${separator}voiceRetry=${Date.now()}`;
}

function retryVoiceAudioLoad(audio) {
    if (audio.dataset.voiceRetry === "1") {
        toast("语音文件加载失败，请稍后重试");
        return;
    }
    const originalSrc = audio.dataset.originalSrc || audio.currentSrc || audio.src;
    audio.dataset.originalSrc = originalSrc;
    audio.dataset.voiceRetry = "1";
    audio.src = cacheBustedVoiceUrl(originalSrc);
    audio.load();
}

function wireVoiceAudio(root = document) {
    root.querySelectorAll?.("audio[data-voice-audio]:not([data-voice-wired])").forEach(audio => {
        audio.dataset.voiceWired = "true";
        audio.setAttribute("data-original-src", audio.getAttribute("src") || "");
        audio.setAttribute("data-voice-retry", "0");
        audio.addEventListener("canplay", () => {
            audio.dataset.voiceRetry = "0";
        });
        audio.addEventListener("error", () => retryVoiceAudioLoad(audio));
    });
}
async function startVoiceRecording() {
    if (!navigator.mediaDevices?.getUserMedia) {
        return toast("当前浏览器不支持录音");
    }
    if (!AppState.conversationId) return toast("请先选择一个会话");
    if (voiceAudioContext) return;
    voiceStream = await navigator.mediaDevices.getUserMedia({
        audio: {echoCancellation: true, noiseSuppression: true, autoGainControl: true}
    });
    if (!voicePointerDown) {
        voiceStream.getTracks().forEach(track => track.stop());
        voiceStream = null;
        return;
    }
    const stream = voiceStream;
    await createVoiceProcessor(stream);
    voiceRecordingStartedAt = Date.now();
    if (!voicePointerDown) {
        await stopVoiceRecording();
        return;
    }
    $("#messageForm")?.classList.add("composer-recording");
    toast("正在录音，松开发送", 0);
}

async function stopVoiceRecording() {
    if (!voiceAudioContext && !voiceStream) return;
    const elapsed = Date.now() - voiceRecordingStartedAt;
    if (elapsed < MIN_VOICE_RECORDING_MS) {
        if (!voiceStopTimer) {
            voiceStopTimer = setTimeout(() => {
                voiceStopTimer = null;
                stopVoiceRecording().catch(error => toast(error.message || "语音消息发送失败"));
            }, MIN_VOICE_RECORDING_MS - elapsed);
        }
        return;
    }
    const stream = voiceStream;
    const chunks = voicePcmChunks;
    const sampleRate = chunks[0]?.sampleRate || voiceAudioContext?.sampleRate || 48000;
    stream?.getTracks().forEach(track => track.stop());
    voiceStream = null;
    $("#messageForm")?.classList.remove("composer-recording");
    await stopVoiceProcessor();
    const length = chunks.reduce((sum, item) => sum + item.chunk.length, 0);
    if (length <= 0) return toast("录音内容为空，请重新录制");
    if (voiceMaxAmplitude < MIN_VOICE_AMPLITUDE) return toast("没有检测到声音，请靠近麦克风重录");
    const samples = new Float32Array(length);
    let offset = 0;
    for (const item of chunks) {
        samples.set(item.chunk, offset);
        offset += item.chunk.length;
    }
    const blob = encodeWav(samples, sampleRate);
    voicePcmChunks = [];
    if (blob.size < MIN_VOICE_BLOB_BYTES) return toast("录音太短，请长按重录");
    try {
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
    } catch (error) {
        toast(error.message || "语音消息发送失败");
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

$("#conversationFilters")?.addEventListener("click", event => {
    const button = event.target.closest("[data-conv-filter]");
    if (!button) return;
    convFilter = button.dataset.convFilter || "";
    $$("[data-conv-filter]").forEach(b => b.classList.toggle("active", b === button));
    renderConversationList();
});

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

$("#saveGroupSettingsButton")?.addEventListener("click", () => {
    saveGroupSettings().catch(error => toast(error.message || "群聊设置保存失败"));
});

$(".chat-background-picker")?.addEventListener("click", event => {
    const button = event.target.closest("[data-chat-background]");
    if (!button) return;
    selectedGroupBackgroundKey = normalizeGroupBackgroundKey(button.dataset.chatBackground);
    selectedGroupBackgroundUrl = "";
    renderGroupBackgroundPreview("");
    $$("[data-chat-background]").forEach(item => {
        item.classList.toggle("active", item === button);
    });
    applyChatBackground(selectedGroupBackgroundKey);
});

$("#uploadGroupBackgroundButton")?.addEventListener("click", () => $("#groupBackgroundUploadInput")?.click());

$("#groupBackgroundUploadInput")?.addEventListener("change", async event => {
    const file = event.currentTarget.files?.[0];
    if (!file) return;
    try {
        toast("背景图上传中...", 0);
        const media = await uploadGroupBackground(file);
        selectedGroupBackgroundUrl = media.url;
        renderGroupBackgroundPreview(selectedGroupBackgroundUrl);
        applyChatBackground(selectedGroupBackgroundKey, selectedGroupBackgroundUrl);
        await saveGroupSettings();
        toast("聊天背景已更新");
    } catch (error) {
        toast(error.message || "背景图上传失败");
    } finally {
        event.currentTarget.value = "";
    }
});

$("#clearGroupBackgroundButton")?.addEventListener("click", async () => {
    selectedGroupBackgroundUrl = "";
    renderGroupBackgroundPreview("");
    applyChatBackground(selectedGroupBackgroundKey);
    try {
        await saveGroupSettings();
        toast("自定义背景已清除");
    } catch (error) {
        toast(error.message || "清除背景失败");
    }
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
    const msgSelect = event.target.closest("[data-msg-select]");
    if (msgSelect) {
        const messageId = Number(msgSelect.dataset.msgSelect);
        msgSelect.checked = !msgSelect.checked;
        toggleMessageSelect(messageId, msgSelect);
        return;
    }
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
    if (!AppState.conversationId) return toast("请先选择一个会话");
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
    const input = event.currentTarget;
    const file = input.files?.[0];
    if (!file) return;
    if (!AppState.conversationId) {
        input.value = "";
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
    } catch (error) {
        toast(error.message || "图片发送失败");
    } finally {
        input.value = "";
    }
});

$("#groupBackgroundUploadInput")?.addEventListener("change", async event => {
    const input = event.currentTarget;
    const file = input.files?.[0];
    if (!file) return;
    try {
        toast("背景图上传中...", 0);
        const media = await uploadGroupBackground(file);
        selectedGroupBackgroundUrl = media.url;
        renderGroupBackgroundPreview(selectedGroupBackgroundUrl);
        applyChatBackground(selectedGroupBackgroundKey, selectedGroupBackgroundUrl);
        await saveGroupSettings();
        toast("聊天背景已更新");
    } catch (error) {
        toast(error.message || "背景图上传失败");
    } finally {
        input.value = "";
    }
});

$("#micButton")?.addEventListener("pointerdown", event => {
    event.preventDefault();
    if (voiceAudioContext) return;
    event.currentTarget.setPointerCapture?.(event.pointerId);
    voicePointerDown = true;
    startVoiceRecording().catch(error => toast(error.message || "无法开始录音"));
});

["pointerup", "pointercancel"].forEach(type => {
    $("#micButton")?.addEventListener(type, event => {
        voicePointerDown = false;
        event.currentTarget.releasePointerCapture?.(event.pointerId);
        stopVoiceRecording().catch(error => toast(error.message || "语音消息发送失败"));
    });
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

    async function loadGroupAnnouncement() {
        if (AppState.selectedConversation?.type !== "GROUP" || !AppState.conversationId) {
            $("#groupAnnouncementCard") && ($("#groupAnnouncementCard").hidden = true);
            return;
        }
        ChatApi.get(`/chat/groups/${AppState.conversationId}/announcement`).then(anno => {
            const card = $("#groupAnnouncementCard");
            const textEl = $("#groupAnnouncementText");
            if (!card || !textEl) return;
            card.hidden = false;
            textEl.textContent = anno || "暂无群公告";
            textEl.style.fontStyle = anno ? "normal" : "italic";
        }).catch(() => {});
        ChatApi.get(`/chat/groups/${AppState.conversationId}/members`).then(members => {
            const myRole = members.find(m => m.userId === AppState.me?.id)?.role || "";
            $("#editAnnouncementButton") && ($("#editAnnouncementButton").hidden = myRole !== "OWNER" && myRole !== "ADMIN");
        }).catch(() => {});
    }

    $("#editAnnouncementButton")?.addEventListener("click", () => { $("#groupAnnouncementText").hidden = true; $("#groupAnnouncementEditor").hidden = false; $("#editAnnouncementButton").hidden = true; });
    $("#saveAnnouncementButton")?.addEventListener("click", async () => {
        const content = $("#groupAnnouncementInput")?.value || "";
        await ChatApi.put(`/chat/groups/${AppState.conversationId}/announcement`, content);
        $("#groupAnnouncementText").textContent = content || "暂无群公告";
        $("#groupAnnouncementText").style.fontStyle = content ? "normal" : "italic";
        $("#groupAnnouncementText").hidden = false; $("#groupAnnouncementEditor").hidden = true; $("#editAnnouncementButton").hidden = false;
        toast("群公告已更新");
    });
    $("#cancelAnnouncementButton")?.addEventListener("click", () => { $("#groupAnnouncementText").hidden = false; $("#groupAnnouncementEditor").hidden = true; $("#editAnnouncementButton").hidden = false; });

    function renderPoll(message) {
        const match = (message.content || "").match(/^!poll\s+(.+?)\n([\s\S]*)$/);
        if (!match) return "";
        const title = match[1]; const options = match[2].split("\n").filter(o => o.trim());
        if (options.length < 2) return "";
        return `<div class="poll-card"><b class="poll-title">投票：${escapeHtml(title)}</b>${options.map(o => `<div class="poll-option"><span>${escapeHtml(o.trim())}</span><b></b></div>`).join("")}</div>`;
    }

    function saveComposerDraft(conversationId) {
        const draft = $("#messageForm")?.elements.content?.value || "";
        if (draft) localStorage.setItem(`draft_${conversationId}`, draft);
        else localStorage.removeItem(`draft_${conversationId}`);
    }

$("#burnButton")?.addEventListener("click", () => {
    burnMode = !burnMode;
    $("#burnButton")?.classList.toggle("active", burnMode);
    $("#messageForm input[name='content']")?.setAttribute("placeholder", burnMode ? "输入阅后即焚消息..." : "输入消息...");
    toast(burnMode ? "阅后即焚模式已开启" : "阅后即焚模式已关闭");
});

$("#timeCapsuleButton")?.addEventListener("click", () => {
    if (!AppState.conversationId) return toast("请先选择一个会话");
    const input = $("#timeCapsuleUnlockInput");
    if (input && !input.value) {
        const next = new Date(Date.now() + 10 * 60 * 1000);
        next.setSeconds(0, 0);
        input.value = `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, "0")}-${String(next.getDate()).padStart(2, "0")}T${String(next.getHours()).padStart(2, "0")}:${String(next.getMinutes()).padStart(2, "0")}`;
    }
    $("#timeCapsuleDialog")?.showModal();
});

$("#sendTimeCapsuleButton")?.addEventListener("click", async () => {
    const contentInput = $("#messageForm")?.elements.content;
    const content = contentInput?.value.trim() || "";
    const unlockAt = $("#timeCapsuleUnlockInput")?.value || "";
    if (!content) return toast("请先在输入框写下胶囊内容");
    if (!unlockAt) return toast("请选择胶囊打开时间");
    await sendMessage({
        conversationId: AppState.conversationId,
        type: "TIME_CAPSULE",
        content,
        mediaId: null,
        replyToMessageId: replyDraft?.id || null,
        unlockAt
    });
    contentInput.value = "";
    clearReplyDraft();
    $("#timeCapsuleDialog")?.close();
    toast("时光胶囊已埋下");
});

$("#refreshMoodWeatherButton")?.addEventListener("click", () => {
    loadMoodWeather().then(() => toast("群聊天气已刷新")).catch(error => toast(error.message || "群聊天气刷新失败"));
});

$("#historySearchButton")?.addEventListener("click", async () => {
    if (!AppState.conversationId) return toast("请先选择一个会话");
    const keyword = prompt("输入要搜索的聊天内容");
    if (keyword !== null) await loadHistory(keyword.trim());
});

    function updateSelectionBar() {
        const bar = $("#selectionBar");
        if (!bar) return;
        bar.hidden = selectedMessageIds.size === 0;
        $("#selectionCount") && ($("#selectionCount").textContent = `已选择 ${selectedMessageIds.size} 条消息`);
    }

    function toggleSelectMode() {
        selectMode = !selectMode;
        selectedMessageIds.clear();
        updateSelectionBar();
        $$(".message-row").forEach(row => row.classList.toggle("selecting", selectMode));
        $$(".message-select").forEach(cb => { cb.checked = false; cb.classList.remove("checked"); });
        $("#selectModeToggle")?.classList.toggle("active", selectMode);
    }

    function toggleMessageSelect(messageId, checkbox) {
        if (checkbox.checked) selectedMessageIds.add(messageId);
        else selectedMessageIds.delete(messageId);
        checkbox.classList.toggle("checked", checkbox.checked);
        updateSelectionBar();
    }

    async function deleteSelectedMessages() {
        if (!selectedMessageIds.size) return;
        const ids = Array.from(selectedMessageIds);
        await ChatApi.post("/chat/messages/hide", ids);
        ids.forEach(id => { const el = $(`[data-message-id="${id}"]`); if (el) el.remove(); });
        selectedMessageIds.clear();
        updateSelectionBar();
        toast(`已删除 ${ids.length} 条消息`);
    }

    function renderMessageCheckbox(messageId) {
        return `<button class="message-select" type="button" data-msg-select="${messageId}" aria-label="选择消息"></button>`;
    }

$("#selectModeToggle")?.addEventListener("click", toggleSelectMode);

$("#cancelSelection")?.addEventListener("click", toggleSelectMode);

$("#deleteSelectedMessages")?.addEventListener("click", () => {
    if (!confirm(`确认删除选中的 ${selectedMessageIds.size} 条消息？（仅你这边看不到）`)) return;
    deleteSelectedMessages().catch(error => toast(error.message || "删除失败"));
});

$("#clearHistory")?.addEventListener("click", async () => {
    if (!AppState.conversationId) return toast("请先选择一个会话");
    if (!confirm("确定清空此会话的所有聊天记录吗？此操作仅对你生效，对方不受影响。")) return;
    await ChatApi.post(`/chat/conversations/${AppState.conversationId}/clear`);
    await loadHistory();
    await loadConversations({selectFirst: false, reloadHistory: false});
    toast("聊天记录已清空");
});

$("#exportHistory")?.addEventListener("click", () => {
    if (!AppState.conversationId) return toast("请先选择一个会话");
    location.href = `api/chat/conversations/${AppState.conversationId}/export`;
});

$("#refreshConversations")?.addEventListener("click", () => loadConversations({selectFirst: false}));

$("#wordcloudButton")?.addEventListener("click", async () => {
    if (!AppState.conversationId) return toast("请先选择一个会话");
    toast("生成词云中...", 0);
    const texts = await ChatApi.get(`/chat/conversations/${AppState.conversationId}/wordcloud`);
    renderWordCloud(texts);
    toast("");
});

function renderWordCloud(texts) {
    const stopWords = new Set(["的", "了", "在", "是", "我", "你", "他", "她", "它", "我们", "你们", "他们", "这个", "那个", "可以", "什么", "怎么", "为什么", "因为", "所以", "但是", "然后", "已经", "正在", "没有", "觉得", "知道", "应该", "可能", "真的", "非常", "比较"]);
    const freq = {};
    texts.forEach(text => {
        const cleaned = String(text).replace(/[^\u4e00-\u9fa5a-zA-Z0-9]/g, "");
        for (let i = 0; i < cleaned.length - 1; i++) {
            const bigram = cleaned.slice(i, i + 2);
            if (!stopWords.has(bigram) && bigram.length === 2) freq[bigram] = (freq[bigram] || 0) + 1;
        }
    });
    const words = Object.entries(freq).sort((a, b) => b[1] - a[1]).slice(0, 80);
    if (!words.length) return toast("暂无足够数据生成词云");
    const canvas = document.createElement("canvas");
    canvas.width = 600; canvas.height = 400;
    canvas.style.cssText = "max-width:100%;border-radius:8px;display:block;margin:0 auto";
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "#f8fbff"; ctx.fillRect(0, 0, 600, 400);
    const maxFreq = words[0][1];
    const colors = ["#536dfe","#ff6b7b","#58d79a","#ffb86b","#b885ff","#8fd8ff","#ff7da8","#79e0a7"];
    words.forEach(([word, count]) => {
        const size = 14 + (count / maxFreq) * 36;
        const x = 30 + Math.random() * 500;
        const y = 30 + Math.random() * 340;
        ctx.font = `900 ${size}px system-ui`;
        ctx.fillStyle = colors[Math.abs(word.charCodeAt(0)) % colors.length];
        ctx.globalAlpha = .7 + (count / maxFreq) * .3;
        ctx.save(); ctx.translate(x, y); ctx.rotate((Math.random() - .5) * .6); ctx.fillText(word, 0, 0); ctx.restore();
    });
    ctx.globalAlpha = 1;
    const dialog = document.createElement("dialog");
    dialog.className = "voice-dialog";
    dialog.innerHTML = `<form method="dialog"><h2>聊天词云</h2><p>${conversationName(AppState.selectedConversation)}</p></form>`;
    dialog.querySelector("form").appendChild(canvas);
    document.body.appendChild(dialog);
    dialog.showModal();
    dialog.addEventListener("close", () => dialog.remove());
}

initChatColumnResizers();

