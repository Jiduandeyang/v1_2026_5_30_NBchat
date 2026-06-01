let voiceSocket;
let localStream;
let peer;
let activeCallId;
let activePeerId;
let pendingIncomingCall;
let cachedIceServers;

function selectedPeerId() {
    if (!AppState.selectedConversation || AppState.selectedConversation.type !== "PRIVATE" || !AppState.selectedConversation.peerId) return null;
    return AppState.selectedConversation.peerId;
}

function voiceSocketUrl() {
    const protocol = location.protocol === "https:" ? "wss" : "ws";
    return `${protocol}://${location.host}${location.pathname.replace(/\/[^/]*$/, "")}/ws/voice`;
}

function voiceDialog() {
    return $("#voiceDialog");
}

function showVoiceDialog(title, message, mode) {
    $("#voiceDialogTitle") && ($("#voiceDialogTitle").textContent = title);
    $("#voiceDialogMessage") && ($("#voiceDialogMessage").textContent = message);
    $("#acceptVoiceCallButton") && ($("#acceptVoiceCallButton").hidden = mode !== "incoming");
    $("#rejectVoiceCallButton") && ($("#rejectVoiceCallButton").hidden = mode !== "incoming");
    $("#endVoiceCallButton") && ($("#endVoiceCallButton").hidden = mode === "incoming");
    const dialog = voiceDialog();
    if (dialog && !dialog.open) dialog.showModal();
    refreshIcons();
}

function closeVoiceDialog() {
    const dialog = voiceDialog();
    if (dialog?.open) dialog.close();
}

function ensureRemoteAudio() {
    let audio = $("#remoteVoiceAudio");
    if (!audio) {
        audio = document.createElement("audio");
        audio.id = "remoteVoiceAudio";
        audio.autoplay = true;
        audio.playsInline = true;
        audio.hidden = true;
        document.body.appendChild(audio);
    }
    return audio;
}

async function loadIceServers() {
    if (cachedIceServers) return cachedIceServers;
    cachedIceServers = await ChatApi.get("/voice/ice-servers");
    return cachedIceServers;
}

async function ensureLocalStream() {
    if (localStream) return localStream;
    if (!navigator.mediaDevices?.getUserMedia) {
        throw new Error("当前浏览器不支持麦克风权限");
    }
    localStream = await navigator.mediaDevices.getUserMedia({audio: true});
    return localStream;
}

async function createPeer(targetUserId) {
    if (peer) return peer;
    peer = new RTCPeerConnection({iceServers: await loadIceServers()});
    peer.onicecandidate = event => {
        if (event.candidate && activeCallId) {
            sendVoiceSignal(targetUserId, "ice-candidate", JSON.stringify(event.candidate));
        }
    };
    peer.ontrack = event => {
        ensureRemoteAudio().srcObject = event.streams[0];
    };
    peer.onconnectionstatechange = () => {
        if (["failed", "disconnected", "closed"].includes(peer.connectionState)) {
            cleanupVoiceCall(false);
        }
    };
    return peer;
}

function connectVoiceSocket() {
    if (voiceSocket && voiceSocket.readyState !== WebSocket.CLOSED) return voiceSocket;
    voiceSocket = new WebSocket(voiceSocketUrl());
    voiceSocket.addEventListener("message", handleVoiceSignal);
    voiceSocket.addEventListener("close", () => {
        voiceSocket = null;
    });
    return voiceSocket;
}

function waitVoiceSocketOpen() {
    const socket = connectVoiceSocket();
    if (socket.readyState === WebSocket.OPEN) return Promise.resolve(socket);
    return new Promise((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error("语音信令连接超时")), 3000);
        socket.addEventListener("open", () => {
            clearTimeout(timer);
            resolve(socket);
        }, {once: true});
        socket.addEventListener("error", () => {
            clearTimeout(timer);
            reject(new Error("语音信令连接失败"));
        }, {once: true});
    });
}

async function sendVoiceSignal(targetUserId, type, payload = "") {
    const socket = await waitVoiceSocketOpen();
    socket.send(JSON.stringify({
        targetUserId,
        callId: activeCallId,
        type,
        payload
    }));
}

async function preparePeer(targetUserId) {
    const stream = await ensureLocalStream();
    const rtc = await createPeer(targetUserId);
    const existingSenders = rtc.getSenders().map(sender => sender.track);
    stream.getTracks().forEach(track => {
        if (!existingSenders.includes(track)) rtc.addTrack(track, stream);
    });
    return rtc;
}

async function createAndSendOffer(targetUserId) {
    const rtc = await preparePeer(targetUserId);
    const offer = await rtc.createOffer();
    await rtc.setLocalDescription(offer);
    await sendVoiceSignal(targetUserId, "offer", JSON.stringify(offer));
}

