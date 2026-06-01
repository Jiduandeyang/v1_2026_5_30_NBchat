let uploadedMomentMedia = [];
let momentOldestId = null;
let momentCanLoadMore = false;

function setMomentBusy(isBusy) {
    const form = $("#momentForm");
    if (!form) return;
    form.classList.toggle("is-busy", isBusy);
    $$("button,input,textarea,select").forEach(element => {
        if (form.contains(element)) element.disabled = isBusy;
    });
}

function renderMomentMedia(media = []) {
    if (!media.length) return "";
    return `<div class="moment-media">${media.map(file => `
        <a href="${escapeHtml(file.url)}" target="_blank" rel="noreferrer">
            <img src="${escapeHtml(file.url)}" alt="${escapeHtml(file.originalName || "朋友圈图片")}">
        </a>
    `).join("")}</div>`;
}

function renderMomentComments(momentId, comments = []) {
    const target = $(`[data-moment-comments="${momentId}"]`);
    if (!target) return;
    target.innerHTML = comments.map(comment => `
        <div class="moment-comment">
            <span><strong>${escapeHtml(comment.userName)}</strong>${escapeHtml(comment.content)}</span>
            ${comment.userId === AppState.me?.id ? `<button type="button" data-delete-comment="${comment.id}" data-comment-moment="${momentId}" title="删除评论">×</button>` : ""}
        </div>
    `).join("");
}

async function loadMomentComments(momentId) {
    const comments = await ChatApi.get(`/moments/${momentId}/comments`);
    renderMomentComments(momentId, comments);
}

function renderMomentFeed() {
    const target = $("#momentFeed");
    if (!target) return;
    target.innerHTML = AppState.moments.map(moment => `
        <article class="feed-card" data-moment-card="${moment.id}">
            <header>
                ${avatarHtml(moment.authorName, null)}
                <span>
                    <strong>${escapeHtml(moment.authorName)}</strong>
                    <span>${formatDate(moment.createdAt)} · ${escapeHtml(moment.visibility || "ALL_FRIENDS")}</span>
                </span>
            </header>
            <p>${escapeHtml(moment.text || "")}</p>
            ${renderMomentMedia(moment.media || [])}
            <div class="moment-actions">
                <button class="small-button moment-like-button ${moment.likedByMe ? "active" : ""}" type="button" ${moment.likedByMe ? `data-unlike="${moment.id}"` : `data-like="${moment.id}"`}>
                    <i data-lucide="heart"></i> ${moment.likedByMe ? "取消赞" : "点赞"}
                </button>
                <span>${moment.likeCount || 0} 赞 · ${moment.commentCount || 0} 评论</span>
            </div>
            <form class="moment-comment-form" data-comment-form="${moment.id}">
                <input name="content" placeholder="写评论...">
                <button class="small-button" type="submit">发送</button>
            </form>
            <div class="moment-comments" data-moment-comments="${moment.id}"></div>
        </article>
    `).join("") || `<div class="empty-state"><i data-lucide="image"></i><strong>暂无朋友圈</strong><span>发布一条图文动态吧。</span></div>`;
    if (momentCanLoadMore && AppState.moments.length) {
        target.insertAdjacentHTML("beforeend", `<button class="history-load-more" type="button" id="loadMoreMoments">加载更多动态</button>`);
    }
    refreshIcons();
    AppState.moments.forEach(moment => loadMomentComments(moment.id).catch(() => {}));
}

async function loadMoments(options = {}) {
    const {append = false} = options;
    const beforeId = append ? momentOldestId : null;
    const rows = await ChatApi.get(`/moments?limit=10${beforeId ? `&beforeId=${beforeId}` : ""}`);
    AppState.moments = append ? [...AppState.moments, ...rows] : rows;
    momentOldestId = AppState.moments.at(-1)?.id || null;
    momentCanLoadMore = rows.length === 10;
    renderMomentFeed();
}

window.loadMoments = loadMoments;

async function loadMoreMoments() {
    if (!momentOldestId || !momentCanLoadMore) return;
    await loadMoments({append: true});
}

