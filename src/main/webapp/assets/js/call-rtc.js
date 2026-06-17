(function () {
    let peer;
    let localStream;
    let remoteAudioStream;
    let remoteVideoStream;
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

    function addTrackOnce(stream, track) {
        if (!stream || !track || stream.getTracks().includes(track)) return;
        stream.addTrack(track);
    }

    function playMedia(element) {
        if (!element) return;
        element.play?.().catch(() => {
            const unlockButton = document.getElementById("unlockVoicePlaybackButton");
            if (unlockButton) unlockButton.hidden = false;
        });
    }

    function onceTrackCanPlay(track, callback) {
        if (!track || typeof callback !== "function") return;
        let reported = false;
        const report = () => {
            if (reported) return;
            reported = true;
            callback();
        };
        if (!track.muted && track.readyState === "live") {
            window.setTimeout(report, 0);
        }
        track.onunmute = report;
    }

    async function getLocalMedia(mode) {
        const wantsVideo = mode === "video";
        return navigator.mediaDevices.getUserMedia({
            audio: {echoCancellation: true, noiseSuppression: true, autoGainControl: true},
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

    async function createPeer(iceServers, elements, onStateChange, onRemoteMedia) {
        if (peer) return peer;
        const reportRemoteMedia = kind => onRemoteMedia?.(kind);
        peer = new RTCPeerConnection({
            iceServers,
            iceTransportPolicy: "all",
            bundlePolicy: "max-bundle",
            rtcpMuxPolicy: "require"
        });
        remoteAudioStream = new MediaStream();
        remoteVideoStream = new MediaStream();
        peer.onicecandidate = event => {
            if (event.candidate && signalSender) {
                signalSender("ice", event.candidate.toJSON());
            }
        };
        peer.ontrack = event => {
            if (event.track?.kind === "audio") {
                addTrackOnce(remoteAudioStream, event.track);
                const remoteAudio = ensureRemoteAudio(elements?.remoteAudio);
                remoteAudio.srcObject = remoteAudioStream;
                playMedia(remoteAudio);
                onceTrackCanPlay(event.track, () => reportRemoteMedia("audio"));
            }
            if (event.track?.kind === "video") {
                addTrackOnce(remoteVideoStream, event.track);
                const remoteVideo = elements?.remoteVideo;
                if (remoteVideo) {
                    remoteVideo.srcObject = remoteVideoStream;
                    remoteVideo.autoplay = true;
                    remoteVideo.playsInline = true;
                    remoteVideo.muted = true;
                    remoteVideo.setAttribute("playsinline", "");
                    remoteVideo.onloadedmetadata = () => playMedia(remoteVideo);
                    playMedia(remoteVideo);
                    onceTrackCanPlay(event.track, () => {
                        if ("requestVideoFrameCallback" in remoteVideo) {
                            remoteVideo.requestVideoFrameCallback(() => reportRemoteMedia("video"));
                        } else if (remoteVideo.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA) {
                            reportRemoteMedia("video");
                        } else {
                            remoteVideo.addEventListener("loadeddata", () => reportRemoteMedia("video"), {once: true});
                        }
                    });
                }
            }
        };
        peer.onconnectionstatechange = () => onStateChange?.(peer.connectionState);
        return peer;
    }

    async function prepare({mode, iceServers, elements, onSignal, onStateChange, onRemoteMedia}) {
        preparedMode = mode === "video" ? "video" : "audio";
        close(true);
        signalSender = onSignal;
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
        await createPeer(iceServers, elements, onStateChange, onRemoteMedia);
        await addOrReplaceTracks();
        return localStream;
    }

    async function unlockPlayback(elements) {
        playMedia(elements?.remoteVideo || document.getElementById("remoteVideoStream"));
        playMedia(elements?.remoteAudio || document.getElementById("remoteCallAudio"));
        const unlockButton = document.getElementById("unlockVoicePlaybackButton");
        if (unlockButton) unlockButton.hidden = true;
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
        remoteAudioStream = null;
        remoteVideoStream = null;
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
        unlockPlayback,
        close
    };
})();
