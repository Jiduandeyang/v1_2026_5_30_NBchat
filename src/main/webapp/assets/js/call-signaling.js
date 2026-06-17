(function () {
    const SIGNAL_TYPES = {
        INVITE: "call-invite",
        ACCEPTED: "call-accepted",
        REJECTED: "call-rejected",
        ENDED: "call-ended",
        OFFER: "offer",
        ANSWER: "answer",
        ICE: "ice"
    };
    const listeners = new Set();
    let socket;
    let reconnectTimer;
    let openPromise;

    function socketUrl() {
        const protocol = location.protocol === "https:" ? "wss" : "ws";
        return `${protocol}://${location.host}${location.pathname.replace(/\/[^/]*$/, "")}/ws/voice`;
    }

    function normalizePayload(payload) {
        if (!payload) return {};
        if (typeof payload === "object") return payload;
        try {
            return JSON.parse(payload);
        } catch (error) {
            return {};
        }
    }

    function emit(raw) {
        const signal = {...raw, payloadObject: normalizePayload(raw.payload)};
        listeners.forEach(listener => listener(signal));
    }

    function connect() {
        if (socket?.readyState === WebSocket.OPEN || socket?.readyState === WebSocket.CONNECTING) {
            return socket;
        }
        socket = new WebSocket(socketUrl());
        socket.addEventListener("open", () => {
            if (reconnectTimer) {
                clearTimeout(reconnectTimer);
                reconnectTimer = null;
            }
        });
        socket.addEventListener("message", event => emit(JSON.parse(event.data)));
        socket.addEventListener("close", () => {
            socket = null;
            openPromise = null;
            if (!reconnectTimer && document.visibilityState === "visible") {
                reconnectTimer = window.setTimeout(() => {
                    reconnectTimer = null;
                    connect();
                }, 1200);
            }
        });
        socket.addEventListener("error", () => socket?.close());
        return socket;
    }

    function waitForOpen() {
        const current = connect();
        if (current.readyState === WebSocket.OPEN) return Promise.resolve(current);
        if (openPromise) return openPromise;
        openPromise = new Promise((resolve, reject) => {
            const timer = window.setTimeout(() => reject(new Error("Call signaling timeout.")), 8000);
            current.addEventListener("open", () => {
                clearTimeout(timer);
                resolve(current);
            }, {once: true});
            current.addEventListener("close", () => {
                clearTimeout(timer);
                openPromise = null;
                reject(new Error("Call signaling disconnected."));
            }, {once: true});
            current.addEventListener("error", () => {
                clearTimeout(timer);
                openPromise = null;
                reject(new Error("Call signaling failed."));
            }, {once: true});
        });
        return openPromise;
    }

    async function send(targetUserId, callId, type, payload = {}) {
        const current = await waitForOpen();
        current.send(JSON.stringify({
            targetUserId,
            callId,
            type,
            payload: typeof payload === "string" ? payload : JSON.stringify(payload)
        }));
    }

    window.CallSignaling = {
        SIGNAL_TYPES,
        connect,
        waitForOpen,
        send,
        onSignal(listener) {
            listeners.add(listener);
            return () => listeners.delete(listener);
        }
    };
})();