function spawnLikeHearts(button) {
    if (!button || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    for (let index = 0; index < 7; index += 1) {
        const heart = document.createElement("span");
        heart.className = "like-heart-particle";
        heart.textContent = "♥";
        heart.style.setProperty("--heart-x", `${(Math.random() - .5) * 54}px`);
        heart.style.setProperty("--heart-y", `${-28 - Math.random() * 34}px`);
        heart.style.setProperty("--heart-delay", `${index * 34}ms`);
        button.appendChild(heart);
        heart.addEventListener("animationend", () => heart.remove(), {once: true});
    }
}

function renderMomentPreview(files = []) {
    const target = $("#momentPreview");
    if (!target) return;
    target.innerHTML = files.map(file => `<img src="${escapeHtml(file.url)}" alt="${escapeHtml(file.name)}">`).join("");
}

async function uploadMomentImage(file) {
    const formData = new FormData();
    formData.append("kind", "MOMENT_IMAGE");
    formData.append("file", file);
    return ChatApi.request("/media", {method: "POST", body: formData});
}

$("#momentImagesInput")?.addEventListener("change", async event => {
    const selectedFiles = Array.from(event.currentTarget.files || []);
    const files = selectedFiles.slice(0, 6);
    if (!files.length) return;
    renderMomentPreview(files.map(file => ({name: file.name, url: URL.createObjectURL(file)})));
    toast("朋友圈图片上传中...", 0);
    setMomentBusy(true);
    try {
        uploadedMomentMedia = [];
        for (const file of files) {
            const media = await uploadMomentImage(file);
            uploadedMomentMedia.push(media);
        }
    } catch (error) {
        toast(error.message || "朋友圈图片上传失败");
        uploadedMomentMedia = [];
        renderMomentPreview([]);
        event.currentTarget.value = "";
        return;
    } finally {
        setMomentBusy(false);
    }
    $("#momentForm").elements.mediaIds.value = uploadedMomentMedia.map(item => item.id).join(",");
    renderMomentPreview(uploadedMomentMedia.map(item => ({name: item.originalName, url: item.url})));
    updateDashboardMetrics();
    toast(selectedFiles.length > 6 ? "最多上传 6 张图片，已自动截取前 6 张" : "图片已上传");
});

$("#momentForm")?.addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    const text = form.elements.text.value.trim();
    const mediaIds = form.elements.mediaIds.value.split(",").map(value => Number(value.trim())).filter(Boolean);
    if (!text && !mediaIds.length) return toast("朋友圈至少需要文字或图片");
    setMomentBusy(true);
    try {
        await ChatApi.post("/moments", {
            text,
            visibility: form.elements.visibility.value,
            mediaIds
        });
        form.reset();
        uploadedMomentMedia = [];
        renderMomentPreview([]);
        toast("动态已发布");
        await loadMoments();
        updateDashboardMetrics?.();
    } catch (error) {
        toast(error.message || "朋友圈发布失败");
    } finally {
        setMomentBusy(false);
    }
});

document.addEventListener("click", async event => {
    const likeButton = event.target.closest("[data-like]");
    const unlikeButton = event.target.closest("[data-unlike]");
    const deleteCommentButton = event.target.closest("[data-delete-comment]");
    if (event.target.closest("#loadMoreMoments")) {
        await loadMoreMoments();
        return;
    }
    if (likeButton) {
        const id = likeButton.dataset.like;
        spawnLikeHearts(likeButton);
        await ChatApi.post(`/moments/${id}/likes`);
        await loadMoments();
        return;
    }
    if (unlikeButton) {
        await ChatApi.delete(`/moments/${unlikeButton.dataset.unlike}/likes`);
        await loadMoments();
        return;
    }
    if (deleteCommentButton) {
        await ChatApi.delete(`/moments/${deleteCommentButton.dataset.commentMoment}/comments/${deleteCommentButton.dataset.deleteComment}`);
        await loadMomentComments(Number(deleteCommentButton.dataset.commentMoment));
    }
});

document.addEventListener("submit", async event => {
    const form = event.target.closest("[data-comment-form]");
    if (!form) return;
    event.preventDefault();
    const momentId = Number(form.dataset.commentForm);
    const content = form.elements.content.value.trim();
    if (!content) return toast("请输入评论内容");
    await ChatApi.post(`/moments/${momentId}/comments`, {content});
    form.reset();
    await loadMomentComments(momentId);
    await loadMoments();
});
