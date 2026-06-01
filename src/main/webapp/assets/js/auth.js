const stage = document.querySelector("#mascotStage");
const authTitle = document.querySelector("#authTitle");
const authSubtitle = document.querySelector("#authSubtitle");

const authState = {
    identityFocused: false,
    activePassword: null,
    passwordPeekTimer: null,
    passwordPeekReturnTimer: null
};

function activatePanel(panelId) {
    const tab = document.querySelector(`[data-panel="${panelId}"]`);
    if (!tab) return;
    document.querySelectorAll(".tab").forEach(item => item.classList.remove("active"));
    document.querySelectorAll(".auth-form").forEach(panel => panel.classList.remove("active"));
    tab.classList.add("active");
    document.querySelector(`#${panelId}`).classList.add("active");
    authTitle.textContent = tab.dataset.title;
    authSubtitle.textContent = tab.dataset.subtitle;
    stage?.classList.toggle("registering", panelId === "registerPanel");
    if (location.hash !== `#${panelId}`) {
        history.replaceState(null, "", `#${panelId}`);
    }
    updateCharacterMood();
}

document.querySelectorAll(".tab").forEach(button => {
    button.addEventListener("click", () => activatePanel(button.dataset.panel));
});

document.querySelectorAll("[data-panel-trigger]").forEach(button => {
    button.addEventListener("click", () => activatePanel(button.dataset.panelTrigger));
});

document.addEventListener("pointermove", event => {
    const viewportDx = (event.clientX / Math.max(window.innerWidth, 1) - 0.5) * 2;
    stage?.style.setProperty("--body-skew", `${viewportDx * 4}deg`);

    if (stage?.classList.contains("password-secret") && !stage.classList.contains("password-peeking")) {
        pointPupilsAway();
        return;
    }

    if (stage?.classList.contains("password-peeking")) {
        pointPupilsToForm();
        return;
    }

    document.querySelectorAll(".peek-stage .eye span").forEach(pupil => {
        const eye = pupil.closest(".eye");
        const rect = eye.getBoundingClientRect();
        const centerX = rect.left + rect.width / 2;
        const centerY = rect.top + rect.height / 2;
        const angle = Math.atan2(event.clientY - centerY, event.clientX - centerX);
        const distance = Math.hypot(event.clientX - centerX, event.clientY - centerY);
        const maxDistance = 9;
        const ratio = Math.min(1, distance / 180);
        const easedDistance = maxDistance * (1 - Math.pow(1 - ratio, 2));
        pupil.style.transform = `translate(${Math.cos(angle) * easedDistance}px, ${Math.sin(angle) * easedDistance}px)`;
    });
});

function pointPupilsAway() {
    document.querySelectorAll(".peek-stage .eye span").forEach((pupil, index) => {
        const x = index % 2 === 0 ? -8 : -6;
        const y = index % 3 === 0 ? -5 : 4;
        pupil.style.transform = `translate(${x}px, ${y}px)`;
    });
}

function pointPupilsToForm() {
    document.querySelectorAll(".peek-stage .eye span").forEach((pupil, index) => {
        const x = index % 2 === 0 ? 8 : 9;
        const y = index % 3 === 0 ? -2 : 3;
        pupil.style.transform = `translate(${x}px, ${y}px)`;
    });
}

function scheduleBlink(peeper) {
    const delay = Math.random() * 4000 + 3000;
    window.setTimeout(() => {
        peeper.classList.add("blinking");
        window.setTimeout(() => {
            peeper.classList.remove("blinking");
            scheduleBlink(peeper);
        }, 150);
    }, delay);
}

document.querySelectorAll(".peeper").forEach(scheduleBlink);

function activePasswordHasValue() {
    return authState.activePassword && authState.activePassword.value.length > 0;
}

function activePasswordVisible() {
    return activePasswordHasValue() && authState.activePassword.type === "text";
}

function activePasswordHidden() {
    return activePasswordHasValue() && authState.activePassword.type === "password";
}

function clearPasswordPeekLoop() {
    window.clearTimeout(authState.passwordPeekTimer);
    window.clearTimeout(authState.passwordPeekReturnTimer);
    authState.passwordPeekTimer = null;
    authState.passwordPeekReturnTimer = null;
    stage?.classList.remove("password-peeking");
}

