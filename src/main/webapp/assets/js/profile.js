function paintProfile(me) {
    const form = $("#profileForm");
    if (!form || !me) return;
    form.elements.nickname.value = me.nickname || "";
    form.elements.signature.value = me.signature || "";
    form.elements.avatarUrl.value = me.avatarUrl || "";
    form.elements.backgroundUrl.value = me.backgroundUrl || "";
    const cover = $("#profileCover");
    if (cover) {
        cover.style.backgroundImage = me.backgroundUrl ? `url(${me.backgroundUrl})` : "";
        cover.style.backgroundSize = me.backgroundUrl ? "cover" : "";
        cover.style.backgroundPosition = me.backgroundUrl ? "center" : "";
    }
    paintAvatar($("#profileAvatar"), me);
}

async function loadProfile() {
    AppState.me = await ChatApi.get("/users/me");
    paintProfile(AppState.me);
    updateIdentity();
}

window.loadProfile = loadProfile;

$("#profileForm")?.addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    AppState.me = await ChatApi.put("/users/me", {
        nickname: form.elements.nickname.value.trim(),
        signature: form.elements.signature.value.trim(),
        avatarUrl: form.elements.avatarUrl.value.trim(),
        backgroundUrl: form.elements.backgroundUrl.value.trim()
    });
    paintProfile(AppState.me);
    updateIdentity();
    toast("个人资料已保存");
});
