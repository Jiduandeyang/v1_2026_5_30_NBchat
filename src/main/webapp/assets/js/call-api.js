(function () {
    const emptyBody = {};

    window.CallApi = {
        iceServers() {
            return ChatApi.get("/voice/ice-servers");
        },
        start(peerId, callMode, conversationId) {
            return ChatApi.post(`/voice/calls/${peerId}`, {callMode, conversationId});
        },
        incoming() {
            return ChatApi.get("/voice/calls/incoming");
        },
        get(callId) {
            return ChatApi.get(`/voice/calls/${callId}`);
        },
        accept(callId) {
            return ChatApi.post(`/voice/calls/${callId}/accept`, emptyBody);
        },
        reject(callId) {
            return ChatApi.post(`/voice/calls/${callId}/reject`, emptyBody);
        },
        end(callId) {
            if (!callId) return Promise.resolve(null);
            return ChatApi.post(`/voice/calls/${callId}/end`, emptyBody);
        },
        endDuringUnload(callId) {
            if (!callId) return;
            const body = new Blob(["{}"], {type: "application/json"});
            navigator.sendBeacon?.(`api/voice/calls/${callId}/end`, body);
        }
    };
})();
