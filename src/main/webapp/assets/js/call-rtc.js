(function () {
    let peer;
    let localStream;
    let remoteStream;
    let signalSender;
    let pendingIceCandidates = [];
    let preparedMode = "audio";

    function setVideoElement(video, stream, muted) {
        if (!video) return;
        video.srcObject = stream;
        video.autoplay = true;
        video.playsInline = true;
        video.muted = muted;
        video.setAttribute("playsinline", "");
        video.play?.().catch(() => null);
    }

    function ensureRemoteAudio(remoteAudio) {
        let audio = remoteAudio || document.getElementById("remoteCallAudio");
        if (!audio) {
            audio = document.createElement("audio");
            audio.id = "remoteCallAudio";
            audio.hidden = true;
            document.body.appendChild(audio);
        }
        audio.autoplay = true;
        audio.playsInline = true;
        audio.muted = false;
        audio.volume = 1;
        audio.setAttribute("playsinline", "");
        return audio;
    }

    async function getLocalMedia(mode) {
        const wantsVideo = mode === "video";
        return navigator.mediaDevices.getUserMedia({
            audio: true,
            video: wantsVideo ? {width: {ideal: 960}, height: {ideal: 540}, facingMode: "user"} : false
        });
    }

    function addOrReplaceTracks() {
        if (!peer || !localStream) return;
        for (const track of localStream.getTracks()) {
            const sender = peer.getSenders().find(item => item.track?.kind === track.kind);
            if (sender) {
                sender.replaceTrack(track);
            } else {
                peer.addTrack(track, localStream);
            }
        }
    }

    async function flushPendingIceCandidates() {
        if (!peer?.remoteDescription) return;
        const candidates = pendingIceCandidates;
        pendingIceCandidates = [];
        for (const candidate of candidates) {
            await peer.addIceCandidate(candidate).catch(() => null);
        }
    }

    async function createPeer(iceServers, elements, onStateChange) {
        if (peer) return peer;
        peer = new RTCPeerConnection({
            iceServers,
            bundlePolicy: "max-bundle",
            rtcpMuxPolicy: "require"
        });
        remoteStream = new MediaStream();
        peer.onicecandidate = event => {
            if (event.candidate && signalSender) {
                signalSender("ice", event.candidate.toJSON());
            }
        };
        peer.ontrack = event => {
            const stream = event.streams[0] || remoteStream;
            if (!event.streams.length && event.track) {
                remoteStream.addTrack(event.track);
            }
            const remoteAudio = ensureRemoteAudio(elements?.remoteAudio);
            remoteAudio.srcObject = stream;
            remoteAudio.play().catch(() => null);
            if (preparedMode === "video") {
                const remoteVideo = elements?.remoteVideo;
                if (remoteVideo) {
                    remoteVideo.srcObject = stream;
                    remoteVideo.autoplay = true;
                    remoteVideo.playsInline = true;
                    remoteVideo.setAttribute("playsinline", "");
                    remoteVideo.play?.().catch(() => null);
                }
            }
        };
        peer.onconnectionstatechange = () => onStateChange?.(peer.connectionState);
        return peer;
    }

    async function prepare({mode, iceServers, elements, onSignal, onStateChange}) {
        preparedMode = mode === "video" ? "video" : "audio";
        signalSender = onSignal;
        close(true);
        localStream = await getLocalMedia(preparedMode);
        const localVideo = elements?.localVideo;
        if (localVideo) {
            localVideo.srcObject = localStream;
            localVideo.autoplay = true;
            localVideo.playsInline = true;
            localVideo.muted = true;
            localVideo.setAttribute("playsinline", "");
            localVideo.play?.().catch(() => null);
        }
        await createPeer(iceServers, elements, onStateChange);
        addOrReplaceTracks();
        return localStream;
    }

    async function createOffer() {
        if (!peer) throw new Error("Peer connection is not ready.");
        const offer = await peer.createOffer();
        await peer.setLocalDescription(offer);
        return peer.localDescription;
    }

    async function handleOffer(offer) {
        if (!peer) throw new Error("Peer connection is not ready.");
        await peer.setRemoteDescription(new RTCSessionDescription(offer));
        await flushPendingIceCandidates();
        const answer = await peer.createAnswer();
        await peer.setLocalDescription(answer);
        return peer.localDescription;
    }

    async function handleAnswer(answer) {
        if (!peer || peer.signalingState !== "have-local-offer") return;
        await peer.setRemoteDescription(new RTCSessionDescription(answer));
        await flushPendingIceCandidates();
    }

    async function addIceCandidate(candidate) {
        if (!peer || !candidate) return;
        const ice = new RTCIceCandidate(candidate);
        if (!peer.remoteDescription) {
            pendingIceCandidates.push(ice);
            return;
        }
        await peer.addIceCandidate(ice).catch(() => null);
    }

    function close(stopTracks = true) {
        if (peer) {
            peer.onicecandidate = null;
            peer.ontrack = null;
            peer.onconnectionstatechange = null;
            peer.close();
            peer = null;
        }
        if (stopTracks && localStream) {
            localStream.getTracks().forEach(track => track.stop());
        }
        localStream = null;
        remoteStream = null;
        signalSender = null;
        pendingIceCandidates = [];
    }

    window.CallRtc = {
        prepare,
        createOffer,
        handleOffer,
        handleAnswer,
        addIceCandidate,
        flushPendingIceCandidates,
        close
    };
})();
