(function () {
    const INCOMING_CALL_ENDPOINT = "/voice/calls/incoming";
    const ACTIVE_CALL_ENDPOINT_TEMPLATE = "/voice/calls/${state.callId}";
    const POLL_MS = 2500;
    const state = {
        callId: null,
        peerId: null,
        mode: "audio",
        role: null,
        incoming: null,
        offerSent: false,
        remoteAudioReady: false,
        remoteVideoReady: false,
        busy: false
    };
    let pollTimer;

    function selectedPeerId() {
        const selectedConversation = AppState.selectedConversation;
        if (!selectedConversation || selectedConversation.type !== "PRIVATE") return null;
        return selectedConversation.peerId || null;
    }

    function elements() {
        return {
            dialog: document.getElementById("voiceDialog"),
            title: document.getElementById("voiceDialogTitle"),
            message: document.getElementById("voiceDialogMessage"),
            acceptButton: document.getElementById("acceptVoiceCallButton"),
            rejectButton: document.getElementById("rejectVoiceCallButton"),
            endButton: document.getElementById("endVoiceCallButton"),
            stage: document.getElementById("videoCallStage"),
            remoteVideo: document.getElementById("remoteVideoStream"),
            localVideo: document.getElementById("localVideoStream"),
            remoteAudio: document.getElementById("remoteCallAudio")
        };
    }

    function setVideoStage(visible) {
        const ui = elements();
        if (ui.stage) ui.stage.hidden = !visible;
        if (!visible) {
            if (ui.remoteVideo) ui.remoteVideo.srcObject = null;
            if (ui.localVideo) ui.localVideo.srcObject = null;
        }
    }

    function resetRemoteMediaState() {
        state.remoteAudioReady = false;
        state.remoteVideoReady = false;
    }

    function markRemoteMedia(kind) {
        if (kind === "audio") state.remoteAudioReady = true;
        if (kind === "video") state.remoteVideoReady = true;
    }

    function hasRequiredRemoteMedia() {
        return state.mode === "video"
            ? state.remoteAudioReady && state.remoteVideoReady
            : state.remoteAudioReady;
    }

    function showCallDialog(title, message, mode) {
        const ui = elements();
        if (ui.title) ui.title.textContent = title;
        if (ui.message) ui.message.textContent = message;
        if (ui.acceptButton) ui.acceptButton.hidden = mode !== "incoming";
        if (ui.rejectButton) ui.rejectButton.hidden = mode !== "incoming";
        if (ui.endButton) ui.endButton.hidden = mode === "incoming";
        setVideoStage(state.mode === "video");
        if (ui.dialog && !ui.dialog.open) ui.dialog.showModal();
        window.lucide?.createIcons();
    }

    function closeCallDialog() {
        const dialog = elements().dialog;
        if (dialog?.open) dialog.close();
    }

    function toastMessage(message) {
        if (typeof toast === "function") toast(message);
        else console.info(message);
    }

    async function prepareRtc() {
        const iceServers = await CallApi.iceServers();
        const ui = elements();
        await CallRtc.prepare({
            mode: state.mode,
            iceServers,
            elements: ui,
            onSignal: (type, payload) => CallSignaling.send(state.peerId, state.callId, type, payload),
            onRemoteMedia: kind => {
                markRemoteMedia(kind);
                if (hasRequiredRemoteMedia()) {
                    showCallDialog(state.mode === "video" ? "Video call" : "Voice call", "Connected.", "active");
                }
            },
            onStateChange: connectionState => {
                if (["failed", "closed"].includes(connectionState)) {
                    toastMessage("Call connection ended.");
                    cleanup(false);
                }
            }
        });
    }

    async function sendOfferOnce() {
        if (!state.callId || !state.peerId || state.offerSent) return;
        state.offerSent = true;
        try {
            const offer = await CallRtc.createOffer();
            await CallSignaling.send(state.peerId, state.callId, "offer", offer);
            showCallDialog(state.mode === "video" ? "Video call" : "Voice call", "Connecting media...", "active");
        } catch (error) {
            state.offerSent = false;
            throw error;
        }
    }

    async function startCall(mode) {
        if (state.busy || state.callId) {
            toastMessage("Please end the current call first.");
            return;
        }
        const peerId = selectedPeerId();
        if (!peerId) {
            toastMessage("Please select a private chat first.");
            return;
        }
        state.busy = true;
        state.mode = mode === "video" ? "video" : "audio";
        state.peerId = peerId;
        state.role = "caller";
        state.offerSent = false;
        resetRemoteMediaState();
        try {
            await CallSignaling.waitForOpen();
            await prepareRtc();
            state.callId = await CallApi.start(peerId, state.mode, AppState.conversationId);
            showCallDialog(state.mode === "video" ? "Calling video" : "Calling voice", "Waiting for answer...", "active");
        } catch (error) {
            await cleanup(true, false);
            toastMessage(error.message || "Call failed.");
        } finally {
            state.busy = false;
        }
    }

    async function acceptCall() {
        if (!state.incoming || state.busy) return;
        state.busy = true;
        const incoming = state.incoming;
        state.callId = incoming.callId;
        state.peerId = incoming.fromUserId;
        state.mode = incoming.callMode || "audio";
        state.role = "callee";
        state.offerSent = false;
        state.incoming = null;
        resetRemoteMediaState();
        try {
            await prepareRtc();
            await CallApi.accept(state.callId);
            showCallDialog(state.mode === "video" ? "Video call" : "Voice call", "Accepted. Waiting for media...", "active");
        } catch (error) {
            await cleanup(true);
            toastMessage(error.message || "Could not accept call.");
        } finally {
            state.busy = false;
        }
    }

    async function rejectCall() {
        if (!state.incoming) return;
        const incoming = state.incoming;
        await CallApi.reject(incoming.callId).catch(() => null);
        await CallSignaling.send(incoming.fromUserId, incoming.callId, "call-rejected", {}).catch(() => null);
        state.incoming = null;
        await cleanup(true, false);
    }

    async function endCall() {
        const callId = state.callId;
        const peerId = state.peerId;
        if (callId) await CallApi.end(callId).catch(() => null);
        if (callId && peerId) await CallSignaling.send(peerId, callId, "call-ended", {}).catch(() => null);
        await cleanup(true, false);
    }

    async function cleanup(closeDialog, notifyServer = true) {
        const callId = state.callId;
        if (notifyServer && callId) {
            await CallApi.end(callId).catch(() => null);
        }
        CallRtc.close();
        state.callId = null;
        state.peerId = null;
        state.mode = "audio";
        state.role = null;
        state.incoming = null;
        state.offerSent = false;
        resetRemoteMediaState();
        state.busy = false;
        setVideoStage(false);
        if (closeDialog) closeCallDialog();
    }

    async function handleInvite(signal) {
        if (state.callId || state.incoming || state.busy) {
            await CallApi.reject(signal.callId).catch(() => null);
            await CallSignaling.send(signal.fromUserId, signal.callId, "call-rejected", {}).catch(() => null);
            return;
        }
        state.incoming = {
            callId: signal.callId,
            fromUserId: signal.fromUserId,
            callMode: signal.payloadObject?.callMode || "audio"
        };
        state.mode = state.incoming.callMode;
        showCallDialog(state.mode === "video" ? "Incoming video call" : "Incoming voice call",
            `User ${signal.fromUserId} is calling`, "incoming");
    }

    async function handleAccepted(signal) {
        if (!state.callId || signal.callId !== state.callId || state.role !== "caller") return;
        state.mode = signal.payloadObject?.callMode || state.mode;
        await sendOfferOnce();
        if (hasRequiredRemoteMedia()) {
            showCallDialog(state.mode === "video" ? "Video call" : "Voice call", "Connected.", "active");
        }
    }

    async function handleSignal(signal) {
        try {
            if (signal.type === "call-invite") return handleInvite(signal);
            if (signal.type === "call-accepted") return handleAccepted(signal);
            if (signal.type === "call-rejected") {
                toastMessage("Call rejected.");
                return cleanup(true, false);
            }
            if (signal.type === "call-ended") {
                toastMessage("Call ended.");
                return cleanup(true, false);
            }
            if (!state.callId || signal.callId !== state.callId) return;
            if (signal.type === "offer") {
                const answer = await CallRtc.handleOffer(signal.payloadObject);
                await CallSignaling.send(signal.fromUserId, state.callId, "answer", answer);
                showCallDialog(state.mode === "video" ? "Video call" : "Voice call", "Connecting media...", "active");
                return;
            }
            if (signal.type === "answer") {
                await CallRtc.handleAnswer(signal.payloadObject);
                if (hasRequiredRemoteMedia()) {
                    showCallDialog(state.mode === "video" ? "Video call" : "Voice call", "Connected.", "active");
                } else {
                    showCallDialog(state.mode === "video" ? "Video call" : "Voice call", "Connecting media...", "active");
                }
                return;
            }
            if (signal.type === "ice") {
                await CallRtc.addIceCandidate(signal.payloadObject);
            }
        } catch (error) {
            console.warn("Call signal handling failed", error);
        }
    }

    async function pollCallState() {
        if (document.visibilityState !== "visible") return;
        if (state.callId) {
            const call = await CallApi.get(state.callId).catch(() => null);
            if (call && ["ENDED", "REJECTED", "MISSED", "BUSY"].includes(call.status)) {
                await cleanup(true, false);
                return;
            }
            if (call?.status === "ACCEPTED" && state.role === "caller") {
                state.mode = call.callMode || state.mode;
                await sendOfferOnce().catch(() => null);
            }
            return;
        }
        if (!state.incoming && !state.busy) {
            const incoming = await ChatApi.get(INCOMING_CALL_ENDPOINT).catch(() => null);
            if (incoming?.id && incoming.status === "RINGING") {
                await handleInvite({
                    fromUserId: incoming.callerId,
                    callId: incoming.id,
                    payloadObject: {callMode: incoming.callMode || "audio"}
                });
            }
        }
    }

    function init() {
        document.getElementById("voiceButton")?.addEventListener("click", () => startCall("audio"));
        document.getElementById("videoButton")?.addEventListener("click", () => startCall("video"));
        document.getElementById("acceptVoiceCallButton")?.addEventListener("click", () => acceptCall());
        document.getElementById("rejectVoiceCallButton")?.addEventListener("click", () => rejectCall());
        document.getElementById("endVoiceCallButton")?.addEventListener("click", () => endCall());
        document.getElementById("unlockVoicePlaybackButton")?.addEventListener("click", () => CallRtc.unlockPlayback(elements()));
        CallSignaling.onSignal(handleSignal);
        CallSignaling.connect();
        if (!pollTimer) pollTimer = window.setInterval(() => pollCallState().catch(() => null), POLL_MS);
        document.addEventListener("visibilitychange", () => {
            if (document.visibilityState === "visible") {
                CallSignaling.connect();
                pollCallState().catch(() => null);
            }
        });
        window.addEventListener("online", () => {
            CallSignaling.connect();
            pollCallState().catch(() => null);
        });
        window.addEventListener("pagehide", () => {
            if (state.callId) {
                const body = new Blob(["{}"], {type: "application/json"});
                navigator.sendBeacon?.(`api/voice/calls/${state.callId}/end`, body);
                CallApi.endDuringUnload(state.callId);
            }
        });
    }

    window.CallController = {
        init,
        startCall,
        acceptCall,
        rejectCall,
        endCall,
        pollCallState
    };
})();
