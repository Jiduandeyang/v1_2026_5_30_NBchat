(function () {
    window.startPrivateVoiceCall = () => window.CallController?.startCall("audio");
    window.startPrivateVideoCall = () => window.CallController?.startCall("video");
    window.acceptVoiceCall = () => window.CallController?.acceptCall();
    window.rejectVoiceCall = () => window.CallController?.rejectCall();
    window.endVoiceCall = () => window.CallController?.endCall();
    window.CallController?.init();
})();