function schedulePasswordPeekLoop() {
    clearPasswordPeekLoop();
    if (!activePasswordVisible()) return;
    authState.passwordPeekTimer = window.setTimeout(() => {
        if (!activePasswordVisible()) return;
        stage?.classList.add("password-peeking");
        pointPupilsToForm();
        authState.passwordPeekReturnTimer = window.setTimeout(() => {
            stage?.classList.remove("password-peeking");
            pointPupilsAway();
            schedulePasswordPeekLoop();
        }, 800);
    }, Math.random() * 3000 + 2000);
}

function updateCharacterMood() {
    if (!stage) return;
    stage.classList.remove("identity-typing", "password-secret", "password-visible");

    if (activePasswordHidden()) {
        stage.classList.add("password-secret");
        pointPupilsAway();
        clearPasswordPeekLoop();
        return;
    }

    if (activePasswordVisible()) {
        stage.classList.add("password-visible");
        if (stage.classList.contains("password-peeking")) {
            pointPupilsToForm();
        } else {
            pointPupilsAway();
        }
        if (!authState.passwordPeekTimer && !authState.passwordPeekReturnTimer) {
            schedulePasswordPeekLoop();
        }
        return;
    }

    clearPasswordPeekLoop();
    if (authState.identityFocused) {
        stage.classList.add("identity-typing");
    }
}

document.querySelectorAll('input[name="username"], input[name="qqEmail"], input[type="email"]').forEach(input => {
    input.addEventListener("focus", () => {
        authState.identityFocused = true;
        updateCharacterMood();
    });
    input.addEventListener("input", updateCharacterMood);
    input.addEventListener("blur", () => {
        authState.identityFocused = false;
        updateCharacterMood();
    });
});

document.querySelectorAll('.password-wrap input[type="password"], .password-wrap input[type="text"]').forEach(input => {
    input.addEventListener("focus", () => {
        authState.activePassword = input;
        updateCharacterMood();
    });
    input.addEventListener("input", updateCharacterMood);
    input.addEventListener("blur", () => {
        window.setTimeout(() => {
            if (document.activeElement?.classList?.contains("password-toggle")) return;
            if (authState.activePassword === input) {
                authState.activePassword = input.value.length > 0 ? input : null;
                updateCharacterMood();
            }
        }, 0);
    });
});

document.querySelectorAll(".password-toggle").forEach(button => {
    button.addEventListener("click", () => {
        const input = button.closest(".password-wrap").querySelector("input");
        const visible = input.type === "text";
        input.type = visible ? "password" : "text";
        button.textContent = visible ? "◎" : "◉";
        button.setAttribute("aria-label", visible ? "显示密码" : "隐藏密码");
        authState.activePassword = input;
        input.focus();
        updateCharacterMood();
    });
});

document.querySelectorAll("[data-code]").forEach(button => {
    button.addEventListener("click", async () => {
        const form = button.closest("form");
        const qqEmail = form.qqEmail.value.trim();
        const path = button.dataset.code === "register" ? "/auth/register/code" : "/auth/password-reset/code";
        try {
            await ChatApi.post(path, {qqEmail});
            toast("验证码已发送，请查看 QQ 邮箱。");
        } catch (error) {
            toast(error.message);
        }
    });
});

document.querySelector("#loginPanel").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    try {
        await ChatApi.post("/auth/login", {username: form.username.value.trim(), password: form.password.value});
        location.href = "app.html";
    } catch (error) {
        toast(error.message);
    }
});

document.querySelector("#registerPanel").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    try {
        await ChatApi.post("/auth/register", {
            username: form.username.value.trim(),
            nickname: form.nickname.value.trim(),
            qqEmail: form.qqEmail.value.trim(),
            code: form.code.value.trim(),
            password: form.password.value
        });
        location.href = "app.html";
    } catch (error) {
        toast(error.message);
    }
});

document.querySelector("#resetPanel").addEventListener("submit", async event => {
    event.preventDefault();
    const form = event.currentTarget;
    try {
        await ChatApi.post("/auth/password-reset", {
            qqEmail: form.qqEmail.value.trim(),
            code: form.code.value.trim(),
            newPassword: form.newPassword.value
        });
        toast("密码已更新，可以重新登录。");
        activatePanel("loginPanel");
    } catch (error) {
        toast(error.message);
    }
});

if (location.hash) {
    activatePanel(location.hash.slice(1));
} else {
    updateCharacterMood();
}