async function handleVoiceSignal(event) {
    const signal = JSON.parse(event.data);

    if (signal.type === "call-invite") {
        pendingIncomingCall = signal;
        activeCallId = signal.callId;
        activePeerId = signal.fromUserId;
        showVoiceDialog("收到语音通话", `用户 ${signal.fromUserId} 正在呼叫你`, "incoming");
        return;
    }

    if (signal.type === "call-accepted") {
        activeCallId = signal.callId;
        activePeerId = signal.fromUserId;
        showVoiceDialog("语音通话", "对方已接听，正在建立连接", "active");
        await createAndSendOffer(signal.fromUserId);
        return;
    }

    if (signal.type === "call-rejected") {
        toast("对方已拒绝语音通话");
        cleanupVoiceCall(true);
        return;
    }

    if (signal.type === "call-ended") {
        toast("语音通话已结束");
        cleanupVoiceCall(true);
        return;
    }

    activeCallId = signal.callId;
    activePeerId = signal.fromUserId;
    const rtc = await preparePeer(signal.fromUserId);

    if (signal.type === "offer") {
        await rtc.setRemoteDescription(JSON.parse(signal.payload));
        const answer = await rtc.createAnswer();
        await rtc.setLocalDescription(answer);
        await sendVoiceSignal(signal.fromUserId, "answer", JSON.stringify(answer));
        showVoiceDialog("语音通话中", "已接入通话", "active");
    }
    if (signal.type === "answer") {
        await rtc.setRemoteDescription(JSON.parse(signal.payload));
        showVoiceDialog("语音通话中", "通话已连接", "active");
    }
    if (signal.type === "ice-candidate") {
        await rtc.addIceCandidate(JSON.parse(signal.payload));
    }
}

function cleanupVoiceCall(closeDialog) {
    peer?.close();
    peer = null;
    localStream?.getTracks().forEach(track => track.stop());
    localStream = null;
    activeCallId = null;
    activePeerId = null;
    pendingIncomingCall = null;
    const audio = $("#remoteVoiceAudio");
    if (audio) audio.srcObject = null;
    if (closeDialog) closeVoiceDialog();
}

async function startPrivateVoiceCall() {
    const peerId = selectedPeerId();
    if (!peerId) return toast("请选择一对一私聊后再发起语音");
    try {
        activePeerId = peerId;
        activeCallId = await ChatApi.post(`/voice/calls/${peerId}`);
        await waitVoiceSocketOpen();
        await sendVoiceSignal(peerId, "call-invite", JSON.stringify({conversationId: AppState.conversationId}));
        showVoiceDialog("正在呼叫", "等待对方接听", "active");
    } catch (error) {
        cleanupVoiceCall(true);
        toast(error.message || "无法发起语音，请检查浏览器麦克风权限");
    }
}

async function acceptVoiceCall() {
    if (!pendingIncomingCall) return;
    try {
        await ChatApi.post(`/voice/calls/${pendingIncomingCall.callId}/accept`);
        activeCallId = pendingIncomingCall.callId;
        activePeerId = pendingIncomingCall.fromUserId;
        await preparePeer(activePeerId);
        await sendVoiceSignal(activePeerId, "call-accepted");
        showVoiceDialog("语音通话", "已接听，正在建立连接", "active");
        pendingIncomingCall = null;
    } catch (error) {
        toast(error.message || "接听失败");
    }
}

async function rejectVoiceCall() {
    if (!pendingIncomingCall) return;
    await ChatApi.post(`/voice/calls/${pendingIncomingCall.callId}/reject`);
    activeCallId = pendingIncomingCall.callId;
    await sendVoiceSignal(pendingIncomingCall.fromUserId, "call-rejected");
    cleanupVoiceCall(true);
}

async function endVoiceCall() {
    const callId = activeCallId;
    const peerId = activePeerId;
    if (callId) {
        await ChatApi.post(`/voice/calls/${callId}/end`).catch(() => null);
    }
    if (peerId) {
        await sendVoiceSignal(peerId, "call-ended").catch(() => null);
    }
    cleanupVoiceCall(true);
}

$("#voiceButton")?.addEventListener("click", startPrivateVoiceCall);
$("#acceptVoiceCallButton")?.addEventListener("click", () => acceptVoiceCall().catch(error => toast(error.message || "接听失败")));
$("#rejectVoiceCallButton")?.addEventListener("click", () => rejectVoiceCall().catch(error => toast(error.message || "拒绝失败")));
$("#endVoiceCallButton")?.addEventListener("click", () => endVoiceCall().catch(error => toast(error.message || "挂断失败")));
